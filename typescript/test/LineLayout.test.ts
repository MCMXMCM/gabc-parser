// A port of the Kotlin library's LineLayoutTest.

import { assert, describe, expect, it } from "vitest";
import type { GregorianPlacedStroke } from "../src/GregorianLayout.js";
import { GregorianScoreParser, type GregorianLyricMark, type GregorianScore } from "../src/GregorianNotation.js";
import { zip, zipWithNext } from "./collections.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

/** One staff space at the default notation scale. */
const staffSpace = 12.5;

const widths = Array.from({ length: (820 - 240) / 5 + 1 }, (_, index) => 240 + 5 * index);

/**
 * The spaces between the notes and the staffs they are set on: the spaces the GABC writes between a syllable's neumes;
 * staffs justified at their words and bars, never within a word; and breaks taken where the chant books take them. A
 * port of the Swift package's Tests/GABCLayoutTests/LineLayoutTests.swift.
 */
describe("LineLayoutTest", () => {
  it("theSpacesWrittenBetweenASyllablesNeumesPartThem", () => {
    // `/` writes a quarter staff space, `//` and a space within the group half of one, and `!` none.
    const score = GregorianScoreParser.parse("%% (c4) A(f/g//h i!j)men.(h) (::)");
    const layout = layoutEngine().layout(score, 760);
    const syllable = score.neumes.find((neume) => neume.lyric === "A")!;
    const noteIds = new Set(
      score.neumes
        .filter((neume) => neume.syllableId === syllable.syllableId)
        .flatMap((neume) => neume.notes)
        .map((note) => note.id),
    );
    const heads = layout.glyphs
      .filter((glyph) => glyph.eventId != null && noteIds.has(glyph.eventId))
      .sort((first, second) => first.frame.left - second.frame.left);
    expect(heads.length).toBe(5);
    const gaps = zipWithNext(heads).map(([previous, next]) => next.frame.left - previous.frame.right);
    for (const [gap, expected] of zip(gaps, [0.25, 0.5, 0.5, 0])) {
      assert.closeTo(gap, expected * staffSpace, 0.01);
    }
  });

  it("eachStaffButTheLastIsJustifiedToItsEnd", () => {
    const score = GregorianScoreParser.parse(PSALM);
    for (const width of widths) {
      const layout = layoutEngine().layout(score, width);
      expect(layout.staffs.length, `width ${width}`).toBeGreaterThan(1);
      for (const staff of layout.staffs.slice(0, -1)) {
        const end = staff.frame.right;
        // The words reach the staff's end, or the notes or a bar the room short of the custos that is theirs.
        const words = layout.lyrics
          .filter((lyric) => staff.systemFrame.top <= lyric.origin.y && lyric.origin.y < staff.systemFrame.bottom)
          .map((lyric) => lyric.origin.x + lyric.width - end);
        const notes = layout.neumes
          .filter((neume) => neume.lineIndex === staff.index)
          .map((neume) => neume.inkFrame.right - (end - 10));
        const bars = layout.strokes
          .filter((stroke) => staff.frame.top <= midY(stroke) && midY(stroke) <= staff.frame.bottom)
          .flatMap((stroke) => {
            const kind = stroke.kind;
            if (kind.kind !== "division") return [];
            return [stroke.start.x + (kind.division === "final" ? 4.5 : 6) - (end - 10)];
          });
        const reaches = [...words, ...notes, ...bars];
        const reach = reaches.length > 0 ? Math.max(...reaches) : null;
        expect(reach).not.toBeNull();
        assert.closeTo(reach!, 0, 1, `width ${width}, staff ${staff.index}`);
      }
    }
  });

  it("aBarStandsMidwayBetweenItsNotes", () => {
    // Words of one letter under single notes, so that no word holds a bar back.
    const body = Array.from(
      { length: 24 },
      (_, index) => `${["a", "e", "o"][index % 3]}(${["f", "g", "h", "g"][index % 4]})` + (index % 4 === 3 ? " (,)" : ""),
    ).join(" ");
    const score = GregorianScoreParser.parse(`%% (c4) ${body} (::)`);
    let bars = 0;
    for (const width of widths) {
      const layout = layoutEngine().layout(score, width);
      for (const staff of layout.staffs) {
        const heads = layout.glyphs
          .filter(
            (glyph) =>
              glyph.eventId != null && staff.frame.top - 20 <= glyph.frame.top && glyph.frame.bottom <= staff.frame.bottom + 20,
          )
          .map((glyph) => glyph.frame);
        for (const stroke of layout.strokes) {
          const kind = stroke.kind;
          if (kind.kind !== "division") continue;
          if (kind.division !== "minima") continue;
          if (midY(stroke) < staff.frame.top || midY(stroke) > staff.frame.bottom) continue;
          const rightsBefore = heads.filter((head) => head.right < stroke.start.x).map((head) => head.right);
          if (rightsBefore.length === 0) continue;
          const before = Math.max(...rightsBefore);
          const leftsAfter = heads.filter((head) => head.left > stroke.start.x).map((head) => head.left);
          if (leftsAfter.length === 0) continue;
          const after = Math.min(...leftsAfter);
          assert.closeTo(after - stroke.start.x, stroke.start.x - before, 0.5, `width ${width}, staff ${staff.index}`);
          bars += 1;
        }
      }
    }
    expect(bars).toBeGreaterThan(0);
  });

  it("theCustosStandsFlushWithTheStaffsEnd", () => {
    const score = GregorianScoreParser.parse(PSALM);
    const layout = layoutEngine().layout(score, 390);
    const custodes = layout.glyphs.filter((glyph) => glyph.kind.kind === "custos");
    expect(custodes.length).toBe(layout.staffs.length - 1);
    for (const custos of custodes) {
      assert.closeTo(custos.frame.right, layout.staffs[0]!.frame.right, 0.01);
    }
  });

  it("rubricsStayWithTheWordsTheyHeadOrClose", () => {
    const score = GregorianScoreParser.parse(RESPONSORY);
    const marks: Map<string, GregorianLyricMark> = new Map(
      score.elements
        .flatMap((element) => (element.kind === "lyricMark" ? [element.mark] : []))
        .map((mark) => [mark.id, mark] as const),
    );
    expect([...marks.values()].filter((mark) => mark.beginsWhatFollows).length).toBe(4);
    assertRubricsStayWithTheirWords(score, marks, true);

    // Asterisks written with the word after them, as a psalm's mediant is (`*~ab`): bound to it, and still never
    // beginning a staff.
    const mediants = GregorianScoreParser.parse(
      "%% (c4) " +
        Array.from({ length: 6 }, () => "læ(h)tí(h)ti(h)a(h) *~ab(h) in(h)i(h)mí(h)cis(h) me(g)is.(f.) (::)").join(" "),
    );
    const asterisks = new Map(
      mediants.elements
        .flatMap((element) => (element.kind === "lyricMark" ? [element.mark] : []))
        .map((mark) => [mark.id, mark] as const),
    );
    expect(asterisks.size).toBe(6);
    assertRubricsStayWithTheirWords(mediants, asterisks, false);
  });

  it("anAccidentalStandsJustBeforeItsNoteOnTheSameStaff", () => {
    const body = Array.from({ length: 20 }, (_, index) =>
      index % 3 === 1 ? "Ky(ixhi)" : `ri(${["g", "h", "f"][index % 3]})`,
    ).join(" ");
    const score = GregorianScoreParser.parse(`%% (c4) ${body} (::)`);
    let flats = 0;
    for (const width of widths) {
      const layout = layoutEngine().layout(score, width);
      const heads = layout.glyphs.filter((glyph) => glyph.eventId != null);
      for (const flat of layout.glyphs.filter((glyph) => glyph.kind.kind === "flat")) {
        // The nearest note after the flat, on its staff: about a quarter of a staff space after it.
        const after = heads.filter(
          (head) => head.frame.left > flat.frame.left && Math.abs(head.frame.center.y - flat.frame.center.y) < 2 * staffSpace,
        );
        const next = after.length > 0 ? after.reduce((least, head) => (head.frame.left < least.frame.left ? head : least)) : null;
        expect(next, `a flat ends a staff at width ${width}`).not.toBeNull();
        assert.closeTo(next!.frame.left - flat.frame.right, 0.27 * staffSpace, 0.25, `width ${width}`);
        flats += 1;
      }
    }
    expect(flats).toBeGreaterThan(0);
  });

  it("noStaffBeginsWithABar", () => {
    const score = GregorianScoreParser.parse(PSALM);
    for (const width of widths) {
      const layout = layoutEngine().layout(score, width);
      for (const stroke of layout.strokes) {
        if (stroke.kind.kind !== "division") continue;
        const staff = layout.staffs.find((each) => each.frame.top <= midY(stroke) && midY(stroke) <= each.frame.bottom);
        if (!staff) continue;
        expect(
          layout.events.some((event) => event.lineIndex === staff.index && event.frame.right < stroke.start.x),
          `a bar begins staff ${staff.index} at width ${width}`,
        ).toBe(true);
      }
    }
  });

  it("aLongMelismaIsDividedOnlyWhereFourNotesStandOnEitherSide", () => {
    const score = GregorianScoreParser.parse(
      "%% (c4) Al(f)le(gh)lú(hjih/gh/ghg/hjih/ghgf/ghg/fgf/ghg/hih/gf/ghg/fe)ia.(gf) (::)",
    );
    const syllable = score.neumes.find((neume) => neume.lyric === "lú")!;
    const noteIds = new Set(
      score.neumes
        .filter((neume) => neume.syllableId === syllable.syllableId)
        .flatMap((neume) => neume.notes)
        .map((note) => note.id),
    );
    let divided = 0;
    for (const width of widths) {
      const layout = layoutEngine().layout(score, width);
      const lines = layout.events.filter((event) => noteIds.has(event.eventId)).map((event) => event.lineIndex);
      const counts = new Map<number, number>();
      for (const line of lines) counts.set(line, (counts.get(line) ?? 0) + 1);
      if (counts.size <= 1) continue;
      divided += 1;
      for (const [line, count] of counts) {
        if (line === lines[0] || line === lines[lines.length - 1]) {
          expect(count, `width ${width}, staff ${line}`).toBeGreaterThanOrEqual(4);
        }
      }
    }
    expect(divided).toBeGreaterThan(0);
  });

  it("theLastStaffHoldsMoreThanOneSyllable", () => {
    const score = GregorianScoreParser.parse(PSALM);
    for (const width of widths) {
      const layout = layoutEngine().layout(score, width);
      const last = layout.staffs[layout.staffs.length - 1];
      if (last == null) continue;
      if (layout.staffs.length <= 1) continue;
      const syllables = layout.neumes.filter((neume) => neume.lineIndex === last.index && neume.lyric.length > 0).length;
      expect(syllables, `width ${width}`).toBeGreaterThan(1);
    }
  });
});

function assertRubricsStayWithTheirWords(
  score: GregorianScore,
  marks: ReadonlyMap<string, GregorianLyricMark>,
  writtenWithBars: boolean,
): void {
  for (const width of widths) {
    const layout = layoutEngine().layout(score, width);
    for (const staff of layout.staffs) {
      const words = layout.lyrics
        .filter((lyric) => staff.systemFrame.top <= lyric.origin.y && lyric.origin.y < staff.systemFrame.bottom)
        .sort((first, second) => first.origin.x - second.origin.x);
      const firstMark = words.length > 0 ? marks.get(words[0]!.neumeId) : undefined;
      if (firstMark != null) {
        expect(
          firstMark.beginsWhatFollows && !firstMark.text.startsWith("*"),
          `${firstMark.text} begins a staff at width ${width}`,
        ).toBe(true);
      }
      const lastMark = words.length > 0 ? marks.get(words[words.length - 1]!.neumeId) : undefined;
      if (lastMark != null) {
        expect(lastMark.beginsWhatFollows, `${lastMark.text} ends a staff at width ${width}`).toBe(false);
      }
      // Within a staff, a rubric that heads what follows stands after the bar written with it.
      if (!writtenWithBars) continue;
      for (const lyric of words.slice(1).filter((each) => marks.get(each.neumeId)?.beginsWhatFollows === true)) {
        const bar = layout.strokes.some(
          (stroke) =>
            stroke.kind.kind === "division" &&
            staff.frame.top <= midY(stroke) &&
            midY(stroke) <= staff.frame.bottom &&
            stroke.start.x <= lyric.origin.x + 0.5 &&
            words.every((each) => each.origin.x >= lyric.origin.x || each.origin.x + each.width <= stroke.start.x + 0.5),
        );
        expect(bar, `${lyric.text} stands before its bar at width ${width}`).toBe(true);
      }
    }
  }
}

/** A psalm verse: short words under single notes, dotted cadences and bars, laid out at widths that make several staffs of it. */
const PSALM = [
  "%% (c3) Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
  "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
  "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
  "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
  "in(h) o(f)re(h) me(g)o.(f.) (::)",
].join(" ");

/** A short responsory: each rubric that heads a respond or a verse is written with the bar before it. */
const RESPONSORY = [
  "%% (c4)In(f) ma(f)nus(f) tu(fg)as(g) Dó(gf)mi(g)ne,(g.h.) *(;)",
  "Com(h')mén(f)do(gh) spí(g)ri(fd)tum(fg) me(g.)um.(f.) R/.(::)In(f) ma(f)nus...(f)",
  "V/.(::)Red(h)e(h)mí(ixhi)sti(h) nos(h) Dó(gh)mi(g)ne,(g'_) (,) De(h)us(g') ve(g)ri(gf)tá(g)tis.(g.h.)",
  "R/.(::)Com(h')mén(f)do(gh) spí(g)ri(fd)tum(fg) me(g.)um.(f.) V/.(::)In(f) ma(f)nus...(f) (::)",
].join(" ");

/** The height of a stroke's middle: a quarter bar's is the top line it crosses. */
function midY(stroke: GregorianPlacedStroke): number {
  return (stroke.start.y + stroke.end.y) / 2;
}
