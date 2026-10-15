import GABCSwiftUI
import SwiftUI
import XCTest

final class GregorianScoreDrawingTests: XCTestCase {
    func testPreparedDrawingBatchesStaticScoreOperationsWithoutMergingOverlaps() throws {
        let glyphs = [
            GregorianPlacedGlyph(
                kind: .notehead(.punctum),
                frame: CGRect(x: 10, y: 10, width: 8, height: 8)
            ),
            GregorianPlacedGlyph(
                kind: .notehead(.punctum),
                frame: CGRect(x: 30, y: 10, width: 8, height: 8)
            ),
            GregorianPlacedGlyph(
                kind: .notehead(.punctum),
                frame: CGRect(x: 31, y: 11, width: 8, height: 8)
            )
        ]
        let strokes = [
            GregorianPlacedStroke(
                kind: .staff,
                start: CGPoint(x: 0, y: 8),
                end: CGPoint(x: 100, y: 8),
                lineWidth: 1
            ),
            GregorianPlacedStroke(
                kind: .staff,
                start: CGPoint(x: 0, y: 12),
                end: CGPoint(x: 100, y: 12),
                lineWidth: 1
            ),
            GregorianPlacedStroke(
                kind: .connector,
                start: CGPoint(x: 30, y: 10),
                end: CGPoint(x: 30, y: 20),
                lineWidth: 2
            )
        ]
        let layout = GregorianLayout(
            size: CGSize(width: 100, height: 40),
            staffs: [],
            glyphs: glyphs,
            strokes: strokes,
            lyrics: [],
            events: [],
            neumes: []
        )

        let drawing = GregorianScoreDrawing(layout: layout)
        let tile = try XCTUnwrap(drawing.tiles.first)

        XCTAssertEqual(drawing.tiles.count, 1)
        XCTAssertEqual(tile.strokeBatches.count, 2)
        XCTAssertEqual(tile.glyphPaths.count, 2)
    }

    func testPreparedDrawingUsesExactStaffSystemFramesAsRenderTiles() {
        let firstFrame = CGRect(x: 0, y: 0, width: 100, height: 40)
        let secondFrame = CGRect(x: 0, y: 40, width: 100, height: 50)
        let layout = GregorianLayout(
            size: CGSize(width: 100, height: 90),
            staffs: [
                GregorianStaffLayout(
                    index: 0,
                    frame: CGRect(x: 10, y: 10, width: 80, height: 12),
                    systemFrame: firstFrame,
                    firstEventID: "first"
                ),
                GregorianStaffLayout(
                    index: 1,
                    frame: CGRect(x: 10, y: 50, width: 80, height: 12),
                    systemFrame: secondFrame,
                    firstEventID: "second"
                )
            ],
            glyphs: [
                GregorianPlacedGlyph(
                    kind: .notehead(.punctum),
                    frame: CGRect(x: 20, y: 12, width: 8, height: 8)
                ),
                GregorianPlacedGlyph(
                    kind: .notehead(.punctum),
                    frame: CGRect(x: 20, y: 52, width: 8, height: 8)
                )
            ],
            strokes: [],
            lyrics: [
                GregorianPlacedLyric(
                    text: "First",
                    origin: CGPoint(x: 20, y: 25),
                    width: 30,
                    fontSize: 12,
                    neumeID: "first"
                ),
                GregorianPlacedLyric(
                    text: "Second",
                    origin: CGPoint(x: 20, y: 70),
                    width: 40,
                    fontSize: 12,
                    neumeID: "second"
                )
            ],
            events: [],
            neumes: []
        )

        let drawing = GregorianScoreDrawing(layout: layout)

        XCTAssertEqual(drawing.tiles.map(\.frame), [firstFrame, secondFrame])
        XCTAssertEqual(drawing.tiles.map(\.lyrics.count), [1, 1])
        XCTAssertEqual(drawing.tiles.map(\.glyphPaths.count), [1, 1])
    }

    func testGlyphLibraryDrawsEachOutlineAsTheSwiftUIBuilderDid() {
        for name in GregorianGlyphName.allCases {
            let glyph = GregorianPlacedGlyph(
                kind: .catalog(name),
                frame: CGRect(x: 12.5, y: 30.25, width: 6.25, height: 7.75)
            )
            let drawn = Self.elements(of: GregorianGlyphLibrary.path(for: glyph))
            let built = Self.elements(of: Self.swiftUIPath(for: glyph))

            XCTAssertEqual(drawn.kinds, built.kinds, name.rawValue)
            XCTAssertEqual(drawn.points.count, built.points.count, name.rawValue)
            for (lhs, rhs) in zip(drawn.points, built.points) {
                XCTAssertEqual(lhs.x, rhs.x, accuracy: 0.0001, name.rawValue)
                XCTAssertEqual(lhs.y, rhs.y, accuracy: 0.0001, name.rawValue)
            }
        }
    }

    private static func elements(of path: Path) -> (kinds: String, points: [CGPoint]) {
        var kinds = ""
        var points: [CGPoint] = []
        path.forEach { element in
            switch element {
            case let .move(to):
                kinds += "m"
                points.append(to)
            case let .line(to):
                kinds += "l"
                points.append(to)
            case let .quadCurve(to, control):
                kinds += "q"
                points += [control, to]
            case let .curve(to, control1, control2):
                kinds += "c"
                points += [control1, control2, to]
            case .closeSubpath:
                kinds += "z"
            }
        }
        return (kinds, points)
    }

    /// The glyph outline as it was built before it was shared with Core
    /// Graphics clients: a SwiftUI path from the display list.
    private static func swiftUIPath(for glyph: GregorianPlacedGlyph) -> Path {
        let definition = GregorianGlyphCatalog.definition(for: glyph.kind.catalogName)
        guard definition.bounds.width > 0, definition.bounds.height > 0 else {
            return Path()
        }

        var result = Path()
        for commands in definition.paths {
            var path = Path()
            for command in commands {
                switch command {
                case let .move(point):
                    path.move(to: point)
                case let .line(point):
                    path.addLine(to: point)
                case let .quadratic(control, end):
                    path.addQuadCurve(to: end, control: control)
                case let .cubic(control1, control2, end):
                    path.addCurve(to: end, control1: control1, control2: control2)
                case .close:
                    path.closeSubpath()
                }
            }
            result.addPath(path)
        }

        let sourceBounds = CGRect(
            x: -definition.origin.x,
            y: -definition.origin.y,
            width: definition.bounds.width,
            height: definition.bounds.height
        )
        let scaleX = glyph.frame.width / sourceBounds.width
        let scaleY = glyph.frame.height / sourceBounds.height
        return result.applying(
            CGAffineTransform(
                a: scaleX,
                b: 0,
                c: 0,
                d: scaleY,
                tx: glyph.frame.minX - sourceBounds.minX * scaleX,
                ty: glyph.frame.minY - sourceBounds.minY * scaleY
            )
        )
    }

    func testPreparedScoreLaysTheScoreOutAtItsWidth() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "name: Kyrie; %% (c4) Ky(f)ri(gh)e(h) e(f)le(g)i(h)son.(g.) (::)"
        )
        let prepared = GregorianPreparedScore(score: score, width: 390)

        XCTAssertEqual(prepared.layout.size.width, 390)
        XCTAssertEqual(prepared.layout.events.map(\.eventID), score.eventIDs)
        XCTAssertEqual(prepared.drawing.tiles.count, prepared.layout.staffs.count)
    }

    func testLyricScaleFollowsDynamicType() {
        XCTAssertEqual(GregorianLayoutMetrics.lyricScale(for: .large), 1)
        XCTAssertLessThan(GregorianLayoutMetrics.lyricScale(for: .xSmall), 1)
        XCTAssertEqual(GregorianLayoutMetrics.lyricScale(for: .accessibility5), 2.05)
    }

    @MainActor
    func testCanvasDrawsInkAtItsLaidOutSize() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "name: Kyrie; %% (c4) Ky(f)ri(gh)e(h) e(f)le(g)i(h)son.(g.) (::)"
        )
        let prepared = GregorianPreparedScore(score: score, width: 390)
        let renderer = ImageRenderer(
            content: GregorianScoreCanvas(prepared, highlightedEventID: score.eventIDs[1])
                .gregorianScoreStyle(
                    GregorianScoreStyle(ink: .black, rubric: .red, highlight: .red)
                )
        )
        renderer.scale = 1

        let image = try XCTUnwrap(renderer.cgImage)
        // Rendered to whole pixels.
        XCTAssertEqual(CGFloat(image.width), prepared.layout.size.width, accuracy: 1)
        XCTAssertEqual(CGFloat(image.height), prepared.layout.size.height, accuracy: 1)

        let pixels = try opaquePixels(in: image)
        XCTAssertGreaterThan(pixels.dark, 200, "staff lines, neumes and lyrics")
        XCTAssertGreaterThan(pixels.red, 10, "the highlighted neume")
    }

    private func opaquePixels(in image: CGImage) throws -> (dark: Int, red: Int) {
        let width = image.width
        let height = image.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let context = try XCTUnwrap(
            CGContext(
                data: &bytes,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

        var dark = 0
        var red = 0
        for offset in stride(from: 0, to: bytes.count, by: 4) where bytes[offset + 3] > 200 {
            let (r, g, b) = (bytes[offset], bytes[offset + 1], bytes[offset + 2])
            if r < 60, g < 60, b < 60 {
                dark += 1
            } else if r > 180, g < 80, b < 80 {
                red += 1
            }
        }
        return (dark, red)
    }
}
