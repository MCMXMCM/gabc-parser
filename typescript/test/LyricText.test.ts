// A port of the Kotlin library's LyricTextTest, itself a port of the Swift package's
// Tests/GABCParserTests/LyricTextTests.swift: how the words of a score are read, the rubrics written among them, the
// words each begins and ends, and the letters a score marks to be centred.

import { describe, expect, it } from "vitest";
import { GregorianScoreParser, type GregorianLyricMark, type GregorianScore } from "../src/GregorianNotation.js";

function marks(score: GregorianScore): GregorianLyricMark[] {
  return score.elements.flatMap((element) => (element.kind === "lyricMark" ? [element.mark] : []));
}

/** The lyrics and marks in the order written, marks in brackets. */
function lyricSequence(score: GregorianScore): string[] {
  return score.elements.flatMap((element) => {
    switch (element.kind) {
      case "neume":
        return element.neume.lyric.length > 0 ? [element.neume.lyric] : [];
      case "lyricMark":
        return [`[${element.mark.text}]`];
      default:
        return [];
    }
  });
}

describe("LyricTextTest", () => {
  it("aRubricAndItsBarEndTheWordBeforeThem", () => {
    // A respond written straight after its sign and bar, with no space.
    const score = GregorianScoreParser.parse("%% (c4) Dó(h)mi(g)ne,(f.) R/.(::)In(f) ma(g)nus(h) (::)");
    expect(lyricSequence(score)).toEqual(["Dó", "mi", "ne,", "[℟.]", "In", "ma", "nus"]);
    expect(score.neumes.find((neume) => neume.lyric === "In")?.startsWord).toBe(true);
    expect(score.lyricText).toBe("Dómine, ℟. In manus");
  });

  it("aClefChangeWithinAWordLeavesTheWordWhole", () => {
    const score = GregorianScoreParser.parse("%% (c4) Dó(f)mi(g)(c3)ne(h) (::)");
    expect(score.neumes.map((neume) => neume.startsWord)).toEqual([true, false, false]);
    expect(score.lyricText).toBe("Dómine");
  });

  it("rubricsWrittenBeforeASungSyllableStandApartFromIt", () => {
    const versicle = GregorianScoreParser.parse("%% (c4) V/. Dó(h)mi(h)ne,(h) ex(h)áu(h)di(g) (::)");
    expect(lyricSequence(versicle)).toEqual(["[℣.]", "Dó", "mi", "ne,", "ex", "áu", "di"]);
    expect(marks(versicle).map((mark) => mark.style)).toEqual(["rubric"]);
    expect(versicle.neumes[0]?.startsWord).toBe(true);
    expect(versicle.lyricText).toBe("℣. Dómine, exáudi");

    const stanza = GregorianScoreParser.parse("%% (c4) sóm(g)ni(h)a,(h.) (::) 2. Pro(f)cul(g) (::)");
    expect(lyricSequence(stanza)).toEqual(["sóm", "ni", "a,", "[2.]", "Pro", "cul"]);
    expect(marks(stanza).map((mark) => mark.style)).toEqual(["rubric"]);
    expect(stanza.neumes.find((neume) => neume.lyric === "Pro")?.startsWord).toBe(true);

    // GABC's tilde is a space that no line break may take.
    const mediant = GregorianScoreParser.parse("%% (c4) læ(h)tí(h)ti(h)a(h) *~ab(h) in(h)i(h)mí(h)cis(h) (::)");
    expect(lyricSequence(mediant)).toEqual(["læ", "tí", "ti", "a", "[*]", "ab", "in", "i", "mí", "cis"]);
    expect(mediant.lyricText).not.toContain("~");
  });

  it("rubricsWrittenAfterASungSyllableStandApartFromIt", () => {
    const flex = GregorianScoreParser.parse("%% (c3) Quó(h)ni(h)am hæc, †(h) et(h) ip(h)se(h) (::)");
    expect(lyricSequence(flex)).toEqual(["Quó", "ni", "am hæc,", "[†]", "et", "ip", "se"]);
    expect(marks(flex).map((mark) => mark.style)).toEqual(["rubric"]);
    expect(flex.neumes.find((neume) => neume.lyric === "et")?.startsWord).toBe(true);
  });

  it("verseNumbersRepeatSignsAndCrossesAreRubrics", () => {
    const score = GregorianScoreParser.parse(
      "%% (c4) 2.() Pro(f) e(g) iij.(::) e(g) ij.(::) **() ✠() 12.() fi(h) (::)",
    );
    expect(marks(score).map((mark) => mark.text)).toEqual(["2.", "iij.", "ij.", "**", "✠", "12."]);
    expect(marks(score).map((mark) => mark.style)).toEqual(Array.from({ length: 6 }, () => "rubric"));
  });

  it("aRubricThatHeadsWhatFollowsStandsAfterTheBarWrittenWithIt", () => {
    const score = GregorianScoreParser.parse(
      "%% (c4) me(g.)um.(f.) R/.(::)In(f) ma(f)nus(f) Dó(gf)mi(g)ne,(g.) *(;) Com(h)mén(f)do(gh) (::)",
    );
    const sequence = score.elements.flatMap((element) => {
      switch (element.kind) {
        case "neume":
          return [element.neume.lyric];
        case "lyricMark":
          return [`[${element.mark.text}]`];
        case "division":
          return ["|"];
        default:
          return [];
      }
    });
    expect(sequence).toEqual([
      "me",
      "um.",
      "|",
      "[℟.]",
      "In",
      "ma",
      "nus",
      "Dó",
      "mi",
      "ne,",
      "[*]",
      "|",
      "Com",
      "mén",
      "do",
      "|",
    ]);
  });

  it("aRubricHeadsTheWordsAfterItOrClosesThoseBeforeIt", () => {
    const cases: [gabc: string, text: string, begins: boolean][] = [
      ["%% (c4) V/. Dó(h)mi(h)ne(h) (::)", "℣.", true],
      ["%% (c4) ne,(f.) R/.(::)In(f) (::)", "℟.", true],
      ["%% (c4) a,(h.) (::) 2.() Pro(f)cul(g) (::)", "2.", true],
      ["%% (c4) ✠() Ma(f)gní(g)fi(h)cat(h) (::)", "✠", true],
      ["%% (c4) am:(hg..) (:) † In(i)tra(i) (::)", "†", true],
      ["%% (c3) Quó(h)ni(h)am hæc, †(h) et(h) (::)", "†", false],
      ["%% (c3) Eu(ei)ge,(i) *() ser(i)ve(i) (::)", "*", false],
      ["%% (c4) e(g) iij.(::)", "iij.", false],
    ];
    for (const [gabc, text, begins] of cases) {
      const mark = marks(GregorianScoreParser.parse(gabc)).find((each) => each.text === text);
      expect(mark?.beginsWhatFollows, gabc).toBe(begins);
    }
  });

  it("aSungSyllableMadeOnlyOfSignsIsLeftWhole", () => {
    const score = GregorianScoreParser.parse("%% (c4) *(h) 2.(g) (::)");
    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["*", "2."]);
    expect(marks(score)).toEqual([]);
  });
});
