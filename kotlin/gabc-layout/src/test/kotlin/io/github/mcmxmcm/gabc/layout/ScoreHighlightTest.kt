package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianEvent
import io.github.mcmxmcm.gabc.parser.GregorianEventTimeline
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreHighlightTest {
    @Test
    fun tapResolvesToTheNeumeWhoseTargetHoldsIt() {
        val target = GregorianNeumePlacement(
            id = "event-1",
            eventIds = listOf("event-1"),
            lyric = "In",
            inkFrame = GregorianRect.of(24f, 20f, 12f, 18f),
            hitFrame = GregorianRect.of(16f, 8f, 44f, 44f),
            lineIndex = 0,
        )
        val layout = GregorianLayout(
            size = GregorianSize(390f, 100f),
            staffs = emptyList(),
            glyphs = emptyList(),
            strokes = emptyList(),
            lyrics = emptyList(),
            events = emptyList(),
            neumes = listOf(target),
        )

        assertEquals(target.id, layout.neumeAt(GregorianPoint(30f, 30f))?.id)
        assertNull(layout.neumeAt(GregorianPoint(300f, 80f)))
    }

    @Test
    fun highlightIncludesWholeNeumeAndCorrespondingSyllable() {
        val firstSyllableNeume = GregorianNeumePlacement(
            id = "event-1",
            eventIds = listOf("event-1"),
            lyric = "Ky",
            inkFrame = GregorianRect.of(10f, 10f, 10f, 10f),
            hitFrame = GregorianRect.of(5f, 5f, 20f, 20f),
            lineIndex = 0,
        )
        val activeNeume = GregorianNeumePlacement(
            id = "event-2",
            eventIds = listOf("event-2", "event-3"),
            lyric = "",
            inkFrame = GregorianRect.of(30f, 10f, 16f, 10f),
            hitFrame = GregorianRect.of(25f, 5f, 26f, 20f),
            lineIndex = 0,
        )
        val nextSyllableNeume = GregorianNeumePlacement(
            id = "event-4",
            eventIds = listOf("event-4"),
            lyric = "ri",
            inkFrame = GregorianRect.of(60f, 10f, 10f, 10f),
            hitFrame = GregorianRect.of(55f, 5f, 20f, 20f),
            lineIndex = 0,
        )
        val layout = GregorianLayout(
            size = GregorianSize(100f, 40f),
            staffs = emptyList(),
            glyphs = emptyList(),
            strokes = emptyList(),
            lyrics = emptyList(),
            events = emptyList(),
            neumes = listOf(
                firstSyllableNeume,
                activeNeume,
                nextSyllableNeume,
            ),
        )
        val timeline = GregorianEventTimeline(
            listOf(
                GregorianEvent(id = "event-1", syllableId = "kyrie-ky", relativePitch = 0),
                GregorianEvent(id = "event-2", syllableId = "kyrie-ky", relativePitch = 1),
                GregorianEvent(id = "event-3", syllableId = "kyrie-ky", relativePitch = 2),
                GregorianEvent(id = "event-4", syllableId = "kyrie-ri", relativePitch = 1),
            ),
        )

        val highlight = unwrap(
            GregorianScoreHighlight.of("event-2", timeline, layout),
        )

        assertEquals(activeNeume.id, highlight.neume.id)
        assertEquals(setOf("event-2", "event-3"), highlight.eventIds)
        assertEquals(setOf("event-1", "event-2"), highlight.syllableNeumeIds)
    }

    @Test
    fun highlightTakesSyllablesFromTheParsedScore() {
        // One group, split into two neumes by `!`, is one syllable.
        val score = GregorianScoreParser.parse("%% (c4) Ky(fg!hi)ri(g) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)

        val highlight = unwrap(
            GregorianScoreHighlight.of("note-3", score, layout),
        )

        assertEquals("note-2", highlight.neume.id)
        assertEquals(setOf("note-2", "note-3"), highlight.eventIds)
        assertEquals(setOf("note-0", "note-2"), highlight.syllableNeumeIds)
        assertNull(GregorianScoreHighlight.of("missing", score, layout))
    }

    /** As XCTUnwrap: the test fails where the value is null. */
    private fun <T : Any> unwrap(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
