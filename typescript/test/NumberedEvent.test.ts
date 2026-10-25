// A port of the Kotlin library's NumberedEventTest.

import { describe, expect, it } from "vitest";
import { GregorianScore, GregorianScoreParser, GregorianScoreParserError } from "../src/GregorianNotation.js";
import type { GregorianTimeline, GregorianTimelineEvent } from "../src/GregorianTimeline.js";
import { SharedFixture } from "./sharedFixture.js";

/**
 * Parsing without a compiled timeline: the parser names each note and syllable itself, as the content compiler
 * names a timeline's events.
 */
describe("NumberedEventTest", () => {
  it("namesNotesInOrderAndGivesEachNotatedGroupASyllable", () => {
    // A forced break and a clef write no note, and take no syllable.
    const score = GregorianScoreParser.parse("name: clefs; %% (c3) Al(dfe)le(g) (z) (f3) lú(hi)ia.(g.) (::)", {
      scoreId: "clefs",
    });

    expect(score.eventIds).toEqual(Array.from({ length: 7 }, (_, index) => `clefs-note-${index}`));
    expect(score.neumes.map((neume) => neume.syllableId)).toEqual([
      "clefs-syllable-0",
      "clefs-syllable-1",
      "clefs-syllable-2",
      "clefs-syllable-3",
    ]);
    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["Al", "le", "lú", "ia."]);
  });

  it("namesAreUnprefixedWithoutAScoreIdentifier", () => {
    const score = GregorianScoreParser.parse("%% (c4) Ky(fg)ri(h) (::)");

    expect(score.eventIds).toEqual(["note-0", "note-1", "note-2"]);
    expect(score.neumes.map((neume) => neume.id)).toEqual(["note-0", "note-2"]);
    expect(score.neumes.map((neume) => neume.syllableId)).toEqual(["syllable-0", "syllable-1"]);
  });

  it("lyricMarksAreNamedAfterTheirScore", () => {
    // Two scores of one document keep the names of their marks apart, as they keep the names of their notes.
    const gabc = "%% (c4) *() a(g) (::)";
    const marks = (score: GregorianScore) =>
      score.elements.flatMap((element) => (element.kind === "lyricMark" ? [element.mark.id] : []));

    expect(marks(GregorianScoreParser.parse(gabc, { scoreId: "one" }))).toEqual(["one-lyric-mark-5-7"]);
    expect(marks(GregorianScoreParser.parse(gabc, { scoreId: "two" }))).toEqual(["two-lyric-mark-5-7"]);
    expect(marks(GregorianScoreParser.parse(gabc))).toEqual(["lyric-mark-5-7"]);
  });

  it("relativePitchCountsStaffStepsFromH", () => {
    const score = GregorianScoreParser.parse("%% (c4) a(h) b(a) c(m) d(G) (::)");

    expect(score.neumes.flatMap((neume) => neume.notes).map((note) => note.relativePitch)).toEqual([0, -7, 5, -1]);
    expect(score.neumes.flatMap((neume) => neume.notes).map((note) => note.pitch)).toEqual([7, 0, 12, 6]);
  });

  it("melismaGroupWithoutLyricIsASyllableOfItsOwn", () => {
    // As the compiler names it, the melisma written after "Ky" in a group of its own is a syllable of its own,
    // and carries no lyric.
    const score = GregorianScoreParser.parse("%% (c4) Ky(f)(gh)ri(g)e(h) (::)", { scoreId: "k" });

    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["Ky", "", "ri", "e"]);
    expect(score.neumes.map((neume) => neume.syllableId)).toEqual([
      "k-syllable-0",
      "k-syllable-1",
      "k-syllable-2",
      "k-syllable-3",
    ]);
    expect(score.lyricText).toBe("Kyrie");
  });

  it("writtenCustosIsAnEventAndAccidentalIsNot", () => {
    const custos = GregorianScoreParser.parse("%% (c3) tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)");
    expect(custos.eventIds.length).toBe(5);
    expect(custos.neumes.map((neume) => neume.writtenNoteCount)).toEqual([1, 1, 0, 1, 1]);
    expect(custos.neumes[2]!.syllableId).toBe("syllable-2");

    const accidental = GregorianScoreParser.parse("%% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)");
    expect(accidental.eventIds.length).toBe(5);
    expect(accidental.neumes.map((neume) => neume.form)).toEqual(["punctum", "punctum", "podatus", "punctum"]);
  });

  it("sharedFixturesNumberAsTheirCompiledTimelines", () => {
    for (const fixture of SharedFixture.all()) {
      const numbered = GregorianScoreParser.parse(fixture.gabc, { scoreId: fixture.scoreId });
      expect(numbered.eventIds, fixture.name).toEqual(
        Array.from({ length: fixture.eventCount }, (_, index) => `${fixture.scoreId}-note-${index}`),
      );
      expect(
        numbered.neumes.map((neume) => neume.notes.length),
        fixture.name,
      ).toEqual(fixture.neumeSizes);

      // The timeline the compiler gives these scores names their notes and syllables the same: parsed against
      // it, the score is the same.
      const pitches = numbered.neumes.flatMap((neume) => neume.notes).map((note) => note.relativePitch);
      const events: GregorianTimelineEvent[] = [];
      for (const [syllableIndex, neumeSize] of fixture.neumeSizes.entries()) {
        for (let repetition = 0; repetition < neumeSize; repetition += 1) {
          events.push({
            id: `${fixture.scoreId}-note-${events.length}`,
            syllableId: `${fixture.scoreId}-syllable-${syllableIndex}`,
            relativePitch: pitches[events.length]!,
          });
        }
      }
      const compiled = GregorianScoreParser.parse(fixture.gabc, { timeline: { events } });
      expect(compiled, fixture.name).toStrictEqual(numbered);
    }
  });

  it("notationWithoutAHeaderIsReadAsTheBody", () => {
    const body = "(c4) Ky(f)ri(g)e(h) e(f)le(g)i(h)son.(g.) (::)";
    const withHeader = GregorianScoreParser.parse(`name: Kyrie; mode: 6;\n%%${body}`);
    const withoutHeader = GregorianScoreParser.parse(body);

    expect(withoutHeader).toStrictEqual(withHeader);
    expect(withoutHeader.lyricText).toBe("Kyrie eleison.");
  });

  it("emptyNotationIsAnEmptyScore", () => {
    expect(GregorianScoreParser.parse("")).toStrictEqual(new GregorianScore([]));
    expect(GregorianScoreParser.parse("name: empty;\n%%\n")).toStrictEqual(new GregorianScore([]));
  });

  it("malformedNotationStillFails", () => {
    const unterminated = assertThrows(GregorianScoreParserError, () => {
      GregorianScoreParser.parse("%% (c4) Ky(f");
    });
    expect(unterminated.reason, `Unexpected error: ${unterminated}`).toMatchObject({ kind: "unterminatedNotation" });
    const unsupported = assertThrows(GregorianScoreParserError, () => {
      GregorianScoreParser.parse("%% (c4) Ky(f?)");
    });
    expect(unsupported.reason, `Unexpected error: ${unsupported}`).toMatchObject({
      kind: "unsupportedToken",
      token: "?",
    });
    const invalidClef = assertThrows(GregorianScoreParserError, () => {
      GregorianScoreParser.parse("%% (c5) Ky(f)");
    });
    expect(invalidClef.reason, `Unexpected error: ${invalidClef}`).toMatchObject({
      kind: "invalidClef",
      token: "c5",
    });
  });

  it("anyTimelineTypeCanNameTheNotes", () => {
    class Note implements GregorianTimelineEvent {
      constructor(
        readonly id: string,
        readonly syllableId: string,
        readonly relativePitch: number,
      ) {}
    }

    class Timeline implements GregorianTimeline<Note> {
      constructor(readonly events: readonly Note[]) {}
    }

    const score = GregorianScoreParser.parse("%% (c4) Ky(fg)ri(h) (::)", {
      timeline: new Timeline([new Note("a", "ky", -2), new Note("b", "ky", -1), new Note("c", "ri", 0)]),
    });

    expect(score.eventIds).toEqual(["a", "b", "c"]);
    expect(score.neumes.map((neume) => neume.syllableId)).toEqual(["ky", "ri"]);
  });
});

/** JUnit's `assertThrows`: fails the test unless the block throws an error of the given type, and returns it. */
function assertThrows<T>(type: new (...args: never[]) => T, block: () => void): T {
  try {
    block();
  } catch (error) {
    expect(error).toBeInstanceOf(type);
    return error as T;
  }
  return expect.unreachable(`Expected the block to throw ${type.name}`);
}
