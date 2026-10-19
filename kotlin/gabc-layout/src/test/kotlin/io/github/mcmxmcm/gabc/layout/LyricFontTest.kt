package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.io.File

// The Swift tests first register the bundled EB Garamond (`GregorianLyricFont.registerBundledFont()`, in setUp and
// as an assertion of its own). Registering the face, and finding it by name through the platform, is the Android
// font library's (gabc-lyric-font) and has no counterpart on the JVM: here the face is read from its file, as the
// measuring fixture reads it.
class LyricFontTest {
    @Test
    fun bundledEBGaramondCanBeFoundByName() {
        val path = checkNotNull(System.getProperty("gabc.lyricFont")) { "gabc.lyricFont names the bundled face" }
        val font = Font.createFont(Font.TRUETYPE_FONT, File(path)).deriveFont(21f)
        // CoreText finds the face by the default name; here the face must be the one the default name names.
        assertEquals(GregorianLyricFont.DEFAULT_NAME, font.psName)
        assertEquals("EBGaramond-Regular", font.psName)
        assertEquals("EB Garamond", font.family)
    }

    @Test
    fun lyricsAndInitialsNameTheFontTheyWereMeasuredIn() {
        val score = GregorianScoreParser.parse("%% (c4) Cla(f)ma(g)vé(h)runt(i) iu(j)sti(i) (::)")
        // The Swift engine measures with CoreText, in whatever face the metrics name; the fixture's engine measures
        // every lyric in EB Garamond, whatever is named. This one measures each face as itself, as CoreText does.
        val engine = GregorianEngravingLayoutEngine(NamedFaceMeasurer)
        val garamond = engine.layout(score, width = 390f, openingLabel = "8g")
        assertFalse(garamond.lyrics.isEmpty())
        assertEquals(1, garamond.initials.size)
        assertTrue(garamond.lyrics.all { it.fontName == "EBGaramond-Regular" })
        assertTrue(garamond.initials.all { it.fontName == "EBGaramond-Regular" })

        val georgia = engine.layout(
            score,
            width = 390f,
            metrics = GregorianLayoutMetrics(lyricFontName = "Georgia"),
            openingLabel = "8g",
        )
        assertTrue(georgia.lyrics.all { it.fontName == "Georgia" })
        assertTrue(georgia.initials.all { it.fontName == "Georgia" })
        // Each face is measured as itself.
        assertNotEquals(garamond.lyrics.map { it.width }, georgia.lyrics.map { it.width })
    }

    /**
     * Measures a lyric in the face named, as CoreText does for the Swift package's engine: EB Garamond as the fixture
     * measures it, any other face as the JDK finds it by name (a face the JDK cannot find falls back to its own
     * default, which is not EB Garamond either), with the fixture's kerning, ligatures, bold and slant.
     */
    private object NamedFaceMeasurer : GregorianLyricMeasurer {
        private val context = FontRenderContext(null, true, true)

        override fun width(text: String, style: GregorianLyricStyle, fontSize: Float, fontName: String): Float {
            if (fontName == GregorianLyricFont.DEFAULT_NAME) return EBGaramondMeasurer.width(text, style, fontSize, fontName)
            if (text.isEmpty()) return 0f
            val attributes = mapOf(
                TextAttribute.SIZE to fontSize,
                TextAttribute.KERNING to TextAttribute.KERNING_ON,
                TextAttribute.LIGATURES to TextAttribute.LIGATURES_ON,
                TextAttribute.WEIGHT to if (style == GregorianLyricStyle.Accented) TextAttribute.WEIGHT_BOLD else TextAttribute.WEIGHT_REGULAR,
                TextAttribute.POSTURE to if (style == GregorianLyricStyle.Preparatory) TextAttribute.POSTURE_OBLIQUE else TextAttribute.POSTURE_REGULAR,
            )
            return TextLayout(text, Font(fontName, Font.PLAIN, 1).deriveFont(attributes), context).advance
        }
    }
}
