package io.github.mcmxmcm.gabc.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class GlyphPathTest {
    @Test
    fun everyCatalogGlyphHasAnOutline() {
        for (name in GregorianGlyphName.entries.filter { it != GregorianGlyphName.None }) {
            val bounds = boundingBoxOfPath(GregorianGlyphCatalog.definition(name).paths)
            // A path with no points has the null rectangle for its box, which is empty.
            assertFalse(name.name, bounds == null || bounds.isEmpty)
        }
    }

    @Test
    fun placedOutlineMapsTheGlyphsBoundsOntoItsFrame() {
        // The catalog's bounds are those of the notehead: a stem (the podatus's, say) may reach beyond them, and
        // beyond the frame.
        val frame = GregorianRect.of(12.5f, 40f, 6.25f, 7.5f)
        for (name in GregorianGlyphName.entries.filter { it != GregorianGlyphName.None }) {
            val definition = GregorianGlyphCatalog.definition(name)
            // The accents have no width in the catalog, and are not drawn.
            if (!(definition.bounds.width > 0 && definition.bounds.height > 0)) {
                // A CGPath is empty when it holds no elements: here, no commands.
                assertTrue(
                    name.name,
                    GregorianPlacedGlyph(GregorianGlyphKind.Catalog(name), frame).path.all { it.isEmpty() },
                )
                continue
            }
            val outline = checkNotNull(boundingBoxOfPath(definition.paths)) { name.name }
            val scaleX = frame.width / definition.bounds.width
            val scaleY = frame.height / definition.bounds.height
            val expected = GregorianRect.of(
                frame.left + (outline.left + definition.origin.x) * scaleX,
                frame.top + (outline.top + definition.origin.y) * scaleY,
                outline.width * scaleX,
                outline.height * scaleY,
            )

            val placed = checkNotNull(
                boundingBoxOfPath(GregorianPlacedGlyph(GregorianGlyphKind.Catalog(name), frame).path),
            ) { name.name }
            assertEquals(name.name, expected.left, placed.left, 0.0001f)
            assertEquals(name.name, expected.top, placed.top, 0.0001f)
            assertEquals(name.name, expected.width, placed.width, 0.0001f)
            assertEquals(name.name, expected.height, placed.height, 0.0001f)
        }
    }

    /**
     * The box of a path as CGPath's `boundingBoxOfPath` measures it: every point the outline passes through, the
     * extremes of its curves included, and not their control points. Null for a path with no points, where
     * CoreGraphics gives the null rectangle. Measured in Double, as CoreGraphics measures in CGFloat.
     */
    private fun boundingBoxOfPath(path: List<List<GregorianPathCommand>>): GregorianRect? {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun include(x: Double, y: Double) {
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
        fun include(point: GregorianPoint) = include(point.x.toDouble(), point.y.toDouble())

        for (commands in path) {
            // Each outline is a path of its own, added to the glyph's, as the Swift package builds it.
            var current: GregorianPoint? = null
            var subpathStart: GregorianPoint? = null
            for (command in commands) {
                when (command) {
                    is GregorianPathCommand.Move -> {
                        include(command.point)
                        current = command.point
                        subpathStart = command.point
                    }
                    is GregorianPathCommand.Line -> {
                        // CoreGraphics ignores a segment with no current point.
                        if (current == null) continue
                        include(command.point)
                        current = command.point
                    }
                    is GregorianPathCommand.Quadratic -> {
                        val start = current ?: continue
                        include(command.end)
                        val xs = doubleArrayOf(start.x.toDouble(), command.control.x.toDouble(), command.end.x.toDouble())
                        val ys = doubleArrayOf(start.y.toDouble(), command.control.y.toDouble(), command.end.y.toDouble())
                        for (t in quadraticExtremes(xs) + quadraticExtremes(ys)) {
                            include(quadratic(xs, t), quadratic(ys, t))
                        }
                        current = command.end
                    }
                    is GregorianPathCommand.Cubic -> {
                        val start = current ?: continue
                        include(command.end)
                        val xs = doubleArrayOf(
                            start.x.toDouble(),
                            command.control1.x.toDouble(),
                            command.control2.x.toDouble(),
                            command.end.x.toDouble(),
                        )
                        val ys = doubleArrayOf(
                            start.y.toDouble(),
                            command.control1.y.toDouble(),
                            command.control2.y.toDouble(),
                            command.end.y.toDouble(),
                        )
                        for (t in cubicExtremes(xs) + cubicExtremes(ys)) {
                            include(cubic(xs, t), cubic(ys, t))
                        }
                        current = command.end
                    }
                    GregorianPathCommand.Close -> current = subpathStart
                }
            }
        }
        if (minX > maxX || minY > maxY) return null
        return GregorianRect(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
    }

    /** Where, strictly between its ends, a quadratic Bézier coordinate turns. */
    private fun quadraticExtremes(p: DoubleArray): List<Double> {
        val denominator = p[0] - 2 * p[1] + p[2]
        if (denominator == 0.0) return emptyList()
        return listOf((p[0] - p[1]) / denominator).filter { it > 0 && it < 1 }
    }

    /** Where, strictly between its ends, a cubic Bézier coordinate turns: the roots of its derivative. */
    private fun cubicExtremes(p: DoubleArray): List<Double> {
        val a = -p[0] + 3 * p[1] - 3 * p[2] + p[3]
        val b = 2 * (p[0] - 2 * p[1] + p[2])
        val c = p[1] - p[0]
        val roots = if (a == 0.0) {
            if (b == 0.0) emptyList() else listOf(-c / b)
        } else {
            val discriminant = b * b - 4 * a * c
            if (discriminant < 0) {
                emptyList()
            } else {
                // The stable form of the quadratic formula.
                val q = -0.5 * (b + Math.copySign(sqrt(discriminant), b))
                if (q == 0.0) listOf(0.0) else listOf(q / a, c / q)
            }
        }
        return roots.filter { it > 0 && it < 1 }
    }

    private fun quadratic(p: DoubleArray, t: Double): Double {
        val u = 1 - t
        return u * u * p[0] + 2 * u * t * p[1] + t * t * p[2]
    }

    private fun cubic(p: DoubleArray, t: Double): Double {
        val u = 1 - t
        return u * u * u * p[0] + 3 * u * u * t * p[1] + 3 * u * t * t * p[2] + t * t * t * p[3]
    }
}
