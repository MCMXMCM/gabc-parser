package io.github.mcmxmcm.gabc.parser

import io.github.mcmxmcm.gabc.layout.GregorianEngravingLayoutEngine
import io.github.mcmxmcm.gabc.layout.GregorianGlyphKind
import io.github.mcmxmcm.gabc.layout.GregorianGlyphName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// A port of the Swift package's Tests/GABCParserTests/GregorianScoreParserTests.swift.
class GregorianScoreParserTest {
    @Test
    fun testSharedCompilerEngravingFixtures() {
        for (fixture in SharedFixture.all()) {
            var noteIndex = 0
            val events = mutableListOf<GregorianEvent>()
            for ((syllableIndex, neumeSize) in fixture.neumeSizes.withIndex()) {
                repeat(neumeSize) {
                    events.add(
                        GregorianEvent(
                            id = "${fixture.scoreId}-note-$noteIndex",
                            syllableId = "${fixture.scoreId}-syllable-$syllableIndex",
                            relativePitch = 0,
                        ),
                    )
                    noteIndex += 1
                }
            }
            val score = GregorianScoreParser.parse(
                fixture.gabc,
                GregorianEventTimeline(events),
            )
            assertEquals(fixture.name, events.map { it.id }, score.eventIds)
            assertEquals(fixture.name, fixture.neumeSizes, score.neumes.map { it.notes.size })
        }
    }

    @Test
    fun testLiquescentInclinatumEndsAnAncus() {
        // `F~` is a diminished inclinatum, not a note of its own: `hGF~` is the ancus, a climacus whose last
        // inclinatum is liquescent.
        val score = GregorianScoreParser.parse("%% (c4) a(hGF~) b(hGF) (::)")

        assertEquals(listOf(GregorianNeumeForm.Ancus, GregorianNeumeForm.Climacus), score.neumes.map { it.form })
        val ancus = score.neumes.first()
        assertEquals(
            listOf(GregorianNoteShape.Punctum, GregorianNoteShape.Inclinatum, GregorianNoteShape.Inclinatum),
            ancus.notes.map { it.shape },
        )
        assertEquals(GregorianLiquescence.Small, ancus.notes.last().liquescence)

        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertTrue(
            layout.glyphs.any {
                it.eventId == ancus.notes.last().id &&
                    it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.PunctumInclinatumLiquescent)
            },
        )
    }

    @Test
    fun testOnlyRAndR0MarkAHollowNote() {
        // r and r0 are hollow notes; r1 to r5 are signs written above a note.
        val gabc = "name: test; %% (c3) no(hr1)bis(hr) ló(hr0)rum(h.r3) (::)"
        val timeline = makeTimeline(pitches = listOf(9, 9, 9, 9), syllables = listOf("no", "bis", "ló", "rum"))
        val score = GregorianScoreParser.parse(gabc, timeline)
        assertEquals(listOf(false, true, true, false), score.neumes.flatMap { neume -> neume.notes.map { it.isCavum } })
    }

    @Test
    fun testMapsCompilerEventsAndPreservesNeumeGrouping() {
        val gabc = "name: test; %% (c4) Ky(fg_)ri(h.w)e(ixj~) (;) test(k) (::)"
        val timeline = makeTimeline(
            pitches = listOf(5, 6, 7, 9, 10),
            syllables = listOf("Ky", "Ky", "ri", "e", "test"),
        )

        val score = GregorianScoreParser.parse(gabc, timeline)
        assertEquals(timeline.events.map { it.id }, score.eventIds)
        assertEquals(4, score.neumes.size)
        assertEquals(2, score.neumes.firstOrNull()?.notes?.size)
        assertEquals(true, score.neumes.firstOrNull()?.notes?.lastOrNull()?.hasEpisema)
        assertEquals(GregorianNoteShape.Quilisma, score.neumes[1].notes.firstOrNull()?.shape)
        assertEquals(true, score.neumes[1].notes.firstOrNull()?.hasMora)
        assertEquals(GregorianNoteShape.Liquescent, score.neumes[2].notes.firstOrNull()?.shape)
        val accidental = unwrap(
            score.elements.mapNotNull { element -> (element as? GregorianNotationElement.Accidental)?.mark }.firstOrNull(),
        )
        assertEquals(GregorianAccidental.Flat, accidental.kind)
        assertEquals(8, accidental.pitch)

        val clefs = score.elements.mapNotNull { element -> (element as? GregorianNotationElement.Clef)?.clef }
        assertEquals(listOf(GregorianClef(GregorianClefKind.C, 4)), clefs)

        val divisions = score.elements.mapNotNull { element -> (element as? GregorianNotationElement.Division)?.division }
        assertEquals(listOf(GregorianDivision.Minor, GregorianDivision.Final), divisions)
    }

    @Test
    fun testInManusAccidentalIsNotEngravedOrMappedAsAnExtraNote() {
        val timeline = makeTimeline(
            pitches = listOf(7, 7, 7, 8, 7),
            syllables = listOf("Red", "e", "mí", "mí", "sti"),
        )
        val score = GregorianScoreParser.parse(
            "name: In manus; %% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)",
            timeline,
        )

        val mi = unwrap(score.neumes.firstOrNull { it.lyric == "mí" })
        assertEquals(listOf(7, 8), mi.notes.map { it.pitch })
        assertEquals(GregorianNeumeForm.Podatus, mi.form)
        val accidental = unwrap(
            score.elements.mapNotNull { element -> (element as? GregorianNotationElement.Accidental)?.mark }.firstOrNull(),
        )
        assertEquals(GregorianAccidental.Flat, accidental.kind)
        assertEquals(8, accidental.pitch)

        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertTrue(
            layout.glyphs.any { it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.PodatusLower) },
        )
        assertTrue(
            layout.glyphs.any { it.kind == GregorianGlyphKind.Catalog(GregorianGlyphName.PodatusUpper) },
        )
        assertFalse(
            layout.glyphs.any {
                listOf(GregorianGlyphName.Porrectus1, GregorianGlyphName.Porrectus2, GregorianGlyphName.Porrectus3, GregorianGlyphName.Porrectus4)
                    .contains(it.kind.catalogName)
            },
        )
    }

    @Test
    fun testClefAndDivisionsDoNotConsumeTimelineEvents() {
        val timeline = makeTimeline(pitches = listOf(5, 6, 7), syllables = listOf("Ky", "ri", "e"))
        val score = GregorianScoreParser.parse(
            "name: clef; %% Ky(c4f)ri(g)e.(h.) (::)",
            timeline,
        )
        assertEquals(timeline.events.map { it.id }, score.eventIds)
        assertEquals(listOf("Ky", "ri", "e."), score.neumes.map { it.lyric })
    }

    @Test
    fun testFlatCClefDoesNotBecomeTimelineNotesAndIsEngraved() {
        val timeline = makeTimeline(pitches = listOf(5, 6), syllables = listOf("Ky", "Ky"))
        val score = GregorianScoreParser.parse(
            "name: flat clef; %% (cb4) Ky(fg) (::)",
            timeline,
        )

        assertEquals(timeline.events.map { it.id }, score.eventIds)
        val clef = unwrap(
            score.elements.mapNotNull { element -> (element as? GregorianNotationElement.Clef)?.clef }.firstOrNull(),
        )
        assertEquals(GregorianClef(GregorianClefKind.C, 4, flattensB = true), clef)

        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertTrue(layout.glyphs.any { it.kind == GregorianGlyphKind.Flat })
    }

    @Test
    fun testClassifiesNamedNeumeFormsFromPitchContours() {
        val timeline = makeTimeline(
            pitches = (0 until 10).toList(),
            syllables = listOf("po", "po", "cli", "cli", "scan", "scan", "scan", "tor", "tor", "tor"),
        )
        val score = GregorianScoreParser.parse(
            "name: forms; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) (::)",
            timeline,
        )

        assertEquals(
            listOf(GregorianNeumeForm.Podatus, GregorianNeumeForm.Clivis, GregorianNeumeForm.Scandicus, GregorianNeumeForm.Torculus),
            score.neumes.map { it.form },
        )
    }

    @Test
    fun testPreservesDetailedNoteModifiers() {
        val timeline = makeTimeline(pitches = listOf(5), syllables = listOf("Ky"))
        val score = GregorianScoreParser.parse(
            "name: modifiers; %% (c4) Ky(f.._0#') (::)",
            timeline,
        )
        val note = unwrap(score.neumes.firstOrNull()?.notes?.firstOrNull())

        assertEquals(GregorianAccidental.Sharp, note.accidental)
        assertEquals(2, note.moraCount)
        assertEquals(GregorianEpisemaPosition.Below, note.episemaPosition)
        assertTrue(note.hasIctus)
    }

    @Test
    fun testRemovesPsalmTonePointingDelimitersFromLyrics() {
        val timeline = makeTimeline(
            pitches = (0 until 8).toList(),
            syllables = listOf("ta", "ber", "ná", "_cu_", "_la_", "*Ja*", "cob.", "*"),
        )
        val score = GregorianScoreParser.parse(
            "name: Psalmus 86; %% (c4) ta(j)ber(j)ná(j)_cu_(h)_la_(j) *Ja*(k)cob.(j.) *(h) (::)",
            timeline,
        )

        assertEquals(
            listOf("ta", "ber", "ná", "cu", "la", "Ja", "cob.", "*"),
            score.neumes.map { it.lyric },
        )
        assertEquals(
            listOf(
                GregorianLyricStyle.Regular, GregorianLyricStyle.Regular, GregorianLyricStyle.Regular, GregorianLyricStyle.Preparatory,
                GregorianLyricStyle.Preparatory, GregorianLyricStyle.Accented, GregorianLyricStyle.Regular, GregorianLyricStyle.Regular,
            ),
            score.neumes.map { it.lyricStyle },
        )
    }

    @Test
    fun testIgnoresGABCLineCommentsInsteadOfRenderingPercentSigns() {
        val timeline = makeTimeline(
            pitches = listOf(7, 8),
            syllables = listOf("Hó", "die"),
        )
        val score = GregorianScoreParser.parse(
            "name: Invitatorium; %% (c3)\r\n%\r\nHó(h)% source note\r\ndie(i) (::)",
            timeline,
        )

        assertEquals(listOf("Hó", "die"), score.neumes.map { it.lyric })
        assertFalse(score.neumes.any { it.lyric.contains("%") })
        assertFalse(score.neumes.any { it.lyric.contains("source note") })
    }

    @Test
    fun testPreservesNoteLessLiturgicalAsteriskAsRubric() {
        val timeline = makeTimeline(
            pitches = listOf(7, 6, 5),
            syllables = listOf("fac", "fac", "Dó"),
        )
        val score = GregorianScoreParser.parse(
            "name: Psalmus 6; %% (c4) fac(gh) *()Dó(f) (::)",
            timeline,
        )
        val mark = unwrap(
            score.elements.mapNotNull { element -> (element as? GregorianNotationElement.LyricMark)?.mark }.firstOrNull(),
        )

        assertEquals("*", mark.text)
        assertEquals(GregorianLyricStyle.Rubric, mark.style)
        assertTrue(mark.startsWord)
        assertEquals(listOf("fac", "Dó"), score.neumes.map { it.lyric })
        assertEquals(timeline.events.map { it.id }, score.eventIds)

        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertEquals(listOf("fac", "*", "Dó"), layout.lyrics.map { it.text })
        assertEquals(
            listOf(GregorianLyricStyle.Regular, GregorianLyricStyle.Rubric, GregorianLyricStyle.Regular),
            layout.lyrics.map { it.style },
        )

        val fac = unwrap(layout.lyrics.firstOrNull { it.text == "fac" })
        val asterisk = unwrap(layout.lyrics.firstOrNull { it.text == "*" })
        val domine = unwrap(layout.lyrics.firstOrNull { it.text == "Dó" })
        assertGreaterThan(
            asterisk.origin.x,
            fac.origin.x + fac.width,
        )
        assertGreaterThan(
            domine.origin.x,
            asterisk.origin.x + asterisk.width,
        )
    }

    @Test
    fun testProjectsCompleteLatinWithoutLosingWordBoundaries() {
        val timeline = makeTimeline(
            pitches = listOf(5, 6, 7, 8, 9, 10, 9),
            syllables = listOf("Sal", "ve,", "Re", "gí", "na", "Dó", "mi"),
        )
        val score = GregorianScoreParser.parse(
            "name: Salve; %% (c4) Sal(f)ve,(g) Re(h)gí(i)na(j) *()Dó(k)mi(j) (::)",
            timeline,
        )

        assertEquals("Salve, Regína * Dómi", score.lyricText)
    }

    @Test
    fun testUsesVersicleAndResponseGlyphsInsteadOfAsciiSlashes() {
        val timeline = makeTimeline(
            pitches = listOf(5, 6),
            syllables = listOf("Jube", "Amen"),
        )
        val score = GregorianScoreParser.parse(
            "name: fallback marks; %% (c4) V/.()Jube(f) R/.()Amen(g) (::)",
            timeline,
        )
        val marks = score.elements.mapNotNull { element -> (element as? GregorianNotationElement.LyricMark)?.mark }

        assertEquals(listOf("℣.", "℟."), marks.map { it.text })
        assertEquals(listOf(GregorianLyricStyle.Rubric, GregorianLyricStyle.Rubric), marks.map { it.style })

        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertEquals(listOf("℣.", "Jube", "℟.", "Amen"), layout.lyrics.map { it.text })
        assertFalse(layout.lyrics.any { it.text.contains("/") })
    }

    @Test
    fun testZeroSpaceBoundarySplitsNamedNeumesWithoutLosingLyricIdentity() {
        val timeline = makeTimeline(
            pitches = listOf(5, 6, 7, 8),
            syllables = listOf("Ky", "Ky", "Ky", "Ky"),
        )
        val score = GregorianScoreParser.parse(
            "name: boundary; %% (c4) Ky(fg!hi) (::)",
            timeline,
        )

        assertEquals(2, score.neumes.size)
        assertEquals(listOf(GregorianNeumeForm.Podatus, GregorianNeumeForm.Podatus), score.neumes.map { it.form })
        assertEquals(listOf("Ky", ""), score.neumes.map { it.lyric })
        assertEquals(timeline.events.map { it.id }, score.eventIds)
    }

    @Test
    fun testWrittenCustosKeepsItsEventButIsNotANote() {
        // A versicle before its collect: the custos, the double bar, and
        // the clef of the collect.
        val timeline = makeTimeline(
            pitches = listOf(9, 5, 5, 9, 9),
            syllables = listOf("tó", "ris.", "", "O", "rémus."),
        )
        val score = GregorianScoreParser.parse(
            "name: custos; %% (c3) tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
            timeline,
        )

        assertEquals(timeline.events.map { it.id }, score.eventIds)
        assertEquals(
            listOf(
                listOf(GregorianNoteShape.Punctum),
                listOf(GregorianNoteShape.Punctum),
                listOf(GregorianNoteShape.Custos),
                listOf(GregorianNoteShape.Punctum),
                listOf(GregorianNoteShape.Punctum),
            ),
            score.neumes.map { neume -> neume.notes.map { it.shape } },
        )
        assertEquals(listOf(1, 1, 0, 1, 1), score.neumes.map { it.writtenNoteCount })
        val custos = unwrap(score.neumes[2].notes.firstOrNull())
        assertTrue(custos.isCustos)
        assertEquals(5, custos.pitch)
        assertEquals(1, custos.repetitionCount)
        assertFalse(score.neumes[1].notes[0].isCustos)
    }

    @Test
    fun testCustosStandsApartFromTheNotesBesideIt() {
        val timeline = makeTimeline(
            pitches = listOf(5, 6, 7, 8, 9),
            syllables = listOf("Ky", "Ky", "Ky", "Ky", "Ky"),
        )
        val score = GregorianScoreParser.parse(
            "name: custos; %% (c4) Ky(fgh+ij) (::)",
            timeline,
        )

        assertEquals(timeline.events.map { it.id }, score.eventIds)
        assertEquals(listOf(2, 1, 2), score.neumes.map { it.notes.size })
        assertEquals(
            listOf(GregorianNeumeForm.Podatus, GregorianNeumeForm.Punctum, GregorianNeumeForm.Podatus),
            score.neumes.map { it.form },
        )
        assertEquals(listOf(false, true, false), score.neumes.map { it.notes[0].isCustos })
        assertEquals(listOf("Ky", "", ""), score.neumes.map { it.lyric })
    }

    @Test
    fun testPlusAmongOtherSignsIsNotACustos() {
        // Only a pitch whose signs end in `+` is a custos, as in Exsurge.
        val timeline = makeTimeline(pitches = listOf(5), syllables = listOf("Ky"))
        val score = GregorianScoreParser.parse(
            "name: custos; %% (c4) Ky(f+.) (::)",
            timeline,
        )

        val note = unwrap(score.neumes.firstOrNull()?.notes?.firstOrNull())
        assertEquals(GregorianNoteShape.Punctum, note.shape)
        assertTrue(note.hasMora)
    }

    @Test
    fun testRepeatedVirgaAndStrophaAreOneEventWrittenSeveralTimes() {
        val timeline = makeTimeline(
            pitches = listOf(7, 7, 7, 7, 7),
            syllables = listOf("bi", "tri", "di", "tris", "one"),
        )
        val score = GregorianScoreParser.parse(
            "name: repeated; %% (c4) bi(hvv) tri(hvvv) di(hss.) tris(hsss) one(hv) (::)",
            timeline,
        )

        assertEquals(timeline.events.map { it.id }, score.eventIds)
        assertEquals(listOf(1, 1, 1, 1, 1), score.neumes.map { it.notes.size })
        assertEquals(listOf(2, 3, 2, 3, 1), score.neumes.map { it.notes[0].repetitionCount })
        assertEquals(listOf(2, 3, 2, 3, 1), score.neumes.map { it.writtenNoteCount })
        assertEquals(
            listOf(
                GregorianNeumeForm.Bivirga,
                GregorianNeumeForm.Trivirga,
                GregorianNeumeForm.Distropha,
                GregorianNeumeForm.Tristropha,
                GregorianNeumeForm.Virga,
            ),
            score.neumes.map { it.form },
        )
        assertEquals(
            listOf(
                GregorianNoteShape.Virga,
                GregorianNoteShape.Virga,
                GregorianNoteShape.Stropha,
                GregorianNoteShape.Stropha,
                GregorianNoteShape.Virga,
            ),
            score.neumes.map { it.notes[0].shape },
        )
        assertEquals(1, score.neumes[2].notes[0].moraCount)
    }

    @Test
    fun testRepeatedNoteIsANeumeOfItsOwnWithinAGroup() {
        // From the corpus: a bivirga before descending diamonds, and a
        // distropha between a clivis and a torculus.
        val bivirga = GregorianScoreParser.parse(
            "name: repeated; %% (c4) et(gvvF'ED) (::)",
            makeTimeline(pitches = listOf(6, 5, 4, 3), syllables = listOf("et", "et", "et", "et")),
        )
        assertEquals(listOf(1, 3), bivirga.neumes.map { it.notes.size })
        assertEquals(listOf(GregorianNeumeForm.Bivirga, GregorianNeumeForm.PunctaInclinata), bivirga.neumes.map { it.form })
        assertEquals(listOf(2, 3), bivirga.neumes.map { it.writtenNoteCount })

        val distropha = GregorianScoreParser.parse(
            "name: repeated; %% (c4) ri(igfssded) (::)",
            makeTimeline(
                pitches = listOf(8, 6, 5, 3, 4, 3),
                syllables = listOf("ri", "ri", "ri", "ri", "ri", "ri"),
            ),
        )
        assertEquals(listOf(2, 1, 3), distropha.neumes.map { it.notes.size })
        assertEquals(
            listOf(GregorianNeumeForm.Clivis, GregorianNeumeForm.Distropha, GregorianNeumeForm.Torculus),
            distropha.neumes.map { it.form },
        )

        // A repeated note that ends a rising figure still stands alone.
        val scandicus = GregorianScoreParser.parse(
            "name: repeated; %% (c4) Ky(fghvv) (::)",
            makeTimeline(pitches = listOf(5, 6, 7), syllables = listOf("Ky", "Ky", "Ky")),
        )
        assertEquals(listOf(2, 1), scandicus.neumes.map { it.notes.size })
        assertEquals(listOf(GregorianNeumeForm.Podatus, GregorianNeumeForm.Bivirga), scandicus.neumes.map { it.form })
    }

    @Test
    fun testVirgaAndStrophaSignsOfDifferentKindsDoNotRepeatANote() {
        val timeline = makeTimeline(pitches = listOf(7, 7), syllables = listOf("a", "b"))
        val score = GregorianScoreParser.parse(
            "name: repeated; %% (c4) a(hsv) b(hvsv) (::)",
            timeline,
        )

        assertEquals(listOf(1, 1), score.neumes.map { it.notes[0].repetitionCount })
    }

    @Test
    fun testWordBeginsAfterAClefOrBarThatNoSpaceFollows() {
        // As many collects are written: a space before the bar, none after.
        val timeline = makeTimeline(
            pitches = listOf(7, 6, 7, 7, 6, 7, 7),
            syllables = listOf("O", "ré", "ré", "mus.", "De", "us,", "qui"),
        )
        val score = GregorianScoreParser.parse(
            "name: words; %% (c4)O(h)ré(gh)mus.(h.) (:)De(g)us,(h) (,)qui(h) (::)",
            timeline,
        )

        assertEquals(listOf("O", "ré", "mus.", "De", "us,", "qui"), score.neumes.map { it.lyric })
        assertEquals(
            listOf(true, false, false, true, false, true),
            score.neumes.map { it.startsWord },
        )
        assertEquals("Orémus. Deus, qui", score.lyricText)

        // No hyphen joins the end of one word to the beginning of the next.
        val layout = GregorianEngravingLayoutEngine().layout(score, width = 390f)
        assertFalse(
            layout.lyrics.any {
                it.neumeId == "lyric-hyphen-${score.neumes[2].id}-${score.neumes[3].id}" ||
                    it.neumeId == "lyric-hyphen-${score.neumes[4].id}-${score.neumes[5].id}"
            },
        )

        // A versicle sign after the clef, and the word after a clef that
        // follows the sign, each begin a word.
        val versicle = GregorianScoreParser.parse(
            "name: words; %% (c3)V/.() (c4)Tu(h) es(h) (::)",
            makeTimeline(pitches = listOf(7, 7), syllables = listOf("Tu", "es")),
        )
        val marks = versicle.elements.mapNotNull { element -> (element as? GregorianNotationElement.LyricMark)?.mark }
        assertEquals(listOf(true), marks.map { it.startsWord })
        assertEquals(listOf(true, true), versicle.neumes.map { it.startsWord })
        assertEquals("℣. Tu es", versicle.lyricText)
    }

    @Test
    fun testWordContinuesThroughAMelismaAndTheBarsWithinIt() {
        // A bar inside a word, with no space on either side.
        val breath = GregorianScoreParser.parse(
            "name: words; %% (c4) al(f)le(g)lú(gh)(,)ia.(g) (::)",
            makeTimeline(
                pitches = listOf(5, 6, 6, 7, 6),
                syllables = listOf("al", "le", "lú", "lú", "ia."),
            ),
        )
        assertEquals(listOf(true, false, false, false), breath.neumes.map { it.startsWord })
        assertEquals("allelúia.", breath.lyricText)

        // From the corpus: a melisma carried over bars and spaces before
        // the word's last syllable. Notes without words belong to the
        // syllable before them, and the word is still unfinished.
        val melisma = GregorianScoreParser.parse(
            "name: words; %% (c4) co(f)ró(gh) (;) (ij) (,)(hg)na(g) (;) stel(h)lá(g)rum(f) (::)",
            makeTimeline(
                pitches = listOf(5, 6, 7, 8, 9, 7, 6, 6, 7, 6, 5),
                syllables = listOf("co", "ró", "ró", "x", "x", "y", "y", "na", "stel", "lá", "rum"),
            ),
        )
        assertEquals("coróna stellárum", melisma.lyricText)
        assertEquals(
            listOf(true, false, false, true, false, false),
            melisma.neumes.filter { it.lyric.isNotEmpty() }.map { it.startsWord },
        )

        // An elided vowel written after an empty group stays with its word.
        val elision = GregorianScoreParser.parse(
            "name: words; %% (c4) Cum(k) Patr(k) ()_e_(i) () et(i) (::)",
            makeTimeline(pitches = listOf(10, 10, 8, 8), syllables = listOf("Cum", "Patr", "e", "et")),
        )
        assertEquals("Cum Patre et", elision.lyricText)
    }

    @Test
    fun testTimelineMismatchIsAValidationFailure() {
        val timeline = makeTimeline(pitches = listOf(5), syllables = listOf("Ky"))
        val error = assertThrows(Throwable::class.java) {
            GregorianScoreParser.parse(
                "name: mismatch; %% Ky(c4fg)",
                timeline,
            )
        }
        assertTrue("Unexpected error: $error", error is GregorianScoreParserError.TooManyNotationNotes)
    }

    @Test
    fun testUnsupportedTokenIsAValidationFailure() {
        val timeline = makeTimeline(pitches = listOf(5), syllables = listOf("Ky"))
        val error = assertThrows(Throwable::class.java) {
            GregorianScoreParser.parse(
                "name: unsupported; %% Ky(c4f?)",
                timeline,
            )
        }
        assertTrue(
            "Unexpected error: $error",
            error is GregorianScoreParserError.UnsupportedToken && error.token == "?",
        )
    }

    private fun makeTimeline(pitches: List<Int>, syllables: List<String>): GregorianEventTimeline {
        require(pitches.size == syllables.size)
        var syllableNumber = 0
        var previousSyllable: String? = null
        return GregorianEventTimeline(
            pitches.zip(syllables).mapIndexed { index, pair ->
                if (pair.second != previousSyllable) {
                    syllableNumber += 1
                    previousSyllable = pair.second
                }
                GregorianEvent(
                    id = "fixture-note-$index",
                    syllableId = "fixture-syllable-$syllableNumber",
                    relativePitch = pair.first - 7,
                )
            },
        )
    }

    /** XCTUnwrap: fails the test when the value is null, and returns it otherwise. */
    private fun <T : Any> unwrap(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    /** XCTAssertGreaterThan. */
    private fun assertGreaterThan(first: Float, second: Float) {
        assertTrue("($first) is not greater than ($second)", first > second)
    }
}
