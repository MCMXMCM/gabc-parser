import GABCLayout
import GABCLyricFont
import XCTest

/// The score as a screen shows it and a page would set it: the words no
/// lower than the notes need.
final class ScreenAndPageTests: XCTestCase {
    /// One step of the staff, at the default notation scale.
    private let staffStep: CGFloat = 6.25

    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    private func layout(_ notation: String) throws -> GregorianLayout {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) a(\(notation)) (::)")
        return GregorianEngravingLayoutEngine().layout(score: score, width: 390)
    }

    /// The lowest ink of a layout's notes and signs, its staff lines aside.
    private func lowestInk(_ layout: GregorianLayout) -> CGFloat {
        let glyphs = layout.glyphs.map(\.frame.maxY)
        let strokes = layout.strokes
            .filter { $0.kind != .staff }
            .map { max($0.start.y, $0.end.y) + $0.lineWidth / 2 }
        return (glyphs + strokes).max() ?? 0
    }

    /// The highest ink of a layout's notes and signs, its staff lines aside.
    private func highestInk(_ layout: GregorianLayout) -> CGFloat {
        let glyphs = layout.glyphs.map(\.frame.minY)
        let strokes = layout.strokes
            .filter { $0.kind != .staff }
            .map { min($0.start.y, $0.end.y) - $0.lineWidth / 2 }
        return (glyphs + strokes).min() ?? 0
    }

    func testTheWordsStandAsFarBelowTheLowestInkAsBelowANoteOnTheBottomLine() throws {
        // d is on the bottom line, c in the space below it and b on a ledger
        // line; an ictus stands below its note, and a clivis hangs from its
        // stem.
        let bottomLine = try layout("d")
        let words = try XCTUnwrap(bottomLine.lyrics.first).origin.y
        let clearance = words - lowestInk(bottomLine)
        for notation in ["c", "b", "d'", "dc", "fc"] {
            let lower = try layout(notation)
            let lyric = try XCTUnwrap(lower.lyrics.first, notation)
            XCTAssertEqual(lyric.origin.y - lowestInk(lower), clearance, accuracy: 0.01, notation)
        }
        // A note in the space below the staff lowers the words by a step, no
        // more.
        XCTAssertEqual(try XCTUnwrap(layout("c").lyrics.first).origin.y - words, staffStep, accuracy: 0.01)

        // Notes higher on the staff leave the words where a note on the
        // bottom line does.
        let high = try layout("h")
        let staff = try XCTUnwrap(high.staffs.first)
        let bottomStaff = try XCTUnwrap(bottomLine.staffs.first)
        XCTAssertEqual(
            try XCTUnwrap(high.lyrics.first).origin.y - staff.frame.maxY,
            words - bottomStaff.frame.maxY,
            accuracy: 0.01
        )
    }

    func testAStaffsBandHoldsAllItsInk() throws {
        // The episema of a liquescent clivis above the staff rises higher
        // than its notes leave room for: the staff is set lower to hold it,
        // a point to spare.
        let lifted = try layout("k_i~")
        let liftedStaff = try XCTUnwrap(lifted.staffs.first)
        XCTAssertGreaterThan(liftedStaff.frame.minY, try XCTUnwrap(layout("ki~").staffs.first).frame.minY)
        let episema = try XCTUnwrap(lifted.strokes.first { $0.kind == .episema })
        XCTAssertGreaterThanOrEqual(episema.start.y - episema.lineWidth / 2, liftedStaff.systemFrame.minY + 1 - 0.01)
        for notation in ["k_i~", "k_", "l_", "j_", "j"] {
            let layout = try layout(notation)
            let staff = try XCTUnwrap(layout.staffs.first, notation)
            XCTAssertGreaterThanOrEqual(highestInk(layout), staff.systemFrame.minY, notation)
        }
    }

    func testOnAScreenTheStaffAndItsStrokesFallOnWholePixels() throws {
        // Stems, an episema, ledger lines above and below the staff, and
        // bars of every kind, over several staffs.
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) Lau(f)da(fhv)te(hg/hiH'G) Dó(kl)mi(lk)num(k.) (,) om(ji)nes(hg_) gen(gf)tes:(fe..) (;) "
                + "lau(dc)da(cd)te(b) e(dfd)um(d.) (:) om(hj)nes(jk) pó(lm)pu(ml)li.(k) (::)"
        )
        let engine = GregorianEngravingLayoutEngine()
        for scale in [2.0, 3.0] {
            let layout = engine.layout(
                score: score,
                width: 260,
                metrics: GregorianLayoutMetrics(displayScale: scale)
            )
            XCTAssertGreaterThan(layout.staffs.count, 1)
            func onGrid(_ points: CGFloat, offset: CGFloat = 0, _ message: String) {
                let pixels = points * scale - offset
                XCTAssertEqual(pixels, pixels.rounded(), accuracy: 0.001, "\(scale)x: \(message)")
            }
            // A staff space of whole pixels, the nearest to 12.5 points, and
            // every staff begun on the grid.
            let staffSpace = try XCTUnwrap(layout.staffs.first).frame.height / 3
            XCTAssertEqual(staffSpace, (12.5 * scale).rounded() / scale, accuracy: 0.001)
            for staff in layout.staffs {
                onGrid(staff.systemFrame.minY, "staff \(staff.index) begins")
            }
            for stroke in layout.strokes {
                // Whole pixels wide, two at least, with its edges between
                // pixels.
                let pixels = (stroke.lineWidth * scale).rounded()
                onGrid(stroke.lineWidth, "\(stroke.kind) width")
                XCTAssertGreaterThanOrEqual(pixels, 2)
                let offset: CGFloat = pixels.truncatingRemainder(dividingBy: 2) == 0 ? 0 : 0.5
                if stroke.start.y == stroke.end.y {
                    onGrid(stroke.start.y, offset: offset, "\(stroke.kind) across")
                } else if stroke.start.x == stroke.end.x {
                    onGrid(stroke.start.x, offset: offset, "\(stroke.kind) up and down")
                }
            }
            // Stems and joins one weight: a point, or as near as whole
            // pixels come.
            let stems = Set(layout.strokes.filter { $0.kind == .connector }.map(\.lineWidth))
            XCTAssertEqual(stems, [(1.125 * scale).rounded() / scale])
        }
    }

    func testAPieceWithoutAPsalmToneTakesAnInitialUnderItsMode() throws {
        XCTAssertEqual(GregorianEngravingLayoutEngine.openingLabel(forMode: "8", opensPiece: true), "8")
        XCTAssertEqual(GregorianEngravingLayoutEngine.openingLabel(forMode: "4. alt", opensPiece: true), "4")
        XCTAssertEqual(GregorianEngravingLayoutEngine.openingLabel(forMode: "VIII", opensPiece: true), "8")
        XCTAssertEqual(GregorianEngravingLayoutEngine.openingLabel(forMode: "8g", opensPiece: true), "8G")
        XCTAssertNil(GregorianEngravingLayoutEngine.openingLabel(forMode: "irreg.", opensPiece: true))
        // Only a piece takes one: a psalm's verses, a tone with no
        // conclusion, take none.
        XCTAssertNil(GregorianEngravingLayoutEngine.openingLabel(forMode: "8", opensPiece: false))

        let score = try GregorianScoreParser.parse(gabc: "%% (c4) TE(f) lu(g)cis(h) (::)")
        let engine = GregorianEngravingLayoutEngine()
        XCTAssertTrue(engine.layout(score: score, width: 390, openingLabel: "8").initials.isEmpty)
        let hymn = engine.layout(score: score, width: 390, openingLabel: "8", opensPiece: true)
        let initial = try XCTUnwrap(hymn.initials.first)
        XCTAssertEqual(initial.text, "T")
        XCTAssertEqual(initial.annotation, "8")
        XCTAssertEqual(initial.style, .accented)
        XCTAssertEqual(hymn.lyrics.first?.text, "e")
        // A piece with no mode takes an initial with nothing above it.
        let unmarked = engine.layout(score: score, width: 390, opensPiece: true)
        XCTAssertEqual(unmarked.initials.map(\.text), ["T"])
        XCTAssertNil(unmarked.initials.first?.annotation)
    }

    func testAnInitialStandsOnTheWordsBaselineUnderItsAnnotation() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) SAL(g)ve(h) Re(h)gí(g)na(f) (::)")
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390, openingLabel: "1", opensPiece: true)
        let initial = try XCTUnwrap(layout.initials.first)
        let lyric = try XCTUnwrap(layout.lyrics.first)
        // The rest of the word small, however the GABC writes it.
        XCTAssertEqual(lyric.text, "al")
        let puer = GregorianEngravingLayoutEngine().layout(
            score: try GregorianScoreParser.parse(gabc: "%% (c4) PU(ei)ER(i.) *() na(iji)tus(h) (::)"),
            width: 390,
            openingLabel: "7",
            opensPiece: true
        )
        XCTAssertEqual(puer.lyrics.prefix(3).map(\.text), ["u", "er", "*"])
        // EB Garamond rises 1.007 em above its baseline.
        let ascent = 1.007
        XCTAssertEqual(initial.origin.y + ascent * initial.fontSize, lyric.origin.y + ascent * lyric.fontSize, accuracy: 0.05)
        // The annotation centred over the initial, its capitals about the top
        // line.
        let annotationOrigin = try XCTUnwrap(initial.annotationOrigin)
        let staff = try XCTUnwrap(layout.staffs.first)
        let annotationWidth = 2 * (initial.origin.x + initial.width / 2 - annotationOrigin.x)
        XCTAssertGreaterThan(annotationWidth, 0)
        let capitals = annotationOrigin.y + ascent * initial.annotationFontSize
        XCTAssertGreaterThan(capitals, staff.frame.minY)
        XCTAssertLessThan(capitals - 0.65 * initial.annotationFontSize, staff.frame.minY)
    }

    func testAnInitialLeavesOutTheFirstVersesNumber() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) 1. Ec(g)ce(h) nunc(j) be(j)ne(j)dí(j)ci(j)te(j) Dó(k)mi(j)num.(j.) 2.(::) Qui(j) sta(j)tis(j) (::)"
        )
        let engine = GregorianEngravingLayoutEngine()
        let psalm = engine.layout(score: score, width: 390, openingLabel: "8g")
        XCTAssertEqual(psalm.initials.map(\.text), ["E"])
        let texts = psalm.lyrics.map(\.text)
        XCTAssertFalse(texts.contains("1."))
        XCTAssertEqual(texts.prefix(2), ["c", "ce"])
        // The other verses keep their numbers.
        XCTAssertTrue(texts.contains("2."))
        // Without an initial, the first verse keeps its number too.
        XCTAssertEqual(engine.layout(score: score, width: 390).lyrics.first?.text, "1.")
    }

    func testAFirstSyllableTheInitialTakesWholeLeavesAHyphen() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) A(f)ve(g) Ma(h)rí(i)a(h) (::)")
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390, openingLabel: "1", opensPiece: true)
        XCTAssertEqual(layout.initials.map(\.text), ["A"])
        XCTAssertEqual(layout.lyrics.prefix(2).map(\.text), ["-", "ve"])
        // Under its notes, and no second hyphen after it.
        let hyphen = layout.lyrics[0]
        let note = try XCTUnwrap(layout.events.first { $0.eventID == score.eventIDs[0] })
        XCTAssertEqual(hyphen.origin.x + hyphen.width / 2, note.frame.midX, accuracy: 0.5)
        XCTAssertFalse(layout.lyrics.contains { $0.neumeID.hasPrefix("lyric-hyphen-\(score.neumes[0].id)-") })
    }
}
