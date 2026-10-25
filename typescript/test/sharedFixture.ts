import { readFileSync } from "node:fs";

/**
 * Scores whose events and neumes a content compiler and the native engraver agree on: each with the number of its
 * events, and the number of notes in each of its neumes. Read from the Swift package's fixtures
 * (`Tests/GABCParserTests/Fixtures`), as the Kotlin library's tests read them.
 */
export interface SharedFixture {
  readonly name: string;
  readonly scoreId: string;
  readonly gabc: string;
  readonly eventCount: number;
  readonly neumeSizes: readonly number[];
}

const fixturesFile = new URL("../../Tests/GABCParserTests/Fixtures/gabc-engraving-fixtures.json", import.meta.url);

export const SharedFixture = {
  all(): SharedFixture[] {
    const fixtures: unknown = JSON.parse(readFileSync(fixturesFile, "utf8"));
    if (!Array.isArray(fixtures)) throw new Error("The shared fixtures are not a list");
    return fixtures.map((element: unknown) => {
      const fixture = element as Record<string, unknown>;
      return {
        name: field(fixture, "name", isString),
        scoreId: field(fixture, "scoreID", isString),
        gabc: field(fixture, "gabc", isString),
        eventCount: field(fixture, "eventCount", isInteger),
        neumeSizes: field(fixture, "neumeSizes", isIntegerList),
      };
    });
  },
};

/** A fixture's value for a key, which must be present and of its type, as Kotlin's `getValue` and `int` require. */
function field<T>(fixture: Record<string, unknown>, key: string, isValid: (value: unknown) => value is T): T {
  const value = fixture[key];
  if (!isValid(value)) throw new Error(`A shared fixture's ${key} is missing or malformed`);
  return value;
}

function isString(value: unknown): value is string {
  return typeof value === "string";
}

function isInteger(value: unknown): value is number {
  return Number.isInteger(value);
}

function isIntegerList(value: unknown): value is number[] {
  return Array.isArray(value) && value.every(isInteger);
}
