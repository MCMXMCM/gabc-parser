package io.github.mcmxmcm.gabc.layout

/**
 * How a placed glyph's outlines are scaled from the catalog's units into its frame, then moved to it: a point
 * (x, y) of the outlines is drawn at (x * scaleX + translateX, y * scaleY + translateY).
 */
public data class GregorianGlyphTransform(val scaleX: Float, val scaleY: Float, val translateX: Float, val translateY: Float) {
    public fun apply(point: GregorianPoint): GregorianPoint = GregorianPoint(point.x * scaleX + translateX, point.y * scaleY + translateY)
}

/** The transform that sets the glyph's outlines in its frame; null for a glyph that has no extent to draw. */
public val GregorianPlacedGlyph.transform: GregorianGlyphTransform?
    get() {
        val definition = GregorianGlyphCatalog.definition(kind.catalogName)
        if (definition.bounds.width <= 0f || definition.bounds.height <= 0f) return null
        // The outlines' box begins at minus the origin; it's scaled to the frame, then set at it.
        val scaleX = frame.width / definition.bounds.width
        val scaleY = frame.height / definition.bounds.height
        return GregorianGlyphTransform(
            scaleX = scaleX,
            scaleY = scaleY,
            translateX = frame.left + definition.origin.x * scaleX,
            translateY = frame.top + definition.origin.y * scaleY,
        )
    }

/**
 * The glyph's outlines, scaled into its frame, in layout coordinates: a display list for each. Fill them together
 * with the even-odd rule: some glyphs need it.
 */
public val GregorianPlacedGlyph.path: List<List<GregorianPathCommand>>
    get() {
        val transform = transform ?: return emptyList()
        val definition = GregorianGlyphCatalog.definition(kind.catalogName)
        return definition.paths.map { commands ->
            commands.map { command ->
                when (command) {
                    is GregorianPathCommand.Move -> GregorianPathCommand.Move(transform.apply(command.point))
                    is GregorianPathCommand.Line -> GregorianPathCommand.Line(transform.apply(command.point))
                    is GregorianPathCommand.Quadratic ->
                        GregorianPathCommand.Quadratic(transform.apply(command.control), transform.apply(command.end))
                    is GregorianPathCommand.Cubic -> GregorianPathCommand.Cubic(
                        transform.apply(command.control1),
                        transform.apply(command.control2),
                        transform.apply(command.end),
                    )
                    GregorianPathCommand.Close -> GregorianPathCommand.Close
                }
            }
        }
    }
