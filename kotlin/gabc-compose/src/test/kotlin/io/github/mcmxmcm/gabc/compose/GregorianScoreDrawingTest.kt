package io.github.mcmxmcm.gabc.compose

import io.github.mcmxmcm.gabc.layout.EBGaramondMeasurer
import io.github.mcmxmcm.gabc.layout.GregorianGlyphKind
import io.github.mcmxmcm.gabc.layout.GregorianLayout
import io.github.mcmxmcm.gabc.layout.GregorianLayoutMetrics
import io.github.mcmxmcm.gabc.layout.GregorianPlacedGlyph
import io.github.mcmxmcm.gabc.layout.GregorianPlacedLyric
import io.github.mcmxmcm.gabc.layout.GregorianPlacedStroke
import io.github.mcmxmcm.gabc.layout.GregorianPoint
import io.github.mcmxmcm.gabc.layout.GregorianRect
import io.github.mcmxmcm.gabc.layout.GregorianSize
import io.github.mcmxmcm.gabc.layout.GregorianStaffLayout
import io.github.mcmxmcm.gabc.layout.GregorianStrokeKind
import io.github.mcmxmcm.gabc.parser.GregorianNoteShape
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// GregorianScoreDrawingTests.swift, for what runs on the JVM. Drawing the outlines, and drawing a canvas, are tested
// on a device (src/androidTest).
class GregorianScoreDrawingTest {
    @Test fun preparedDrawingBatchesStaticScoreOperationsWithoutMergingOverlaps() {
        val glyphs = listOf(
            GregorianPlacedGlyph(GregorianGlyphKind.Notehead(GregorianNoteShape.Punctum), GregorianRect.of(10f, 10f, 8f, 8f)),
            GregorianPlacedGlyph(GregorianGlyphKind.Notehead(GregorianNoteShape.Punctum), GregorianRect.of(30f, 10f, 8f, 8f)),
            GregorianPlacedGlyph(GregorianGlyphKind.Notehead(GregorianNoteShape.Punctum), GregorianRect.of(31f, 11f, 8f, 8f)),
        )
        val strokes = listOf(
            GregorianPlacedStroke(GregorianStrokeKind.Staff, GregorianPoint(0f, 8f), GregorianPoint(100f, 8f), lineWidth = 1f),
            GregorianPlacedStroke(GregorianStrokeKind.Staff, GregorianPoint(0f, 12f), GregorianPoint(100f, 12f), lineWidth = 1f),
            GregorianPlacedStroke(GregorianStrokeKind.Connector, GregorianPoint(30f, 10f), GregorianPoint(30f, 20f), lineWidth = 2f),
        )
        val layout = GregorianLayout(
            size = GregorianSize(100f, 40f),
            staffs = emptyList(),
            glyphs = glyphs,
            strokes = strokes,
            lyrics = emptyList(),
            events = emptyList(),
            neumes = emptyList(),
        )

        val drawing = GregorianScoreDrawing(layout)
        val tile = drawing.tiles.first()

        assertEquals(1, drawing.tiles.size)
        assertEquals(2, tile.strokeBatches.size)
        assertEquals(2, tile.glyphBatches.size)
    }

    @Test fun preparedDrawingUsesExactStaffSystemFramesAsRenderTiles() {
        val firstFrame = GregorianRect.of(0f, 0f, 100f, 40f)
        val secondFrame = GregorianRect.of(0f, 40f, 100f, 50f)
        val layout = GregorianLayout(
            size = GregorianSize(100f, 90f),
            staffs = listOf(
                GregorianStaffLayout(0, GregorianRect.of(10f, 10f, 80f, 12f), systemFrame = firstFrame, firstEventId = "first"),
                GregorianStaffLayout(1, GregorianRect.of(10f, 50f, 80f, 12f), systemFrame = secondFrame, firstEventId = "second"),
            ),
            glyphs = listOf(
                GregorianPlacedGlyph(GregorianGlyphKind.Notehead(GregorianNoteShape.Punctum), GregorianRect.of(20f, 12f, 8f, 8f)),
                GregorianPlacedGlyph(GregorianGlyphKind.Notehead(GregorianNoteShape.Punctum), GregorianRect.of(20f, 52f, 8f, 8f)),
            ),
            strokes = emptyList(),
            lyrics = listOf(
                GregorianPlacedLyric(text = "First", origin = GregorianPoint(20f, 25f), width = 30f, fontSize = 12f, neumeId = "first"),
                GregorianPlacedLyric(text = "Second", origin = GregorianPoint(20f, 70f), width = 40f, fontSize = 12f, neumeId = "second"),
            ),
            events = emptyList(),
            neumes = emptyList(),
        )

        val drawing = GregorianScoreDrawing(layout)

        assertEquals(listOf(firstFrame, secondFrame), drawing.tiles.map { it.frame })
        assertEquals(listOf(1, 1), drawing.tiles.map { it.lyrics.size })
        assertEquals(listOf(1, 1), drawing.tiles.map { it.glyphBatches.size })
    }

    @Test fun preparedScoreLaysTheScoreOutAtItsWidth() {
        val score = GregorianScoreParser.parse("name: Kyrie; %% (c4) Ky(f)ri(gh)e(h) e(f)le(g)i(h)son.(g.) (::)")
        val prepared = GregorianPreparedScore.prepare(score, width = 390f, measurer = EBGaramondMeasurer)

        assertEquals(390f, prepared.layout.size.width)
        assertEquals(score.eventIds, prepared.layout.events.map { it.eventId })
        assertEquals(prepared.layout.staffs.size, prepared.drawing.tiles.size)
    }

    @Test fun lyricScaleFollowsTheSystemFontSize() {
        assertEquals(1.0, GregorianLayoutMetrics.lyricScale(1f), 0.0)
        assertTrue(GregorianLayoutMetrics.lyricScale(0.85f) < 1)
        assertEquals(1.65, GregorianLayoutMetrics.lyricScale(2f), 0.0)
    }

    @Test fun eachNeumeIsReadWithTheNotesItDraws() {
        val score = GregorianScoreParser.parse("%% (c4) Be(gvv)ne(h)di(ghg)cá(h)mus(g) (::)")
        val layout = GregorianPreparedScore.prepare(score, width = 390f, measurer = EBGaramondMeasurer).layout
        assertEquals(
            listOf("Be, 2-note neume", "ne, one-note neume", "di, 3-note neume", "cá, one-note neume", "mus, one-note neume"),
            layout.neumes.map(::accessibilityLabel),
        )
    }
}
