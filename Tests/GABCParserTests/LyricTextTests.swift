import GABCParser
import XCTest

/// How the words of a score are read: the rubrics written among them, the
/// words each begins and ends, and the letters a score marks to be centred.
final class LyricTextTests: XCTestCase {
    private func marks(_ score: GregorianScore) -> [GregorianLyricMark] {
        score.elements.compactMap { element in
            guard case let .lyricMark(mark) = element else { return nil }
            return mark
        }
    }

    /// The lyrics and marks in the order written, marks in brackets.
    private func lyricSequence(_ score: GregorianScore) -> [String] {
        score.elements.compactMap { element in
            switch element {
            case let .neume(neume): neume.lyric.isEmpty ? nil : neume.lyric
            case let .lyricMark(mark): "[\(mark.text)]"
            default: nil
            }
        }
    }

    func testARubricAndItsBarEndTheWordBeforeThem() throws {
        // A respond written straight after its sign and bar, with no space.
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) Dó(h)mi(g)ne,(f.) R/.(::)In(f) ma(g)nus(h) (::)"
        )
        XCTAssertEqual(lyricSequence(score), ["Dó", "mi", "ne,", "[℟.]", "In", "ma", "nus"])
        let respond = try XCTUnwrap(score.neumes.first { $0.lyric == "In" })
        XCTAssertTrue(respond.startsWord)
        XCTAssertEqual(score.lyricText, "Dómine, ℟. In manus")
    }

    func testAClefChangeWithinAWordLeavesTheWordWhole() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) Dó(f)mi(g)(c3)ne(h) (::)")
        XCTAssertEqual(score.neumes.map(\.startsWord), [true, false, false])
        XCTAssertEqual(score.lyricText, "Dómine")
    }

    func testRubricsWrittenBeforeASungSyllableStandApartFromIt() throws {
        let versicle = try GregorianScoreParser.parse(
            gabc: "%% (c4) V/. Dó(h)mi(h)ne,(h) ex(h)áu(h)di(g) (::)"
        )
        XCTAssertEqual(lyricSequence(versicle), ["[℣.]", "Dó", "mi", "ne,", "ex", "áu", "di"])
        XCTAssertEqual(marks(versicle).map(\.style), [.rubric])
        XCTAssertEqual(versicle.neumes.first?.startsWord, true)
        XCTAssertEqual(versicle.lyricText, "℣. Dómine, exáudi")

        let stanza = try GregorianScoreParser.parse(
            gabc: "%% (c4) sóm(g)ni(h)a,(h.) (::) 2. Pro(f)cul(g) (::)"
        )
        XCTAssertEqual(lyricSequence(stanza), ["sóm", "ni", "a,", "[2.]", "Pro", "cul"])
        XCTAssertEqual(marks(stanza).map(\.style), [.rubric])
        XCTAssertEqual(stanza.neumes.first { $0.lyric == "Pro" }?.startsWord, true)

        // GABC's tilde is a space that no line break may take.
        let mediant = try GregorianScoreParser.parse(
            gabc: "%% (c4) læ(h)tí(h)ti(h)a(h) *~ab(h) in(h)i(h)mí(h)cis(h) (::)"
        )
        XCTAssertEqual(
            lyricSequence(mediant),
            ["læ", "tí", "ti", "a", "[*]", "ab", "in", "i", "mí", "cis"]
        )
        XCTAssertFalse(mediant.lyricText.contains("~"))
    }

    func testRubricsWrittenAfterASungSyllableStandApartFromIt() throws {
        let flex = try GregorianScoreParser.parse(
            gabc: "%% (c3) Quó(h)ni(h)am hæc, †(h) et(h) ip(h)se(h) (::)"
        )
        XCTAssertEqual(lyricSequence(flex), ["Quó", "ni", "am hæc,", "[†]", "et", "ip", "se"])
        XCTAssertEqual(marks(flex).map(\.style), [.rubric])
        XCTAssertEqual(flex.neumes.first { $0.lyric == "et" }?.startsWord, true)
    }

    func testVerseNumbersRepeatSignsAndCrossesAreRubrics() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) 2.() Pro(f) e(g) iij.(::) e(g) ij.(::) **() ✠() 12.() fi(h) (::)"
        )
        XCTAssertEqual(marks(score).map(\.text), ["2.", "iij.", "ij.", "**", "✠", "12."])
        XCTAssertEqual(marks(score).map(\.style), Array(repeating: .rubric, count: 6))
    }

    func testARubricThatHeadsWhatFollowsStandsAfterTheBarWrittenWithIt() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) me(g.)um.(f.) R/.(::)In(f) ma(f)nus(f) Dó(gf)mi(g)ne,(g.) *(;) Com(h)mén(f)do(gh) (::)"
        )
        let sequence = score.elements.compactMap { element -> String? in
            switch element {
            case let .neume(neume): neume.lyric
            case let .lyricMark(mark): "[\(mark.text)]"
            case .division: "|"
            default: nil
            }
        }
        XCTAssertEqual(
            sequence,
            ["me", "um.", "|", "[℟.]", "In", "ma", "nus", "Dó", "mi", "ne,", "[*]", "|", "Com", "mén", "do", "|"]
        )
    }

    func testARubricHeadsTheWordsAfterItOrClosesThoseBeforeIt() throws {
        let cases: [(gabc: String, mark: String, begins: Bool)] = [
            ("%% (c4) V/. Dó(h)mi(h)ne(h) (::)", "℣.", true),
            ("%% (c4) ne,(f.) R/.(::)In(f) (::)", "℟.", true),
            ("%% (c4) a,(h.) (::) 2.() Pro(f)cul(g) (::)", "2.", true),
            ("%% (c4) ✠() Ma(f)gní(g)fi(h)cat(h) (::)", "✠", true),
            ("%% (c4) am:(hg..) (:) † In(i)tra(i) (::)", "†", true),
            ("%% (c3) Quó(h)ni(h)am hæc, †(h) et(h) (::)", "†", false),
            ("%% (c3) Eu(ei)ge,(i) *() ser(i)ve(i) (::)", "*", false),
            ("%% (c4) e(g) iij.(::)", "iij.", false),
        ]
        for testCase in cases {
            let score = try GregorianScoreParser.parse(gabc: testCase.gabc)
            let mark = try XCTUnwrap(marks(score).first { $0.text == testCase.mark }, testCase.gabc)
            XCTAssertEqual(mark.beginsWhatFollows, testCase.begins, testCase.gabc)
        }
    }

    func testASungSyllableMadeOnlyOfSignsIsLeftWhole() throws {
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) *(h) 2.(g) (::)")
        XCTAssertEqual(score.neumes.map(\.lyric), ["*", "2."])
        XCTAssertTrue(marks(score).isEmpty)
    }
}
