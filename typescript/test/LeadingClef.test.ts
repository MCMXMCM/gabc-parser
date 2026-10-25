// A port of the Kotlin library's own LeadingClefTest, for a slip the Swift tests don't cover: a clef that opens a staff
// is set at its head, and takes no room in it, as Swift's `continue` past the staff's advance leaves it.

import { assert, describe, expect, it } from "vitest";
import { GregorianEngravingLayoutEngine } from "../src/GregorianLayout.js";
import { GregorianScoreParser } from "../src/GregorianNotation.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

describe("LeadingClefTest", () => {
  it("aClefThatOpensALaterStaffTakesNoRoomInIt", () => {
    const score = GregorianScoreParser.parse("%% (c3) Al(h) (z) (c3) Al(h) (::)");
    const layout = layoutEngine().layout(score, 390);
    const first = minBy(
      layout.events.filter((event) => event.lineIndex === 0),
      (event) => event.frame.left,
    );
    const second = minBy(
      layout.events.filter((event) => event.lineIndex === 1),
      (event) => event.frame.left,
    );
    assert.closeTo(second.frame.left, first.frame.left, 0.001);
  });

  it("anOpeningLabelMayEndInANumeralOfAnyScript", () => {
    expect(GregorianEngravingLayoutEngine.openingLabel("8g²")).toBe("8G²");
  });
});

/** Kotlin's `minBy`: the first element of the least key, and an error where there is none. */
function minBy<T>(items: readonly T[], key: (item: T) => number): T {
  if (items.length === 0) throw new Error("Collection is empty.");
  return items.reduce((least, item) => (key(item) < key(least) ? item : least));
}
