package io.github.mcmxmcm.gabc.compose

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import io.github.mcmxmcm.gabc.layout.GregorianGlyphCatalog
import io.github.mcmxmcm.gabc.layout.GregorianGlyphName
import io.github.mcmxmcm.gabc.layout.GregorianPathCommand
import io.github.mcmxmcm.gabc.layout.GregorianPlacedGlyph
import io.github.mcmxmcm.gabc.layout.transform
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts the generated, platform-neutral Exsurge display list into a Compose [Path]. There is no SVG parser, font
 * lookup, JavaScript or web view in the rendering path. Each glyph's outline is built once, then scaled into each
 * frame it is placed in.
 */
public object GregorianGlyphLibrary {
    private val outlines = ConcurrentHashMap<GregorianGlyphName, Path>()

    /** A placed glyph's outline in layout coordinates, filled with the even-odd rule, which some glyphs need. */
    public fun path(glyph: GregorianPlacedGlyph): Path {
        val result = Path().apply { fillType = PathFillType.EvenOdd }
        val transform = glyph.transform ?: return result
        result.addPath(outline(glyph.kind.catalogName))
        result.transform(
            Matrix().apply {
                translate(transform.translateX, transform.translateY)
                scale(transform.scaleX, transform.scaleY)
            },
        )
        return result
    }

    private fun outline(name: GregorianGlyphName): Path = outlines.getOrPut(name) {
        Path().apply {
            for (commands in GregorianGlyphCatalog.definition(name).paths) {
                for (command in commands) {
                    when (command) {
                        is GregorianPathCommand.Move -> moveTo(command.point.x, command.point.y)
                        is GregorianPathCommand.Line -> lineTo(command.point.x, command.point.y)
                        is GregorianPathCommand.Quadratic -> quadraticTo(command.control.x, command.control.y, command.end.x, command.end.y)
                        is GregorianPathCommand.Cubic -> cubicTo(
                            command.control1.x, command.control1.y,
                            command.control2.x, command.control2.y,
                            command.end.x, command.end.y,
                        )
                        GregorianPathCommand.Close -> close()
                    }
                }
            }
        }
    }
}
