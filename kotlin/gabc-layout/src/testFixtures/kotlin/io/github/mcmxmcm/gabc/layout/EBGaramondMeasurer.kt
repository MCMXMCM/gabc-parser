package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.io.File

/**
 * Measures lyrics in the EB Garamond the Swift package carries (`gabc.lyricFont`), with the JDK's own text layout:
 * kerned, with ligatures and fractional advances, as CoreText measures them for the Swift package's tests. A bold
 * syllable is the face's bold weight, as near as the JDK draws one; an italic is slanted.
 */
public object EBGaramondMeasurer : GregorianLyricMeasurer {
    private val face: Font by lazy {
        val path = checkNotNull(System.getProperty("gabc.lyricFont")) { "gabc.lyricFont names the font to measure lyrics in" }
        Font.createFont(Font.TRUETYPE_FONT, File(path))
    }
    private val context = FontRenderContext(null, true, true)

    override fun width(text: String, style: GregorianLyricStyle, fontSize: Float, fontName: String): Float {
        if (text.isEmpty()) return 0f
        val attributes = mapOf(
            TextAttribute.SIZE to fontSize,
            TextAttribute.KERNING to TextAttribute.KERNING_ON,
            TextAttribute.LIGATURES to TextAttribute.LIGATURES_ON,
            TextAttribute.WEIGHT to if (style == GregorianLyricStyle.Accented) TextAttribute.WEIGHT_BOLD else TextAttribute.WEIGHT_REGULAR,
            TextAttribute.POSTURE to if (style == GregorianLyricStyle.Preparatory) TextAttribute.POSTURE_OBLIQUE else TextAttribute.POSTURE_REGULAR,
        )
        return TextLayout(text, face.deriveFont(attributes), context).advance
    }
}

/** A layout engine that measures lyrics in EB Garamond, as the Swift package's engine does by default. */
public fun GregorianEngravingLayoutEngine(): GregorianEngravingLayoutEngine = GregorianEngravingLayoutEngine(EBGaramondMeasurer)
