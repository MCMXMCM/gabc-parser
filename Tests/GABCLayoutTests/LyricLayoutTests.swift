import CoreText
import GABCLayout
import GABCLyricFont
import XCTest

/// The words under the notes, set as the chant books set them: each sung
/// vowel under its syllable's first note; the syllables of a word either
/// joined, or parted by one hyphen set just after the first; and words kept
/// apart by more than anything within a word.
final class LyricLayoutTests: XCTestCase {
    /// Melismas within words, short syllables under wide neumes and long
    /// ones under single notes, rubrics, verse numbers and bars, at widths
    /// narrow enough to carry words on to the next staff.
    private static let scores = [
        "name: preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
        "name: kyrie; %% (c4) Ky(f)ri(gfgh/ijh)e(hg) *() e(gh)lé(ixhi)i(h)son.(hg..) (::)",
        "name: alleluia; %% (c4) Al(f)le(gh)lú(hjihgh/ghg)ia.(gf) (::)",
        "name: antiphon; %% (c4) Be(f)ne(g)dí(h)ctus(h) Dó(g)mi(h)nus(f) De(g)us(h) Is(h)ra(g)ël,(f.) qui(g)a(h) vi(h)si(g)tá(h)vit(i) (;) et(h) fe(g)cit(h) re(g)dem(f)pti(g)ó(h)nem(g) ple(f)bis(g) su(h)æ.(g.) (::)",
        "name: hymn; %% (c4) Te(f) lu(g)cis(h) an(g)te(f) tér(g)mi(h)num,(h.) (,) Re(h)rum(i) Cre(h)á(g)tor,(f) pós(g)ci(h)mus,(h.) (;) 2.() Pro(f)cul(g) re(h)cé(g)dant(f) sóm(g)ni(h)a,(h.) (::)",
        "name: responsory; %% (c4) In(f) ma(g)nus(h) tu(h)as,(g) Dó(h)mi(g)ne,(f.) R/.(::)In(f) ma(g)nus(h) tu(h)as.(g.) (::)",
        "name: verse; %% (c3) Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,) in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
        "name: wide; %% (c4) San(h)ctís(h)si(h)mæ(h) Tri(h)ni(h)tá(h)tis(h) sǽ(h)cu(h)la(h) sæ(h)cu(h)ló(h)rum.(h.) (::)",
        "name: melisma; %% (c4) Sa(fgfh/hjhih)ul(g) A(f)men.(f.) (::)",
    ]

    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    func testWordsAreJoinedOrPartedByOneHyphenAndKeptApart() throws {
        for gabc in Self.scores {
            let score = try GregorianScoreParser.parse(gabc: gabc)
            for width: CGFloat in [240, 320, 390, 820] {
                assertLyricInvariants(score, width: width, "\(gabc) at width \(width)")
            }
        }
    }

    func testTheHyphenIsSetInTheLyricsTypeJustAfterItsSyllable() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "name: hyphen; %% (c4) Sa(fgfh/hjhih)ul(g) (::)"
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let first = try XCTUnwrap(layout.lyrics.first { $0.text == "Sa" })
        let second = try XCTUnwrap(layout.lyrics.first { $0.text == "ul" })
        let hyphen = try XCTUnwrap(
            layout.lyrics.first { $0.neumeID == "lyric-hyphen-\(first.neumeID)-\(second.neumeID)" },
            "A syllable parted from the next of its word by a melisma takes a hyphen."
        )
        XCTAssertEqual(hyphen.text, "-")
        XCTAssertEqual(hyphen.fontSize, first.fontSize)
        XCTAssertEqual(hyphen.fontName, first.fontName)
        XCTAssertEqual(hyphen.origin.y, first.origin.y)
        XCTAssertEqual(hyphen.origin.x, first.origin.x + first.width, accuracy: 0.5)
        XCTAssertEqual(layout.lyrics.filter { $0.text == "-" }.count, 1)
    }

    func testNoHyphenJoinsAWordToARubricOrAcrossOne() throws {
        let score = try GregorianScoreParser.parse(gabc: Self.scores[5])
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let marks = Set(
            score.elements.compactMap { element -> String? in
                guard case let .lyricMark(mark) = element else { return nil }
                return mark.id
            }
        )
        for hyphen in layout.lyrics where hyphen.text == "-" {
            XCTAssertFalse(
                marks.contains { hyphen.neumeID.contains($0) },
                "\(hyphen.neumeID) joins a rubric"
            )
        }
        let ne = try XCTUnwrap(score.neumes.first { $0.lyric == "ne," })
        XCTAssertFalse(layout.lyrics.contains { $0.neumeID.hasPrefix("lyric-hyphen-\(ne.id)-") })
    }

    func testTheSungVowelStandsOverTheSyllablesFirstNote() throws {
        // Each case: a score, the syllable, and the letters before its vowel
        // and of its vowel.
        let cases: [(gabc: String, syllable: String, before: String, vowel: String)] = [
            ("%% (c4) Dó(h)mi(g)ne(h) (::)", "Dó", "D", "ó"),
            ("%% (c4) Ky(fgh)ri(g)e(f) (::)", "Ky", "K", "y"),
            ("%% (c4) sǽ(h)cu(g)la(h) (::)", "sǽ", "s", "ǽ"),
            ("%% (c4) Is(h)ra(g)ël(h) (::)", "ël", "", "ë"),
            // A consonantal i or u is not the syllable's vowel.
            ("%% (c4) e(h)ius(g) (::)", "ius", "i", "u"),
            ("%% (c4) qui(h)a(g) (::)", "qui", "qu", "i"),
            ("%% (c4) san(h)guis(g) (::)", "guis", "gu", "i"),
            // A note that carries the end of one word and the beginning of
            // the next is sung on the vowel of the next.
            ("%% (c4) e(h)t Dó(g)mi(h)nus(h) (::)", "t Dó", "t D", "ó"),
            // Braces name the letters to centre.
            ("%% (c4) Al(f)le(g)lú(h){ia}.(g) (::)", "ia.", "", "ia"),
        ]
        for testCase in cases {
            let score = try GregorianScoreParser.parse(gabc: testCase.gabc)
            let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
            let lyric = try XCTUnwrap(
                layout.lyrics.first { $0.text == testCase.syllable },
                "\(testCase.syllable) is not set as one lyric"
            )
            let neume = try XCTUnwrap(score.neumes.first { $0.id == lyric.neumeID })
            let note = try XCTUnwrap(layout.events.first { $0.eventID == neume.notes[0].id })
            let vowelCentre = lyric.origin.x
                + width(testCase.before, size: lyric.fontSize)
                + width(testCase.vowel, size: lyric.fontSize) / 2
            XCTAssertEqual(
                vowelCentre,
                note.frame.midX,
                accuracy: 0.75,
                "\(testCase.syllable): its vowel is not under its first note"
            )
        }
    }

    // MARK: - The invariants

    private enum TextKind: Equatable {
        case syllable(GregorianNeume)
        case mark
        case hyphen
    }

    private struct PlacedText {
        let lyric: GregorianPlacedLyric
        let kind: TextKind
        var left: CGFloat { lyric.origin.x }
        var right: CGFloat { lyric.origin.x + lyric.width }
    }

    private func assertLyricInvariants(
        _ score: GregorianScore,
        width: CGFloat,
        _ context: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: width)
        let neumes = Dictionary(uniqueKeysWithValues: score.neumes.map { ($0.id, $0) })
        let texts = layout.lyrics.map { lyric -> PlacedText in
            if lyric.neumeID.hasPrefix("lyric-hyphen-") {
                return PlacedText(lyric: lyric, kind: .hyphen)
            }
            if let neume = neumes[lyric.neumeID] {
                return PlacedText(lyric: lyric, kind: .syllable(neume))
            }
            return PlacedText(lyric: lyric, kind: .mark)
        }
        let lines = Dictionary(grouping: texts) { ($0.lyric.origin.y * 100).rounded() }
            .values
            .map { $0.sorted { $0.left < $1.left } }
        let fontSize = layout.lyrics.first?.fontSize ?? 21
        let hyphenAdvance = self.width("-", size: fontSize)
        let wordSpace = max(hyphenAdvance, 0.5 * 12.5)

        for staff in lines {
            for pair in zip(staff, staff.dropFirst()) {
                XCTAssertLessThanOrEqual(
                    pair.0.right, pair.1.left + 0.5,
                    "\(pair.0.lyric.text) runs into \(pair.1.lyric.text): \(context)",
                    file: file, line: line
                )
            }
            let words = staff.filter { $0.kind != .hyphen }
            for (previous, next) in zip(words, words.dropFirst()) {
                let gap = next.left - previous.right
                let hyphens = staff.filter {
                    $0.kind == .hyphen && $0.left >= previous.left && $0.left < next.left
                }
                if case let .syllable(first) = previous.kind,
                   case let .syllable(second) = next.kind,
                   !second.startsWord {
                    XCTAssertNotEqual(first.syllableID, second.syllableID, context, file: file, line: line)
                    XCTAssertLessThanOrEqual(
                        hyphens.count, 1,
                        "\(first.lyric)-\(second.lyric) takes more than one hyphen: \(context)",
                        file: file, line: line
                    )
                    if let hyphen = hyphens.first {
                        XCTAssertEqual(
                            hyphen.left, previous.right, accuracy: 0.5,
                            "the hyphen after \(first.lyric) stands away from it: \(context)",
                            file: file, line: line
                        )
                        XCTAssertGreaterThanOrEqual(
                            gap, hyphenAdvance - 0.5,
                            "\(first.lyric)-\(second.lyric) has no room for its hyphen: \(context)",
                            file: file, line: line
                        )
                    } else {
                        XCTAssertEqual(
                            gap, 0, accuracy: 0.5,
                            "\(first.lyric) \(second.lyric) are parted with no hyphen: \(context)",
                            file: file, line: line
                        )
                    }
                } else {
                    XCTAssertTrue(
                        hyphens.isEmpty,
                        "a hyphen joins \(previous.lyric.text) to \(next.lyric.text): \(context)",
                        file: file, line: line
                    )
                    XCTAssertGreaterThanOrEqual(
                        gap, wordSpace - 0.5,
                        "\(previous.lyric.text) \(next.lyric.text) are too close for two words: \(context)",
                        file: file, line: line
                    )
                }
            }
        }

        // A word divided at a staff's end carries a hyphen at that end.
        let syllables = score.elements.compactMap { element -> GregorianNeume? in
            guard case let .neume(neume) = element, !neume.lyric.isEmpty else { return nil }
            return neume
        }
        let lineOfNeume = Dictionary(uniqueKeysWithValues: layout.neumes.map { ($0.id, $0.lineIndex) })
        var previousLyricWasMark = false
        var previous: GregorianNeume?
        for element in score.elements {
            switch element {
            case .lyricMark:
                previousLyricWasMark = true
                previous = nil
            case let .neume(neume) where !neume.lyric.isEmpty:
                if let previous, !previousLyricWasMark, !neume.startsWord,
                   let before = lineOfNeume[previous.id], let after = lineOfNeume[neume.id],
                   before != after {
                    XCTAssertTrue(
                        layout.lyrics.contains { $0.neumeID == "lyric-hyphen-\(previous.id)-\(neume.id)" },
                        "\(previous.lyric) ends a staff with no hyphen: \(context)",
                        file: file, line: line
                    )
                }
                previous = neume
                previousLyricWasMark = false
            default:
                continue
            }
        }
        XCTAssertFalse(syllables.isEmpty, context, file: file, line: line)
    }

    private func width(_ text: String, size: CGFloat) -> CGFloat {
        guard !text.isEmpty else { return 0 }
        let font = CTFontCreateWithName(GregorianLyricFont.defaultName as CFString, size, nil)
        let attributed = CFAttributedStringCreate(
            nil, text as CFString, [kCTFontAttributeName: font] as CFDictionary
        )!
        return CGFloat(CTLineGetTypographicBounds(CTLineCreateWithAttributedString(attributed), nil, nil, nil))
    }
}
