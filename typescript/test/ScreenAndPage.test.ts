// A port of the Kotlin library's ScreenAndPageTest.

import { assert, describe, expect, it } from "vitest";
import { GregorianEngravingLayoutEngine, type GregorianLayout } from "../src/GregorianLayout.js";
import { GregorianScoreParser } from "../src/GregorianNotation.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

/** One step of the staff, at the default notation scale. */
const staffStep = 6.25;

/** Kotlin's `layout(notation)`, renamed so that the layouts the tests name `layout` do not hide it. */
function layoutOf(notation: string): GregorianLayout {
  return layoutEngine().layout(GregorianScoreParser.parse(`%% (c4) a(${notation}) (::)`), 390);
}

/** The lowest ink of a layout's notes and signs, its staff lines aside. */
function lowestInk(layout: GregorianLayout): number {
  const glyphs = layout.glyphs.map((glyph) => glyph.frame.bottom);
  const strokes = layout.strokes
    .filter((stroke) => stroke.kind.kind !== "staff")
    .map((stroke) => Math.max(stroke.start.y, stroke.end.y) + stroke.lineWidth / 2);
  const ink = [...glyphs, ...strokes];
  return ink.length > 0 ? Math.max(...ink) : 0;
}

/** The highest ink of a layout's notes and signs, its staff lines aside. */
function highestInk(layout: GregorianLayout): number {
  const glyphs = layout.glyphs.map((glyph) => glyph.frame.top);
  const strokes = layout.strokes
    .filter((stroke) => stroke.kind.kind !== "staff")
    .map((stroke) => Math.min(stroke.start.y, stroke.end.y) - stroke.lineWidth / 2);
  const ink = [...glyphs, ...strokes];
  return ink.length > 0 ? Math.min(...ink) : 0;
}

/**
 * The score as a screen shows it and a page would set it: the words no lower than the notes need. A port of the Swift
 * package's Tests/GABCLayoutTests/ScreenAndPageTests.swift.
 */
describe("ScreenAndPageTest", () => {
  it("theWordsStandAsFarBelowTheLowestInkAsBelowANoteOnTheBottomLine", () => {
    // d is on the bottom line, c in the space below it and b on a ledger line; an ictus stands below its note, and
    // a clivis hangs from its stem.
    const bottomLine = layoutOf("d");
    const words = bottomLine.lyrics[0]!.origin.y;
    const clearance = words - lowestInk(bottomLine);
    for (const notation of ["c", "b", "d'", "dc", "fc"]) {
      const lower = layoutOf(notation);
      assert.closeTo(lower.lyrics[0]!.origin.y - lowestInk(lower), clearance, 0.01, notation);
    }
    // A note in the space below the staff lowers the words by a step, no more.
    assert.closeTo(layoutOf("c").lyrics[0]!.origin.y - words, staffStep, 0.01);

    // Notes higher on the staff leave the words where a note on the bottom line does.
    const high = layoutOf("h");
    assert.closeTo(
      high.lyrics[0]!.origin.y - high.staffs[0]!.frame.bottom,
      words - bottomLine.staffs[0]!.frame.bottom,
      0.01,
    );
  });

  it("aStaffsBandHoldsAllItsInk", () => {
    // The episema of a liquescent clivis above the staff rises higher than its notes leave room for: the staff is
    // set lower to hold it, a point to spare.
    const lifted = layoutOf("k_i~");
    expect(lifted.staffs[0]!.frame.top).toBeGreaterThan(layoutOf("ki~").staffs[0]!.frame.top);
    const episema = lifted.strokes.find((stroke) => stroke.kind.kind === "episema")!;
    expect(episema.start.y - episema.lineWidth / 2).toBeGreaterThanOrEqual(lifted.staffs[0]!.systemFrame.top + 1 - 0.01);
    for (const notation of ["k_i~", "k_", "l_", "j_", "j"]) {
      const layout = layoutOf(notation);
      expect(highestInk(layout), notation).toBeGreaterThanOrEqual(layout.staffs[0]!.systemFrame.top);
    }
  });

  it("onAScreenTheStaffAndItsStrokesFallOnWholePixels", () => {
    // Stems, an episema, ledger lines above and below the staff, and bars of every kind, over several staffs.
    const score = GregorianScoreParser.parse(
      "%% (c4) Lau(f)da(fhv)te(hg/hiH'G) Dó(kl)mi(lk)num(k.) (,) om(ji)nes(hg_) gen(gf)tes:(fe..) (;) " +
        "lau(dc)da(cd)te(b) e(dfd)um(d.) (:) om(hj)nes(jk) pó(lm)pu(ml)li.(k) (::)",
    );
    const engine = layoutEngine();
    for (const scale of [2, 3]) {
      const layout = engine.layout(score, 260, { metrics: { displayScale: scale } });
      expect(layout.staffs.length).toBeGreaterThan(1);
      const onGrid = (points: number, offset: number, message: string) => {
        const pixels = points * scale - offset;
        assert.closeTo(pixels, Math.round(pixels), 0.01, `${scale}x: ${message}`);
      };
      // A staff space of whole pixels, the nearest to 12.5 points, and every staff begun on the grid.
      const staffSpace = layout.staffs[0]!.frame.height / 3;
      assert.closeTo(staffSpace, Math.round(12.5 * scale) / scale, 0.001);
      for (const staff of layout.staffs) onGrid(staff.systemFrame.top, 0, `staff ${staff.index} begins`);
      for (const stroke of layout.strokes) {
        // Whole pixels wide, two at least, with its edges between pixels.
        const pixels = Math.round(stroke.lineWidth * scale);
        onGrid(stroke.lineWidth, 0, `${JSON.stringify(stroke.kind)} width`);
        expect(pixels).toBeGreaterThanOrEqual(2);
        const offset = pixels % 2 === 0 ? 0 : 0.5;
        if (stroke.start.y === stroke.end.y) {
          onGrid(stroke.start.y, offset, `${JSON.stringify(stroke.kind)} across`);
        } else if (stroke.start.x === stroke.end.x) {
          onGrid(stroke.start.x, offset, `${JSON.stringify(stroke.kind)} up and down`);
        }
      }
      // Stems and joins one weight: a point, or as near as whole pixels come.
      const stems = new Set(layout.strokes.filter((stroke) => stroke.kind.kind === "connector").map((stroke) => stroke.lineWidth));
      expect(stems).toEqual(new Set([Math.round(1.125 * scale) / scale]));
    }
  });

  it("aPieceWithoutAPsalmToneTakesAnInitialUnderItsMode", () => {
    const label = (mode: string, opensPiece: boolean) => GregorianEngravingLayoutEngine.openingLabel(mode, opensPiece);
    expect(label("8", true)).toBe("8");
    expect(label("4. alt", true)).toBe("4");
    expect(label("VIII", true)).toBe("8");
    expect(label("8g", true)).toBe("8G");
    expect(label("irreg.", true)).toBeNull();
    // Only a piece takes one: a psalm's verses, a tone with no conclusion, take none.
    expect(label("8", false)).toBeNull();

    const score = GregorianScoreParser.parse("%% (c4) TE(f) lu(g)cis(h) (::)");
    const engine = layoutEngine();
    expect(engine.layout(score, 390, { openingLabel: "8" }).initials).toEqual([]);
    const hymn = engine.layout(score, 390, { openingLabel: "8", opensPiece: true });
    const initial = hymn.initials[0]!;
    expect(initial.text).toBe("T");
    expect(initial.annotation).toBe("8");
    expect(initial.style).toBe("accented");
    expect(hymn.lyrics[0]!.text).toBe("e");
    // A piece with no mode takes an initial with nothing above it.
    const unmarked = engine.layout(score, 390, { opensPiece: true });
    expect(unmarked.initials.map((each) => each.text)).toEqual(["T"]);
    expect(unmarked.initials[0]!.annotation).toBeNull();
  });

  it("anInitialStandsOnTheWordsBaselineUnderItsAnnotation", () => {
    const score = GregorianScoreParser.parse("%% (c4) SAL(g)ve(h) Re(h)gí(g)na(f) (::)");
    const layout = layoutEngine().layout(score, 390, { openingLabel: "1", opensPiece: true });
    const initial = layout.initials[0]!;
    const lyric = layout.lyrics[0]!;
    // The rest of the word small, however the GABC writes it.
    expect(lyric.text).toBe("al");
    const puer = layoutEngine().layout(GregorianScoreParser.parse("%% (c4) PU(ei)ER(i.) *() na(iji)tus(h) (::)"), 390, {
      openingLabel: "7",
      opensPiece: true,
    });
    expect(puer.lyrics.slice(0, 3).map((each) => each.text)).toEqual(["u", "er", "*"]);
    // EB Garamond rises 1.007 em above its baseline.
    const ascent = 1.007;
    assert.closeTo(initial.origin.y + ascent * initial.fontSize, lyric.origin.y + ascent * lyric.fontSize, 0.05);
    // The annotation centred over the initial, its capitals about the top line.
    const annotationOrigin = initial.annotationOrigin!;
    const staff = layout.staffs[0]!;
    expect(2 * (initial.origin.x + initial.width / 2 - annotationOrigin.x)).toBeGreaterThan(0);
    const capitals = annotationOrigin.y + ascent * initial.annotationFontSize;
    expect(capitals).toBeGreaterThan(staff.frame.top);
    expect(capitals - 0.65 * initial.annotationFontSize).toBeLessThan(staff.frame.top);
  });

  it("anInitialLeavesOutTheFirstVersesNumber", () => {
    const score = GregorianScoreParser.parse(
      "%% (c4) 1. Ec(g)ce(h) nunc(j) be(j)ne(j)dí(j)ci(j)te(j) Dó(k)mi(j)num.(j.) 2.(::) Qui(j) sta(j)tis(j) (::)",
    );
    const engine = layoutEngine();
    const psalm = engine.layout(score, 390, { openingLabel: "8g" });
    expect(psalm.initials.map((each) => each.text)).toEqual(["E"]);
    const texts = psalm.lyrics.map((each) => each.text);
    expect(texts).not.toContain("1.");
    expect(texts.slice(0, 2)).toEqual(["c", "ce"]);
    // The other verses keep their numbers.
    expect(texts).toContain("2.");
    // Without an initial, the first verse keeps its number too.
    expect(engine.layout(score, 390).lyrics[0]!.text).toBe("1.");
  });

  it("aFirstSyllableTheInitialTakesWholeLeavesAHyphen", () => {
    const score = GregorianScoreParser.parse("%% (c4) A(f)ve(g) Ma(h)rí(i)a(h) (::)");
    const layout = layoutEngine().layout(score, 390, { openingLabel: "1", opensPiece: true });
    expect(layout.initials.map((each) => each.text)).toEqual(["A"]);
    expect(layout.lyrics.slice(0, 2).map((each) => each.text)).toEqual(["-", "ve"]);
    // Under its notes, and no second hyphen after it.
    const hyphen = layout.lyrics[0]!;
    const note = layout.events.find((event) => event.eventId === score.eventIds[0])!;
    assert.closeTo(hyphen.origin.x + hyphen.width / 2, note.frame.center.x, 0.5);
    expect(layout.lyrics.some((lyric) => lyric.neumeId.startsWith(`lyric-hyphen-${score.neumes[0]!.id}-`))).toBe(false);
  });
});
