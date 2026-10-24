import GABCLayout
import GABCLyricFont
import XCTest

/// The spaces between the notes and the staffs they are set on: the spaces
/// the GABC writes between a syllable's neumes; staffs justified at their
/// words and bars, never within a word; and breaks taken where the chant
/// books take them.
final class LineLayoutTests: XCTestCase {
    /// One staff space at the default notation scale.
    private let staffSpace: CGFloat = 12.5

    /// A psalm verse: short words under single notes, dotted cadences and
    /// bars, laid out at widths that make several staffs of it.
    private static let psalm = [
        "%% (c3) Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
        "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
        "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
        "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
        "in(h) o(f)re(h) me(g)o.(f.) (::)",
    ].joined(separator: " ")

    /// A short responsory: each rubric that heads a respond or a verse is
    /// written with the bar before it.
    private static let responsory = [
        "%% (c4)In(f) ma(f)nus(f) tu(fg)as(g) Dó(gf)mi(g)ne,(g.h.) *(;)",
        "Com(h')mén(f)do(gh) spí(g)ri(fd)tum(fg) me(g.)um.(f.) R/.(::)In(f) ma(f)nus...(f)",
        "V/.(::)Red(h)e(h)mí(ixhi)sti(h) nos(h) Dó(gh)mi(g)ne,(g'_) (,) De(h)us(g') ve(g)ri(gf)tá(g)tis.(g.h.)",
        "R/.(::)Com(h')mén(f)do(gh) spí(g)ri(fd)tum(fg) me(g.)um.(f.) V/.(::)In(f) ma(f)nus...(f) (::)",
    ].joined(separator: " ")

    private let widths = stride(from: CGFloat(240), through: 820, by: 5)

    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    func testTheSpacesWrittenBetweenASyllablesNeumesPartThem() throws {
        // `/` writes a quarter staff space, `//` and a space within the
        // group half of one, and `!` none.
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) A(f/g//h i!j)men.(h) (::)")
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let syllable = try XCTUnwrap(score.neumes.first { $0.lyric == "A" })
        let noteIDs = Set(
            score.neumes.filter { $0.syllableID == syllable.syllableID }.flatMap(\.notes).map(\.id)
        )
        let heads = layout.glyphs
            .filter { $0.eventID.map(noteIDs.contains) == true }
            .sorted { $0.frame.minX < $1.frame.minX }
        XCTAssertEqual(heads.count, 5)
        let gaps = zip(heads, heads.dropFirst()).map { $1.frame.minX - $0.frame.maxX }
        for (gap, expected) in zip(gaps, [0.25, 0.5, 0.5, 0]) {
            XCTAssertEqual(gap, expected * staffSpace, accuracy: 0.01)
        }
    }

    func testEachStaffButTheLastIsJustifiedToItsEnd() throws {
        let score = try GregorianScoreParser.parse(gabc: Self.psalm)
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            XCTAssertGreaterThan(layout.staffs.count, 1, "width \(width)")
            for staff in layout.staffs.dropLast() {
                let end = staff.frame.maxX
                // The words reach the staff's end, or the notes or a bar
                // the room short of the custos that is theirs.
                let words = layout.lyrics
                    .filter { staff.systemFrame.minY <= $0.origin.y && $0.origin.y < staff.systemFrame.maxY }
                    .map { $0.origin.x + $0.width - end }
                let notes = layout.neumes
                    .filter { $0.lineIndex == staff.index }
                    .map { $0.inkFrame.maxX - (end - 10) }
                let bars = layout.strokes
                    .filter { staff.frame.minY <= $0.midY && $0.midY <= staff.frame.maxY }
                    .compactMap { stroke -> CGFloat? in
                        guard case let .division(division) = stroke.kind else { return nil }
                        return stroke.start.x + (division == .final ? 4.5 : 6) - (end - 10)
                    }
                let reach = try XCTUnwrap((words + notes + bars).max())
                XCTAssertEqual(reach, 0, accuracy: 1, "width \(width), staff \(staff.index)")
            }
        }
    }

    func testABarStandsMidwayBetweenItsNotes() throws {
        // Words of one letter under single notes, so that no word holds a
        // bar back.
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) " + (0..<24).map { index in
                "\(["a", "e", "o"][index % 3])(\(["f", "g", "h", "g"][index % 4]))"
                    + (index % 4 == 3 ? " (,)" : "")
            }.joined(separator: " ") + " (::)"
        )
        var bars = 0
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            for staff in layout.staffs {
                let heads = layout.glyphs
                    .filter { $0.eventID != nil && staff.frame.minY - 20 <= $0.frame.minY && $0.frame.maxY <= staff.frame.maxY + 20 }
                    .map(\.frame)
                for stroke in layout.strokes {
                    guard case .division(.minima) = stroke.kind,
                          staff.frame.minY <= stroke.midY, stroke.midY <= staff.frame.maxY,
                          let before = heads.filter({ $0.maxX < stroke.start.x }).map(\.maxX).max(),
                          let after = heads.filter({ $0.minX > stroke.start.x }).map(\.minX).min() else {
                        continue
                    }
                    XCTAssertEqual(
                        stroke.start.x - before,
                        after - stroke.start.x,
                        accuracy: 0.5,
                        "width \(width), staff \(staff.index)"
                    )
                    bars += 1
                }
            }
        }
        XCTAssertGreaterThan(bars, 0)
    }

    func testTheCustosStandsFlushWithTheStaffsEnd() throws {
        let score = try GregorianScoreParser.parse(gabc: Self.psalm)
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        let custodes = layout.glyphs.filter { $0.kind == .custos }
        XCTAssertEqual(custodes.count, layout.staffs.count - 1)
        for custos in custodes {
            XCTAssertEqual(custos.frame.maxX, layout.staffs[0].frame.maxX, accuracy: 0.01)
        }
    }

    func testRubricsStayWithTheWordsTheyHeadOrClose() throws {
        let score = try GregorianScoreParser.parse(gabc: Self.responsory)
        let marks = Dictionary(
            uniqueKeysWithValues: score.elements.compactMap { element -> (String, GregorianLyricMark)? in
                guard case let .lyricMark(mark) = element else { return nil }
                return (mark.id, mark)
            }
        )
        XCTAssertEqual(marks.values.filter(\.beginsWhatFollows).count, 4)
        assertRubricsStayWithTheirWords(score, marks: marks, writtenWithBars: true)

        // Asterisks written with the word after them, as a psalm's mediant
        // is (`*~ab`): bound to it, and still never beginning a staff.
        let mediants = try GregorianScoreParser.parse(
            gabc: "%% (c4) " + (0..<6).map { _ in
                "læ(h)tí(h)ti(h)a(h) *~ab(h) in(h)i(h)mí(h)cis(h) me(g)is.(f.) (::)"
            }.joined(separator: " ")
        )
        let asterisks = Dictionary(
            uniqueKeysWithValues: mediants.elements.compactMap { element -> (String, GregorianLyricMark)? in
                guard case let .lyricMark(mark) = element else { return nil }
                return (mark.id, mark)
            }
        )
        XCTAssertEqual(asterisks.count, 6)
        assertRubricsStayWithTheirWords(mediants, marks: asterisks, writtenWithBars: false)
    }

    private func assertRubricsStayWithTheirWords(
        _ score: GregorianScore,
        marks: [String: GregorianLyricMark],
        writtenWithBars: Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            for staff in layout.staffs {
                let words = layout.lyrics
                    .filter { staff.systemFrame.minY <= $0.origin.y && $0.origin.y < staff.systemFrame.maxY }
                    .sorted { $0.origin.x < $1.origin.x }
                if let first = words.first, let mark = marks[first.neumeID] {
                    XCTAssertTrue(
                        mark.beginsWhatFollows && !mark.text.hasPrefix("*"),
                        "\(mark.text) begins a staff at width \(width)",
                        file: file, line: line
                    )
                }
                if let last = words.last, let mark = marks[last.neumeID] {
                    XCTAssertFalse(mark.beginsWhatFollows, "\(mark.text) ends a staff at width \(width)", file: file, line: line)
                }
                // Within a staff, a rubric that heads what follows stands
                // after the bar written with it.
                for lyric in words.dropFirst() where writtenWithBars && marks[lyric.neumeID]?.beginsWhatFollows == true {
                    let bar = layout.strokes.contains { stroke in
                        guard case .division = stroke.kind else { return false }
                        return staff.frame.minY <= stroke.midY && stroke.midY <= staff.frame.maxY
                            && stroke.start.x <= lyric.origin.x + 0.5
                            && words.allSatisfy { $0.origin.x >= lyric.origin.x || $0.origin.x + $0.width <= stroke.start.x + 0.5 }
                    }
                    XCTAssertTrue(bar, "\(lyric.text) stands before its bar at width \(width)", file: file, line: line)
                }
            }
        }
    }

    func testAnAccidentalStandsJustBeforeItsNoteOnTheSameStaff() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) " + (0..<20).map { index in
                index % 3 == 1 ? "Ky(ixhi)" : "ri(\(["g", "h", "f"][index % 3]))"
            }.joined(separator: " ") + " (::)"
        )
        var flats = 0
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            let heads = layout.glyphs.filter { $0.eventID != nil }
            for flat in layout.glyphs where flat.kind == .flat {
                // The nearest note after the flat, on its staff: about a
                // quarter of a staff space after it.
                let next = heads
                    .filter { $0.frame.minX > flat.frame.minX && abs($0.frame.midY - flat.frame.midY) < 2 * staffSpace }
                    .min { $0.frame.minX < $1.frame.minX }
                let gap = try XCTUnwrap(next, "a flat ends a staff at width \(width)").frame.minX - flat.frame.maxX
                XCTAssertEqual(gap, 0.27 * staffSpace, accuracy: 0.25, "width \(width)")
                flats += 1
            }
        }
        XCTAssertGreaterThan(flats, 0)
    }

    func testNoStaffBeginsWithABar() throws {
        let score = try GregorianScoreParser.parse(gabc: Self.psalm)
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            for stroke in layout.strokes {
                guard case .division = stroke.kind,
                      let staff = layout.staffs.first(where: {
                          $0.frame.minY <= stroke.midY && stroke.midY <= $0.frame.maxY
                      }) else {
                    continue
                }
                XCTAssertTrue(
                    layout.events.contains { $0.lineIndex == staff.index && $0.frame.maxX < stroke.start.x },
                    "a bar begins staff \(staff.index) at width \(width)"
                )
            }
        }
    }

    func testALongMelismaIsDividedOnlyWhereFourNotesStandOnEitherSide() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) Al(f)le(gh)lú(hjih/gh/ghg/hjih/ghgf/ghg/fgf/ghg/hih/gf/ghg/fe)ia.(gf) (::)"
        )
        let syllable = try XCTUnwrap(score.neumes.first { $0.lyric == "lú" })
        let noteIDs = Set(
            score.neumes.filter { $0.syllableID == syllable.syllableID }.flatMap(\.notes).map(\.id)
        )
        var divided = 0
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            let lines = layout.events.filter { noteIDs.contains($0.eventID) }.map(\.lineIndex)
            let counts = Dictionary(grouping: lines) { $0 }.mapValues(\.count)
            guard counts.count > 1 else { continue }
            divided += 1
            for (line, count) in counts where line == lines.first || line == lines.last {
                XCTAssertGreaterThanOrEqual(count, 4, "width \(width), staff \(line)")
            }
        }
        XCTAssertGreaterThan(divided, 0)
    }

    func testTheLastStaffHoldsMoreThanOneSyllable() throws {
        let score = try GregorianScoreParser.parse(gabc: Self.psalm)
        for width in widths {
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
            guard let last = layout.staffs.last, layout.staffs.count > 1 else { continue }
            let syllables = layout.neumes.filter { $0.lineIndex == last.index && !$0.lyric.isEmpty }
            XCTAssertGreaterThan(syllables.count, 1, "width \(width)")
        }
    }
}

private extension GregorianPlacedStroke {
    /// The height of a stroke's middle: a quarter bar's is the top line it
    /// crosses.
    var midY: CGFloat { (start.y + end.y) / 2 }
}
