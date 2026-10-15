import CoreGraphics
import GABCLayout
import XCTest

final class GlyphPathTests: XCTestCase {
    func testEveryCatalogGlyphHasAnOutline() {
        for name in GregorianGlyphName.allCases where name != .none {
            let bounds = GregorianGlyphCatalog.definition(for: name).path.boundingBoxOfPath
            XCTAssertFalse(bounds.isEmpty, name.rawValue)
        }
    }

    func testPlacedOutlineMapsTheGlyphsBoundsOntoItsFrame() {
        // The catalog's bounds are those of the notehead: a stem (the
        // podatus's, say) may reach beyond them, and beyond the frame.
        let frame = CGRect(x: 12.5, y: 40, width: 6.25, height: 7.5)
        for name in GregorianGlyphName.allCases where name != .none {
            let definition = GregorianGlyphCatalog.definition(for: name)
            // The accents have no width in the catalog, and are not drawn.
            guard definition.bounds.width > 0, definition.bounds.height > 0 else {
                XCTAssertTrue(
                    GregorianPlacedGlyph(kind: .catalog(name), frame: frame).path.isEmpty,
                    name.rawValue
                )
                continue
            }
            let outline = definition.path.boundingBoxOfPath
            let scaleX = frame.width / definition.bounds.width
            let scaleY = frame.height / definition.bounds.height
            let expected = CGRect(
                x: frame.minX + (outline.minX + definition.origin.x) * scaleX,
                y: frame.minY + (outline.minY + definition.origin.y) * scaleY,
                width: outline.width * scaleX,
                height: outline.height * scaleY
            )

            let placed = GregorianPlacedGlyph(kind: .catalog(name), frame: frame)
                .path.boundingBoxOfPath
            XCTAssertEqual(placed.minX, expected.minX, accuracy: 0.0001, name.rawValue)
            XCTAssertEqual(placed.minY, expected.minY, accuracy: 0.0001, name.rawValue)
            XCTAssertEqual(placed.width, expected.width, accuracy: 0.0001, name.rawValue)
            XCTAssertEqual(placed.height, expected.height, accuracy: 0.0001, name.rawValue)
        }
    }
}
