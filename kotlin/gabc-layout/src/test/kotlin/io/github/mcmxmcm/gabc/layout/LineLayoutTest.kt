package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianDivision
import io.github.mcmxmcm.gabc.parser.GregorianLyricMark
import io.github.mcmxmcm.gabc.parser.GregorianNotationElement
import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The spaces between the notes and the staffs they are set on: the spaces the GABC writes between a syllable's neumes;
 * staffs justified at their words and bars, never within a word; and breaks taken where the chant books take them. A
 * port of the Swift package's Tests/GABCLayoutTests/LineLayoutTests.swift.
 */
class LineLayoutTest {
    /** One staff space at the default notation scale. */
    private val staffSpace = 12.5f

    private val widths = (240..820 step 5).map { it.toFloat() }

    @Test
    fun theSpacesWrittenBetweenASyllablesNeumesPartThem() {
        // `/` writes a quarter staff space, `//` and a space within the group half of one, and `!` none.
        val score = GregorianScoreParser.parse("%% (c4) A(f/g//h i!j)men.(h) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 760f)
        val syllable = score.neumes.first { it.lyric == "A" }
        val noteIds = score.neumes.filter { it.syllableId == syllable.syllableId }.flatMap { it.notes }.map { it.id }.toSet()
        val heads = layout.glyphs.filter { it.eventId in noteIds }.sortedBy { it.frame.left }
        assertEquals(5, heads.size)
        val gaps = heads.zipWithNext { previous, next -> next.frame.left - previous.frame.right }
        for ((gap, expected) in gaps.zip(listOf(0.25f, 0.5f, 0.5f, 0f))) {
            assertEquals(expected * staffSpace, gap, 0.01f)
        }
    }

    @Test
    fun eachStaffButTheLastIsJustifiedToItsEnd() {
        val score = GregorianScoreParser.parse(PSALM)
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            assertTrue("width $width", layout.staffs.size > 1)
            for (staff in layout.staffs.dropLast(1)) {
                val end = staff.frame.right
                // The words reach the staff's end, or the notes or a bar the room short of the custos that is theirs.
                val words = layout.lyrics
                    .filter { staff.systemFrame.top <= it.origin.y && it.origin.y < staff.systemFrame.bottom }
                    .map { it.origin.x + it.width - end }
                val notes = layout.neumes.filter { it.lineIndex == staff.index }.map { it.inkFrame.right - (end - 10) }
                val bars = layout.strokes
                    .filter { staff.frame.top <= it.start.y && it.start.y <= staff.frame.bottom }
                    .mapNotNull { stroke ->
                        val kind = stroke.kind as? GregorianStrokeKind.Division ?: return@mapNotNull null
                        stroke.start.x + (if (kind.division == GregorianDivision.Final) 4.5f else 6f) - (end - 10)
                    }
                val reach = (words + notes + bars).maxOrNull()
                assertNotNull(reach)
                assertEquals("width $width, staff ${staff.index}", 0f, reach!!, 1f)
            }
        }
    }

    @Test
    fun aBarStandsMidwayBetweenItsNotes() {
        // Words of one letter under single notes, so that no word holds a bar back.
        val body = (0 until 24).joinToString(" ") { index ->
            "${listOf("a", "e", "o")[index % 3]}(${listOf("f", "g", "h", "g")[index % 4]})" + if (index % 4 == 3) " (,)" else ""
        }
        val score = GregorianScoreParser.parse("%% (c4) $body (::)")
        var bars = 0
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            for (staff in layout.staffs) {
                val heads = layout.glyphs
                    .filter { it.eventId != null && staff.frame.top - 20 <= it.frame.top && it.frame.bottom <= staff.frame.bottom + 20 }
                    .map { it.frame }
                for (stroke in layout.strokes) {
                    val kind = stroke.kind as? GregorianStrokeKind.Division ?: continue
                    if (kind.division != GregorianDivision.Minima) continue
                    if (stroke.start.y < staff.frame.top || stroke.start.y > staff.frame.bottom) continue
                    val before = heads.filter { it.right < stroke.start.x }.maxOfOrNull { it.right } ?: continue
                    val after = heads.filter { it.left > stroke.start.x }.minOfOrNull { it.left } ?: continue
                    assertEquals("width $width, staff ${staff.index}", stroke.start.x - before, after - stroke.start.x, 0.5f)
                    bars += 1
                }
            }
        }
        assertTrue(bars > 0)
    }

    @Test
    fun theCustosStandsFlushWithTheStaffsEnd() {
        val score = GregorianScoreParser.parse(PSALM)
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        val custodes = layout.glyphs.filter { it.kind == GregorianGlyphKind.Custos }
        assertEquals(layout.staffs.size - 1, custodes.size)
        for (custos in custodes) {
            assertEquals(layout.staffs[0].frame.right, custos.frame.right, 0.01f)
        }
    }

    @Test
    fun rubricsStayWithTheWordsTheyHeadOrClose() {
        val score = GregorianScoreParser.parse(RESPONSORY)
        val marks: Map<String, GregorianLyricMark> = score.elements
            .mapNotNull { (it as? GregorianNotationElement.LyricMark)?.mark }
            .associateBy { it.id }
        assertEquals(4, marks.values.count { it.beginsWhatFollows })
        assertRubricsStayWithTheirWords(score, marks, writtenWithBars = true)

        // Asterisks written with the word after them, as a psalm's mediant is (`*~ab`): bound to it, and still never
        // beginning a staff.
        val mediants = GregorianScoreParser.parse(
            "%% (c4) " + List(6) { "læ(h)tí(h)ti(h)a(h) *~ab(h) in(h)i(h)mí(h)cis(h) me(g)is.(f.) (::)" }.joinToString(" "),
        )
        val asterisks = mediants.elements.mapNotNull { (it as? GregorianNotationElement.LyricMark)?.mark }.associateBy { it.id }
        assertEquals(6, asterisks.size)
        assertRubricsStayWithTheirWords(mediants, asterisks, writtenWithBars = false)
    }

    private fun assertRubricsStayWithTheirWords(score: GregorianScore, marks: Map<String, GregorianLyricMark>, writtenWithBars: Boolean) {
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            for (staff in layout.staffs) {
                val words = layout.lyrics
                    .filter { staff.systemFrame.top <= it.origin.y && it.origin.y < staff.systemFrame.bottom }
                    .sortedBy { it.origin.x }
                words.firstOrNull()?.let { first -> marks[first.neumeId] }?.let { mark ->
                    assertTrue("${mark.text} begins a staff at width $width", mark.beginsWhatFollows && !mark.text.startsWith("*"))
                }
                words.lastOrNull()?.let { last -> marks[last.neumeId] }?.let { mark ->
                    assertFalse("${mark.text} ends a staff at width $width", mark.beginsWhatFollows)
                }
                // Within a staff, a rubric that heads what follows stands after the bar written with it.
                if (!writtenWithBars) continue
                for (lyric in words.drop(1).filter { marks[it.neumeId]?.beginsWhatFollows == true }) {
                    val bar = layout.strokes.any { stroke ->
                        stroke.kind is GregorianStrokeKind.Division &&
                            staff.frame.top <= stroke.start.y && stroke.start.y <= staff.frame.bottom &&
                            stroke.start.x <= lyric.origin.x + 0.5f &&
                            words.all { it.origin.x >= lyric.origin.x || it.origin.x + it.width <= stroke.start.x + 0.5f }
                    }
                    assertTrue("${lyric.text} stands before its bar at width $width", bar)
                }
            }
        }
    }

    @Test
    fun anAccidentalStandsJustBeforeItsNoteOnTheSameStaff() {
        val body = (0 until 20).joinToString(" ") { index ->
            if (index % 3 == 1) "Ky(ixhi)" else "ri(${listOf("g", "h", "f")[index % 3]})"
        }
        val score = GregorianScoreParser.parse("%% (c4) $body (::)")
        var flats = 0
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            val heads = layout.glyphs.filter { it.eventId != null }
            for (flat in layout.glyphs.filter { it.kind == GregorianGlyphKind.Flat }) {
                // The nearest note after the flat, on its staff: about a quarter of a staff space after it.
                val next = heads
                    .filter { it.frame.left > flat.frame.left && abs(it.frame.center.y - flat.frame.center.y) < 2 * staffSpace }
                    .minByOrNull { it.frame.left }
                assertNotNull("a flat ends a staff at width $width", next)
                assertEquals("width $width", 0.27f * staffSpace, next!!.frame.left - flat.frame.right, 0.25f)
                flats += 1
            }
        }
        assertTrue(flats > 0)
    }

    @Test
    fun noStaffBeginsWithABar() {
        val score = GregorianScoreParser.parse(PSALM)
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            for (stroke in layout.strokes) {
                if (stroke.kind !is GregorianStrokeKind.Division) continue
                val staff = layout.staffs.firstOrNull { it.frame.top <= stroke.start.y && stroke.start.y <= it.frame.bottom } ?: continue
                assertTrue(
                    "a bar begins staff ${staff.index} at width $width",
                    layout.events.any { it.lineIndex == staff.index && it.frame.right < stroke.start.x },
                )
            }
        }
    }

    @Test
    fun aLongMelismaIsDividedOnlyWhereFourNotesStandOnEitherSide() {
        val score = GregorianScoreParser.parse(
            "%% (c4) Al(f)le(gh)lú(hjih/gh/ghg/hjih/ghgf/ghg/fgf/ghg/hih/gf/ghg/fe)ia.(gf) (::)",
        )
        val syllable = score.neumes.first { it.lyric == "lú" }
        val noteIds = score.neumes.filter { it.syllableId == syllable.syllableId }.flatMap { it.notes }.map { it.id }.toSet()
        var divided = 0
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            val lines = layout.events.filter { it.eventId in noteIds }.map { it.lineIndex }
            val counts = lines.groupingBy { it }.eachCount()
            if (counts.size <= 1) continue
            divided += 1
            for ((line, count) in counts) {
                if (line == lines.first() || line == lines.last()) {
                    assertTrue("width $width, staff $line", count >= 4)
                }
            }
        }
        assertTrue(divided > 0)
    }

    @Test
    fun theLastStaffHoldsMoreThanOneSyllable() {
        val score = GregorianScoreParser.parse(PSALM)
        for (width in widths) {
            val layout = GregorianEngravingLayoutEngine().layout(score, width = width)
            val last = layout.staffs.lastOrNull() ?: continue
            if (layout.staffs.size <= 1) continue
            val syllables = layout.neumes.count { it.lineIndex == last.index && it.lyric.isNotEmpty() }
            assertTrue("width $width", syllables > 1)
        }
    }

    private companion object {
        /** A psalm verse: short words under single notes, dotted cadences and bars, laid out at widths that make several staffs of it. */
        val PSALM = listOf(
            "%% (c3) Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
            "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
            "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
            "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
            "in(h) o(f)re(h) me(g)o.(f.) (::)",
        ).joinToString(" ")

        /** A short responsory: each rubric that heads a respond or a verse is written with the bar before it. */
        val RESPONSORY = listOf(
            "%% (c4)In(f) ma(f)nus(f) tu(fg)as(g) Dó(gf)mi(g)ne,(g.h.) *(;)",
            "Com(h')mén(f)do(gh) spí(g)ri(fd)tum(fg) me(g.)um.(f.) R/.(::)In(f) ma(f)nus...(f)",
            "V/.(::)Red(h)e(h)mí(ixhi)sti(h) nos(h) Dó(gh)mi(g)ne,(g'_) (,) De(h)us(g') ve(g)ri(gf)tá(g)tis.(g.h.)",
            "R/.(::)Com(h')mén(f)do(gh) spí(g)ri(fd)tum(fg) me(g.)um.(f.) V/.(::)In(f) ma(f)nus...(f) (::)",
        ).joinToString(" ")
    }
}
