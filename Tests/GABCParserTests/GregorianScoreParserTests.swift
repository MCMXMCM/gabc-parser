import GABCLayout
import GABCLyricFont
import GABCParser
import XCTest

final class GregorianScoreParserTests: XCTestCase {
    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    func testSharedCompilerEngravingFixtures() throws {
        for fixture in try SharedFixture.all() {
            var noteIndex = 0
            var events: [GregorianEvent] = []
            for (syllableIndex, neumeSize) in fixture.neumeSizes.enumerated() {
                for _ in 0..<neumeSize {
                    events.append(
                        GregorianEvent(
                            id: "\(fixture.scoreID)-note-\(noteIndex)",
                            syllableID: "\(fixture.scoreID)-syllable-\(syllableIndex)",
                            relativePitch: 0
                        )
                    )
                    noteIndex += 1
                }
            }
            let score = try GregorianScoreParser.parse(
                gabc: fixture.gabc,
                timeline: GregorianEventTimeline(events: events)
            )
            XCTAssertEqual(score.eventIDs, events.map(\.id), fixture.name)
            XCTAssertEqual(score.neumes.map { $0.notes.count }, fixture.neumeSizes, fixture.name)
        }
    }

    func testOnlyRAndR0MarkAHollowNote() throws {
        // r and r0 are hollow notes; r1 to r5 are signs written above a note.
        let gabc = "name: test; %% (c3) no(hr1)bis(hr) ló(hr0)rum(h.r3) (::)"
        let timeline = makeTimeline(pitches: [9, 9, 9, 9], syllables: ["no", "bis", "ló", "rum"])
        let score = try GregorianScoreParser.parse(gabc: gabc, timeline: timeline)
        XCTAssertEqual(score.neumes.flatMap { $0.notes.map(\.isCavum) }, [false, true, true, false])
    }

    func testMapsCompilerEventsAndPreservesNeumeGrouping() throws {
        let gabc = "name: test; %% (c4) Ky(fg_)ri(h.w)e(ixj~) (;) test(k) (::)"
        let timeline = makeTimeline(
            pitches: [5, 6, 7, 9, 10],
            syllables: ["Ky", "Ky", "ri", "e", "test"]
        )

        let score = try GregorianScoreParser.parse(gabc: gabc, timeline: timeline)
        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
        XCTAssertEqual(score.neumes.count, 4)
        XCTAssertEqual(score.neumes.first?.notes.count, 2)
        XCTAssertEqual(score.neumes.first?.notes.last?.hasEpisema, true)
        XCTAssertEqual(score.neumes[1].notes.first?.shape, .quilisma)
        XCTAssertEqual(score.neumes[1].notes.first?.hasMora, true)
        XCTAssertEqual(score.neumes[2].notes.first?.shape, .liquescent)
        let accidental = try XCTUnwrap(
            score.elements.compactMap { element -> GregorianAccidentalMark? in
                guard case let .accidental(accidental) = element else { return nil }
                return accidental
            }.first
        )
        XCTAssertEqual(accidental.kind, .flat)
        XCTAssertEqual(accidental.pitch, 8)

        let clefs = score.elements.compactMap { element -> GregorianClef? in
            guard case let .clef(clef) = element else { return nil }
            return clef
        }
        XCTAssertEqual(clefs, [GregorianClef(kind: .c, line: 4)])

        let divisions = score.elements.compactMap { element -> GregorianDivision? in
            guard case let .division(division) = element else { return nil }
            return division
        }
        XCTAssertEqual(divisions, [.minor, .final])
    }

    func testInManusAccidentalIsNotEngravedOrMappedAsAnExtraNote() throws {
        let timeline = makeTimeline(
            pitches: [7, 7, 7, 8, 7],
            syllables: ["Red", "e", "mí", "mí", "sti"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: In manus; %% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)",
            timeline: timeline
        )

        let mi = try XCTUnwrap(score.neumes.first(where: { $0.lyric == "mí" }))
        XCTAssertEqual(mi.notes.map(\.pitch), [7, 8])
        XCTAssertEqual(mi.form, .podatus)
        let accidental = try XCTUnwrap(
            score.elements.compactMap { element -> GregorianAccidentalMark? in
                guard case let .accidental(accidental) = element else { return nil }
                return accidental
            }.first
        )
        XCTAssertEqual(accidental.kind, .flat)
        XCTAssertEqual(accidental.pitch, 8)

        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertTrue(
            layout.glyphs.contains { $0.kind == .catalog(.podatusLower) }
        )
        XCTAssertTrue(
            layout.glyphs.contains { $0.kind == .catalog(.podatusUpper) }
        )
        XCTAssertFalse(
            layout.glyphs.contains {
                [.porrectus1, .porrectus2, .porrectus3, .porrectus4]
                    .contains($0.kind.catalogName)
            }
        )
    }

    func testClefAndDivisionsDoNotConsumeTimelineEvents() throws {
        let timeline = makeTimeline(pitches: [5, 6, 7], syllables: ["Ky", "ri", "e"])
        let score = try GregorianScoreParser.parse(
            gabc: "name: clef; %% Ky(c4f)ri(g)e.(h.) (::)",
            timeline: timeline
        )
        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
        XCTAssertEqual(score.neumes.map(\.lyric), ["Ky", "ri", "e."])
    }

    func testFlatCClefDoesNotBecomeTimelineNotesAndIsEngraved() throws {
        let timeline = makeTimeline(pitches: [5, 6], syllables: ["Ky", "Ky"])
        let score = try GregorianScoreParser.parse(
            gabc: "name: flat clef; %% (cb4) Ky(fg) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
        let clef = try XCTUnwrap(score.elements.compactMap { element -> GregorianClef? in
            guard case let .clef(clef) = element else { return nil }
            return clef
        }.first)
        XCTAssertEqual(clef, GregorianClef(kind: .c, line: 4, flattensB: true))

        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertTrue(layout.glyphs.contains { $0.kind == .flat })
    }

    func testLiquescentInclinatumEndsAnAncus() throws {
        // `F~` is a diminished inclinatum, not a note of its own: `hGF~` is
        // the ancus, a climacus whose last inclinatum is liquescent.
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) a(hGF~) b(hGF) (::)")

        XCTAssertEqual(score.neumes.map(\.form), [.ancus, .climacus])
        let ancus = try XCTUnwrap(score.neumes.first)
        XCTAssertEqual(ancus.notes.map(\.shape), [.punctum, .inclinatum, .inclinatum])
        XCTAssertEqual(ancus.notes.last?.liquescence, .small)

        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertTrue(
            layout.glyphs.contains {
                $0.eventID == ancus.notes.last?.id && $0.kind == .catalog(.punctumInclinatumLiquescent)
            }
        )
    }

    func testClassifiesNamedNeumeFormsFromPitchContours() throws {
        let timeline = makeTimeline(
            pitches: Array(0..<10),
            syllables: ["po", "po", "cli", "cli", "scan", "scan", "scan", "tor", "tor", "tor"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: forms; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.neumes.map(\.form), [.podatus, .clivis, .scandicus, .torculus])
    }

    func testPreservesDetailedNoteModifiers() throws {
        let timeline = makeTimeline(pitches: [5], syllables: ["Ky"])
        let score = try GregorianScoreParser.parse(
            gabc: "name: modifiers; %% (c4) Ky(f.._0#') (::)",
            timeline: timeline
        )
        let note = try XCTUnwrap(score.neumes.first?.notes.first)

        XCTAssertEqual(note.accidental, .sharp)
        XCTAssertEqual(note.moraCount, 2)
        XCTAssertEqual(note.episemaPosition, .below)
        XCTAssertTrue(note.hasIctus)
    }

    func testRemovesPsalmTonePointingDelimitersFromLyrics() throws {
        let timeline = makeTimeline(
            pitches: Array(0..<8),
            syllables: ["ta", "ber", "ná", "_cu_", "_la_", "*Ja*", "cob.", "*"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: Psalmus 86; %% (c4) ta(j)ber(j)ná(j)_cu_(h)_la_(j) *Ja*(k)cob.(j.) *(h) (::)",
            timeline: timeline
        )

        XCTAssertEqual(
            score.neumes.map(\.lyric),
            ["ta", "ber", "ná", "cu", "la", "Ja", "cob.", "*"]
        )
        XCTAssertEqual(
            score.neumes.map(\.lyricStyle),
            [
                .regular, .regular, .regular, .preparatory,
                .preparatory, .accented, .regular, .regular
            ]
        )
    }

    func testIgnoresGABCLineCommentsInsteadOfRenderingPercentSigns() throws {
        let timeline = makeTimeline(
            pitches: [7, 8],
            syllables: ["Hó", "die"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: Invitatorium; %% (c3)\r\n%\r\nHó(h)% source note\r\ndie(i) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.neumes.map(\.lyric), ["Hó", "die"])
        XCTAssertFalse(score.neumes.contains { $0.lyric.contains("%") })
        XCTAssertFalse(score.neumes.contains { $0.lyric.contains("source note") })
    }

    func testPreservesNoteLessLiturgicalAsteriskAsRubric() throws {
        let timeline = makeTimeline(
            pitches: [7, 6, 5],
            syllables: ["fac", "fac", "Dó"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: Psalmus 6; %% (c4) fac(gh) *()Dó(f) (::)",
            timeline: timeline
        )
        let mark = try XCTUnwrap(
            score.elements.compactMap { element -> GregorianLyricMark? in
                guard case let .lyricMark(mark) = element else { return nil }
                return mark
            }.first
        )

        XCTAssertEqual(mark.text, "*")
        XCTAssertEqual(mark.style, .rubric)
        XCTAssertTrue(mark.startsWord)
        XCTAssertEqual(score.neumes.map(\.lyric), ["fac", "Dó"])
        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))

        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertEqual(layout.lyrics.map(\.text), ["fac", "*", "Dó"])
        XCTAssertEqual(layout.lyrics.map(\.style), [.regular, .rubric, .regular])

        let fac = try XCTUnwrap(layout.lyrics.first { $0.text == "fac" })
        let asterisk = try XCTUnwrap(layout.lyrics.first { $0.text == "*" })
        let domine = try XCTUnwrap(layout.lyrics.first { $0.text == "Dó" })
        XCTAssertGreaterThan(
            asterisk.origin.x,
            fac.origin.x + fac.width
        )
        XCTAssertGreaterThan(
            domine.origin.x,
            asterisk.origin.x + asterisk.width
        )
    }

    func testProjectsCompleteLatinWithoutLosingWordBoundaries() throws {
        let timeline = makeTimeline(
            pitches: [5, 6, 7, 8, 9, 10, 9],
            syllables: ["Sal", "ve,", "Re", "gí", "na", "Dó", "mi"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: Salve; %% (c4) Sal(f)ve,(g) Re(h)gí(i)na(j) *()Dó(k)mi(j) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.lyricText, "Salve, Regína * Dómi")
    }

    func testUsesVersicleAndResponseGlyphsInsteadOfAsciiSlashes() throws {
        let timeline = makeTimeline(
            pitches: [5, 6],
            syllables: ["Jube", "Amen"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: fallback marks; %% (c4) V/.()Jube(f) R/.()Amen(g) (::)",
            timeline: timeline
        )
        let marks = score.elements.compactMap { element -> GregorianLyricMark? in
            guard case let .lyricMark(mark) = element else { return nil }
            return mark
        }

        XCTAssertEqual(marks.map(\.text), ["℣.", "℟."])
        XCTAssertEqual(marks.map(\.style), [.rubric, .rubric])

        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertEqual(layout.lyrics.map(\.text), ["℣.", "Jube", "℟.", "Amen"])
        XCTAssertFalse(layout.lyrics.contains { $0.text.contains("/") })
    }

    func testZeroSpaceBoundarySplitsNamedNeumesWithoutLosingLyricIdentity() throws {
        let timeline = makeTimeline(
            pitches: [5, 6, 7, 8],
            syllables: ["Ky", "Ky", "Ky", "Ky"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: boundary; %% (c4) Ky(fg!hi) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.neumes.count, 2)
        XCTAssertEqual(score.neumes.map(\.form), [.podatus, .podatus])
        XCTAssertEqual(score.neumes.map(\.lyric), ["Ky", ""])
        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
    }

    func testWrittenCustosKeepsItsEventButIsNotANote() throws {
        // A versicle before its collect: the custos, the double bar, and
        // the clef of the collect.
        let timeline = makeTimeline(
            pitches: [9, 5, 5, 9, 9],
            syllables: ["tó", "ris.", "", "O", "rémus."]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: custos; %% (c3) tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
        XCTAssertEqual(
            score.neumes.map { $0.notes.map(\.shape) },
            [[.punctum], [.punctum], [.custos], [.punctum], [.punctum]]
        )
        XCTAssertEqual(score.neumes.map(\.writtenNoteCount), [1, 1, 0, 1, 1])
        let custos = try XCTUnwrap(score.neumes[2].notes.first)
        XCTAssertTrue(custos.isCustos)
        XCTAssertEqual(custos.pitch, 5)
        XCTAssertEqual(custos.repetitionCount, 1)
        XCTAssertFalse(score.neumes[1].notes[0].isCustos)
    }

    func testCustosStandsApartFromTheNotesBesideIt() throws {
        let timeline = makeTimeline(
            pitches: [5, 6, 7, 8, 9],
            syllables: ["Ky", "Ky", "Ky", "Ky", "Ky"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: custos; %% (c4) Ky(fgh+ij) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
        XCTAssertEqual(score.neumes.map { $0.notes.count }, [2, 1, 2])
        XCTAssertEqual(score.neumes.map(\.form), [.podatus, .punctum, .podatus])
        XCTAssertEqual(score.neumes.map { $0.notes[0].isCustos }, [false, true, false])
        XCTAssertEqual(score.neumes.map(\.lyric), ["Ky", "", ""])
    }

    func testPlusAmongOtherSignsIsNotACustos() throws {
        // Only a pitch whose signs end in `+` is a custos, as in Exsurge.
        let timeline = makeTimeline(pitches: [5], syllables: ["Ky"])
        let score = try GregorianScoreParser.parse(
            gabc: "name: custos; %% (c4) Ky(f+.) (::)",
            timeline: timeline
        )

        let note = try XCTUnwrap(score.neumes.first?.notes.first)
        XCTAssertEqual(note.shape, .punctum)
        XCTAssertTrue(note.hasMora)
    }

    func testZZeroWritesACustosAndBreaksNoLine() throws {
        // A respond's end before the verse's change of clef, as GABC
        // sources write it: the custos, the double bar and the new clef.
        let score = try GregorianScoreParser.parse(
            gabc: "name: z0; %% (c4) cis.(f_c) (z0::c3) V/.() Pro(d)pter(d) (z) quod(d) (::Z)"
        )
        let breaks = score.elements.filter { $0 == .forcedBreak }.count
        XCTAssertEqual(breaks, 2)
        XCTAssertEqual(score.elements.filter { $0 == .division(.final) }.count, 2)
        XCTAssertEqual(score.lyricText, "cis. ℣. Propter quod")
    }

    func testRepeatedVirgaAndStrophaAreOneEventWrittenSeveralTimes() throws {
        let timeline = makeTimeline(
            pitches: [7, 7, 7, 7, 7],
            syllables: ["bi", "tri", "di", "tris", "one"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) bi(hvv) tri(hvvv) di(hss.) tris(hsss) one(hv) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.eventIDs, timeline.events.map(\.id))
        XCTAssertEqual(score.neumes.map { $0.notes.count }, [1, 1, 1, 1, 1])
        XCTAssertEqual(score.neumes.map { $0.notes[0].repetitionCount }, [2, 3, 2, 3, 1])
        XCTAssertEqual(score.neumes.map(\.writtenNoteCount), [2, 3, 2, 3, 1])
        XCTAssertEqual(
            score.neumes.map(\.form),
            [.bivirga, .trivirga, .distropha, .tristropha, .virga]
        )
        XCTAssertEqual(
            score.neumes.map { $0.notes[0].shape },
            [.virga, .virga, .stropha, .stropha, .virga]
        )
        XCTAssertEqual(score.neumes[2].notes[0].moraCount, 1)
    }

    func testRepeatedNoteIsANeumeOfItsOwnWithinAGroup() throws {
        // From the corpus: a bivirga before descending diamonds, and a
        // distropha between a clivis and a torculus.
        let bivirga = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) et(gvvF'ED) (::)",
            timeline: makeTimeline(pitches: [6, 5, 4, 3], syllables: ["et", "et", "et", "et"])
        )
        XCTAssertEqual(bivirga.neumes.map { $0.notes.count }, [1, 3])
        XCTAssertEqual(bivirga.neumes.map(\.form), [.bivirga, .punctaInclinata])
        XCTAssertEqual(bivirga.neumes.map(\.writtenNoteCount), [2, 3])

        let distropha = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) ri(igfssded) (::)",
            timeline: makeTimeline(
                pitches: [8, 6, 5, 3, 4, 3],
                syllables: ["ri", "ri", "ri", "ri", "ri", "ri"]
            )
        )
        XCTAssertEqual(distropha.neumes.map { $0.notes.count }, [2, 1, 3])
        XCTAssertEqual(distropha.neumes.map(\.form), [.clivis, .distropha, .torculus])

        // A repeated note that ends a rising figure still stands alone.
        let scandicus = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) Ky(fghvv) (::)",
            timeline: makeTimeline(pitches: [5, 6, 7], syllables: ["Ky", "Ky", "Ky"])
        )
        XCTAssertEqual(scandicus.neumes.map { $0.notes.count }, [2, 1])
        XCTAssertEqual(scandicus.neumes.map(\.form), [.podatus, .bivirga])
    }

    func testVirgaAndStrophaSignsOfDifferentKindsDoNotRepeatANote() throws {
        let timeline = makeTimeline(pitches: [7, 7], syllables: ["a", "b"])
        let score = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) a(hsv) b(hvsv) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.neumes.map { $0.notes[0].repetitionCount }, [1, 1])
    }

    func testWordBeginsAfterAClefOrBarThatNoSpaceFollows() throws {
        // As many collects are written: a space before the bar, none after.
        let timeline = makeTimeline(
            pitches: [7, 6, 7, 7, 6, 7, 7],
            syllables: ["O", "ré", "ré", "mus.", "De", "us,", "qui"]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: words; %% (c4)O(h)ré(gh)mus.(h.) (:)De(g)us,(h) (,)qui(h) (::)",
            timeline: timeline
        )

        XCTAssertEqual(score.neumes.map(\.lyric), ["O", "ré", "mus.", "De", "us,", "qui"])
        XCTAssertEqual(
            score.neumes.map(\.startsWord),
            [true, false, false, true, false, true]
        )
        XCTAssertEqual(score.lyricText, "Orémus. Deus, qui")

        // No hyphen joins the end of one word to the beginning of the next.
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertFalse(
            layout.lyrics.contains {
                $0.neumeID == "lyric-hyphen-\(score.neumes[2].id)-\(score.neumes[3].id)"
                    || $0.neumeID == "lyric-hyphen-\(score.neumes[4].id)-\(score.neumes[5].id)"
            }
        )

        // A versicle sign after the clef, and the word after a clef that
        // follows the sign, each begin a word.
        let versicle = try GregorianScoreParser.parse(
            gabc: "name: words; %% (c3)V/.() (c4)Tu(h) es(h) (::)",
            timeline: makeTimeline(pitches: [7, 7], syllables: ["Tu", "es"])
        )
        let marks = versicle.elements.compactMap { element -> GregorianLyricMark? in
            guard case let .lyricMark(mark) = element else { return nil }
            return mark
        }
        XCTAssertEqual(marks.map(\.startsWord), [true])
        XCTAssertEqual(versicle.neumes.map(\.startsWord), [true, true])
        XCTAssertEqual(versicle.lyricText, "℣. Tu es")
    }

    func testWordContinuesThroughAMelismaAndTheBarsWithinIt() throws {
        // A bar inside a word, with no space on either side.
        let breath = try GregorianScoreParser.parse(
            gabc: "name: words; %% (c4) al(f)le(g)lú(gh)(,)ia.(g) (::)",
            timeline: makeTimeline(
                pitches: [5, 6, 6, 7, 6],
                syllables: ["al", "le", "lú", "lú", "ia."]
            )
        )
        XCTAssertEqual(breath.neumes.map(\.startsWord), [true, false, false, false])
        XCTAssertEqual(breath.lyricText, "allelúia.")

        // From the corpus: a melisma carried over bars and spaces before
        // the word's last syllable. Notes without words belong to the
        // syllable before them, and the word is still unfinished.
        let melisma = try GregorianScoreParser.parse(
            gabc: "name: words; %% (c4) co(f)ró(gh) (;) (ij) (,)(hg)na(g) (;) stel(h)lá(g)rum(f) (::)",
            timeline: makeTimeline(
                pitches: [5, 6, 7, 8, 9, 7, 6, 6, 7, 6, 5],
                syllables: ["co", "ró", "ró", "x", "x", "y", "y", "na", "stel", "lá", "rum"]
            )
        )
        XCTAssertEqual(melisma.lyricText, "coróna stellárum")
        XCTAssertEqual(
            melisma.neumes.filter { !$0.lyric.isEmpty }.map(\.startsWord),
            [true, false, false, true, false, false]
        )

        // An elided vowel written after an empty group stays with its word.
        let elision = try GregorianScoreParser.parse(
            gabc: "name: words; %% (c4) Cum(k) Patr(k) ()_e_(i) () et(i) (::)",
            timeline: makeTimeline(pitches: [10, 10, 8, 8], syllables: ["Cum", "Patr", "e", "et"])
        )
        XCTAssertEqual(elision.lyricText, "Cum Patre et")
    }

    func testTimelineMismatchIsAValidationFailure() {
        let timeline = makeTimeline(pitches: [5], syllables: ["Ky"])
        XCTAssertThrowsError(
            try GregorianScoreParser.parse(
                gabc: "name: mismatch; %% Ky(c4fg)",
                timeline: timeline
            )
        ) { error in
            guard case GregorianScoreParserError.tooManyNotationNotes = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    func testUnsupportedTokenIsAValidationFailure() {
        let timeline = makeTimeline(pitches: [5], syllables: ["Ky"])
        XCTAssertThrowsError(
            try GregorianScoreParser.parse(
                gabc: "name: unsupported; %% Ky(c4f?)",
                timeline: timeline
            )
        ) { error in
            guard case GregorianScoreParserError.unsupportedToken("?", _) = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    private func makeTimeline(pitches: [Int], syllables: [String]) -> GregorianEventTimeline {
        precondition(pitches.count == syllables.count)
        var syllableNumber = 0
        var previousSyllable: String?
        return GregorianEventTimeline(
            events: zip(pitches, syllables).enumerated().map { index, pair in
                if pair.1 != previousSyllable {
                    syllableNumber += 1
                    previousSyllable = pair.1
                }
                return GregorianEvent(
                    id: "fixture-note-\(index)",
                    syllableID: "fixture-syllable-\(syllableNumber)",
                    relativePitch: pair.0 - 7
                )
            }
        )
    }
}
