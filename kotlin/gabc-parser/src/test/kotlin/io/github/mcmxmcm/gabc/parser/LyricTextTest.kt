package io.github.mcmxmcm.gabc.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A port of the Swift package's Tests/GABCParserTests/LyricTextTests.swift: how the words of a score are read, the
// rubrics written among them, the words each begins and ends, and the letters a score marks to be centred.
class LyricTextTest {
    private fun marks(score: GregorianScore): List<GregorianLyricMark> =
        score.elements.mapNotNull { (it as? GregorianNotationElement.LyricMark)?.mark }

    /** The lyrics and marks in the order written, marks in brackets. */
    private fun lyricSequence(score: GregorianScore): List<String> = score.elements.mapNotNull { element ->
        when (element) {
            is GregorianNotationElement.Neume -> element.neume.lyric.ifEmpty { null }
            is GregorianNotationElement.LyricMark -> "[${element.mark.text}]"
            else -> null
        }
    }

    @Test
    fun aRubricAndItsBarEndTheWordBeforeThem() {
        // A respond written straight after its sign and bar, with no space.
        val score = GregorianScoreParser.parse("%% (c4) Dó(h)mi(g)ne,(f.) R/.(::)In(f) ma(g)nus(h) (::)")
        assertEquals(listOf("Dó", "mi", "ne,", "[℟.]", "In", "ma", "nus"), lyricSequence(score))
        assertTrue(score.neumes.first { it.lyric == "In" }.startsWord)
        assertEquals("Dómine, ℟. In manus", score.lyricText)
    }

    @Test
    fun aClefChangeWithinAWordLeavesTheWordWhole() {
        val score = GregorianScoreParser.parse("%% (c4) Dó(f)mi(g)(c3)ne(h) (::)")
        assertEquals(listOf(true, false, false), score.neumes.map { it.startsWord })
        assertEquals("Dómine", score.lyricText)
    }

    @Test
    fun rubricsWrittenBeforeASungSyllableStandApartFromIt() {
        val versicle = GregorianScoreParser.parse("%% (c4) V/. Dó(h)mi(h)ne,(h) ex(h)áu(h)di(g) (::)")
        assertEquals(listOf("[℣.]", "Dó", "mi", "ne,", "ex", "áu", "di"), lyricSequence(versicle))
        assertEquals(listOf(GregorianLyricStyle.Rubric), marks(versicle).map { it.style })
        assertTrue(versicle.neumes.first().startsWord)
        assertEquals("℣. Dómine, exáudi", versicle.lyricText)

        val stanza = GregorianScoreParser.parse("%% (c4) sóm(g)ni(h)a,(h.) (::) 2. Pro(f)cul(g) (::)")
        assertEquals(listOf("sóm", "ni", "a,", "[2.]", "Pro", "cul"), lyricSequence(stanza))
        assertEquals(listOf(GregorianLyricStyle.Rubric), marks(stanza).map { it.style })
        assertTrue(stanza.neumes.first { it.lyric == "Pro" }.startsWord)

        // GABC's tilde is a space that no line break may take.
        val mediant = GregorianScoreParser.parse("%% (c4) læ(h)tí(h)ti(h)a(h) *~ab(h) in(h)i(h)mí(h)cis(h) (::)")
        assertEquals(listOf("læ", "tí", "ti", "a", "[*]", "ab", "in", "i", "mí", "cis"), lyricSequence(mediant))
        assertFalse(mediant.lyricText.contains("~"))
    }

    @Test
    fun rubricsWrittenAfterASungSyllableStandApartFromIt() {
        val flex = GregorianScoreParser.parse("%% (c3) Quó(h)ni(h)am hæc, †(h) et(h) ip(h)se(h) (::)")
        assertEquals(listOf("Quó", "ni", "am hæc,", "[†]", "et", "ip", "se"), lyricSequence(flex))
        assertEquals(listOf(GregorianLyricStyle.Rubric), marks(flex).map { it.style })
        assertTrue(flex.neumes.first { it.lyric == "et" }.startsWord)
    }

    @Test
    fun verseNumbersRepeatSignsAndCrossesAreRubrics() {
        val score = GregorianScoreParser.parse(
            "%% (c4) 2.() Pro(f) e(g) iij.(::) e(g) ij.(::) **() ✠() 12.() fi(h) (::)",
        )
        assertEquals(listOf("2.", "iij.", "ij.", "**", "✠", "12."), marks(score).map { it.text })
        assertEquals(List(6) { GregorianLyricStyle.Rubric }, marks(score).map { it.style })
    }

    @Test
    fun aSungSyllableMadeOnlyOfSignsIsLeftWhole() {
        val score = GregorianScoreParser.parse("%% (c4) *(h) 2.(g) (::)")
        assertEquals(listOf("*", "2."), score.neumes.map { it.lyric })
        assertTrue(marks(score).isEmpty())
    }
}
