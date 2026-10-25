// A port of the Kotlin library's EngravingInvariantTest.

import { describe, expect, it } from "vitest";
import { catalogGlyph, catalogName, sameGlyphKind } from "../src/GregorianLayout.js";
import { GregorianScoreParser, type GregorianScore } from "../src/GregorianNotation.js";
import { groupBy, zipWithNext } from "./collections.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

/**
 * The checks a notation validator makes of every score in a corpus, made here of scores that exercise each kind of
 * sign.
 */
describe("EngravingInvariantTest", () => {
  it("scoresKeepTheirIdentityAndInkAtEveryWidth", () => {
    for (const gabc of SCORES) {
      assertEngravingInvariants(GregorianScoreParser.parse(gabc), gabc);
    }
  });

  it("semanticFixturesEngraveAsNamedForms", () => {
    const score = GregorianScoreParser.parse("name: semantic; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) (::)");
    expect(score.neumes.map((neume) => neume.form)).toEqual(["podatus", "clivis", "scandicus", "torculus"]);
    const names = new Set(
      layoutEngine()
        .layout(score, 390)
        .glyphs.map((glyph) => catalogName(glyph.kind)),
    );
    expect(names.has("PodatusLowerShort")).toBe(true);
    expect(names.has("PodatusUpperShort")).toBe(true);

    const boundary = GregorianScoreParser.parse("name: boundary; %% (c4) Ky(fg!hi) (::)");
    expect(boundary.neumes.map((neume) => neume.form)).toEqual(["podatus", "podatus"]);
    expect(boundary.neumes.map((neume) => neume.lyric)).toEqual(["Ky", ""]);

    const written = GregorianScoreParser.parse("name: written; %% (c3) s0(f.) (f+::) (c4) s2(hvv) s3(hsss) s4(h.) (::)");
    expect(written.neumes.map((neume) => neume.notes[0]!.isCustos)).toEqual([false, true, false, false, false]);
    expect(written.neumes.map((neume) => neume.writtenNoteCount)).toEqual([1, 0, 2, 3, 1]);
    expect(written.neumes.map((neume) => neume.form)).toEqual(["punctum", "punctum", "bivirga", "tristropha", "punctum"]);
    const writtenLayout = layoutEngine().layout(written, 390);
    expect(writtenLayout.events.map((event) => event.eventId)).toEqual(written.eventIds);
    expect(writtenLayout.glyphs.filter((glyph) => glyph.kind.kind === "custos").length).toBe(1);
    expect(writtenLayout.neumes.map((neume) => neume.noteCount)).toEqual([1, 2, 3, 1]);
    // A bivirga is two heads, each with its stem.
    const bivirga = written.eventIds[2]!;
    expect(
      writtenLayout.glyphs.filter(
        (glyph) => sameGlyphKind(glyph.kind, catalogGlyph("PunctumQuadratum")) && glyph.eventId === bivirga,
      ).length,
    ).toBe(2);
    expect(
      writtenLayout.strokes.filter((stroke) => stroke.kind.kind === "connector" && stroke.eventId === bivirga).length,
    ).toBe(2);
    expect(writtenLayout.glyphs.filter((glyph) => sameGlyphKind(glyph.kind, catalogGlyph("Stropha"))).length).toBe(3);
  });
});

function assertEngravingInvariants(notation: GregorianScore, name: string): void {
  const notes = notation.neumes.flatMap((neume) => neume.notes);
  const custodes = new Set(notes.filter((note) => note.isCustos).map((note) => note.id));
  const repeatedNotes = notes.filter((note) => note.repetitionCount > 1);
  const writtenNoteCount = notation.neumes.reduce((total, neume) => total + neume.writtenNoteCount, 0);

  for (const width of [320, 390, 768]) {
    const context = `${name} at width ${width}`;
    const layout = layoutEngine().layout(notation, width);
    expect(
      layout.events.map((event) => event.eventId),
      `lost event identity: ${context}`,
    ).toEqual(notation.eventIds);
    expect(
      Number.isFinite(layout.size.width) &&
        Number.isFinite(layout.size.height) &&
        layout.glyphs.every(
          (glyph) =>
            Number.isFinite(glyph.frame.left) &&
            Number.isFinite(glyph.frame.top) &&
            Number.isFinite(glyph.frame.width) &&
            Number.isFinite(glyph.frame.height),
        ),
      `non-finite geometry: ${context}`,
    ).toBe(true);
    // A custos keeps its event's place but is no place to begin
    // singing, and every other neume draws at least one note.
    expect(
      layout.neumes.every((neume) => neume.noteCount > 0 && !neume.eventIds.some((id) => custodes.has(id))),
      `offers a custos as a neume: ${context}`,
    ).toBe(true);
    expect(
      layout.neumes.reduce((total, neume) => total + neume.noteCount, 0),
      `lost a written note: ${context}`,
    ).toBe(writtenNoteCount);
    // It is drawn as a custos, in its event's place, and not as a
    // note that a highlight may light.
    for (const custos of custodes) {
      const placement = layout.events.find((event) => event.eventId === custos);
      expect(
        placement != null &&
          layout.glyphs.some((glyph) => glyph.kind.kind === "custos" && placement.frame.contains(glyph.frame)),
        `custos not drawn in its place: ${context}`,
      ).toBe(true);
      expect(
        layout.glyphs.some((glyph) => glyph.eventId === custos),
        `custos drawn as a note: ${context}`,
      ).toBe(false);
    }
    // A note written twice or thrice is drawn as often.
    for (const note of repeatedNotes) {
      const drawn = layout.glyphs.filter((glyph) => {
        const glyphName = glyph.kind.kind === "catalog" ? glyph.kind.name : null;
        if (glyph.eventId !== note.id || glyphName == null) return false;
        return glyphName !== "VerticalEpisemaAbove" && glyphName !== "VerticalEpisemaBelow";
      }).length;
      expect(drawn, `repeated note drawn ${drawn} times: ${context}`).toBe(note.repetitionCount);
    }
    for (const staff of groupBy(layout.neumes, (neume) => neume.lineIndex).values()) {
      const ordered = [...staff].sort((first, second) => first.hitFrame.left - second.hitFrame.left);
      for (const [first, second] of zipWithNext(ordered)) {
        assertLessThanOrEqual(`overlapping neume targets: ${context}`, first.hitFrame.right, second.hitFrame.left + 0.01);
      }
    }
  }
}

/** XCTAssertLessThanOrEqual: fails, naming both values, unless the first is at most the second. */
function assertLessThanOrEqual(message: string, first: number, second: number): void {
  expect(first <= second, `(${first}) is greater than (${second}) - ${message}`).toBe(true);
}

const SCORES = [
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
  "name: verse; %% (c3) " +
    [
      "Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
      "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
      "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
      "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
      "in(h) o(f)re(h) me(g)o.(f.) (::)",
    ].join(" "),
];
