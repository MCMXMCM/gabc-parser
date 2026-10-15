import GABCParser
import XCTest

/// Parsing without a compiled timeline: the parser names each note and
/// syllable itself, as the content compiler names a timeline's events.
final class NumberedEventTests: XCTestCase {
    func testNamesNotesInOrderAndGivesEachNotatedGroupASyllable() throws {
        // A forced break and a clef write no note, and take no syllable.
        let score = try GregorianScoreParser.parse(
            gabc: "name: clefs; %% (c3) Al(dfe)le(g) (z) (f3) lú(hi)ia.(g.) (::)",
            scoreID: "clefs"
        )

        XCTAssertEqual(score.eventIDs, (0..<7).map { "clefs-note-\($0)" })
        XCTAssertEqual(
            score.neumes.map(\.syllableID),
            ["clefs-syllable-0", "clefs-syllable-1", "clefs-syllable-2", "clefs-syllable-3"]
        )
        XCTAssertEqual(score.neumes.map(\.lyric), ["Al", "le", "lú", "ia."])
    }

    func testNamesAreUnprefixedWithoutAScoreIdentifier() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) Ky(fg)ri(h) (::)")

        XCTAssertEqual(score.eventIDs, ["note-0", "note-1", "note-2"])
        XCTAssertEqual(score.neumes.map(\.id), ["note-0", "note-2"])
        XCTAssertEqual(score.neumes.map(\.syllableID), ["syllable-0", "syllable-1"])
    }

    func testRelativePitchCountsStaffStepsFromH() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) a(h) b(a) c(m) d(G) (::)")

        XCTAssertEqual(
            score.neumes.flatMap(\.notes).map(\.relativePitch),
            [0, -7, 5, -1]
        )
        XCTAssertEqual(score.neumes.flatMap(\.notes).map(\.pitch), [7, 0, 12, 6])
    }

    func testMelismaGroupWithoutLyricIsASyllableOfItsOwn() throws {
        // As the compiler names it, the melisma written after "Ky" in a group
        // of its own is a syllable of its own, and carries no lyric.
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) Ky(f)(gh)ri(g)e(h) (::)",
            scoreID: "k"
        )

        XCTAssertEqual(score.neumes.map(\.lyric), ["Ky", "", "ri", "e"])
        XCTAssertEqual(
            score.neumes.map(\.syllableID),
            ["k-syllable-0", "k-syllable-1", "k-syllable-2", "k-syllable-3"]
        )
        XCTAssertEqual(score.lyricText, "Kyrie")
    }

    func testWrittenCustosIsAnEventAndAccidentalIsNot() throws {
        let custos = try GregorianScoreParser.parse(
            gabc: "%% (c3) tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)"
        )
        XCTAssertEqual(custos.eventIDs.count, 5)
        XCTAssertEqual(custos.neumes.map(\.writtenNoteCount), [1, 1, 0, 1, 1])
        XCTAssertEqual(custos.neumes[2].syllableID, "syllable-2")

        let accidental = try GregorianScoreParser.parse(gabc: "%% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)")
        XCTAssertEqual(accidental.eventIDs.count, 5)
        XCTAssertEqual(accidental.neumes.map(\.form), [.punctum, .punctum, .podatus, .punctum])
    }

    func testSharedFixturesNumberAsTheirCompiledTimelines() throws {
        for fixture in try SharedFixture.all() {
            let numbered = try GregorianScoreParser.parse(
                gabc: fixture.gabc,
                scoreID: fixture.scoreID
            )
            XCTAssertEqual(
                numbered.eventIDs,
                (0..<fixture.eventCount).map { "\(fixture.scoreID)-note-\($0)" },
                fixture.name
            )
            XCTAssertEqual(numbered.neumes.map { $0.notes.count }, fixture.neumeSizes, fixture.name)

            // The timeline the compiler gives these scores names their notes
            // and syllables the same: parsed against it, the score is the same.
            let pitches = numbered.neumes.flatMap(\.notes).map(\.relativePitch)
            var events: [GregorianEvent] = []
            for (syllableIndex, neumeSize) in fixture.neumeSizes.enumerated() {
                for _ in 0..<neumeSize {
                    events.append(
                        GregorianEvent(
                            id: "\(fixture.scoreID)-note-\(events.count)",
                            syllableID: "\(fixture.scoreID)-syllable-\(syllableIndex)",
                            relativePitch: pitches[events.count]
                        )
                    )
                }
            }
            let compiled = try GregorianScoreParser.parse(
                gabc: fixture.gabc,
                timeline: GregorianEventTimeline(events: events)
            )
            XCTAssertEqual(compiled, numbered, fixture.name)
        }
    }

    func testNotationWithoutAHeaderIsReadAsTheBody() throws {
        let body = "(c4) Ky(f)ri(g)e(h) e(f)le(g)i(h)son.(g.) (::)"
        let withHeader = try GregorianScoreParser.parse(gabc: "name: Kyrie; mode: 6;\n%%\(body)")
        let withoutHeader = try GregorianScoreParser.parse(gabc: body)

        XCTAssertEqual(withoutHeader, withHeader)
        XCTAssertEqual(withoutHeader.lyricText, "Kyrie eleison.")
    }

    func testEmptyNotationIsAnEmptyScore() throws {
        XCTAssertEqual(try GregorianScoreParser.parse(gabc: ""), GregorianScore(elements: []))
        XCTAssertEqual(try GregorianScoreParser.parse(gabc: "name: empty;\n%%\n"), GregorianScore(elements: []))
    }

    func testMalformedNotationStillFails() {
        XCTAssertThrowsError(try GregorianScoreParser.parse(gabc: "%% (c4) Ky(f")) { error in
            guard case GregorianScoreParserError.unterminatedNotation = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
        XCTAssertThrowsError(try GregorianScoreParser.parse(gabc: "%% (c4) Ky(f?)")) { error in
            guard case GregorianScoreParserError.unsupportedToken("?", _) = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
        XCTAssertThrowsError(try GregorianScoreParser.parse(gabc: "%% (c5) Ky(f)")) { error in
            guard case GregorianScoreParserError.invalidClef("c5", _) = error else {
                return XCTFail("Unexpected error: \(error)")
            }
        }
    }

    func testAnyTimelineTypeCanNameTheNotes() throws {
        struct Note: GregorianTimelineEvent {
            let id: String
            let syllableID: String
            let relativePitch: Int
        }
        struct Timeline: GregorianTimeline {
            let events: [Note]
        }

        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) Ky(fg)ri(h) (::)",
            timeline: Timeline(
                events: [
                    Note(id: "a", syllableID: "ky", relativePitch: -2),
                    Note(id: "b", syllableID: "ky", relativePitch: -1),
                    Note(id: "c", syllableID: "ri", relativePitch: 0)
                ]
            )
        )

        XCTAssertEqual(score.eventIDs, ["a", "b", "c"])
        XCTAssertEqual(score.neumes.map(\.syllableID), ["ky", "ri"])
    }
}
