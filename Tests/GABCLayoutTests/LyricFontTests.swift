import CoreText
import GABCLayout
import GABCLyricFont
import XCTest

final class LyricFontTests: XCTestCase {
    override func setUp() {
        super.setUp()
        GregorianLyricFont.registerBundledFont()
    }

    func testBundledEBGaramondCanBeFoundByName() {
        XCTAssertTrue(GregorianLyricFont.registerBundledFont())
        let font = CTFontCreateWithName(GregorianLyricFont.defaultName as CFString, 21, nil)
        XCTAssertEqual(CTFontCopyPostScriptName(font) as String, "EBGaramond-Regular")
        XCTAssertEqual(CTFontCopyFamilyName(font) as String, "EB Garamond")
    }

    func testTheBundledItalicIsTheFamilysItalic() throws {
        let regular = CTFontCreateWithName(GregorianLyricFont.defaultName as CFString, 21, nil)
        let italic = try XCTUnwrap(
            CTFontCreateCopyWithSymbolicTraits(regular, 21, nil, .traitItalic, .traitItalic)
        )
        XCTAssertEqual(CTFontCopyPostScriptName(italic) as String, "EBGaramond-Italic")
        XCTAssertEqual(CTFontCopyFamilyName(italic) as String, "EB Garamond")
    }

    func testAPreparatorySyllableIsMeasuredInTheItalic() throws {
        // Without the italic, a preparatory syllable took the roman's width.
        func width(_ gabc: String) throws -> CGFloat {
            let score = try GregorianScoreParser.parse(gabc: gabc)
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
            return try XCTUnwrap(layout.lyrics.first { $0.text == "pedum" }).width
        }
        XCTAssertNotEqual(
            try width("%% (c4) _pedum_(g) (::)"),
            try width("%% (c4) pedum(g) (::)"),
            accuracy: 0.01
        )
    }

    func testLyricsAndInitialsNameTheFontTheyWereMeasuredIn() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) Cla(f)ma(g)vé(h)runt(i) iu(j)sti(i) (::)")
        let engine = GregorianEngravingLayoutEngine()
        let garamond = engine.layout(score: score, width: 390, openingLabel: "8g")
        XCTAssertFalse(garamond.lyrics.isEmpty)
        XCTAssertEqual(garamond.initials.count, 1)
        XCTAssertTrue(garamond.lyrics.allSatisfy { $0.fontName == "EBGaramond-Regular" })
        XCTAssertTrue(garamond.initials.allSatisfy { $0.fontName == "EBGaramond-Regular" })

        let georgia = engine.layout(
            score: score,
            width: 390,
            metrics: GregorianLayoutMetrics(lyricFontName: "Georgia"),
            openingLabel: "8g"
        )
        XCTAssertTrue(georgia.lyrics.allSatisfy { $0.fontName == "Georgia" })
        XCTAssertTrue(georgia.initials.allSatisfy { $0.fontName == "Georgia" })
        // Each face is measured as itself.
        XCTAssertNotEqual(georgia.lyrics.map(\.width), garamond.lyrics.map(\.width))
    }
}
