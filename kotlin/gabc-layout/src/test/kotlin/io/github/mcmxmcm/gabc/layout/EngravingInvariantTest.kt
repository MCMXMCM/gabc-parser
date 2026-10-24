package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianNeumeForm
import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checks a notation validator makes of every score in a corpus, made here of scores that exercise each kind of
 * sign.
 */
class EngravingInvariantTest {
    @Test
    fun scoresKeepTheirIdentityAndInkAtEveryWidth() {
        for (gabc in SCORES) {
            assertEngravingInvariants(GregorianScoreParser.parse(gabc), gabc)
        }
    }

    @Test
    fun semanticFixturesEngraveAsNamedForms() {
        val score = GregorianScoreParser.parse("name: semantic; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) (::)")
        assertEquals(
            listOf(
                GregorianNeumeForm.Podatus,
                GregorianNeumeForm.Clivis,
                GregorianNeumeForm.Scandicus,
                GregorianNeumeForm.Torculus,
            ),
            score.neumes.map { it.form },
        )
        val names = GregorianEngravingLayoutEngine().layout(score, width = 390f)
            .glyphs.map { it.kind.catalogName }.toSet()
        assertTrue(GregorianGlyphName.PodatusLowerShort in names)
        assertTrue(GregorianGlyphName.PodatusUpperShort in names)

        val boundary = GregorianScoreParser.parse("name: boundary; %% (c4) Ky(fg!hi) (::)")
        assertEquals(listOf(GregorianNeumeForm.Podatus, GregorianNeumeForm.Podatus), boundary.neumes.map { it.form })
        assertEquals(listOf("Ky", ""), boundary.neumes.map { it.lyric })

        val written = GregorianScoreParser.parse(
            "name: written; %% (c3) s0(f.) (f+::) (c4) s2(hvv) s3(hsss) s4(h.) (::)",
        )
        assertEquals(listOf(false, true, false, false, false), written.neumes.map { it.notes[0].isCustos })
        assertEquals(listOf(1, 0, 2, 3, 1), written.neumes.map { it.writtenNoteCount })
        assertEquals(
            listOf(
                GregorianNeumeForm.Punctum,
                GregorianNeumeForm.Punctum,
                GregorianNeumeForm.Bivirga,
                GregorianNeumeForm.Tristropha,
                GregorianNeumeForm.Punctum,
            ),
            written.neumes.map { it.form },
        )
        val writtenLayout = GregorianEngravingLayoutEngine().layout(written, width = 390f)
        assertEquals(written.eventIds, writtenLayout.events.map { it.eventId })
        assertEquals(1, writtenLayout.glyphs.filter { it.kind == GregorianGlyphKind.Custos }.size)
        assertEquals(listOf(1, 2, 3, 1), writtenLayout.neumes.map { it.noteCount })
        // A bivirga is two heads, each with its stem.
        val bivirga = written.eventIds[2]
        assertEquals(
            2,
            writtenLayout.glyphs.count { it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.PunctumQuadratum) && it.eventId == bivirga },
        )
        assertEquals(2, writtenLayout.strokes.count { it.kind == GregorianStrokeKind.Connector && it.eventId == bivirga })
        assertEquals(
            3,
            writtenLayout.glyphs.filter { it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.Stropha) }.size,
        )
    }

    private fun assertEngravingInvariants(notation: GregorianScore, name: String) {
        val notes = notation.neumes.flatMap { it.notes }
        val custodes = notes.filter { it.isCustos }.map { it.id }.toSet()
        val repeatedNotes = notes.filter { it.repetitionCount > 1 }
        val writtenNoteCount = notation.neumes.fold(0) { total, neume -> total + neume.writtenNoteCount }

        for (width in listOf(320f, 390f, 768f)) {
            val context = "$name at width $width"
            val layout = GregorianEngravingLayoutEngine().layout(notation, width = width)
            assertEquals(
                "lost event identity: $context",
                notation.eventIds,
                layout.events.map { it.eventId },
            )
            assertTrue(
                "non-finite geometry: $context",
                layout.size.width.isFinite() && layout.size.height.isFinite() &&
                    layout.glyphs.all {
                        it.frame.left.isFinite() && it.frame.top.isFinite() &&
                            it.frame.width.isFinite() && it.frame.height.isFinite()
                    },
            )
            // A custos keeps its event's place but is no place to begin
            // singing, and every other neume draws at least one note.
            assertTrue(
                "offers a custos as a neume: $context",
                layout.neumes.all { neume ->
                    neume.noteCount > 0 && neume.eventIds.none { it in custodes }
                },
            )
            assertEquals(
                "lost a written note: $context",
                writtenNoteCount,
                layout.neumes.fold(0) { total, neume -> total + neume.noteCount },
            )
            // It is drawn as a custos, in its event's place, and not as a
            // note that a highlight may light.
            for (custos in custodes) {
                val placement = layout.events.firstOrNull { it.eventId == custos }
                assertTrue(
                    "custos not drawn in its place: $context",
                    placement?.let { event ->
                        layout.glyphs.any { glyph ->
                            glyph.kind == GregorianGlyphKind.Custos && glyph.frame in event.frame
                        }
                    } ?: false,
                )
                assertFalse(
                    "custos drawn as a note: $context",
                    layout.glyphs.any { it.eventId == custos },
                )
            }
            // A note written twice or thrice is drawn as often.
            for (note in repeatedNotes) {
                val drawn = layout.glyphs.filter { glyph ->
                    val glyphName = (glyph.kind as? GregorianGlyphKind.Catalog)?.name
                    if (glyph.eventId != note.id || glyphName == null) return@filter false
                    glyphName != GregorianGlyphName.VerticalEpisemaAbove &&
                        glyphName != GregorianGlyphName.VerticalEpisemaBelow
                }.size
                assertEquals(
                    "repeated note drawn $drawn times: $context",
                    note.repetitionCount,
                    drawn,
                )
            }
            for (staff in layout.neumes.groupBy { it.lineIndex }.values) {
                val ordered = staff.sortedBy { it.hitFrame.left }
                for ((first, second) in ordered.zip(ordered.drop(1))) {
                    assertLessThanOrEqual(
                        "overlapping neume targets: $context",
                        first.hitFrame.right,
                        second.hitFrame.left + 0.01f,
                    )
                }
            }
        }
    }

    /** XCTAssertLessThanOrEqual: fails, naming both values, unless the first is at most the second. */
    private fun assertLessThanOrEqual(message: String, first: Float, second: Float) {
        assertTrue("($first) is greater than ($second) - $message", first <= second)
    }

    private companion object {
        val SCORES = listOf(
            "name: Deus in adiutorium; mode: 6; %% (c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)",
            "name: signs; mode: 1; %% (c4) Ky(fg_)ri(h.w)e(ixj~) (;) test(k) (::)",
            "name: clefs; mode: 4; %% (c3) Al(dfe)le(g) (z) (f3) lú(hi)ia.(g.) (::)",
            "name: forms; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) por(hgh) res(hghg) cli(hgf) an(hgf~) (::)",
            "name: written; %% (c3) s0(f.) (f+::) (c4) s2(hvv) s3(hsss) s4(h.) (::)",
            "name: In manus; %% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)",
            "name: ledger; %% (c4) Low(a) high(m) (::)",
            "name: marks; %% (c4) Ky(f.._0#') ri(gr) e(h_) e(i') le(go) i(gwh) son(g<) (h>) (::)",
            "name: repeated; %% (c4) et(gvvF'ED) ri(igfssded) Ky(fghvv) (::)",
            "name: preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
            "name: verse; %% (c3) " + listOf(
                "Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
                "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
                "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
                "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
                "in(h) o(f)re(h) me(g)o.(f.) (::)",
            ).joinToString(" "),
        )
    }
}
