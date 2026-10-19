import GABCLayout
import GABCLyricFont
import XCTest

final class ScoreHighlightTests: XCTestCase {
    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    func testTapResolvesToTheNeumeWhoseTargetHoldsIt() {
        let target = GregorianNeumePlacement(
            id: "event-1",
            eventIDs: ["event-1"],
            lyric: "In",
            inkFrame: CGRect(x: 24, y: 20, width: 12, height: 18),
            hitFrame: CGRect(x: 16, y: 8, width: 44, height: 44),
            lineIndex: 0
        )
        let layout = GregorianLayout(
            size: CGSize(width: 390, height: 100),
            staffs: [],
            glyphs: [],
            strokes: [],
            lyrics: [],
            events: [],
            neumes: [target]
        )

        XCTAssertEqual(layout.neume(at: CGPoint(x: 30, y: 30))?.id, target.id)
        XCTAssertNil(layout.neume(at: CGPoint(x: 300, y: 80)))
    }

    func testHighlightIncludesWholeNeumeAndCorrespondingSyllable() throws {
        let firstSyllableNeume = GregorianNeumePlacement(
            id: "event-1",
            eventIDs: ["event-1"],
            lyric: "Ky",
            inkFrame: CGRect(x: 10, y: 10, width: 10, height: 10),
            hitFrame: CGRect(x: 5, y: 5, width: 20, height: 20),
            lineIndex: 0
        )
        let activeNeume = GregorianNeumePlacement(
            id: "event-2",
            eventIDs: ["event-2", "event-3"],
            lyric: "",
            inkFrame: CGRect(x: 30, y: 10, width: 16, height: 10),
            hitFrame: CGRect(x: 25, y: 5, width: 26, height: 20),
            lineIndex: 0
        )
        let nextSyllableNeume = GregorianNeumePlacement(
            id: "event-4",
            eventIDs: ["event-4"],
            lyric: "ri",
            inkFrame: CGRect(x: 60, y: 10, width: 10, height: 10),
            hitFrame: CGRect(x: 55, y: 5, width: 20, height: 20),
            lineIndex: 0
        )
        let layout = GregorianLayout(
            size: CGSize(width: 100, height: 40),
            staffs: [],
            glyphs: [],
            strokes: [],
            lyrics: [],
            events: [],
            neumes: [
                firstSyllableNeume,
                activeNeume,
                nextSyllableNeume
            ]
        )
        let timeline = GregorianEventTimeline(
            events: [
                GregorianEvent(id: "event-1", syllableID: "kyrie-ky", relativePitch: 0),
                GregorianEvent(id: "event-2", syllableID: "kyrie-ky", relativePitch: 1),
                GregorianEvent(id: "event-3", syllableID: "kyrie-ky", relativePitch: 2),
                GregorianEvent(id: "event-4", syllableID: "kyrie-ri", relativePitch: 1)
            ]
        )

        let highlight = try XCTUnwrap(
            GregorianScoreHighlight(eventID: "event-2", timeline: timeline, layout: layout)
        )

        XCTAssertEqual(highlight.neume.id, activeNeume.id)
        XCTAssertEqual(highlight.eventIDs, ["event-2", "event-3"])
        XCTAssertEqual(highlight.syllableNeumeIDs, ["event-1", "event-2"])
    }

    func testHighlightTakesSyllablesFromTheParsedScore() throws {
        // One group, split into two neumes by `!`, is one syllable.
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) Ky(fg!hi)ri(g) (::)")
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)

        let highlight = try XCTUnwrap(
            GregorianScoreHighlight(eventID: "note-3", score: score, layout: layout)
        )

        XCTAssertEqual(highlight.neume.id, "note-2")
        XCTAssertEqual(highlight.eventIDs, ["note-2", "note-3"])
        XCTAssertEqual(highlight.syllableNeumeIDs, ["note-0", "note-2"])
        XCTAssertNil(GregorianScoreHighlight(eventID: "missing", score: score, layout: layout))
    }
}
