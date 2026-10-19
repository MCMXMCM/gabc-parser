package io.github.mcmxmcm.gabc.parser

import org.junit.Assert.assertEquals
import org.junit.Test

// Kotlin's own: offsets count characters as Swift's String counts them, so a score has the same source ranges, and
// its lyric marks the same identifiers, in both packages.
class SourceOffsetTest {
    @Test fun aCarriageReturnAndLineFeedAreOneCharacter() {
        val score = GregorianScoreParser.parse("name: x; %% (c3)\r\nHó(h) *()Dó(g) (::)")
        assertEquals(GregorianSourceRange(9, 10), score.neumes.first().notes.first().sourceRange)
        val mark = score.elements.filterIsInstance<GregorianNotationElement.LyricMark>().single().mark
        assertEquals("lyric-mark-11-13", mark.id)
    }

    @Test fun anAccentWrittenAfterItsLetterIsOneCharacterWithIt() {
        // "ó" as o and a combining acute.
        val score = GregorianScoreParser.parse("%% (c3) Hó(h) *()Dó(g) (::)")
        assertEquals(GregorianSourceRange(9, 10), score.neumes.first().notes.first().sourceRange)
        assertEquals(
            GregorianScoreParserError.UnsupportedToken("?", 10),
            runCatching { GregorianScoreParser.parse("%% (c3) Hó(h?) (::)") }.exceptionOrNull(),
        )
    }
}
