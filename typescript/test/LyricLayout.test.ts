// A port of the Kotlin library's LyricLayoutTest.

import { assert, describe, expect, it } from "vitest";
import type { GregorianPlacedLyric } from "../src/GregorianLayout.js";
import { GregorianLyricFont } from "../src/GregorianLyricFont.js";
import { GregorianScoreParser, type GregorianNeume, type GregorianScore } from "../src/GregorianNotation.js";
import { groupBy, zipWithNext } from "./collections.js";
import { ebGaramondMeasurer, layoutEngine } from "./ebGaramondMeasurer.js";

/**
 * The words under the notes, set as the chant books set them: each sung vowel under its syllable's first note; the
 * syllables of a word either joined, or parted by one hyphen set just after the first; and words kept apart by more
 * than anything within a word. A port of the Swift package's Tests/GABCLayoutTests/LyricLayoutTests.swift.
 */
describe("LyricLayoutTest", () => {
  it("wordsAreJoinedOrPartedByOneHyphenAndKeptApart", () => {
    for (const gabc of SCORES) {
      const score = GregorianScoreParser.parse(gabc);
      for (const width of [240, 320, 390, 820]) {
        assertLyricInvariants(score, width, `${gabc} at width ${width}`);
      }
    }
  });

  it("theHyphenIsSetInTheLyricsTypeJustAfterItsSyllable", () => {
    const score = GregorianScoreParser.parse("name: hyphen; %% (c4) Sa(fgfh/hjhih)ul(g) (::)");
    const layout = layoutEngine().layout(score, 760);
    const first = layout.lyrics.find((lyric) => lyric.text === "Sa")!;
    const second = layout.lyrics.find((lyric) => lyric.text === "ul")!;
    const hyphen = layout.lyrics.find((lyric) => lyric.neumeId === `lyric-hyphen-${first.neumeId}-${second.neumeId}`);
    expect(hyphen, "A syllable parted from the next of its word by a melisma takes a hyphen.").toBeDefined();
    expect(hyphen!.text).toBe("-");
    expect(hyphen!.fontSize).toBe(first.fontSize);
    expect(hyphen!.fontName).toBe(first.fontName);
    expect(hyphen!.origin.y).toBe(first.origin.y);
    assert.closeTo(hyphen!.origin.x, first.origin.x + first.width, 0.5);
    expect(layout.lyrics.filter((lyric) => lyric.text === "-").length).toBe(1);
  });

  it("noHyphenJoinsAWordToARubricOrAcrossOne", () => {
    const score = GregorianScoreParser.parse(SCORES[5]!);
    const layout = layoutEngine().layout(score, 760);
    const marks = new Set(score.elements.flatMap((element) => (element.kind === "lyricMark" ? [element.mark.id] : [])));
    for (const hyphen of layout.lyrics.filter((lyric) => lyric.text === "-")) {
      expect(
        [...marks].some((mark) => hyphen.neumeId.includes(mark)),
        `${hyphen.neumeId} joins a rubric`,
      ).toBe(false);
    }
    const ne = score.neumes.find((neume) => neume.lyric === "ne,")!;
    expect(layout.lyrics.some((lyric) => lyric.neumeId.startsWith(`lyric-hyphen-${ne.id}-`))).toBe(false);
  });

  it("theSungVowelStandsOverTheSyllablesFirstNote", () => {
    // Each case: a score, the syllable, and the letters before its vowel and of its vowel.
    const cases: [gabc: string, syllable: string, before: string, vowel: string][] = [
      ["%% (c4) Dó(h)mi(g)ne(h) (::)", "Dó", "D", "ó"],
      ["%% (c4) Ky(fgh)ri(g)e(f) (::)", "Ky", "K", "y"],
      ["%% (c4) sǽ(h)cu(g)la(h) (::)", "sǽ", "s", "ǽ"],
      ["%% (c4) Is(h)ra(g)ël(h) (::)", "ël", "", "ë"],
      // A consonantal i or u is not the syllable's vowel.
      ["%% (c4) e(h)ius(g) (::)", "ius", "i", "u"],
      ["%% (c4) qui(h)a(g) (::)", "qui", "qu", "i"],
      ["%% (c4) san(h)guis(g) (::)", "guis", "gu", "i"],
      // A note that carries the end of one word and the beginning of the next is sung on the vowel of the next.
      ["%% (c4) e(h)t Dó(g)mi(h)nus(h) (::)", "t Dó", "t D", "ó"],
      // Braces name the letters to centre.
      ["%% (c4) Al(f)le(g)lú(h){ia}.(g) (::)", "ia.", "", "ia"],
    ];
    for (const [gabc, syllable, before, vowel] of cases) {
      const score = GregorianScoreParser.parse(gabc);
      const layout = layoutEngine().layout(score, 760);
      const lyric = layout.lyrics.find((each) => each.text === syllable);
      expect(lyric, `${syllable} is not set as one lyric`).toBeDefined();
      const neume = score.neumes.find((each) => each.id === lyric!.neumeId)!;
      const note = layout.events.find((event) => event.eventId === neume.notes[0]!.id)!;
      const vowelCentre = lyric!.origin.x + lyricWidth(before, lyric!.fontSize) + lyricWidth(vowel, lyric!.fontSize) / 2;
      assert.closeTo(vowelCentre, note.frame.center.x, 0.75, `${syllable}: its vowel is not under its first note`);
    }
  });
});

type TextKind =
  | { readonly kind: "syllable"; readonly neume: GregorianNeume }
  | { readonly kind: "mark" }
  | { readonly kind: "hyphen" };

class PlacedText {
  constructor(
    readonly lyric: GregorianPlacedLyric,
    readonly kind: TextKind,
  ) {}

  get left(): number {
    return this.lyric.origin.x;
  }

  get right(): number {
    return this.lyric.origin.x + this.lyric.width;
  }
}

function assertLyricInvariants(score: GregorianScore, width: number, context: string): void {
  const layout = layoutEngine().layout(score, width);
  const neumes = new Map(score.neumes.map((neume) => [neume.id, neume] as const));
  const texts = layout.lyrics.map((lyric) => {
    if (lyric.neumeId.startsWith("lyric-hyphen-")) return new PlacedText(lyric, { kind: "hyphen" });
    const neume = neumes.get(lyric.neumeId);
    if (neume != null) return new PlacedText(lyric, { kind: "syllable", neume });
    return new PlacedText(lyric, { kind: "mark" });
  });
  const lines = [...groupBy(texts, (text) => Math.round(text.lyric.origin.y * 100)).values()].map((line) =>
    [...line].sort((first, second) => first.left - second.left),
  );
  const fontSize = layout.lyrics[0]?.fontSize ?? 21;
  const hyphenAdvance = lyricWidth("-", fontSize);
  const wordSpace = Math.max(hyphenAdvance, 0.5 * 12.5);

  for (const staff of lines) {
    for (const [previous, next] of zipWithNext(staff)) {
      expect(previous.right <= next.left + 0.5, `${previous.lyric.text} runs into ${next.lyric.text}: ${context}`).toBe(true);
    }
    const words = staff.filter((text) => text.kind.kind !== "hyphen");
    for (const [previous, next] of zipWithNext(words)) {
      const gap = next.left - previous.right;
      const hyphens = staff.filter((text) => text.kind.kind === "hyphen" && text.left >= previous.left && text.left < next.left);
      const first = previous.kind.kind === "syllable" ? previous.kind.neume : null;
      const second = next.kind.kind === "syllable" ? next.kind.neume : null;
      if (first != null && second != null && !second.startsWord) {
        expect(second.syllableId, context).not.toBe(first.syllableId);
        expect(hyphens.length <= 1, `${first.lyric}-${second.lyric} takes more than one hyphen: ${context}`).toBe(true);
        const hyphen = hyphens[0];
        if (hyphen != null) {
          assert.closeTo(hyphen.left, previous.right, 0.5, `the hyphen after ${first.lyric} stands away from it: ${context}`);
          expect(gap >= hyphenAdvance - 0.5, `${first.lyric}-${second.lyric} has no room for its hyphen: ${context}`).toBe(true);
        } else {
          expect(Math.abs(gap) <= 0.5, `${first.lyric} ${second.lyric} are parted with no hyphen: ${context}`).toBe(true);
        }
      } else {
        expect(hyphens.length === 0, `a hyphen joins ${previous.lyric.text} to ${next.lyric.text}: ${context}`).toBe(true);
        expect(
          gap >= wordSpace - 0.5,
          `${previous.lyric.text} ${next.lyric.text} are too close for two words: ${context}`,
        ).toBe(true);
      }
    }
  }

  // A word divided at a staff's end carries a hyphen at that end.
  const lineOfNeume = new Map(layout.neumes.map((neume) => [neume.id, neume.lineIndex] as const));
  let previous: GregorianNeume | null = null;
  let previousLyricWasMark = false;
  for (const element of score.elements) {
    if (element.kind === "lyricMark") {
      previousLyricWasMark = true;
      previous = null;
    } else if (element.kind === "neume") {
      const neume = element.neume;
      if (neume.lyric.length === 0) continue;
      const last = previous;
      const before = last != null ? lineOfNeume.get(last.id) : undefined;
      const after = lineOfNeume.get(neume.id);
      if (last != null && !previousLyricWasMark && !neume.startsWord && before != null && after != null && before !== after) {
        expect(
          layout.lyrics.some((lyric) => lyric.neumeId === `lyric-hyphen-${last.id}-${neume.id}`),
          `${last.lyric} ends a staff with no hyphen: ${context}`,
        ).toBe(true);
      }
      previous = neume;
      previousLyricWasMark = false;
    }
  }
  expect(
    score.neumes.some((neume) => neume.lyric.length > 0),
    context,
  ).toBe(true);
}

/** Kotlin's `width(text, size)`, renamed so that the width the invariants are laid out at does not hide it. */
function lyricWidth(text: string, size: number): number {
  return ebGaramondMeasurer.width(text, "regular", size, GregorianLyricFont.defaultName);
}

/**
 * Melismas within words, short syllables under wide neumes and long ones under single notes, rubrics, verse
 * numbers and bars, at widths narrow enough to carry words on to the next staff.
 */
const SCORES = [
  "name: preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
  "name: kyrie; %% (c4) Ky(f)ri(gfgh/ijh)e(hg) *() e(gh)lé(ixhi)i(h)son.(hg..) (::)",
  "name: alleluia; %% (c4) Al(f)le(gh)lú(hjihgh/ghg)ia.(gf) (::)",
  "name: antiphon; %% (c4) Be(f)ne(g)dí(h)ctus(h) Dó(g)mi(h)nus(f) De(g)us(h) Is(h)ra(g)ël,(f.) qui(g)a(h) vi(h)si(g)tá(h)vit(i) (;) et(h) fe(g)cit(h) re(g)dem(f)pti(g)ó(h)nem(g) ple(f)bis(g) su(h)æ.(g.) (::)",
  "name: hymn; %% (c4) Te(f) lu(g)cis(h) an(g)te(f) tér(g)mi(h)num,(h.) (,) Re(h)rum(i) Cre(h)á(g)tor,(f) pós(g)ci(h)mus,(h.) (;) 2.() Pro(f)cul(g) re(h)cé(g)dant(f) sóm(g)ni(h)a,(h.) (::)",
  "name: responsory; %% (c4) In(f) ma(g)nus(h) tu(h)as,(g) Dó(h)mi(g)ne,(f.) R/.(::)In(f) ma(g)nus(h) tu(h)as.(g.) (::)",
  "name: verse; %% (c3) Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,) in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
  "name: wide; %% (c4) San(h)ctís(h)si(h)mæ(h) Tri(h)ni(h)tá(h)tis(h) sǽ(h)cu(h)la(h) sæ(h)cu(h)ló(h)rum.(h.) (::)",
  "name: melisma; %% (c4) Sa(fgfh/hjhih)ul(g) A(f)men.(f.) (::)",
];
