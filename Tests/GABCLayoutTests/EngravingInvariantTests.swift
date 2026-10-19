import GABCLayout
import GABCLyricFont
import XCTest

/// The checks a notation validator makes of every score in a corpus, made
/// here of scores that exercise each kind of sign.
final class EngravingInvariantTests: XCTestCase {
    private static let scores = [
        "name: Deus in adiutorium; mode: 6; %% (c4) De(f)us(g) in(g) ad(g)ju(h)tó(g)ri(f)um(g) me(h)um(g) in(f)tén(g)de.(f.) (::)",
        "name: signs; mode: 1; %% (c4) Ky(fg_)ri(h.w)e(ixj~) (;) test(k) (::)",
        "name: clefs; mode: 4; %% (c3) Al(dfe)le(g) (z) (f3) lú(hi)ia.(g.) (::)",
        "name: forms; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) por(hgh) res(hghg) cli(hgf) an(hgf~) (::)",
        "name: written; %% (c3) s0(f.) (f+::) (c4) s2(hvv) s3(hsss) s4(h.) (::)",
        "name: In manus; %% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)",
        "name: ledger; %% (c4) Low(a) high(m) (::)",
        "name: marks; %% (c4) Ky(f.._0#') ri(gr) e(h_) e(i') le(go) i(gwh) son(g<) (h>) (::)",
        "name: repeated; %% (c4) et(gvvF'ED) ri(igfssded) Ky(fghvv) (::)",
        "name: preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
        "name: verse; %% (c3) " + [
            "Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
            "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
            "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
            "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
            "in(h) o(f)re(h) me(g)o.(f.) (::)"
        ].joined(separator: " ")
    ]

    override func setUp() {
        super.setUp()
        // Lyrics are measured in EB Garamond only once it is registered.
        GregorianLyricFont.registerBundledFont()
    }

    func testScoresKeepTheirIdentityAndInkAtEveryWidth() throws {
        for gabc in Self.scores {
            try assertEngravingInvariants(GregorianScoreParser.parse(gabc: gabc), gabc)
        }
    }

    func testSemanticFixturesEngraveAsNamedForms() throws {
        let score = try GregorianScoreParser.parse(
            gabc: "name: semantic; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) (::)"
        )
        XCTAssertEqual(score.neumes.map(\.form), [.podatus, .clivis, .scandicus, .torculus])
        let names = Set(
            GregorianEngravingLayoutEngine().layout(score: score, width: 390)
                .glyphs.map(\.kind.catalogName)
        )
        XCTAssertTrue(names.contains(.podatusLower))
        XCTAssertTrue(names.contains(.podatusUpper))

        let boundary = try GregorianScoreParser.parse(gabc: "name: boundary; %% (c4) Ky(fg!hi) (::)")
        XCTAssertEqual(boundary.neumes.map(\.form), [.podatus, .podatus])
        XCTAssertEqual(boundary.neumes.map(\.lyric), ["Ky", ""])

        let written = try GregorianScoreParser.parse(
            gabc: "name: written; %% (c3) s0(f.) (f+::) (c4) s2(hvv) s3(hsss) s4(h.) (::)"
        )
        XCTAssertEqual(written.neumes.map { $0.notes[0].isCustos }, [false, true, false, false, false])
        XCTAssertEqual(written.neumes.map(\.writtenNoteCount), [1, 0, 2, 3, 1])
        XCTAssertEqual(written.neumes.map(\.form), [.punctum, .punctum, .bivirga, .tristropha, .punctum])
        let writtenLayout = GregorianEngravingLayoutEngine().layout(score: written, width: 390)
        XCTAssertEqual(writtenLayout.events.map(\.eventID), written.eventIDs)
        XCTAssertEqual(writtenLayout.glyphs.filter { $0.kind == .custos }.count, 1)
        XCTAssertEqual(writtenLayout.neumes.map(\.noteCount), [1, 2, 3, 1])
        XCTAssertEqual(writtenLayout.glyphs.filter { $0.kind == .catalog(.virgaShort) }.count, 2)
        XCTAssertEqual(writtenLayout.glyphs.filter { $0.kind == .catalog(.stropha) }.count, 3)
    }

    private func assertEngravingInvariants(
        _ notation: GregorianScore,
        _ name: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let notes = notation.neumes.flatMap(\.notes)
        let custodes = Set(notes.filter(\.isCustos).map(\.id))
        let repeatedNotes = notes.filter { $0.repetitionCount > 1 }
        let writtenNoteCount = notation.neumes.reduce(0) { $0 + $1.writtenNoteCount }

        for width: CGFloat in [320, 390, 768] {
            let context = "\(name) at width \(width)"
            let layout = GregorianEngravingLayoutEngine().layout(score: notation, width: width)
            XCTAssertEqual(
                layout.events.map(\.eventID),
                notation.eventIDs,
                "lost event identity: \(context)",
                file: file,
                line: line
            )
            XCTAssertTrue(
                layout.size.width.isFinite && layout.size.height.isFinite
                    && layout.glyphs.allSatisfy {
                        $0.frame.minX.isFinite && $0.frame.minY.isFinite
                            && $0.frame.width.isFinite && $0.frame.height.isFinite
                    },
                "non-finite geometry: \(context)",
                file: file,
                line: line
            )
            // A custos keeps its event's place but is no place to begin
            // singing, and every other neume draws at least one note.
            XCTAssertTrue(
                layout.neumes.allSatisfy {
                    $0.noteCount > 0 && custodes.isDisjoint(with: $0.eventIDs)
                },
                "offers a custos as a neume: \(context)",
                file: file,
                line: line
            )
            XCTAssertEqual(
                layout.neumes.reduce(0) { $0 + $1.noteCount },
                writtenNoteCount,
                "lost a written note: \(context)",
                file: file,
                line: line
            )
            // It is drawn as a custos, in its event's place, and not as a
            // note that a highlight may light.
            for custos in custodes {
                let placement = layout.events.first { $0.eventID == custos }
                XCTAssertTrue(
                    placement.map { placement in
                        layout.glyphs.contains { glyph in
                            glyph.kind == .custos && placement.frame.contains(glyph.frame)
                        }
                    } ?? false,
                    "custos not drawn in its place: \(context)",
                    file: file,
                    line: line
                )
                XCTAssertFalse(
                    layout.glyphs.contains { $0.eventID == custos },
                    "custos drawn as a note: \(context)",
                    file: file,
                    line: line
                )
            }
            // A note written twice or thrice is drawn as often.
            for note in repeatedNotes {
                let drawn = layout.glyphs.filter { glyph in
                    guard glyph.eventID == note.id,
                          case let .catalog(name) = glyph.kind else {
                        return false
                    }
                    return name != .verticalEpisemaAbove && name != .verticalEpisemaBelow
                }.count
                XCTAssertEqual(
                    drawn,
                    note.repetitionCount,
                    "repeated note drawn \(drawn) times: \(context)",
                    file: file,
                    line: line
                )
            }
            for staff in Dictionary(grouping: layout.neumes, by: \.lineIndex).values {
                let ordered = staff.sorted { $0.hitFrame.minX < $1.hitFrame.minX }
                for pair in zip(ordered, ordered.dropFirst()) {
                    XCTAssertLessThanOrEqual(
                        pair.0.hitFrame.maxX,
                        pair.1.hitFrame.minX + 0.01,
                        "overlapping neume targets: \(context)",
                        file: file,
                        line: line
                    )
                }
            }
        }
    }
}
