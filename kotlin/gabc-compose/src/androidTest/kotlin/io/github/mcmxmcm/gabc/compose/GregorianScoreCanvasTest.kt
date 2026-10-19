package io.github.mcmxmcm.gabc.compose

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import io.github.mcmxmcm.gabc.layout.GregorianGlyphKind
import io.github.mcmxmcm.gabc.layout.GregorianGlyphName
import io.github.mcmxmcm.gabc.layout.GregorianPathCommand
import io.github.mcmxmcm.gabc.layout.GregorianPlacedGlyph
import io.github.mcmxmcm.gabc.layout.GregorianRect
import io.github.mcmxmcm.gabc.layout.path
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// GregorianScoreDrawingTests.swift and GregorianScoreViewTests.swift, for what needs a device to draw.
class GregorianScoreCanvasTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun canvasDrawsInkAtItsLaidOutSize() {
        val score = GregorianScoreParser.parse("name: Kyrie; %% (c4) Ky(f)ri(gh)e(h) e(f)le(g)i(h)son.(g.) (::)")
        val prepared = GregorianPreparedScore.prepare(context, score, width = 390f)
        compose.setContent {
            GregorianScoreStyleProvider(GregorianScoreStyle(ink = Color.Black, rubric = Color.Red, highlight = Color.Red)) {
                GregorianScoreCanvas(prepared, Modifier.testTag("score"), highlightedEventId = score.eventIds[1])
            }
        }
        compose.waitForIdle()

        val bounds = compose.onNodeWithTag("score").getUnclippedBoundsInRoot()
        assertEquals(prepared.layout.size.width, bounds.width.value, 1f)
        assertEquals(prepared.layout.size.height, bounds.height.value, 1f)

        val pixels = compose.onNodeWithTag("score").captureToImage().toPixelMap()
        var dark = 0
        var red = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.alpha < 0.8f) continue
                if (color.red < 0.24f && color.green < 0.24f && color.blue < 0.24f) {
                    dark += 1
                } else if (color.red > 0.7f && color.green < 0.32f && color.blue < 0.32f) {
                    red += 1
                }
            }
        }
        assertTrue("staff lines, neumes and lyrics: $dark", dark > 200)
        assertTrue("the highlighted neume: $red", red > 10)
    }

    @Test fun scoreViewTakesTheHeightOfItsStaffsAtTheWidthItIsOffered() {
        val gabc = "%% (c4) " + List(6) { "Al(f)le(gh)lú(h)ia.(g.) (;)" }.joinToString(" ") + " (::)"
        val expected = GregorianPreparedScore.prepare(context, GregorianScoreParser.parse(gabc), width = 320f).layout
        assertTrue(expected.staffs.size > 1)

        compose.setContent {
            Box(Modifier.width(320.dp).testTag("view")) { GregorianScoreView(gabc) }
        }
        compose.waitForIdle()
        assertEquals(expected.size.height, compose.onNodeWithTag("view").getUnclippedBoundsInRoot().height.value, 0.5f)
    }

    @Test fun glyphLibraryDrawsEachOutlineAsTheLayoutDescribesIt() {
        for (name in GregorianGlyphName.entries) {
            val glyph = GregorianPlacedGlyph(GregorianGlyphKind.Catalog(name), GregorianRect.of(12.5f, 30.25f, 6.25f, 7.75f))
            val drawn = StringBuilder()
            val drawnPoints = mutableListOf<Pair<Float, Float>>()
            for (segment in GregorianGlyphLibrary.path(glyph)) {
                val points = segment.points
                when (segment.type) {
                    PathSegment.Type.Move -> { drawn.append('m'); drawnPoints += points[0] to points[1] }
                    PathSegment.Type.Line -> { drawn.append('l'); drawnPoints += points[2] to points[3] }
                    PathSegment.Type.Quadratic -> {
                        drawn.append('q')
                        drawnPoints += points[2] to points[3]
                        drawnPoints += points[4] to points[5]
                    }
                    PathSegment.Type.Cubic -> {
                        drawn.append('c')
                        drawnPoints += points[2] to points[3]
                        drawnPoints += points[4] to points[5]
                        drawnPoints += points[6] to points[7]
                    }
                    PathSegment.Type.Close -> drawn.append('z')
                    else -> Unit
                }
            }
            val described = StringBuilder()
            val describedPoints = mutableListOf<Pair<Float, Float>>()
            for (commands in glyph.path) {
                for (command in commands) {
                    when (command) {
                        is GregorianPathCommand.Move -> { described.append('m'); describedPoints += command.point.x to command.point.y }
                        is GregorianPathCommand.Line -> { described.append('l'); describedPoints += command.point.x to command.point.y }
                        is GregorianPathCommand.Quadratic -> {
                            described.append('q')
                            describedPoints += command.control.x to command.control.y
                            describedPoints += command.end.x to command.end.y
                        }
                        is GregorianPathCommand.Cubic -> {
                            described.append('c')
                            describedPoints += command.control1.x to command.control1.y
                            describedPoints += command.control2.x to command.control2.y
                            describedPoints += command.end.x to command.end.y
                        }
                        GregorianPathCommand.Close -> described.append('z')
                    }
                }
            }
            assertEquals(name.name, described.toString(), drawn.toString())
            assertEquals(name.name, describedPoints.size, drawnPoints.size)
            for ((lhs, rhs) in drawnPoints.zip(describedPoints)) {
                assertEquals(name.name, rhs.first, lhs.first, 0.001f)
                assertEquals(name.name, rhs.second, lhs.second, 0.001f)
            }
        }
    }
}
