// A port of the Kotlin library's ScoreHighlightTest.

import { describe, expect, it } from "vitest";
import { GregorianRect } from "../src/GregorianGeometry.js";
import { GregorianLayout, type GregorianNeumePlacement } from "../src/GregorianLayout.js";
import { GregorianScoreParser } from "../src/GregorianNotation.js";
import { GregorianScoreHighlight, neumeAt } from "../src/GregorianScoreHighlight.js";
import type { GregorianTimeline } from "../src/GregorianTimeline.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

describe("ScoreHighlightTest", () => {
  it("tapResolvesToTheNeumeWhoseTargetHoldsIt", () => {
    const target = neumePlacement({
      id: "event-1",
      eventIds: ["event-1"],
      lyric: "In",
      inkFrame: GregorianRect.of(24, 20, 12, 18),
      hitFrame: GregorianRect.of(16, 8, 44, 44),
      lineIndex: 0,
    });
    const layout = new GregorianLayout(
      { width: 390, height: 100 }, // size
      [], // staffs
      [], // glyphs
      [], // strokes
      [], // lyrics
      [], // initials
      [], // events
      [target], // neumes
    );

    expect(neumeAt(layout, { x: 30, y: 30 })?.id).toBe(target.id);
    expect(neumeAt(layout, { x: 300, y: 80 })).toBeNull();
  });

  it("highlightIncludesWholeNeumeAndCorrespondingSyllable", () => {
    const firstSyllableNeume = neumePlacement({
      id: "event-1",
      eventIds: ["event-1"],
      lyric: "Ky",
      inkFrame: GregorianRect.of(10, 10, 10, 10),
      hitFrame: GregorianRect.of(5, 5, 20, 20),
      lineIndex: 0,
    });
    const activeNeume = neumePlacement({
      id: "event-2",
      eventIds: ["event-2", "event-3"],
      lyric: "",
      inkFrame: GregorianRect.of(30, 10, 16, 10),
      hitFrame: GregorianRect.of(25, 5, 26, 20),
      lineIndex: 0,
    });
    const nextSyllableNeume = neumePlacement({
      id: "event-4",
      eventIds: ["event-4"],
      lyric: "ri",
      inkFrame: GregorianRect.of(60, 10, 10, 10),
      hitFrame: GregorianRect.of(55, 5, 20, 20),
      lineIndex: 0,
    });
    const layout = new GregorianLayout(
      { width: 100, height: 40 }, // size
      [], // staffs
      [], // glyphs
      [], // strokes
      [], // lyrics
      [], // initials
      [], // events
      [firstSyllableNeume, activeNeume, nextSyllableNeume], // neumes
    );
    const timeline: GregorianTimeline = {
      events: [
        { id: "event-1", syllableId: "kyrie-ky", relativePitch: 0 },
        { id: "event-2", syllableId: "kyrie-ky", relativePitch: 1 },
        { id: "event-3", syllableId: "kyrie-ky", relativePitch: 2 },
        { id: "event-4", syllableId: "kyrie-ri", relativePitch: 1 },
      ],
    };

    const highlight = unwrap(GregorianScoreHighlight.of("event-2", timeline, layout));

    expect(highlight.neume.id).toBe(activeNeume.id);
    expect(highlight.eventIds).toEqual(new Set(["event-2", "event-3"]));
    expect(highlight.syllableNeumeIds).toEqual(new Set(["event-1", "event-2"]));
  });

  it("highlightTakesSyllablesFromTheParsedScore", () => {
    // One group, split into two neumes by `!`, is one syllable.
    const score = GregorianScoreParser.parse("%% (c4) Ky(fg!hi)ri(g) (::)");
    const layout = layoutEngine().layout(score, 390);

    const highlight = unwrap(GregorianScoreHighlight.of("note-3", score, layout));

    expect(highlight.neume.id).toBe("note-2");
    expect(highlight.eventIds).toEqual(new Set(["note-2", "note-3"]));
    expect(highlight.syllableNeumeIds).toEqual(new Set(["note-0", "note-2"]));
    expect(GregorianScoreHighlight.of("missing", score, layout)).toBeNull();
  });
});

/** A neume's placement as Kotlin's constructor makes one by default: drawing a note for each of its events. */
function neumePlacement(fields: Omit<GregorianNeumePlacement, "noteCount">): GregorianNeumePlacement {
  return { ...fields, noteCount: fields.eventIds.length };
}

/** As XCTUnwrap: the test fails where the value is null. */
function unwrap<T>(value: T | null | undefined): T {
  expect(value).toBeDefined();
  expect(value).not.toBeNull();
  return value as T;
}
