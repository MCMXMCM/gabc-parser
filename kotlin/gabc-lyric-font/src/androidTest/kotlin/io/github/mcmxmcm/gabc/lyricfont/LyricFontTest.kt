package io.github.mcmxmcm.gabc.lyricfont

import android.content.Context
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import io.github.mcmxmcm.gabc.layout.GregorianEngravingLayoutEngine
import io.github.mcmxmcm.gabc.layout.GregorianLyricFont
import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// LyricFontTests.swift, for the font itself: Android finds a face by the file it's read from, not by its name.
class LyricFontTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun bundledEBGaramondIsReadAndMeasuresAsItself() {
        val face = GregorianLyricFont.typeface(context)
        assertNotEquals(Typeface.DEFAULT, face)
        val measurer = GregorianLyricFont.measurer(context)
        val garamond = measurer.width("Kyrie eleison", GregorianLyricStyle.Regular, 21f, GregorianLyricFont.DEFAULT_NAME)
        val serif = android.graphics.Paint().apply { typeface = Typeface.SERIF; textSize = 21f }.measureText("Kyrie eleison")
        assertTrue(garamond > 0)
        assertNotEquals(serif, garamond)
        // An accented syllable is set in the bold weight, and is wider.
        assertTrue(measurer.width("Kyrie", GregorianLyricStyle.Accented, 21f, GregorianLyricFont.DEFAULT_NAME) >
            measurer.width("Kyrie", GregorianLyricStyle.Regular, 21f, GregorianLyricFont.DEFAULT_NAME))
    }

    @Test fun lyricsAreLaidOutInTheBundledFace() {
        val score = GregorianScoreParser.parse("%% (c4) Cla(f)ma(g)vé(h)runt(i) iu(j)sti(i) (::)")
        val layout = GregorianEngravingLayoutEngine(GregorianLyricFont.measurer(context)).layout(score, 390f, openingLabel = "8g")
        assertTrue(layout.lyrics.isNotEmpty())
        assertTrue(layout.lyrics.all { it.fontName == GregorianLyricFont.DEFAULT_NAME && it.width > 0 })
        assertTrue(layout.initials.size == 1)
    }
}
