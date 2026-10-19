package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle

/**
 * The face lyrics are measured in, and must be drawn in.
 *
 * The layout engine measures lyrics in the face named by [GregorianLayoutMetrics.lyricFontName], EB Garamond unless
 * another is named, through the [GregorianLyricMeasurer] it is given. An app that bundles its own copy of the face
 * measures with it; `gabc-lyric-font` carries EB Garamond and a measurer for Android.
 */
public object GregorianLyricFont {
    /** The PostScript name of EB Garamond's regular weight. */
    public const val DEFAULT_NAME: String = "EBGaramond-Regular"
}

/**
 * How wide a lyric is set, in points at its size, in the face named and the style given: italic for a preparatory
 * syllable, bold for an accented one. CoreText measures it in the Swift package; the platform's text measures it
 * here.
 */
public fun interface GregorianLyricMeasurer {
    public fun width(text: String, style: GregorianLyricStyle, fontSize: Float, fontName: String): Float
}
