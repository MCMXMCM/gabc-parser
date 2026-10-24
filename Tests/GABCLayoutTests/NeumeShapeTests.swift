import GABCLayout
import GABCLyricFont
import XCTest

/// The neumes drawn as Exsurge draws them, and their signs set where the
/// chant books set them.
final class NeumeShapeTests: XCTestCase {
    /// One staff space, and one step, at the default notation scale.
    private let staffSpace: CGFloat = 12.5
    private let staffStep: CGFloat = 6.25
    private let stemWidth: CGFloat = 18.0 / 16

    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    private func layout(_ notation: String) throws -> (GregorianScore, GregorianLayout) {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) a(\(notation)) (::)")
        return (score, GregorianEngravingLayoutEngine().layout(score: score, width: 390))
    }

    private func heads(_ layout: GregorianLayout, _ score: GregorianScore) -> [GregorianPlacedGlyph] {
        let notes = Set(score.eventIDs)
        return layout.glyphs
            .filter { glyph in
                guard let eventID = glyph.eventID, notes.contains(eventID) else { return false }
                if case .catalog = glyph.kind { return glyph.kind != .catalog(.verticalEpisemaAbove) && glyph.kind != .catalog(.verticalEpisemaBelow) }
                return false
            }
            .sorted { $0.frame.minX < $1.frame.minX }
    }

    private func stems(_ layout: GregorianLayout) -> [GregorianPlacedStroke] {
        layout.strokes.filter { $0.kind == .connector }
    }

    func testAClivisHangsFromAStemOnItsLeft() throws {
        let (score, layout) = try layout("hf")
        let notes = heads(layout, score)
        XCTAssertEqual(notes.count, 2)
        let stem = try XCTUnwrap(stems(layout).first)
        // On the left of the upper note, from its centre down past the lower.
        XCTAssertEqual(stem.start.x, notes[0].frame.minX + stemWidth / 2, accuracy: 0.01)
        XCTAssertEqual(stem.start.y, notes[0].frame.midY, accuracy: 1.5)
        XCTAssertGreaterThan(stem.end.y, notes[1].frame.midY)
    }

    func testAPesIsJoinedOnItsRightWhereItLeaps() throws {
        let (score, layout) = try layout("fh")
        let names = heads(layout, score).map(\.kind)
        XCTAssertEqual(names, [.catalog(.podatusLower), .catalog(.podatusUpper)])
        let stem = try XCTUnwrap(stems(layout).first)
        let lower = heads(layout, score)[0]
        XCTAssertEqual(stem.start.x + stemWidth / 2, lower.frame.maxX, accuracy: 0.01)

        // A pes of a second takes the short halves, which meet.
        let (second, secondLayout) = try self.layout("fg")
        XCTAssertEqual(heads(secondLayout, second).map(\.kind), [.catalog(.podatusLowerShort), .catalog(.podatusUpperShort)])
        XCTAssertTrue(stems(secondLayout).isEmpty)
    }

    func testAPorrectusSetsItsLastNoteOverTheEndOfItsOblique() throws {
        let (score, layout) = try layout("hgh")
        let notes = heads(layout, score)
        let swash = try XCTUnwrap(notes.first { $0.kind == .catalog(.porrectus1) })
        let last = try XCTUnwrap(notes.first { $0.kind == .catalog(.podatusUpperShort) })
        XCTAssertEqual(last.frame.maxX, swash.frame.maxX, accuracy: 0.01)
        // And hangs from a stem on its left.
        XCTAssertTrue(stems(layout).contains { abs($0.start.x - stemWidth / 2 - swash.frame.minX) < 0.01 })
    }

    func testTheDiamondsOfAClimacusStepDownUnjoined() throws {
        let (score, layout) = try layout("hvGF")
        let notes = heads(layout, score)
        XCTAssertEqual(notes.map(\.kind), [.catalog(.punctumQuadratum), .catalog(.punctumInclinatum), .catalog(.punctumInclinatum)])
        // The virga's stem is the only one.
        XCTAssertEqual(stems(layout).count, 1)
        XCTAssertGreaterThan(notes[2].frame.minX, notes[1].frame.minX)
        XCTAssertGreaterThan(notes[2].frame.midY, notes[1].frame.midY)
    }

    func testPlainNotesRepeatedOnOnePitchArePuncta() throws {
        let (score, layout) = try layout("fff")
        XCTAssertEqual(heads(layout, score).map(\.kind), Array(repeating: .catalog(.punctumQuadratum), count: 3))
        // Where the melody goes on, the last of them begins a pressus, its
        // punctum abutting the pes.
        let (pressus, pressusLayout) = try self.layout("ffg")
        XCTAssertEqual(pressus.neumes.map(\.form), [.punctum, .podatus])
        let notes = heads(pressusLayout, pressus)
        XCTAssertEqual(notes[1].frame.minX - notes[0].frame.maxX, 0, accuracy: 0.01)
    }

    func testADotStandsJustAfterItsNoteInTheSpaceAboveALine() throws {
        // h is on the third line, g in the space below it.
        for (notation, raised) in [("h.", true), ("g.", false)] {
            let (score, layout) = try layout(notation)
            let head = try XCTUnwrap(heads(layout, score).first)
            let dot = try XCTUnwrap(layout.glyphs.first { $0.kind == .mora })
            XCTAssertEqual(dot.frame.width, 0.33 * staffSpace, accuracy: 0.01, notation)
            XCTAssertEqual(dot.frame.minX - head.frame.maxX, 0.1 * staffSpace, accuracy: 0.01, notation)
            let headCentre = head.frame.minY + 60.906 / 16
            XCTAssertEqual(dot.frame.midY, headCentre - (raised ? 0.75 * staffStep : 0), accuracy: 0.01, notation)
        }
    }

    func testADoubleDotDotsANeumesLastTwoNotes() throws {
        let (score, layout) = try layout("hg..")
        let notes = heads(layout, score)
        let dots = layout.glyphs.filter { $0.kind == .mora }
        XCTAssertEqual(dots.count, 2)
        // Both after the neume, one at each note's height.
        XCTAssertTrue(dots.allSatisfy { $0.frame.minX > notes[1].frame.maxX })
        XCTAssertEqual(Set(dots.map(\.eventID)), Set(score.eventIDs))
    }

    func testAnEpisemaKeepsOffTheStaffsLines() throws {
        for notation in ["h_", "g_", "f_0", "j_", "hg__"] {
            let (score, layout) = try layout(notation)
            let staff = try XCTUnwrap(layout.staffs.first)
            let lines = (0..<4).map { staff.frame.minY + CGFloat($0) * staffSpace }
            let episemata = layout.strokes.filter { $0.kind == .episema }
            XCTAssertEqual(episemata.count, 1, notation)
            for episema in episemata {
                XCTAssertTrue(lines.allSatisfy { abs($0 - episema.start.y) > 1 }, notation)
            }
            // One that the GABC reaches over two notes spans both.
            if notation == "hg__" {
                let notes = heads(layout, score)
                XCTAssertEqual(episemata[0].start.x, notes[0].frame.minX, accuracy: 0.01)
                XCTAssertEqual(episemata[0].end.x, notes[1].frame.maxX, accuracy: 0.01)
            }
        }
    }

    func testAnIctusStandsBelowUnlessTheNeumeOrTheGABCSetsItAbove() throws {
        for (notation, above) in [("g'", false), ("g'1", true), ("g'0", false), ("h'g", true), ("fg'", true), ("hvGF'", false)] {
            let (_, layout) = try layout(notation)
            let ictus = try XCTUnwrap(
                layout.glyphs.first { $0.kind == .catalog(.verticalEpisemaAbove) || $0.kind == .catalog(.verticalEpisemaBelow) },
                notation
            )
            XCTAssertEqual(ictus.kind == .catalog(.verticalEpisemaAbove), above, notation)
        }
    }

    func testAQuarterBarCrossesTheTopLine() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) a(f) (,) b(g) (::)")
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        let staff = try XCTUnwrap(layout.staffs.first)
        let bar = try XCTUnwrap(layout.strokes.first { $0.kind == .division(.minima) })
        XCTAssertEqual(bar.start.y, staff.frame.minY - staffSpace / 2, accuracy: 0.01)
        XCTAssertEqual(bar.end.y, staff.frame.minY + staffSpace / 2, accuracy: 0.01)
    }
}
