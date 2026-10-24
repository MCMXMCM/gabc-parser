package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import io.github.mcmxmcm.gabc.parser.GregorianNeume
import io.github.mcmxmcm.gabc.parser.GregorianNotationElement
import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The words under the notes, set as the chant books set them: each sung vowel under its syllable's first note; the
 * syllables of a word either joined, or parted by one hyphen set just after the first; and words kept apart by more
 * than anything within a word. A port of the Swift package's Tests/GABCLayoutTests/LyricLayoutTests.swift.
 */
class LyricLayoutTest {
    @Test
    fun wordsAreJoinedOrPartedByOneHyphenAndKeptApart() {
        for (gabc in SCORES) {
            val score = GregorianScoreParser.parse(gabc)
            for (width in listOf(240f, 320f, 390f, 820f)) {
                assertLyricInvariants(score, width, "$gabc at width $width")
            }
        }
    }

    @Test
    fun theHyphenIsSetInTheLyricsTypeJustAfterItsSyllable() {
        val score = GregorianScoreParser.parse("name: hyphen; %% (c4) Sa(fgfh/hjhih)ul(g) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val first = layout.lyrics.first { it.text == "Sa" }
        val second = layout.lyrics.first { it.text == "ul" }
        val hyphen = layout.lyrics.firstOrNull { it.neumeId == "lyric-hyphen-${first.neumeId}-${second.neumeId}" }
        assertNotNull("A syllable parted from the next of its word by a melisma takes a hyphen.", hyphen)
        hyphen!!
        assertEquals("-", hyphen.text)
        assertEquals(first.fontSize, hyphen.fontSize)
        assertEquals(first.fontName, hyphen.fontName)
        assertEquals(first.origin.y, hyphen.origin.y)
        assertEquals(first.origin.x + first.width, hyphen.origin.x, 0.5f)
        assertEquals(1, layout.lyrics.count { it.text == "-" })
    }

    @Test
    fun noHyphenJoinsAWordToARubricOrAcrossOne() {
        val score = GregorianScoreParser.parse(SCORES[5])
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val marks = score.elements.mapNotNull { (it as? GregorianNotationElement.LyricMark)?.mark?.id }.toSet()
        for (hyphen in layout.lyrics.filter { it.text == "-" }) {
            assertFalse("${hyphen.neumeId} joins a rubric", marks.any { hyphen.neumeId.contains(it) })
        }
        val ne = score.neumes.first { it.lyric == "ne," }
        assertFalse(layout.lyrics.any { it.neumeId.startsWith("lyric-hyphen-${ne.id}-") })
    }

    @Test
    fun theSungVowelStandsOverTheSyllablesFirstNote() {
        // Each case: a score, the syllable, and the letters before its vowel and of its vowel.
        val cases = listOf(
            listOf("%% (c4) Dó(h)mi(g)ne(h) (::)", "Dó", "D", "ó"),
            listOf("%% (c4) Ky(fgh)ri(g)e(f) (::)", "Ky", "K", "y"),
            listOf("%% (c4) sǽ(h)cu(g)la(h) (::)", "sǽ", "s", "ǽ"),
            listOf("%% (c4) Is(h)ra(g)ël(h) (::)", "ël", "", "ë"),
            // A consonantal i or u is not the syllable's vowel.
            listOf("%% (c4) e(h)ius(g) (::)", "ius", "i", "u"),
            listOf("%% (c4) qui(h)a(g) (::)", "qui", "qu", "i"),
            listOf("%% (c4) san(h)guis(g) (::)", "guis", "gu", "i"),
            // A note that carries the end of one word and the beginning of the next is sung on the vowel of the next.
            listOf("%% (c4) e(h)t Dó(g)mi(h)nus(h) (::)", "t Dó", "t D", "ó"),
            // Braces name the letters to centre.
            listOf("%% (c4) Al(f)le(g)lú(h){ia}.(g) (::)", "ia.", "", "ia"),
        )
        for ((gabc, syllable, before, vowel) in cases) {
            val score = GregorianScoreParser.parse(gabc)
            val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
            val lyric = layout.lyrics.firstOrNull { it.text == syllable }
            assertNotNull("$syllable is not set as one lyric", lyric)
            lyric!!
            val neume = score.neumes.first { it.id == lyric.neumeId }
            val note = layout.events.first { it.eventId == neume.notes[0].id }
            val vowelCentre = lyric.origin.x + width(before, lyric.fontSize) + width(vowel, lyric.fontSize) / 2
            assertEquals("$syllable: its vowel is not under its first note", note.frame.center.x, vowelCentre, 0.75f)
        }
    }

    private sealed interface TextKind {
        data class Syllable(val neume: GregorianNeume) : TextKind
        data object Mark : TextKind
        data object Hyphen : TextKind
    }

    private class PlacedText(val lyric: GregorianPlacedLyric, val kind: TextKind) {
        val left: Float get() = lyric.origin.x
        val right: Float get() = lyric.origin.x + lyric.width
    }

    private fun assertLyricInvariants(score: GregorianScore, width: Float, context: String) {
        val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
        val neumes = score.neumes.associateBy { it.id }
        val texts = layout.lyrics.map { lyric ->
            when {
                lyric.neumeId.startsWith("lyric-hyphen-") -> PlacedText(lyric, TextKind.Hyphen)
                neumes[lyric.neumeId] != null -> PlacedText(lyric, TextKind.Syllable(neumes.getValue(lyric.neumeId)))
                else -> PlacedText(lyric, TextKind.Mark)
            }
        }
        val lines = texts.groupBy { (it.lyric.origin.y * 100).roundToInt() }.values.map { line -> line.sortedBy { it.left } }
        val fontSize = layout.lyrics.firstOrNull()?.fontSize ?: 21f
        val hyphenAdvance = width("-", fontSize)
        val wordSpace = max(hyphenAdvance, 0.5f * 12.5f)

        for (staff in lines) {
            for ((previous, next) in staff.zipWithNext()) {
                assertTrue(
                    "${previous.lyric.text} runs into ${next.lyric.text}: $context",
                    previous.right <= next.left + 0.5f,
                )
            }
            val words = staff.filter { it.kind != TextKind.Hyphen }
            for ((previous, next) in words.zipWithNext()) {
                val gap = next.left - previous.right
                val hyphens = staff.filter { it.kind == TextKind.Hyphen && it.left >= previous.left && it.left < next.left }
                val first = (previous.kind as? TextKind.Syllable)?.neume
                val second = (next.kind as? TextKind.Syllable)?.neume
                if (first != null && second != null && !second.startsWord) {
                    assertNotEquals(context, first.syllableId, second.syllableId)
                    assertTrue("${first.lyric}-${second.lyric} takes more than one hyphen: $context", hyphens.size <= 1)
                    val hyphen = hyphens.firstOrNull()
                    if (hyphen != null) {
                        assertEquals("the hyphen after ${first.lyric} stands away from it: $context", previous.right, hyphen.left, 0.5f)
                        assertTrue("${first.lyric}-${second.lyric} has no room for its hyphen: $context", gap >= hyphenAdvance - 0.5f)
                    } else {
                        assertTrue("${first.lyric} ${second.lyric} are parted with no hyphen: $context", abs(gap) <= 0.5f)
                    }
                } else {
                    assertTrue("a hyphen joins ${previous.lyric.text} to ${next.lyric.text}: $context", hyphens.isEmpty())
                    assertTrue(
                        "${previous.lyric.text} ${next.lyric.text} are too close for two words: $context",
                        gap >= wordSpace - 0.5f,
                    )
                }
            }
        }

        // A word divided at a staff's end carries a hyphen at that end.
        val lineOfNeume = layout.neumes.associate { it.id to it.lineIndex }
        var previous: GregorianNeume? = null
        var previousLyricWasMark = false
        for (element in score.elements) {
            when (element) {
                is GregorianNotationElement.LyricMark -> {
                    previousLyricWasMark = true
                    previous = null
                }
                is GregorianNotationElement.Neume -> {
                    val neume = element.neume
                    if (neume.lyric.isEmpty()) continue
                    val before = previous?.let { lineOfNeume[it.id] }
                    val after = lineOfNeume[neume.id]
                    if (previous != null && !previousLyricWasMark && !neume.startsWord && before != null && after != null &&
                        before != after
                    ) {
                        assertTrue(
                            "${previous.lyric} ends a staff with no hyphen: $context",
                            layout.lyrics.any { it.neumeId == "lyric-hyphen-${previous!!.id}-${neume.id}" },
                        )
                    }
                    previous = neume
                    previousLyricWasMark = false
                }
                else -> continue
            }
        }
        assertTrue(context, score.neumes.any { it.lyric.isNotEmpty() })
    }

    private fun width(text: String, size: Float): Float =
        EBGaramondMeasurer.width(text, GregorianLyricStyle.Regular, size, GregorianLyricFont.DEFAULT_NAME)

    private companion object {
        /**
         * Melismas within words, short syllables under wide neumes and long ones under single notes, rubrics, verse
         * numbers and bars, at widths narrow enough to carry words on to the next staff.
         */
        val SCORES = listOf(
            "name: preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
            "name: kyrie; %% (c4) Ky(f)ri(gfgh/ijh)e(hg) *() e(gh)lé(ixhi)i(h)son.(hg..) (::)",
            "name: alleluia; %% (c4) Al(f)le(gh)lú(hjihgh/ghg)ia.(gf) (::)",
            "name: antiphon; %% (c4) Be(f)ne(g)dí(h)ctus(h) Dó(g)mi(h)nus(f) De(g)us(h) Is(h)ra(g)ël,(f.) qui(g)a(h) vi(h)si(g)tá(h)vit(i) (;) et(h) fe(g)cit(h) re(g)dem(f)pti(g)ó(h)nem(g) ple(f)bis(g) su(h)æ.(g.) (::)",
            "name: hymn; %% (c4) Te(f) lu(g)cis(h) an(g)te(f) tér(g)mi(h)num,(h.) (,) Re(h)rum(i) Cre(h)á(g)tor,(f) pós(g)ci(h)mus,(h.) (;) 2.() Pro(f)cul(g) re(h)cé(g)dant(f) sóm(g)ni(h)a,(h.) (::)",
            "name: responsory; %% (c4) In(f) ma(g)nus(h) tu(h)as,(g) Dó(h)mi(g)ne,(f.) R/.(::)In(f) ma(g)nus(h) tu(h)as.(g.) (::)",
            "name: verse; %% (c3) Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,) in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
            "name: wide; %% (c4) San(h)ctís(h)si(h)mæ(h) Tri(h)ni(h)tá(h)tis(h) sǽ(h)cu(h)la(h) sæ(h)cu(h)ló(h)rum.(h.) (::)",
            "name: melisma; %% (c4) Sa(fgfh/hjhih)ul(g) A(f)men.(f.) (::)",
        )
    }
}
