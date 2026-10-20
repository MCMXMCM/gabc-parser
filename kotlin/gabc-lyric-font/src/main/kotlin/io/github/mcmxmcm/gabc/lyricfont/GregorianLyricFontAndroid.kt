package io.github.mcmxmcm.gabc.lyricfont

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import io.github.mcmxmcm.gabc.layout.GregorianLyricFont
import io.github.mcmxmcm.gabc.layout.GregorianLyricMeasurer
import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle

/** The bundled EB Garamond, read from the library's assets once and kept. */
private object BundledFaces {
    /** The variable font the Swift package carries, its weight axis from 400 to 800. */
    const val ASSET = "EBGaramond-VariableFont_wght.ttf"

    @Volatile private var faces: Map<GregorianLyricStyle, Typeface>? = null

    fun face(context: Context, style: GregorianLyricStyle): Typeface {
        val known = faces ?: synchronized(this) {
            faces ?: load(context.applicationContext).also { faces = it }
        }
        return known.getValue(style)
    }

    private fun load(context: Context): Map<GregorianLyricStyle, Typeface> {
        fun weight(value: Int) = Typeface.Builder(context.assets, ASSET).setFontVariationSettings("'wght' $value").build()
        val regular = weight(400)
        // An accented syllable is set bold, as CoreText sets the face's bold trait in the Swift package. The font
        // has no italic: a preparatory syllable is slanted, which leaves it as wide as the roman.
        return mapOf(
            GregorianLyricStyle.Regular to regular,
            GregorianLyricStyle.Rubric to regular,
            GregorianLyricStyle.Accented to weight(700),
            GregorianLyricStyle.Preparatory to Typeface.create(regular, Typeface.ITALIC),
        )
    }
}

/**
 * The bundled EB Garamond in a lyric's style, to draw a lyric in where the app does not carry the face itself: the
 * layout measures lyrics in it by default ([GregorianLyricFont.DEFAULT_NAME]).
 */
public fun GregorianLyricFont.typeface(context: Context, style: GregorianLyricStyle = GregorianLyricStyle.Regular): Typeface =
    BundledFaces.face(context, style)

/**
 * A measurer of lyrics in the bundled EB Garamond, with Android's own text shaping, kerned as CoreText kerns them.
 * A measurer holds a paint for each style, sized as it is asked, so each thread that lays scores out takes one of
 * its own. Every font name is measured in EB Garamond: an app that names another face measures with its own.
 */
public fun GregorianLyricFont.measurer(context: Context): GregorianLyricMeasurer {
    // The application's context, which a measurer kept for long can hold without keeping an activity alive.
    val appContext = context.applicationContext
    val paints = HashMap<GregorianLyricStyle, Paint>()
    return GregorianLyricMeasurer { text, style, fontSize, _ ->
        val paint = paints.getOrPut(style) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = typeface(appContext, style) } }
        paint.textSize = fontSize
        paint.measureText(text)
    }
}
