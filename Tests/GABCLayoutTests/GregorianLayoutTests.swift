import GABCLayout
import GABCLyricFont
import XCTest

final class GregorianLayoutTests: XCTestCase {
    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    func testNotationAndLyricsScaleTogetherAcrossReaderRange() throws {
        let score = try parsedFixture(noteCount: 1)
        let engine = GregorianEngravingLayoutEngine()
        let previousMinimum = engine.layout(
            score: score,
            width: 390,
            metrics: GregorianLayoutMetrics(notationScale: 0.8)
        )
        let newMinimum = engine.layout(
            score: score,
            width: 390,
            metrics: GregorianLayoutMetrics(
                notationScale: GregorianLayoutMetrics.minimumNotationScale
            )
        )
        let clampedBelowMinimum = engine.layout(
            score: score,
            width: 390,
            metrics: GregorianLayoutMetrics(notationScale: 0)
        )
        let defaultSize = engine.layout(
            score: score,
            width: 390
        )
        let maximumSize = engine.layout(
            score: score,
            width: 390,
            metrics: GregorianLayoutMetrics(
                notationScale: GregorianLayoutMetrics.maximumNotationScale
            )
        )
        let previousClef = try XCTUnwrap(previousMinimum.glyphs.first)
        let minimumClef = try XCTUnwrap(newMinimum.glyphs.first)
        let clampedClef = try XCTUnwrap(clampedBelowMinimum.glyphs.first)
        let previousLyric = try XCTUnwrap(previousMinimum.lyrics.first)
        let minimumLyric = try XCTUnwrap(newMinimum.lyrics.first)
        let clampedLyric = try XCTUnwrap(clampedBelowMinimum.lyrics.first)
        let defaultClef = try XCTUnwrap(defaultSize.glyphs.first)
        let maximumClef = try XCTUnwrap(maximumSize.glyphs.first)
        let defaultLyric = try XCTUnwrap(defaultSize.lyrics.first)
        let maximumLyric = try XCTUnwrap(maximumSize.lyrics.first)

        XCTAssertEqual(
            minimumClef.frame.width,
            previousClef.frame.width * 0.75,
            accuracy: 0.001
        )
        XCTAssertEqual(
            clampedClef.frame.width,
            minimumClef.frame.width,
            accuracy: 0.001
        )
        XCTAssertEqual(
            minimumLyric.fontSize,
            previousLyric.fontSize * 0.75,
            accuracy: 0.001
        )
        XCTAssertEqual(
            clampedLyric.fontSize,
            minimumLyric.fontSize,
            accuracy: 0.001
        )
        XCTAssertEqual(
            maximumClef.frame.width,
            defaultClef.frame.width
                * GregorianLayoutMetrics.maximumNotationScale,
            accuracy: 0.001
        )
        XCTAssertEqual(
            maximumLyric.fontSize,
            defaultLyric.fontSize
                * GregorianLayoutMetrics.maximumNotationScale,
            accuracy: 0.001
        )
    }

    func testGeneratedCatalogContainsCanonicalExsurgeGeometry() {
        XCTAssertEqual(GregorianGlyphName.allCases.count, 49)
        let punctum = GregorianGlyphCatalog.definition(for: .punctumQuadratum)
        XCTAssertEqual(punctum.bounds.width, 100, accuracy: 0.001)
        XCTAssertEqual(punctum.bounds.height, 123.438, accuracy: 0.001)
        XCTAssertEqual(punctum.origin.x, 50, accuracy: 0.001)
        XCTAssertFalse(punctum.paths.flatMap { $0 }.isEmpty)

        for name in GregorianGlyphName.allCases where name != .none {
            XCTAssertFalse(
                GregorianGlyphCatalog.definition(for: name).paths.flatMap { $0 }.isEmpty,
                "\(name.rawValue) should have generated vector commands"
            )
        }
    }

    func testNamedFormsSelectCanonicalCompositeGlyphs() throws {
        let events = [
            GregorianEvent(id: "p0", syllableID: "s0", relativePitch: 0),
            GregorianEvent(id: "p1", syllableID: "s0", relativePitch: 1),
            GregorianEvent(id: "r0", syllableID: "s1", relativePitch: 2),
            GregorianEvent(id: "r1", syllableID: "s1", relativePitch: 1),
            GregorianEvent(id: "r2", syllableID: "s1", relativePitch: 2)
        ]
        let score = try GregorianScoreParser.parse(
            gabc: "name: composite; %% (c4) Po(fg) rec(hgh) (::)",
            timeline: GregorianEventTimeline(events: events)
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        let names = layout.glyphs.map(\.kind.catalogName)

        // A pes of a second takes Exsurge's short halves.
        XCTAssertTrue(names.contains(.podatusLowerShort))
        XCTAssertTrue(names.contains(.podatusUpperShort))
        XCTAssertTrue(names.contains(.porrectus1))
    }

    func testResponsiveLayoutProducesCustodesAndStableEventFrames() throws {
        let score = try parsedFixture(noteCount: 12)
        let engine = GregorianEngravingLayoutEngine()
        let narrow = engine.layout(score: score, width: 280)
        let wide = engine.layout(score: score, width: 760)

        XCTAssertGreaterThan(narrow.staffs.count, wide.staffs.count)
        XCTAssertLessThanOrEqual(narrow.staffs.count, 3)
        XCTAssertEqual(narrow.events.map(\.eventID), score.eventIDs)
        XCTAssertEqual(Set(narrow.events.map(\.eventID)).count, score.eventIDs.count)
        XCTAssertEqual(
            narrow.glyphs.filter {
                if case .custos = $0.kind { return true }
                return false
            }.count,
            max(0, narrow.staffs.count - 1)
        )
        let countsByLine = Dictionary(grouping: narrow.neumes, by: \.lineIndex)
            .sorted { $0.key < $1.key }
            .map { $0.value.count }
        XCTAssertGreaterThanOrEqual(countsByLine.dropLast().min() ?? 0, 3)
        XCTAssertEqual(
            narrow.glyphs.filter {
                if case .cClef = $0.kind { return true }
                return false
            }.count,
            narrow.staffs.count
        )
    }

    func testMobileStaffsUseFullWidthAndCompactVerticalRhythm() throws {
        let noteCount = 40
        let timeline = GregorianEventTimeline(
            events: (0..<noteCount).map { index in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: 0
                )
            }
        )
        let body = (0..<noteCount)
            .map { _ in "la(f)" }
            .joined(separator: " ")
        let score = try GregorianScoreParser.parse(
            gabc: "name: compact-mobile; %% (c4) \(body) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)

        XCTAssertGreaterThan(layout.staffs.count, 1)
        XCTAssertTrue(layout.initials.isEmpty)
        for staff in layout.staffs {
            XCTAssertEqual(staff.frame.minX, 8, accuracy: 0.01)
            XCTAssertEqual(staff.frame.maxX, 382, accuracy: 0.01)
        }

        for pair in zip(layout.staffs, layout.staffs.dropFirst()) {
            let systemAdvance = pair.1.frame.minY - pair.0.frame.minY
            XCTAssertGreaterThan(systemAdvance, 79)
            XCTAssertLessThan(systemAdvance, 82)
        }
    }

    func testOpeningInitialWithConclusionSitsLeftOfTheFirstClefAndLeavesLaterStaffsFullWidth() throws {
        let syllables = ["Cla", "ma", "vé", "runt", "iu", "sti"]
        let timeline = GregorianEventTimeline(
            events: syllables.enumerated().map { index, syllable in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: index % 4
                )
            }
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: initial; %% (c4) Cla(f)ma(g)vé(h)runt(i) (z) iu(j)sti(i) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(
            score: score,
            width: 390,
            openingLabel: "8g"
        )
        let initial = try XCTUnwrap(layout.initials.first)
        let firstLyric = try XCTUnwrap(layout.lyrics.first)

        XCTAssertEqual(layout.initials.count, 1)
        XCTAssertEqual(initial.text, "C")
        XCTAssertEqual(initial.neumeID, score.neumes[0].id)
        XCTAssertEqual(firstLyric.text, "la")
        XCTAssertEqual(score.neumes[0].lyric, "Cla")
        XCTAssertGreaterThan(layout.staffs[0].frame.minX, 8)
        XCTAssertLessThanOrEqual(
            initial.origin.x + initial.width,
            layout.staffs[0].frame.minX
        )
        XCTAssertEqual(layout.staffs[1].frame.minX, 8, accuracy: 0.01)
        XCTAssertEqual(layout.staffs[1].frame.maxX, 382, accuracy: 0.01)
    }

    func testOpeningInitialPreservesLatinLigatureAsOneCharacter() throws {
        let timeline = GregorianEventTimeline(
            events: [
                GregorianEvent(
                    id: "event-0",
                    syllableID: "syllable-0",
                    relativePitch: 0
                ),
                GregorianEvent(
                    id: "event-1",
                    syllableID: "syllable-1",
                    relativePitch: 1
                )
            ]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: ligature; %% (c4) 1. Æ(f)tér(g) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(
            score: score,
            width: 390,
            openingLabel: "1f"
        )

        XCTAssertEqual(layout.initials.map(\.text), ["Æ"])
        XCTAssertEqual(layout.initials.map(\.annotation), ["1F"])
        XCTAssertFalse(layout.lyrics.contains { $0.text == "Æ" })
    }

    func testOpeningWithoutConclusionKeepsCompleteLyricUnderClef() throws {
        let timeline = GregorianEventTimeline(
            events: [
                GregorianEvent(
                    id: "event-0",
                    syllableID: "syllable-0",
                    relativePitch: 0
                ),
                GregorianEvent(
                    id: "event-1",
                    syllableID: "syllable-1",
                    relativePitch: 1
                )
            ]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: no-conclusion; %% (c4) De(f)us(g) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(
            score: score,
            width: 390,
            openingLabel: "6"
        )

        XCTAssertTrue(layout.initials.isEmpty)
        XCTAssertEqual(layout.lyrics.first?.text, "De")
        XCTAssertEqual(layout.staffs[0].frame.minX, 8, accuracy: 0.01)
    }

    func testOpeningLabelRequiresModeAndConclusion() throws {
        XCTAssertNil(GregorianEngravingLayoutEngine.openingLabel(forMode: nil))
        XCTAssertNil(GregorianEngravingLayoutEngine.openingLabel(forMode: "6"))
        XCTAssertNil(GregorianEngravingLayoutEngine.openingLabel(forMode: "6-alt"))
        XCTAssertNil(GregorianEngravingLayoutEngine.openingLabel(forMode: "in-directum"))
        XCTAssertEqual(
            GregorianEngravingLayoutEngine.openingLabel(forMode: "8g"),
            "8G"
        )
        XCTAssertEqual(
            GregorianEngravingLayoutEngine.openingLabel(forMode: "7c2"),
            "7C2"
        )
        XCTAssertEqual(
            GregorianEngravingLayoutEngine.openingLabel(forMode: "4-alt-d"),
            "4-ALT-D"
        )
        XCTAssertEqual(
            GregorianEngravingLayoutEngine.openingLabel(forMode: "8gstar"),
            "8G*"
        )
        XCTAssertEqual(
            GregorianEngravingLayoutEngine.openingLabel(forMode: "4e star"),
            "4E*"
        )
        // The layout reads the label it is given as a mode: each reads as
        // itself.
        for mode in ["8g", "7c2", "4-alt-d", "8gstar"] {
            let label = GregorianEngravingLayoutEngine.openingLabel(forMode: mode)
            XCTAssertEqual(GregorianEngravingLayoutEngine.openingLabel(forMode: label), label, mode)
        }
        let score = try GregorianScoreParser.parse(gabc: "%% (c4) Mis(g)sus(h) est(h) (::)")
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390, openingLabel: "8G*")
        XCTAssertEqual(layout.initials.first?.annotation, "8G*")
    }

    func testScoreModeAppearsAboveInitialAndOverridesVerseNumber() throws {
        let timeline = GregorianEventTimeline(
            events: [
                GregorianEvent(
                    id: "event-0",
                    syllableID: "syllable-0",
                    relativePitch: 0
                ),
                GregorianEvent(
                    id: "event-1",
                    syllableID: "syllable-1",
                    relativePitch: 1
                )
            ]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: psalm-tone; %% (c3) 1. Prín(f)cipes(g) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(
            score: score,
            width: 390,
            openingLabel: "7c2"
        )
        let initial = try XCTUnwrap(layout.initials.first)

        XCTAssertEqual(initial.text, "P")
        XCTAssertEqual(initial.annotation, "7C2")
        XCTAssertNotNil(initial.annotationOrigin)
    }

    func testNeumeTargetsRemainUsableAndDoNotOverlap() throws {
        let score = try parsedFixture(noteCount: 9)
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)

        for neume in layout.neumes {
            // Short words are set as close as the book sets them, nearer
            // than a finger's width; their targets still meet, and a tap
            // between two goes to the nearer.
            XCTAssertGreaterThanOrEqual(neume.hitFrame.width, 20)
            XCTAssertGreaterThanOrEqual(neume.hitFrame.height, 43.9)
            XCTAssertFalse(neume.inkFrame.isNull)
        }
        for line in Dictionary(grouping: layout.neumes, by: \.lineIndex).values {
            let ordered = line.sorted { $0.hitFrame.minX < $1.hitFrame.minX }
            for pair in zip(ordered, ordered.dropFirst()) {
                XCTAssertLessThanOrEqual(pair.0.hitFrame.maxX, pair.1.hitFrame.minX + 0.01)
            }
        }
    }

    func testLyricsAreMeasuredAndDoNotOverlapOnEachLine() throws {
        let score = try parsedFixture(noteCount: 9)
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        for staff in layout.staffs {
            let lyrics = layout.lyrics
                .filter { lyric in
                    lyric.origin.y >= staff.frame.minY && lyric.origin.y < staff.frame.minY + 94
                }
                .sorted { $0.origin.x < $1.origin.x }
            for pair in zip(lyrics, lyrics.dropFirst()) {
                XCTAssertLessThanOrEqual(pair.0.origin.x + pair.0.width, pair.1.origin.x + 0.5)
            }
        }
    }

    func testAccentedLyricReservesItsBoldAdvanceBeforeFollowingSyllable() throws {
        let syllables = ["con", "fun", "*dán*", "tur"]
        let timeline = GregorianEventTimeline(
            events: syllables.enumerated().map { index, syllable in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: index
                )
            }
        )
        let accentedScore = try GregorianScoreParser.parse(
            gabc: "name: accented-spacing; %% (c4) con(f)fun(g)*dán*(h)tur(i) (::)",
            timeline: timeline
        )
        let regularScore = try GregorianScoreParser.parse(
            gabc: "name: regular-spacing; %% (c4) con(f)fun(g)dán(h)tur(i) (::)",
            timeline: timeline
        )
        let engine = GregorianEngravingLayoutEngine()
        let accentedLayout = engine.layout(score: accentedScore, width: 760)
        let regularLayout = engine.layout(score: regularScore, width: 760)
        let accentedLyric = try XCTUnwrap(
            accentedLayout.lyrics.first { $0.text == "dán" }
        )
        let regularLyric = try XCTUnwrap(
            regularLayout.lyrics.first { $0.text == "dán" }
        )
        let followingLyric = try XCTUnwrap(
            accentedLayout.lyrics.first { $0.text == "tur" }
        )

        XCTAssertEqual(accentedLyric.style, .accented)
        XCTAssertGreaterThan(accentedLyric.width, regularLyric.width)
        XCTAssertLessThanOrEqual(
            accentedLyric.origin.x + accentedLyric.width,
            followingLyric.origin.x + 0.5
        )
    }

    func testSyllablesStayGroupedWithinWords() throws {
        let syllables = ["San", "cte", "no", "bis", "Spí", "ri", "tus"]
        let timeline = GregorianEventTimeline(
            events: syllables.enumerated().map { index, syllable in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: index % 4
                )
            }
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: word-spacing; %% (c4) San(f)cte(g) no(h)bis(i) Spí(j)ri(i)tus(h) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let lyricsByID = Dictionary(uniqueKeysWithValues: layout.lyrics.map { ($0.neumeID, $0) })
        let neumes = score.neumes

        func gap(after firstIndex: Int, before secondIndex: Int) throws -> CGFloat {
            let first = try XCTUnwrap(lyricsByID[neumes[firstIndex].id])
            let second = try XCTUnwrap(lyricsByID[neumes[secondIndex].id])
            return second.origin.x - (first.origin.x + first.width)
        }

        XCTAssertLessThanOrEqual(try gap(after: 0, before: 1), 2)
        XCTAssertGreaterThanOrEqual(try gap(after: 1, before: 2), 4)
        XCTAssertLessThanOrEqual(try gap(after: 1, before: 2), 7)
        XCTAssertLessThanOrEqual(try gap(after: 2, before: 3), 2)
        XCTAssertGreaterThanOrEqual(try gap(after: 3, before: 4), 4)
        XCTAssertLessThanOrEqual(try gap(after: 3, before: 4), 7)
        XCTAssertLessThanOrEqual(try gap(after: 4, before: 5), 2)
        XCTAssertLessThanOrEqual(try gap(after: 5, before: 6), 2)
    }

    func testAutomaticHyphensClarifyWideSyllableGapsWithinAWord() throws {
        let syllables = ["De", "us", "in"]
        let timeline = GregorianEventTimeline(
            events: syllables.enumerated().map { index, syllable in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: index
                )
            }
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: hyphens; %% (c3) De(h)us(h) in(h) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let lyricTexts = layout.lyrics.map(\.text)

        XCTAssertTrue(lyricTexts.contains("-"))
        XCTAssertTrue(
            layout.lyrics.contains {
                $0.neumeID == "lyric-hyphen-event-0-event-1"
            }
        )
        let firstSyllable = try XCTUnwrap(
            layout.lyrics.first { $0.neumeID == "event-0" }
        )
        let hyphen = try XCTUnwrap(
            layout.lyrics.first {
                $0.neumeID == "lyric-hyphen-event-0-event-1"
            }
        )
        XCTAssertEqual(
            hyphen.origin.y,
            firstSyllable.origin.y
                + (firstSyllable.fontSize - hyphen.fontSize) * 1.23 / 2,
            accuracy: 0.001,
            "The smaller hyphen should be vertically centered in the lyric line."
        )
        XCTAssertFalse(
            layout.lyrics.contains {
                $0.neumeID == "lyric-hyphen-event-1-event-2"
            },
            "A GABC word space must not receive a lyric hyphen."
        )
    }

    func testNearbyGABCFragmentsJoinWithoutAutomaticHyphens() throws {
        let fragments = ["Qui", "fe", "ci", "t cæ", "lu", "m e", "t te", "rram."]
        let timeline = GregorianEventTimeline(
            events: fragments.enumerated().map { index, fragment in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: index == fragments.count - 1 ? -2 : 0
                )
            }
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: compact-fragments; %% (c3) R/.() Qui(h) fe(h)ci(h)t cæ(h)lu(h)m e(h)t te(h)rram.(f.) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)

        XCTAssertFalse(
            layout.lyrics.contains { $0.text == "-" },
            "Adjacent musical attachments should read as continuous words, including et rather than e-t."
        )
        let fragmentBeforeT = try XCTUnwrap(
            layout.lyrics.first { $0.neumeID == "event-5" }
        )
        let fragmentBeginningWithT = try XCTUnwrap(
            layout.lyrics.first { $0.neumeID == "event-6" }
        )
        XCTAssertLessThanOrEqual(
            fragmentBeginningWithT.origin.x
                - (fragmentBeforeT.origin.x + fragmentBeforeT.width),
            2,
            "The e and t in et should remain visually joined."
        )
    }

    func testReferencePrecesUseConnectorsOnlyWhereMusicalSpacingNeedsThem() throws {
        let fragments = [
            "De", "us", "in", "ad", "ju", "tó", "ri", "um", "me", "um",
            "in", "tén", "de."
        ]
        let timeline = GregorianEventTimeline(
            events: fragments.enumerated().map { index, fragment in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: 0
                )
            }
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: reference-preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let actualHyphenIDs = Set(
            layout.lyrics.filter { $0.text == "-" }.map(\.neumeID)
        )

        XCTAssertEqual(
            actualHyphenIDs,
            Set([
                "lyric-hyphen-event-0-event-1",
                "lyric-hyphen-event-6-event-7",
                "lyric-hyphen-event-8-event-9"
            ]),
            "Match Exsurge's De-us, adjutóri-um, and me-um connectors without hyphenating every syllable."
        )

        let adjutoriumFragments = [
            "A", "dju", "tó", "ri", "u", "m nó", "strum", "i", "n nó",
            "mi", "ne", "Dó", "mi", "ni."
        ]
        let adjutoriumTimeline = GregorianEventTimeline(
            events: adjutoriumFragments.enumerated().map { index, fragment in
                GregorianEvent(
                    id: "adjutorium-event-\(index)",
                    syllableID: "adjutorium-syllable-\(index)",
                    relativePitch: 0
                )
            }
        )
        let adjutoriumScore = try GregorianScoreParser.parse(
            gabc: "name: reference-adjutorium; %% (c3) V/.() A(h)dju(h)tó(h)ri(h)u(h)m nó(h)strum(h) ✠(,) i(h)n nó(h)mi(h)ne(h) Dó(h)mi(h)ni.(f.) (::)",
            timeline: adjutoriumTimeline
        )
        let adjutoriumLayout = GregorianEngravingLayoutEngine().layout(
            score: adjutoriumScore,
            width: 760
        )

        XCTAssertEqual(
            Set(adjutoriumLayout.lyrics.filter { $0.text == "-" }.map(\.neumeID)),
            Set(["lyric-hyphen-adjutorium-event-3-adjutorium-event-4"]),
            "Match Exsurge's single adjutóri-um connector."
        )
    }

    func testReferenceDoxologyRetainsItsSelectiveConnectors() throws {
        let fragments = [
            "Gló", "ri", "a", "Pa", "tri,", "et", "Fí", "li", "o,", "et",
            "Spi", "rí", "tu", "i", "San", "cto.", "Sic", "ut", "e", "rat",
            "in", "prin", "cí", "pi", "o,", "et", "nunc,"
        ]
        let timeline = GregorianEventTimeline(
            events: fragments.enumerated().map { index, fragment in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: 0
                )
            }
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: reference-doxology; %% (c3) Gló(h)ri(h)a(h) Pa(h)tri,(h) et(h) Fí(h)li(h)o,(h) et(h) Spi(h)rí(h)tu(h)i(h) San(g)cto.(h.) (:) Sic(h)ut(h) e(h)rat(h) in(h) prin(h)cí(h)pi(h)o,(h) et(h) nunc,(h) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 760)
        let actualHyphenIDs = Set(
            layout.lyrics.filter { $0.text == "-" }.map(\.neumeID)
        )
        let expectedReferenceConnectors = Set([
            "lyric-hyphen-event-1-event-2",   // Glóri-a
            "lyric-hyphen-event-6-event-7",   // Fí-li-o
            "lyric-hyphen-event-7-event-8",
            "lyric-hyphen-event-12-event-13", // Spíritu-i
            "lyric-hyphen-event-23-event-24"  // princípi-o
        ])

        XCTAssertTrue(
            expectedReferenceConnectors.isSubset(of: actualHyphenIDs),
            "Missing reference connectors: \(expectedReferenceConnectors.subtracting(actualHyphenIDs))"
        )
        XCTAssertFalse(
            actualHyphenIDs.contains("lyric-hyphen-event-0-event-1"),
            "Glóri should remain joined before the selective ri-a connector."
        )
        XCTAssertFalse(
            actualHyphenIDs.contains("lyric-hyphen-event-18-event-19"),
            "The compact e and rat fragments should read as erat, matching the reference engraving."
        )
    }

    func testAutomaticHyphenMarksAWordContinuedOnTheNextStaff() throws {
        let syllables = [
            "Su", "per", "ca", "li", "fra", "gi", "lis", "ti",
            "ce", "ex", "pi", "a", "li", "do", "cious"
        ]
        let timeline = GregorianEventTimeline(
            events: syllables.enumerated().map { index, syllable in
                GregorianEvent(
                    id: "event-\(index)",
                    syllableID: "syllable-\(index)",
                    relativePitch: index % 5
                )
            }
        )
        let body = zip(syllables, Array("fghijifghijifgh"))
            .map { "\($0.0)(\($0.1))" }
            .joined()
        let score = try GregorianScoreParser.parse(
            gabc: "name: line-end-hyphen; %% (c4) \(body) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 240)
        let neumesByID = Dictionary(uniqueKeysWithValues: layout.neumes.map { ($0.id, $0) })
        let splitPair = try XCTUnwrap(
            zip(score.neumes, score.neumes.dropFirst()).first { previous, next in
                guard let previousPlacement = neumesByID[previous.id],
                      let nextPlacement = neumesByID[next.id] else {
                    return false
                }
                return previousPlacement.lineIndex != nextPlacement.lineIndex
                    && !next.startsWord
            }
        )

        let precedingLyric = try XCTUnwrap(
            layout.lyrics.first { $0.neumeID == splitPair.0.id }
        )
        let hyphen = try XCTUnwrap(
            layout.lyrics.first {
                $0.neumeID
                    == "lyric-hyphen-\(splitPair.0.id)-\(splitPair.1.id)"
            }
        )
        XCTAssertEqual(
            hyphen.origin.y,
            precedingLyric.origin.y
                + (precedingLyric.fontSize - hyphen.fontSize) * 1.23 / 2,
            accuracy: 0.001,
            "A line-end hyphen should be vertically centered in the lyric line."
        )
    }

    func testLedgerLinesAppearForNotesOutsideTheStaff() throws {
        let timeline = GregorianEventTimeline(
            events: [
                GregorianEvent(
                    id: "low",
                    syllableID: "s1",
                    relativePitch: -7
                ),
                GregorianEvent(
                    id: "high",
                    syllableID: "s2",
                    relativePitch: 7
                )
            ]
        )
        let score = try GregorianScoreParser.parse(
            gabc: "name: ledger; %% (c4) Low(a) high(m) (::)",
            timeline: timeline
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        XCTAssertTrue(
            layout.strokes.contains {
                if case .ledger = $0.kind { return true }
                return false
            }
        )
    }

    func testLedgerLinesFollowEachNotesPitch() throws {
        let engine = GregorianEngravingLayoutEngine()
        func ledgers(_ gabc: String) throws -> (GregorianLayout, [GregorianPlacedStroke]) {
            let layout = engine.layout(score: try GregorianScoreParser.parse(gabc: gabc), width: 390)
            return (layout, layout.strokes.filter {
                if case .ledger = $0.kind { return true }
                return false
            })
        }
        let staffGap = 200.0 / 16

        // A virga on the first ledger line above has its line, as a punctum
        // there does, though its stem reaches down into the staff.
        for gabc in ["%% (c4) a(l) (::)", "%% (c4) a(lv) (::)"] {
            let (layout, lines) = try ledgers(gabc)
            XCTAssertEqual(lines.count, 1, gabc)
            XCTAssertEqual(try XCTUnwrap(lines.first).start.y, layout.staffs[0].frame.minY - staffGap, accuracy: 0.001, gabc)
        }

        // The low note of a porrectus, which its swash engraves, has its line
        // below the staff, under that note's head.
        let (layout, lines) = try ledgers("%% (c4) a(gbg) (::)")
        let line = try XCTUnwrap(lines.first)
        XCTAssertEqual(lines.count, 1)
        XCTAssertEqual(line.start.y, layout.staffs[0].frame.maxY + staffGap, accuracy: 0.001)
        let low = try XCTUnwrap(layout.events.first { $0.eventID == "note-1" })
        XCTAssertLessThan(line.start.x, low.frame.midX)
        XCTAssertGreaterThan(line.end.x, low.frame.midX)
    }

    func testFlatCClefSetsItsFlatOnTheBItFlattens() throws {
        let engine = GregorianEngravingLayoutEngine()
        let glyphScale = 1.0 / 16
        let staffGap = 200 * glyphScale
        let flatDefinition = GregorianGlyphCatalog.definition(for: .flat)
        for line in 1...4 {
            let score = try GregorianScoreParser.parse(gabc: "%% (cb\(line)) a(g) (::)")
            let layout = engine.layout(score: score, width: 390)
            let staff = layout.staffs[0].frame
            let clef = try XCTUnwrap(layout.glyphs.first { $0.kind == .cClef })
            let flat = try XCTUnwrap(layout.glyphs.first { $0.kind == .flat })
            let note = try XCTUnwrap(layout.glyphs.first { $0.eventID == "note-0" })

            // On B, a step below the clef's line,
            let flatScale = flat.frame.height / flatDefinition.bounds.height
            let clefLineY = staff.minY + CGFloat(4 - line) * staffGap
            XCTAssertEqual(
                flat.frame.minY + flatDefinition.origin.y * flatScale,
                clefLineY + staffGap / 2,
                accuracy: 0.001,
                "cb\(line)"
            )
            // after the clef and before the first note.
            XCTAssertGreaterThan(flat.frame.minX, clef.frame.maxX, "cb\(line)")
            XCTAssertLessThan(flat.frame.maxX, note.frame.minX, "cb\(line)")
        }
    }

    func testCustosThatEndsAStaffStaysWithinItsSystem() throws {
        // Each staff's custos shows the next staff's first note, here high
        // above the staff, and its stem rises higher still.
        let score = try GregorianScoreParser.parse(
            gabc: "%% (c4) A(g) (z) B(m) (z) C(g) (z) D(l) (::)"
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        let custodes = custosGlyphs(in: layout)
        XCTAssertEqual(custodes.count, 3)
        for custos in custodes {
            XCTAssertTrue(
                layout.staffs.contains { $0.systemFrame.contains(custos.frame) },
                "\(custos.frame) outside \(layout.staffs.map(\.systemFrame))"
            )
        }
    }

    func testWrittenCustosIsDrawnAsACustosAndOffersNoPlaceToBeginSinging() throws {
        let score = try parsedCustosFixture(
            "tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
            custosIndex: 2,
            eventCount: 5
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        let custosEventID = score.eventIDs[2]

        // The event keeps its place in the engraving,
        XCTAssertEqual(layout.events.map(\.eventID), score.eventIDs)
        // but is drawn as a custos, and not as a note the guide may light.
        let custodes = custosGlyphs(in: layout)
        XCTAssertEqual(custodes.count, 1)
        let custos = try XCTUnwrap(custodes.first)
        XCTAssertNil(custos.eventID)
        XCTAssertFalse(layout.glyphs.contains { $0.eventID == custosEventID })
        XCTAssertFalse(layout.strokes.contains { $0.eventID == custosEventID })
        let placement = try XCTUnwrap(layout.events.first { $0.eventID == custosEventID })
        XCTAssertTrue(placement.frame.contains(custos.frame))

        // It stands at its own pitch: that of the note before it, here.
        let glyphScale = 1.0 / 16
        let note = try XCTUnwrap(
            layout.glyphs.first {
                $0.eventID == score.eventIDs[1] && $0.kind == .catalog(.punctumQuadratum)
            }
        )
        XCTAssertEqual(
            custos.frame.minY
                + GregorianGlyphCatalog.definition(for: .custosShort).origin.y * glyphScale,
            note.frame.minY
                + GregorianGlyphCatalog.definition(for: .punctumQuadratum).origin.y * glyphScale,
            accuracy: 0.001
        )
        XCTAssertGreaterThan(custos.frame.minX, note.frame.maxX)

        // The cantor guide cannot begin from it.
        XCTAssertEqual(
            layout.neumes.map(\.id),
            [0, 1, 3, 4].map { score.eventIDs[$0] }
        )
        XCTAssertNil(layout.neume(containing: custosEventID))
        XCTAssertTrue(layout.neumes.allSatisfy { $0.noteCount == 1 })
    }

    func testStaffEndingWithAWrittenCustosIsNotGivenAnother() throws {
        let engine = GregorianEngravingLayoutEngine()
        let written = try parsedCustosFixture(
            "tó(h)ris.(f.) (f+::z) (c4) O(h)rémus.(h.) (::)",
            custosIndex: 2,
            eventCount: 5
        )
        let layout = engine.layout(score: written, width: 390)
        XCTAssertEqual(layout.staffs.count, 2)
        let custodes = custosGlyphs(in: layout)
        XCTAssertEqual(custodes.count, 1)
        // The one custos is the written one, in its place after the note.
        let placement = try XCTUnwrap(layout.events.first { $0.eventID == written.eventIDs[2] })
        XCTAssertEqual(placement.lineIndex, 0)
        XCTAssertTrue(placement.frame.contains(try XCTUnwrap(custodes.first).frame))

        // Without one, the staff is given its own as before.
        let unwritten = try parsedCustosFixture(
            "tó(h)ris.(f.) (::z) (c4) O(h)rémus.(h.) (::)",
            custosIndex: nil,
            eventCount: 4
        )
        let plain = engine.layout(score: unwritten, width: 390)
        XCTAssertEqual(plain.staffs.count, 2)
        XCTAssertEqual(custosGlyphs(in: plain).count, 1)
    }

    func testWrittenCustosStaysOnTheStaffOfTheNoteItFollows() throws {
        let syllables = (0..<14).map { "syl\($0)(h)" }.joined(separator: " ")
        let score = try parsedCustosFixture(
            "\(syllables) end.(f.) (f+::) (c4) O(h)ré(g)mus.(h.) (::)",
            custosIndex: 15,
            eventCount: 19
        )
        let engine = GregorianEngravingLayoutEngine()
        for width in stride(from: CGFloat(240), through: 640, by: 5) {
            let layout = engine.layout(score: score, width: width)
            let lines = Dictionary(
                uniqueKeysWithValues: layout.events.map { ($0.eventID, $0.lineIndex) }
            )
            XCTAssertEqual(
                lines[score.eventIDs[15]],
                lines[score.eventIDs[14]],
                "width \(width)"
            )
            XCTAssertEqual(layout.events.map(\.eventID), score.eventIDs, "width \(width)")
        }
    }

    func testLastInkOfAStaffStopsShortOfItsCustos() throws {
        // A psalm verse is short syllables, hyphens within its words and
        // dotted cadences: little of such a staff can be closed up to fit
        // it, and a dot reaches beyond the room its syllable takes.
        let score = try parsedCustosFixture(
            [
                "Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
                "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
                "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
                "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
                "in(h) o(f)re(h) me(g)o.(f.) (::)"
            ].joined(separator: " "),
            custosIndex: nil,
            eventCount: 46
        )
        let engine = GregorianEngravingLayoutEngine()
        var custosCount = 0
        for width in stride(from: CGFloat(240), through: 800, by: 1) {
            let layout = engine.layout(score: score, width: width)
            XCTAssertEqual(layout.events.map(\.eventID), score.eventIDs, "width \(width)")
            let lines = Dictionary(
                uniqueKeysWithValues: layout.events.map { ($0.eventID, $0.lineIndex) }
            )
            for custos in custosGlyphs(in: layout) {
                let staff = try XCTUnwrap(
                    layout.staffs.first {
                        $0.systemFrame.minY <= custos.frame.midY
                            && custos.frame.midY <= $0.systemFrame.maxY
                    },
                    "width \(width)"
                )
                let glyphInk = layout.glyphs
                    .filter { $0.eventID.flatMap { lines[$0] } == staff.index }
                    .map(\.frame.maxX)
                let strokeInk = layout.strokes
                    .filter { $0.eventID.flatMap { lines[$0] } == staff.index }
                    .map { max($0.start.x, $0.end.x) }
                let ink = try XCTUnwrap((glyphInk + strokeInk).max(), "width \(width)")
                XCTAssertLessThan(ink, custos.frame.minX, "width \(width), staff \(staff.index)")
                XCTAssertLessThanOrEqual(
                    custos.frame.maxX,
                    staff.frame.maxX,
                    "width \(width), staff \(staff.index)"
                )
                custosCount += 1
            }
        }
        XCTAssertGreaterThan(custosCount, 0)
    }

    func testRepeatedNoteIsDrawnAsOftenAsItIsWritten() throws {
        let events = ["bi", "tri", "di", "men"].enumerated().map { index, syllable in
            GregorianEvent(
                id: "event-\(index)",
                syllableID: "syllable-\(index)",
                relativePitch: 0
            )
        }
        let score = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) bi(hvv) tri(hsss) di(hss.) men(g) (::)",
            timeline: GregorianEventTimeline(events: events)
        )
        let layout = GregorianEngravingLayoutEngine().layout(score: score, width: 390)
        // Repeated notes stand apart, as Exsurge sets them: a virga's head a
        // quarter staff space after the last one's stem, which stands inside
        // its head; a stropha an eighth of one after the last.
        let staffSpace = 200 * (1.0 / 16)
        let stemWidth = 18.0 / 16
        let gaps: [GregorianGlyphName: CGFloat] = [
            .punctumQuadratum: 0.25 * staffSpace - stemWidth, .stropha: 0.125 * staffSpace
        ]

        XCTAssertEqual(layout.events.map(\.eventID), score.eventIDs)
        XCTAssertEqual(layout.neumes.map(\.eventIDs.count), [1, 1, 1, 1])
        XCTAssertEqual(layout.neumes.map(\.noteCount), [2, 3, 2, 1])

        let expected: [(GregorianGlyphName, Int)] = [
            (.punctumQuadratum, 2), (.stropha, 3), (.stropha, 2), (.punctumQuadratum, 1)
        ]
        for (index, (name, count)) in expected.enumerated() {
            let eventID = score.eventIDs[index]
            let notes = layout.glyphs
                .filter { $0.eventID == eventID && $0.kind == .catalog(name) }
                .sorted { $0.frame.minX < $1.frame.minX }
            XCTAssertEqual(notes.count, count, eventID)
            for (previous, next) in zip(notes, notes.dropFirst()) {
                XCTAssertEqual(
                    next.frame.minX - previous.frame.maxX,
                    gaps[name] ?? 0,
                    accuracy: 0.001
                )
                XCTAssertEqual(next.frame.minY, previous.frame.minY, accuracy: 0.001)
                XCTAssertEqual(next.frame.size, previous.frame.size)
            }
            // Each head of a virga has its stem, inside its right edge.
            if index == 0 {
                let stems = layout.strokes.filter { $0.eventID == eventID && $0.kind == .connector }
                XCTAssertEqual(stems.count, 2)
                for (head, stem) in zip(notes, stems.sorted { $0.start.x < $1.start.x }) {
                    XCTAssertEqual(stem.start.x, head.frame.maxX - stemWidth / 2, accuracy: 0.001)
                    XCTAssertGreaterThan(stem.end.y, head.frame.maxY)
                }
            }
            // The event answers to all of them together.
            let placement = try XCTUnwrap(layout.events.first { $0.eventID == eventID })
            XCTAssertTrue(notes.allSatisfy { placement.frame.contains($0.frame) })
            let neume = try XCTUnwrap(layout.neume(containing: eventID))
            XCTAssertTrue(notes.allSatisfy { neume.inkFrame.contains($0.frame) })
        }

        // The dot written after a repeated note follows the last of them.
        let dotted = score.eventIDs[2]
        let mora = try XCTUnwrap(
            layout.glyphs.first { $0.eventID == dotted && $0.kind == .mora }
        )
        let lastStropha = try XCTUnwrap(
            layout.glyphs
                .filter { $0.eventID == dotted && $0.kind == .catalog(.stropha) }
                .max { $0.frame.maxX < $1.frame.maxX }
        )
        XCTAssertGreaterThan(mora.frame.minX, lastStropha.frame.maxX)

        // A repeated note takes the room of as many notes written out.
        let writtenOut = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) bi(hvhv) men(g) (::)",
            timeline: GregorianEventTimeline(
                events: ["bi", "bi", "men"].enumerated().map { index, syllable in
                    GregorianEvent(
                        id: "event-\(index)",
                        syllableID: "syllable-\(syllable)",
                        relativePitch: 0
                    )
                }
            )
        )
        let abbreviated = try GregorianScoreParser.parse(
            gabc: "name: repeated; %% (c4) bi(hvv) men(g) (::)",
            timeline: GregorianEventTimeline(
                events: ["bi", "men"].enumerated().map { index, syllable in
                    GregorianEvent(
                        id: "event-\(index)",
                        syllableID: "syllable-\(syllable)",
                        relativePitch: 0
                    )
                }
            )
        )
        let engine = GregorianEngravingLayoutEngine()
        let virgaEvents: Set<String?> = ["event-0", "event-1"]
        let writtenOutVirgae = engine.layout(score: writtenOut, width: 390).glyphs
            .filter { $0.kind == .catalog(.punctumQuadratum) && virgaEvents.contains($0.eventID) }
            .map(\.frame)
        let abbreviatedVirgae = engine.layout(score: abbreviated, width: 390).glyphs
            .filter { $0.kind == .catalog(.punctumQuadratum) && $0.eventID == "event-0" }
            .map(\.frame)
        XCTAssertEqual(writtenOutVirgae.count, 2)
        XCTAssertEqual(abbreviatedVirgae.count, 2)
        for (lhs, rhs) in zip(writtenOutVirgae, abbreviatedVirgae) {
            XCTAssertEqual(lhs.minX, rhs.minX, accuracy: 0.001)
            XCTAssertEqual(lhs.minY, rhs.minY, accuracy: 0.001)
        }
    }

    private func custosGlyphs(in layout: GregorianLayout) -> [GregorianPlacedGlyph] {
        layout.glyphs.filter {
            if case .custos = $0.kind { return true }
            return false
        }
    }

    /// A score in the third-line C clef whose events are one to a syllable,
    /// except that the custos has a syllable to itself.
    private func parsedCustosFixture(
        _ body: String,
        custosIndex: Int?,
        eventCount: Int
    ) throws -> GregorianScore {
        let events = (0..<eventCount).map { index in
            GregorianEvent(
                id: "event-\(index)",
                syllableID: "syllable-\(index)",
                relativePitch: 0
            )
        }
        return try GregorianScoreParser.parse(
            gabc: "name: custos; %% (c3) \(body)",
            timeline: GregorianEventTimeline(events: events)
        )
    }

    private func parsedFixture(noteCount: Int) throws -> GregorianScore {
        let syllables = (0..<noteCount).map { "s\($0)" }
        let body = syllables.enumerated().map { index, syllable in
            "\(syllable)(\(Character(UnicodeScalar(102 + index % 7)!)))"
        }.joined(separator: " ")
        let events = syllables.enumerated().map { index, syllable in
            GregorianEvent(
                id: "event-\(index)",
                syllableID: "syllable-\(index)",
                relativePitch: index % 7
            )
        }
        return try GregorianScoreParser.parse(
            gabc: "name: layout; %% (c4) \(body) (::)",
            timeline: GregorianEventTimeline(events: events)
        )
    }
}
