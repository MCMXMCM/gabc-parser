// A port of the Kotlin library's own SourceOffsetTest: offsets count characters as Swift's String counts them, so a
// score has the same source ranges, and its lyric marks the same identifiers, in each package.

import { describe, expect, it } from "vitest";
import { GregorianScoreParser, GregorianScoreParserError } from "../src/GregorianNotation.js";

describe("SourceOffsetTest", () => {
  it("aCarriageReturnAndLineFeedAreOneCharacter", () => {
    const score = GregorianScoreParser.parse("name: x; %% (c3)\r\nHó(h) *()Dó(g) (::)");
    expect(score.neumes[0]!.notes[0]!.sourceRange).toStrictEqual({ lowerBound: 9, upperBound: 10 });
    const lyricMarks = score.elements.flatMap((element) => (element.kind === "lyricMark" ? [element] : []));
    expect(lyricMarks.length).toBe(1);
    const mark = lyricMarks[0]!.mark;
    expect(mark.id).toBe("lyric-mark-11-13");
  });

  it("anAccentWrittenAfterItsLetterIsOneCharacterWithIt", () => {
    // "ó" as o and a combining acute.
    const score = GregorianScoreParser.parse("%% (c3) Ho\u{301}(h) *()Do\u{301}(g) (::)");
    expect(score.neumes[0]!.notes[0]!.sourceRange).toStrictEqual({ lowerBound: 9, upperBound: 10 });
    const error = exceptionOrNull(() => GregorianScoreParser.parse("%% (c3) Ho\u{301}(h?) (::)"));
    expect(error).toBeInstanceOf(GregorianScoreParserError);
    expect((error as GregorianScoreParserError).reason).toStrictEqual({
      kind: "unsupportedToken",
      token: "?",
      offset: 10,
    });
  });
});

/** Kotlin's `runCatching { }.exceptionOrNull()`: what the block throws, or null when it throws nothing. */
function exceptionOrNull(block: () => unknown): unknown {
  try {
    block();
    return null;
  } catch (error) {
    return error;
  }
}
