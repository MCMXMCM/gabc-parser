import GABCSwiftUI
import SwiftUI
import XCTest

// The examples in README.md, kept compiling and true.

private struct OpeningVersicleView: View {
    let gabc = """
        name: Deus in adiutorium;
        %%
        (c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)
        """

    @State private var sungNote: String?

    var body: some View {
        ScrollView {
            GregorianScoreView(gabc: gabc, highlightedEventID: sungNote) { neume in
                sungNote = neume.eventIDs.first
            }
            .padding()
        }
    }
}

final class ReadmeExampleTests: XCTestCase {
    @MainActor
    func testDrawingExamples() async throws {
        let gabc = OpeningVersicleView().gabc
        _ = OpeningVersicleView().body
        _ = GregorianScoreView(gabc: gabc)
            .gregorianScoreStyle(GregorianScoreStyle(ink: .primary, rubric: .red, highlight: .orange))

        let width: CGFloat = 390
        let prepared = try await Task.detached {
            let score = try GregorianScoreParser.parse(gabc: gabc)
            return GregorianPreparedScore(score: score, width: width)
        }.value

        _ = GregorianScoreCanvas(prepared)
        XCTAssertEqual(prepared.layout.size.width, width)
    }

    func testCoreGraphicsExample() throws {
        let score = try GregorianScoreParser.parse(gabc: OpeningVersicleViewGABC.text)
        let context = try XCTUnwrap(
            CGContext(
                data: nil,
                width: 600,
                height: 400,
                bitsPerComponent: 8,
                bytesPerRow: 0,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )

        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 600)
        for stroke in layout.strokes {
            context.move(to: stroke.start)
            context.addLine(to: stroke.end)
            context.setLineWidth(stroke.lineWidth)
            context.strokePath()
        }
        for glyph in layout.glyphs {
            context.addPath(glyph.path)
            context.fillPath(using: .evenOdd)
        }

        XCTAssertNotNil(context.makeImage())
    }

    func testParsingExample() throws {
        let score = try GregorianScoreParser.parse(gabc: OpeningVersicleViewGABC.text)
        var printed: [String] = []
        for case let .neume(neume) in score.elements {
            printed.append("\(neume.lyric) \(neume.form) \(neume.notes.map(\.pitch))")
        }

        XCTAssertEqual(printed.first, "De punctum [5]")
        XCTAssertEqual(score.lyricText, "Deus in adjutórium meum inténde.")
    }
}

private enum OpeningVersicleViewGABC {
    static let text = """
        name: Deus in adiutorium;
        %%
        (c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)
        """
}
