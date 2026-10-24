package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianDivision
import io.github.mcmxmcm.gabc.parser.GregorianNeumeForm
import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The neumes drawn as Exsurge draws them, and their signs set where the chant books set them. A port of the Swift
 * package's Tests/GABCLayoutTests/NeumeShapeTests.swift.
 */
class NeumeShapeTest {
    /** One staff space, and one step, at the default notation scale. */
    private val staffSpace = 12.5f
    private val staffStep = 6.25f
    private val stemWidth = 18f / 16

    private fun layout(notation: String): Pair<GregorianScore, GregorianLayout> {
        val score = GregorianScoreParser.parse("%% (c4) a($notation) (::)")
        return score to GregorianEngravingLayoutEngine().layout(score, width = 390f)
    }

    private fun heads(layout: GregorianLayout, score: GregorianScore): List<GregorianPlacedGlyph> {
        val notes = score.eventIds.toSet()
        val signs = setOf(
            GregorianGlyphKind.Catalog(GregorianGlyphName.VerticalEpisemaAbove),
            GregorianGlyphKind.Catalog(GregorianGlyphName.VerticalEpisemaBelow),
        )
        return layout.glyphs
            .filter { it.eventId in notes && it.kind is GregorianGlyphKind.Catalog && it.kind !in signs }
            .sortedBy { it.frame.left }
    }

    private fun stems(layout: GregorianLayout): List<GregorianPlacedStroke> = layout.strokes.filter { it.kind == GregorianStrokeKind.Connector }

    private fun catalog(name: GregorianGlyphName): GregorianGlyphKind = GregorianGlyphKind.Catalog(name)

    @Test
    fun aClivisHangsFromAStemOnItsLeft() {
        val (score, layout) = layout("hf")
        val notes = heads(layout, score)
        assertEquals(2, notes.size)
        val stem = stems(layout).first()
        // On the left of the upper note, from its centre down past the lower.
        assertEquals(notes[0].frame.left + stemWidth / 2, stem.start.x, 0.01f)
        assertEquals(notes[0].frame.center.y, stem.start.y, 1.5f)
        assertTrue(stem.end.y > notes[1].frame.center.y)
    }

    @Test
    fun aPesIsJoinedOnItsRightWhereItLeaps() {
        val (score, layout) = layout("fh")
        assertEquals(listOf(catalog(GregorianGlyphName.PodatusLower), catalog(GregorianGlyphName.PodatusUpper)), heads(layout, score).map { it.kind })
        val stem = stems(layout).first()
        val lower = heads(layout, score)[0]
        assertEquals(lower.frame.right, stem.start.x + stemWidth / 2, 0.01f)

        // A pes of a second takes the short halves, which meet.
        val (second, secondLayout) = layout("fg")
        assertEquals(
            listOf(catalog(GregorianGlyphName.PodatusLowerShort), catalog(GregorianGlyphName.PodatusUpperShort)),
            heads(secondLayout, second).map { it.kind },
        )
        assertTrue(stems(secondLayout).isEmpty())
    }

    @Test
    fun aPorrectusSetsItsLastNoteOverTheEndOfItsOblique() {
        val (score, layout) = layout("hgh")
        val notes = heads(layout, score)
        val swash = notes.first { it.kind == catalog(GregorianGlyphName.Porrectus1) }
        val last = notes.first { it.kind == catalog(GregorianGlyphName.PodatusUpperShort) }
        assertEquals(swash.frame.right, last.frame.right, 0.01f)
        // And hangs from a stem on its left.
        assertTrue(stems(layout).any { abs(it.start.x - stemWidth / 2 - swash.frame.left) < 0.01f })
    }

    @Test
    fun theDiamondsOfAClimacusStepDownUnjoined() {
        val (score, layout) = layout("hvGF")
        val notes = heads(layout, score)
        assertEquals(
            listOf(GregorianGlyphName.PunctumQuadratum, GregorianGlyphName.PunctumInclinatum, GregorianGlyphName.PunctumInclinatum).map(::catalog),
            notes.map { it.kind },
        )
        // The virga's stem is the only one.
        assertEquals(1, stems(layout).size)
        assertTrue(notes[2].frame.left > notes[1].frame.left)
        assertTrue(notes[2].frame.center.y > notes[1].frame.center.y)
    }

    @Test
    fun plainNotesRepeatedOnOnePitchArePuncta() {
        val (score, layout) = layout("fff")
        assertEquals(List(3) { catalog(GregorianGlyphName.PunctumQuadratum) }, heads(layout, score).map { it.kind })
        // Where the melody goes on, the last of them begins a pressus, its punctum abutting the pes.
        val (pressus, pressusLayout) = layout("ffg")
        assertEquals(listOf(GregorianNeumeForm.Punctum, GregorianNeumeForm.Podatus), pressus.neumes.map { it.form })
        val notes = heads(pressusLayout, pressus)
        assertEquals(0f, notes[1].frame.left - notes[0].frame.right, 0.01f)
    }

    @Test
    fun aDotStandsJustAfterItsNoteInTheSpaceAboveALine() {
        // h is on the third line, g in the space below it.
        for ((notation, raised) in listOf("h." to true, "g." to false)) {
            val (score, layout) = layout(notation)
            val head = heads(layout, score).first()
            val dot = layout.glyphs.first { it.kind == GregorianGlyphKind.Mora }
            assertEquals(notation, 0.33f * staffSpace, dot.frame.width, 0.01f)
            assertEquals(notation, 0.1f * staffSpace, dot.frame.left - head.frame.right, 0.01f)
            val headCentre = head.frame.top + 60.906f / 16
            assertEquals(notation, headCentre - if (raised) 0.75f * staffStep else 0f, dot.frame.center.y, 0.01f)
        }
    }

    @Test
    fun aDoubleDotDotsANeumesLastTwoNotes() {
        val (score, layout) = layout("hg..")
        val notes = heads(layout, score)
        val dots = layout.glyphs.filter { it.kind == GregorianGlyphKind.Mora }
        assertEquals(2, dots.size)
        // Both after the neume, one at each note's height.
        assertTrue(dots.all { it.frame.left > notes[1].frame.right })
        assertEquals(score.eventIds.toSet(), dots.map { it.eventId }.toSet())
    }

    @Test
    fun anEpisemaKeepsOffTheStaffsLines() {
        for (notation in listOf("h_", "g_", "f_0", "j_", "hg__")) {
            val (score, layout) = layout(notation)
            val staff = layout.staffs.first()
            val lines = (0 until 4).map { staff.frame.top + it * staffSpace }
            val episemata = layout.strokes.filter { it.kind == GregorianStrokeKind.Episema }
            assertEquals(notation, 1, episemata.size)
            for (episema in episemata) assertTrue(notation, lines.all { abs(it - episema.start.y) > 1 })
            // One that the GABC reaches over two notes spans both.
            if (notation == "hg__") {
                val notes = heads(layout, score)
                assertEquals(notes[0].frame.left, episemata[0].start.x, 0.01f)
                assertEquals(notes[1].frame.right, episemata[0].end.x, 0.01f)
            }
        }
    }

    @Test
    fun anIctusStandsBelowUnlessTheNeumeOrTheGabcSetsItAbove() {
        for ((notation, above) in listOf("g'" to false, "g'1" to true, "g'0" to false, "h'g" to true, "fg'" to true, "hvGF'" to false)) {
            val (_, layout) = layout(notation)
            val ictus = layout.glyphs.firstOrNull {
                it.kind == catalog(GregorianGlyphName.VerticalEpisemaAbove) || it.kind == catalog(GregorianGlyphName.VerticalEpisemaBelow)
            }
            assertNotNull(notation, ictus)
            assertEquals(notation, above, ictus!!.kind == catalog(GregorianGlyphName.VerticalEpisemaAbove))
        }
    }

    @Test
    fun aQuarterBarCrossesTheTopLine() {
        val score = GregorianScoreParser.parse("%% (c4) a(f) (,) b(g) (::)")
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        val staff = layout.staffs.first()
        val bar = layout.strokes.first { it.kind == GregorianStrokeKind.Division(GregorianDivision.Minima) }
        assertEquals(staff.frame.top - staffSpace / 2, bar.start.y, 0.01f)
        assertEquals(staff.frame.top + staffSpace / 2, bar.end.y, 0.01f)
    }
}
