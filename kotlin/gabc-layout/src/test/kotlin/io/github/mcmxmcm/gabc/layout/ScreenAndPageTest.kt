package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * The score as a screen shows it and a page would set it: the words no lower than the notes need. A port of the Swift
 * package's Tests/GABCLayoutTests/ScreenAndPageTests.swift.
 */
class ScreenAndPageTest {
    /** One step of the staff, at the default notation scale. */
    private val staffStep = 6.25f

    private fun layout(notation: String): GregorianLayout =
        GregorianEngravingLayoutEngine().layout(GregorianScoreParser.parse("%% (c4) a($notation) (::)"), width = 390f)

    /** The lowest ink of a layout's notes and signs, its staff lines aside. */
    private fun lowestInk(layout: GregorianLayout): Float {
        val glyphs = layout.glyphs.map { it.frame.bottom }
        val strokes = layout.strokes
            .filter { it.kind != GregorianStrokeKind.Staff }
            .map { max(it.start.y, it.end.y) + it.lineWidth / 2 }
        return (glyphs + strokes).maxOrNull() ?: 0f
    }

    @Test
    fun theWordsStandAsFarBelowTheLowestInkAsBelowANoteOnTheBottomLine() {
        // d is on the bottom line, c in the space below it and b on a ledger line; an ictus stands below its note, and
        // a clivis hangs from its stem.
        val bottomLine = layout("d")
        val words = bottomLine.lyrics.first().origin.y
        val clearance = words - lowestInk(bottomLine)
        for (notation in listOf("c", "b", "d'", "dc", "fc")) {
            val lower = layout(notation)
            assertEquals(notation, clearance, lower.lyrics.first().origin.y - lowestInk(lower), 0.01f)
        }
        // A note in the space below the staff lowers the words by a step, no more.
        assertEquals(staffStep, layout("c").lyrics.first().origin.y - words, 0.01f)

        // Notes higher on the staff leave the words where a note on the bottom line does.
        val high = layout("h")
        assertEquals(
            words - bottomLine.staffs.first().frame.bottom,
            high.lyrics.first().origin.y - high.staffs.first().frame.bottom,
            0.01f,
        )
    }

    @Test
    fun onAScreenTheStaffAndItsStrokesFallOnWholePixels() {
        // Stems, an episema, ledger lines above and below the staff, and bars of every kind, over several staffs.
        val score = GregorianScoreParser.parse(
            "%% (c4) Lau(f)da(fhv)te(hg/hiH'G) Dó(kl)mi(lk)num(k.) (,) om(ji)nes(hg_) gen(gf)tes:(fe..) (;) " +
                "lau(dc)da(cd)te(b) e(dfd)um(d.) (:) om(hj)nes(jk) pó(lm)pu(ml)li.(k) (::)",
        )
        val engine = GregorianEngravingLayoutEngine()
        for (scale in listOf(2f, 3f)) {
            val layout = engine.layout(score, width = 260f, metrics = GregorianLayoutMetrics(displayScale = scale.toDouble()))
            assertTrue(layout.staffs.size > 1)
            fun onGrid(points: Float, offset: Float = 0f, message: String) {
                val pixels = points * scale - offset
                assertEquals("${scale}x: $message", Math.round(pixels).toFloat(), pixels, 0.01f)
            }
            // A staff space of whole pixels, the nearest to 12.5 points, and every staff begun on the grid.
            val staffSpace = layout.staffs.first().frame.height / 3
            assertEquals(Math.round(12.5f * scale) / scale, staffSpace, 0.001f)
            for (staff in layout.staffs) onGrid(staff.systemFrame.top, message = "staff ${staff.index} begins")
            for (stroke in layout.strokes) {
                // Whole pixels wide, two at least, with its edges between pixels.
                val pixels = Math.round(stroke.lineWidth * scale)
                onGrid(stroke.lineWidth, message = "${stroke.kind} width")
                assertTrue(pixels >= 2)
                val offset = if (pixels % 2 == 0) 0f else 0.5f
                if (stroke.start.y == stroke.end.y) {
                    onGrid(stroke.start.y, offset, "${stroke.kind} across")
                } else if (stroke.start.x == stroke.end.x) {
                    onGrid(stroke.start.x, offset, "${stroke.kind} up and down")
                }
            }
            // Stems and joins one weight: a point, or as near as whole pixels come.
            val stems = layout.strokes.filter { it.kind == GregorianStrokeKind.Connector }.map { it.lineWidth }.toSet()
            assertEquals(setOf(Math.round(1.125f * scale) / scale), stems)
        }
    }

    @Test
    fun aPieceWithoutAPsalmToneTakesAnInitialUnderItsMode() {
        fun label(mode: String, opensPiece: Boolean) = GregorianEngravingLayoutEngine.openingLabel(mode, opensPiece)
        assertEquals("8", label("8", true))
        assertEquals("4", label("4. alt", true))
        assertEquals("8", label("VIII", true))
        assertEquals("8G", label("8g", true))
        assertNull(label("irreg.", true))
        // Only a piece takes one: a psalm's verses, a tone with no conclusion, take none.
        assertNull(label("8", false))

        val score = GregorianScoreParser.parse("%% (c4) TE(f) lu(g)cis(h) (::)")
        val engine = GregorianEngravingLayoutEngine()
        assertTrue(engine.layout(score, width = 390f, openingLabel = "8").initials.isEmpty())
        val hymn = engine.layout(score, width = 390f, openingLabel = "8", opensPiece = true)
        val initial = hymn.initials.first()
        assertEquals("T", initial.text)
        assertEquals("8", initial.annotation)
        assertEquals(GregorianLyricStyle.Accented, initial.style)
        assertEquals("e", hymn.lyrics.first().text)
        // A piece with no mode takes an initial with nothing above it.
        val unmarked = engine.layout(score, width = 390f, opensPiece = true)
        assertEquals(listOf("T"), unmarked.initials.map { it.text })
        assertNull(unmarked.initials.first().annotation)
    }

    @Test
    fun anInitialStandsOnTheWordsBaselineUnderItsAnnotation() {
        val score = GregorianScoreParser.parse("%% (c4) SAL(g)ve(h) Re(h)gí(g)na(f) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f, openingLabel = "1", opensPiece = true)
        val initial = layout.initials.first()
        val lyric = layout.lyrics.first()
        // The rest of the word small, however the GABC writes it.
        assertEquals("al", lyric.text)
        val puer = GregorianEngravingLayoutEngine().layout(
            GregorianScoreParser.parse("%% (c4) PU(ei)ER(i.) *() na(iji)tus(h) (::)"), width = 390f, openingLabel = "7", opensPiece = true,
        )
        assertEquals(listOf("u", "er", "*"), puer.lyrics.take(3).map { it.text })
        // EB Garamond rises 1.007 em above its baseline.
        val ascent = 1.007f
        assertEquals(lyric.origin.y + ascent * lyric.fontSize, initial.origin.y + ascent * initial.fontSize, 0.05f)
        // The annotation centred over the initial, its capitals about the top line.
        val annotationOrigin = initial.annotationOrigin!!
        val staff = layout.staffs.first()
        assertTrue(2 * (initial.origin.x + initial.width / 2 - annotationOrigin.x) > 0)
        val capitals = annotationOrigin.y + ascent * initial.annotationFontSize
        assertTrue(capitals > staff.frame.top)
        assertTrue(capitals - 0.65f * initial.annotationFontSize < staff.frame.top)
    }

    @Test
    fun anInitialLeavesOutTheFirstVersesNumber() {
        val score = GregorianScoreParser.parse(
            "%% (c4) 1. Ec(g)ce(h) nunc(j) be(j)ne(j)dí(j)ci(j)te(j) Dó(k)mi(j)num.(j.) 2.(::) Qui(j) sta(j)tis(j) (::)",
        )
        val engine = GregorianEngravingLayoutEngine()
        val psalm = engine.layout(score, width = 390f, openingLabel = "8g")
        assertEquals(listOf("E"), psalm.initials.map { it.text })
        val texts = psalm.lyrics.map { it.text }
        assertTrue("1." !in texts)
        assertEquals(listOf("c", "ce"), texts.take(2))
        // The other verses keep their numbers.
        assertTrue("2." in texts)
        // Without an initial, the first verse keeps its number too.
        assertEquals("1.", engine.layout(score, width = 390f).lyrics.first().text)
    }

    @Test
    fun aFirstSyllableTheInitialTakesWholeLeavesAHyphen() {
        val score = GregorianScoreParser.parse("%% (c4) A(f)ve(g) Ma(h)rí(i)a(h) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f, openingLabel = "1", opensPiece = true)
        assertEquals(listOf("A"), layout.initials.map { it.text })
        assertEquals(listOf("-", "ve"), layout.lyrics.take(2).map { it.text })
        // Under its notes, and no second hyphen after it.
        val hyphen = layout.lyrics[0]
        val note = layout.events.first { it.eventId == score.eventIds[0] }
        assertEquals(note.frame.center.x, hyphen.origin.x + hyphen.width / 2, 0.5f)
        assertTrue(layout.lyrics.none { it.neumeId.startsWith("lyric-hyphen-${score.neumes[0].id}-") })
    }
}
