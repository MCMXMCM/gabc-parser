// A port of the Kotlin library's NeumeShapeTest.

import { assert, describe, expect, it } from "vitest";
import type { GregorianGlyphName } from "../src/GregorianGlyphCatalog.js";
import {
  catalogGlyph,
  sameGlyphKind,
  type GregorianGlyphKind,
  type GregorianLayout,
  type GregorianPlacedGlyph,
  type GregorianPlacedStroke,
} from "../src/GregorianLayout.js";
import { GregorianScoreParser, type GregorianScore } from "../src/GregorianNotation.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

/** One staff space, and one step, at the default notation scale. */
const staffSpace = 12.5;
const staffStep = 6.25;
const stemWidth = 18 / 16;

/** Kotlin's `layout(notation)`, renamed so that the layouts the tests name `layout` do not hide it. */
function layoutOf(notation: string): [GregorianScore, GregorianLayout] {
  const score = GregorianScoreParser.parse(`%% (c4) a(${notation}) (::)`);
  return [score, layoutEngine().layout(score, 390)];
}

function heads(layout: GregorianLayout, score: GregorianScore): GregorianPlacedGlyph[] {
  const notes = new Set(score.eventIds);
  const signs = [catalogGlyph("VerticalEpisemaAbove"), catalogGlyph("VerticalEpisemaBelow")];
  return layout.glyphs
    .filter(
      (glyph) =>
        glyph.eventId != null &&
        notes.has(glyph.eventId) &&
        glyph.kind.kind === "catalog" &&
        !signs.some((sign) => sameGlyphKind(glyph.kind, sign)),
    )
    .sort((first, second) => first.frame.left - second.frame.left);
}

function stems(layout: GregorianLayout): GregorianPlacedStroke[] {
  return layout.strokes.filter((stroke) => stroke.kind.kind === "connector");
}

function catalog(name: GregorianGlyphName): GregorianGlyphKind {
  return catalogGlyph(name);
}

/**
 * The neumes drawn as Exsurge draws them, and their signs set where the chant books set them. A port of the Swift
 * package's Tests/GABCLayoutTests/NeumeShapeTests.swift.
 */
describe("NeumeShapeTest", () => {
  it("aClivisHangsFromAStemOnItsLeft", () => {
    const [score, layout] = layoutOf("hf");
    const notes = heads(layout, score);
    expect(notes.length).toBe(2);
    const stem = stems(layout)[0]!;
    // On the left of the upper note, from its centre down past the lower.
    assert.closeTo(stem.start.x, notes[0]!.frame.left + stemWidth / 2, 0.01);
    assert.closeTo(stem.start.y, notes[0]!.frame.center.y, 1.5);
    expect(stem.end.y).toBeGreaterThan(notes[1]!.frame.center.y);
  });

  it("aPesIsJoinedOnItsRightWhereItLeaps", () => {
    const [score, layout] = layoutOf("fh");
    expect(heads(layout, score).map((head) => head.kind)).toStrictEqual([catalog("PodatusLower"), catalog("PodatusUpper")]);
    const stem = stems(layout)[0]!;
    const lower = heads(layout, score)[0]!;
    assert.closeTo(stem.start.x + stemWidth / 2, lower.frame.right, 0.01);

    // A pes of a second takes the short halves, which meet.
    const [second, secondLayout] = layoutOf("fg");
    expect(heads(secondLayout, second).map((head) => head.kind)).toStrictEqual([
      catalog("PodatusLowerShort"),
      catalog("PodatusUpperShort"),
    ]);
    expect(stems(secondLayout)).toEqual([]);
  });

  it("aPorrectusSetsItsLastNoteOverTheEndOfItsOblique", () => {
    const [score, layout] = layoutOf("hgh");
    const notes = heads(layout, score);
    const swash = notes.find((note) => sameGlyphKind(note.kind, catalog("Porrectus1")))!;
    const last = notes.find((note) => sameGlyphKind(note.kind, catalog("PodatusUpperShort")))!;
    assert.closeTo(last.frame.right, swash.frame.right, 0.01);
    // And hangs from a stem on its left.
    expect(stems(layout).some((stem) => Math.abs(stem.start.x - stemWidth / 2 - swash.frame.left) < 0.01)).toBe(true);
  });

  it("theDiamondsOfAClimacusStepDownUnjoined", () => {
    const [score, layout] = layoutOf("hvGF");
    const notes = heads(layout, score);
    expect(notes.map((note) => note.kind)).toStrictEqual(
      (["PunctumQuadratum", "PunctumInclinatum", "PunctumInclinatum"] as const).map((name) => catalog(name)),
    );
    // The virga's stem is the only one.
    expect(stems(layout).length).toBe(1);
    expect(notes[2]!.frame.left).toBeGreaterThan(notes[1]!.frame.left);
    expect(notes[2]!.frame.center.y).toBeGreaterThan(notes[1]!.frame.center.y);
  });

  it("plainNotesRepeatedOnOnePitchArePuncta", () => {
    const [score, layout] = layoutOf("fff");
    expect(heads(layout, score).map((head) => head.kind)).toStrictEqual(
      Array.from({ length: 3 }, () => catalog("PunctumQuadratum")),
    );
    // Where the melody goes on, the last of them begins a pressus, its punctum abutting the pes.
    const [pressus, pressusLayout] = layoutOf("ffg");
    expect(pressus.neumes.map((neume) => neume.form)).toEqual(["punctum", "podatus"]);
    const notes = heads(pressusLayout, pressus);
    assert.closeTo(notes[1]!.frame.left - notes[0]!.frame.right, 0, 0.01);
  });

  it("aDotStandsJustAfterItsNoteInTheSpaceAboveALine", () => {
    // h is on the third line, g in the space below it.
    const cases: [notation: string, raised: boolean][] = [
      ["h.", true],
      ["g.", false],
    ];
    for (const [notation, raised] of cases) {
      const [score, layout] = layoutOf(notation);
      const head = heads(layout, score)[0]!;
      const dot = layout.glyphs.find((glyph) => glyph.kind.kind === "mora")!;
      assert.closeTo(dot.frame.width, 0.33 * staffSpace, 0.01, notation);
      assert.closeTo(dot.frame.left - head.frame.right, 0.1 * staffSpace, 0.01, notation);
      const headCentre = head.frame.top + 60.906 / 16;
      assert.closeTo(dot.frame.center.y, headCentre - (raised ? 0.75 * staffStep : 0), 0.01, notation);
    }
  });

  it("aDoubleDotDotsANeumesLastTwoNotes", () => {
    const [score, layout] = layoutOf("hg..");
    const notes = heads(layout, score);
    const dots = layout.glyphs.filter((glyph) => glyph.kind.kind === "mora");
    expect(dots.length).toBe(2);
    // Both after the neume, one at each note's height.
    expect(dots.every((dot) => dot.frame.left > notes[1]!.frame.right)).toBe(true);
    expect(new Set(dots.map((dot) => dot.eventId))).toEqual(new Set(score.eventIds));
  });

  it("anEpisemaKeepsOffTheStaffsLines", () => {
    for (const notation of ["h_", "g_", "f_0", "j_", "hg__"]) {
      const [score, layout] = layoutOf(notation);
      const staff = layout.staffs[0]!;
      const lines = Array.from({ length: 4 }, (_, index) => staff.frame.top + index * staffSpace);
      const episemata = layout.strokes.filter((stroke) => stroke.kind.kind === "episema");
      expect(episemata.length, notation).toBe(1);
      for (const episema of episemata) expect(lines.every((line) => Math.abs(line - episema.start.y) > 1), notation).toBe(true);
      // One that the GABC reaches over two notes spans both.
      if (notation === "hg__") {
        const notes = heads(layout, score);
        assert.closeTo(episemata[0]!.start.x, notes[0]!.frame.left, 0.01);
        assert.closeTo(episemata[0]!.end.x, notes[1]!.frame.right, 0.01);
      }
    }
  });

  it("anIctusStandsBelowUnlessTheNeumeOrTheGabcSetsItAbove", () => {
    const cases: [notation: string, above: boolean][] = [
      ["g'", false],
      ["g'1", true],
      ["g'0", false],
      ["h'g", true],
      ["fg'", true],
      ["hvGF'", false],
    ];
    for (const [notation, above] of cases) {
      const [, layout] = layoutOf(notation);
      const ictus = layout.glyphs.find(
        (glyph) =>
          sameGlyphKind(glyph.kind, catalog("VerticalEpisemaAbove")) ||
          sameGlyphKind(glyph.kind, catalog("VerticalEpisemaBelow")),
      );
      expect(ictus, notation).toBeDefined();
      expect(sameGlyphKind(ictus!.kind, catalog("VerticalEpisemaAbove")), notation).toBe(above);
    }
  });

  it("aQuarterBarCrossesTheTopLine", () => {
    const score = GregorianScoreParser.parse("%% (c4) a(f) (,) b(g) (::)");
    const layout = layoutEngine().layout(score, 390);
    const staff = layout.staffs[0]!;
    const bar = layout.strokes.find((stroke) => stroke.kind.kind === "division" && stroke.kind.division === "minima")!;
    assert.closeTo(bar.start.y, staff.frame.top - staffSpace / 2, 0.01);
    assert.closeTo(bar.end.y, staff.frame.top + staffSpace / 2, 0.01);
  });
});
