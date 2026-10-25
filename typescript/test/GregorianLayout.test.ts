// A port of the Kotlin library's GregorianLayoutTest, itself a port of the Swift package's
// Tests/GABCLayoutTests/GregorianLayoutTests.swift.

import { assert, describe, expect, it } from "vitest";
import { GregorianRect } from "../src/GregorianGeometry.js";
import { GregorianGlyphCatalog, gregorianGlyphNames, type GregorianGlyphName } from "../src/GregorianGlyphCatalog.js";
import {
  GregorianEngravingLayoutEngine,
  GregorianLayoutMetrics,
  catalogGlyph,
  catalogName,
  sameGlyphKind,
  type GregorianLayout,
  type GregorianPlacedGlyph,
} from "../src/GregorianLayout.js";
import { GregorianScoreParser, type GregorianScore } from "../src/GregorianNotation.js";
import type { GregorianTimeline, GregorianTimelineEvent } from "../src/GregorianTimeline.js";
import { groupBy, zip, zipWithNext } from "./collections.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

describe("GregorianLayoutTest", () => {
  it("testNotationAndLyricsScaleTogetherAcrossReaderRange", () => {
    const score = parsedFixture({ noteCount: 1 });
    const engine = layoutEngine();
    const previousMinimum = engine.layout(score, 390, {
      metrics: { notationScale: 0.8 },
    });
    const newMinimum = engine.layout(score, 390, {
      metrics: {
        notationScale: GregorianLayoutMetrics.minimumNotationScale,
      },
    });
    const clampedBelowMinimum = engine.layout(score, 390, {
      metrics: { notationScale: 0 },
    });
    const defaultSize = engine.layout(score, 390);
    const maximumSize = engine.layout(score, 390, {
      metrics: {
        notationScale: GregorianLayoutMetrics.maximumNotationScale,
      },
    });
    const previousClef = unwrap(previousMinimum.glyphs[0]);
    const minimumClef = unwrap(newMinimum.glyphs[0]);
    const clampedClef = unwrap(clampedBelowMinimum.glyphs[0]);
    const previousLyric = unwrap(previousMinimum.lyrics[0]);
    const minimumLyric = unwrap(newMinimum.lyrics[0]);
    const clampedLyric = unwrap(clampedBelowMinimum.lyrics[0]);
    const defaultClef = unwrap(defaultSize.glyphs[0]);
    const maximumClef = unwrap(maximumSize.glyphs[0]);
    const defaultLyric = unwrap(defaultSize.lyrics[0]);
    const maximumLyric = unwrap(maximumSize.lyrics[0]);

    assert.closeTo(minimumClef.frame.width, previousClef.frame.width * 0.75, 0.001);
    assert.closeTo(clampedClef.frame.width, minimumClef.frame.width, 0.001);
    assert.closeTo(minimumLyric.fontSize, previousLyric.fontSize * 0.75, 0.001);
    assert.closeTo(clampedLyric.fontSize, minimumLyric.fontSize, 0.001);
    assert.closeTo(maximumClef.frame.width, defaultClef.frame.width * GregorianLayoutMetrics.maximumNotationScale, 0.001);
    assert.closeTo(maximumLyric.fontSize, defaultLyric.fontSize * GregorianLayoutMetrics.maximumNotationScale, 0.001);
  });

  it("testGeneratedCatalogContainsCanonicalExsurgeGeometry", () => {
    expect(gregorianGlyphNames.length).toBe(49);
    const punctum = GregorianGlyphCatalog.definition("PunctumQuadratum");
    assert.closeTo(punctum.bounds.width, 100, 0.001);
    assert.closeTo(punctum.bounds.height, 123.438, 0.001);
    assert.closeTo(punctum.origin.x, 50, 0.001);
    expect(punctum.paths.flat().length === 0).toBe(false);

    for (const name of gregorianGlyphNames.filter((each) => each !== "None")) {
      expect(
        GregorianGlyphCatalog.definition(name).paths.flat().length === 0,
        `${name} should have generated vector commands`,
      ).toBe(false);
    }
  });

  it("testNamedFormsSelectCanonicalCompositeGlyphs", () => {
    const events: GregorianTimelineEvent[] = [
      { id: "p0", syllableId: "s0", relativePitch: 0 },
      { id: "p1", syllableId: "s0", relativePitch: 1 },
      { id: "r0", syllableId: "s1", relativePitch: 2 },
      { id: "r1", syllableId: "s1", relativePitch: 1 },
      { id: "r2", syllableId: "s1", relativePitch: 2 },
    ];
    const score = GregorianScoreParser.parse("name: composite; %% (c4) Po(fg) rec(hgh) (::)", { timeline: { events } });
    const layout = layoutEngine().layout(score, 390);
    const names = layout.glyphs.map((glyph) => catalogName(glyph.kind));

    // A pes of a second takes Exsurge's short halves.
    expect(names.includes("PodatusLowerShort")).toBe(true);
    expect(names.includes("PodatusUpperShort")).toBe(true);
    expect(names.includes("Porrectus1")).toBe(true);
  });

  it("testResponsiveLayoutProducesCustodesAndStableEventFrames", () => {
    const score = parsedFixture({ noteCount: 12 });
    const engine = layoutEngine();
    const narrow = engine.layout(score, 280);
    const wide = engine.layout(score, 760);

    expect(narrow.staffs.length).toBeGreaterThan(wide.staffs.length);
    expect(narrow.staffs.length).toBeLessThanOrEqual(3);
    expect(narrow.events.map((event) => event.eventId)).toEqual(score.eventIds);
    expect(new Set(narrow.events.map((event) => event.eventId)).size).toBe(score.eventIds.length);
    expect(narrow.glyphs.filter((glyph) => glyph.kind.kind === "custos").length).toBe(Math.max(0, narrow.staffs.length - 1));
    const countsByLine = [...groupBy(narrow.neumes, (neume) => neume.lineIndex).entries()]
      .sort(([first], [second]) => first - second)
      .map(([, neumes]) => neumes.length);
    const countsBeforeTheLast = countsByLine.slice(0, -1);
    expect(countsBeforeTheLast.length > 0 ? Math.min(...countsBeforeTheLast) : 0).toBeGreaterThanOrEqual(3);
    expect(narrow.glyphs.filter((glyph) => glyph.kind.kind === "cClef").length).toBe(narrow.staffs.length);
  });

  it("testMobileStaffsUseFullWidthAndCompactVerticalRhythm", () => {
    const noteCount = 40;
    const timeline: GregorianTimeline = {
      events: Array.from({ length: noteCount }, (_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: 0,
      })),
    };
    const body = Array.from({ length: noteCount }, () => "la(f)").join(" ");
    const score = GregorianScoreParser.parse(`name: compact-mobile; %% (c4) ${body} (::)`, { timeline });
    const layout = layoutEngine().layout(score, 390);

    expect(layout.staffs.length).toBeGreaterThan(1);
    expect(layout.initials).toEqual([]);
    for (const staff of layout.staffs) {
      assert.closeTo(staff.frame.left, 8, 0.01);
      assert.closeTo(staff.frame.right, 382, 0.01);
    }

    for (const [first, second] of zipWithNext(layout.staffs)) {
      const systemAdvance = second.frame.top - first.frame.top;
      expect(systemAdvance).toBeGreaterThan(79);
      expect(systemAdvance).toBeLessThan(82);
    }
  });

  it("testOpeningInitialWithConclusionSitsLeftOfTheFirstClefAndLeavesLaterStaffsFullWidth", () => {
    const syllables = ["Cla", "ma", "vé", "runt", "iu", "sti"];
    const timeline: GregorianTimeline = {
      events: syllables.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: index % 4,
      })),
    };
    const score = GregorianScoreParser.parse("name: initial; %% (c4) Cla(f)ma(g)vé(h)runt(i) (z) iu(j)sti(i) (::)", {
      timeline,
    });
    const layout = layoutEngine().layout(score, 390, { openingLabel: "8g" });
    const initial = unwrap(layout.initials[0]);
    const firstLyric = unwrap(layout.lyrics[0]);

    expect(layout.initials.length).toBe(1);
    expect(initial.text).toBe("C");
    expect(initial.neumeId).toBe(score.neumes[0]!.id);
    expect(firstLyric.text).toBe("la");
    expect(score.neumes[0]!.lyric).toBe("Cla");
    expect(layout.staffs[0]!.frame.left).toBeGreaterThan(8);
    expect(initial.origin.x + initial.width).toBeLessThanOrEqual(layout.staffs[0]!.frame.left);
    assert.closeTo(layout.staffs[1]!.frame.left, 8, 0.01);
    assert.closeTo(layout.staffs[1]!.frame.right, 382, 0.01);
  });

  it("testOpeningInitialPreservesLatinLigatureAsOneCharacter", () => {
    const timeline: GregorianTimeline = {
      events: [
        { id: "event-0", syllableId: "syllable-0", relativePitch: 0 },
        { id: "event-1", syllableId: "syllable-1", relativePitch: 1 },
      ],
    };
    const score = GregorianScoreParser.parse("name: ligature; %% (c4) 1. Æ(f)tér(g) (::)", { timeline });
    const layout = layoutEngine().layout(score, 390, { openingLabel: "1f" });

    expect(layout.initials.map((initial) => initial.text)).toEqual(["Æ"]);
    expect(layout.initials.map((initial) => initial.annotation)).toEqual(["1F"]);
    expect(layout.lyrics.some((lyric) => lyric.text === "Æ")).toBe(false);
  });

  it("testOpeningWithoutConclusionKeepsCompleteLyricUnderClef", () => {
    const timeline: GregorianTimeline = {
      events: [
        { id: "event-0", syllableId: "syllable-0", relativePitch: 0 },
        { id: "event-1", syllableId: "syllable-1", relativePitch: 1 },
      ],
    };
    const score = GregorianScoreParser.parse("name: no-conclusion; %% (c4) De(f)us(g) (::)", { timeline });
    const layout = layoutEngine().layout(score, 390, { openingLabel: "6" });

    expect(layout.initials).toEqual([]);
    expect(layout.lyrics[0]?.text).toBe("De");
    assert.closeTo(layout.staffs[0]!.frame.left, 8, 0.01);
  });

  it("testOpeningLabelRequiresModeAndConclusion", () => {
    expect(GregorianEngravingLayoutEngine.openingLabel(null)).toBeNull();
    expect(GregorianEngravingLayoutEngine.openingLabel("6")).toBeNull();
    expect(GregorianEngravingLayoutEngine.openingLabel("6-alt")).toBeNull();
    expect(GregorianEngravingLayoutEngine.openingLabel("in-directum")).toBeNull();
    expect(GregorianEngravingLayoutEngine.openingLabel("8g")).toBe("8G");
    expect(GregorianEngravingLayoutEngine.openingLabel("7c2")).toBe("7C2");
    expect(GregorianEngravingLayoutEngine.openingLabel("4-alt-d")).toBe("4-ALT-D");
    expect(GregorianEngravingLayoutEngine.openingLabel("8gstar")).toBe("8G*");
    expect(GregorianEngravingLayoutEngine.openingLabel("4e star")).toBe("4E*");
    // The layout reads the label it is given as a mode: each reads as itself.
    for (const mode of ["8g", "7c2", "4-alt-d", "8gstar"]) {
      const label = GregorianEngravingLayoutEngine.openingLabel(mode);
      expect(GregorianEngravingLayoutEngine.openingLabel(label), mode).toBe(label);
    }
    const score = GregorianScoreParser.parse("%% (c4) Mis(g)sus(h) est(h) (::)");
    const layout = layoutEngine().layout(score, 390, { openingLabel: "8G*" });
    expect(layout.initials[0]?.annotation).toBe("8G*");
  });

  it("testScoreModeAppearsAboveInitialAndOverridesVerseNumber", () => {
    const timeline: GregorianTimeline = {
      events: [
        { id: "event-0", syllableId: "syllable-0", relativePitch: 0 },
        { id: "event-1", syllableId: "syllable-1", relativePitch: 1 },
      ],
    };
    const score = GregorianScoreParser.parse("name: psalm-tone; %% (c3) 1. Prín(f)cipes(g) (::)", { timeline });
    const layout = layoutEngine().layout(score, 390, { openingLabel: "7c2" });
    const initial = unwrap(layout.initials[0]);

    expect(initial.text).toBe("P");
    expect(initial.annotation).toBe("7C2");
    expect(initial.annotationOrigin).not.toBeNull();
  });

  it("testNeumeTargetsRemainUsableAndDoNotOverlap", () => {
    const score = parsedFixture({ noteCount: 9 });
    const layout = layoutEngine().layout(score, 390);

    for (const neume of layout.neumes) {
      // Short words are set as close as the book sets them, nearer than a finger's width; their targets still meet,
      // and a tap between two goes to the nearer.
      expect(neume.hitFrame.width).toBeGreaterThanOrEqual(20);
      expect(neume.hitFrame.height).toBeGreaterThanOrEqual(43.9);
      // Swift's `XCTAssertFalse(neume.inkFrame.isNull)`: where Swift's ink frame stays CGRect.null (nothing was
      // drawn), the Kotlin and TypeScript ports give GregorianRect.zero.
      expect(neume.inkFrame).not.toEqual(GregorianRect.zero);
    }
    for (const line of groupBy(layout.neumes, (neume) => neume.lineIndex).values()) {
      const ordered = [...line].sort((first, second) => first.hitFrame.left - second.hitFrame.left);
      for (const [first, second] of zipWithNext(ordered)) {
        expect(first.hitFrame.right).toBeLessThanOrEqual(second.hitFrame.left + 0.01);
      }
    }
  });

  it("testLyricsAreMeasuredAndDoNotOverlapOnEachLine", () => {
    const score = parsedFixture({ noteCount: 9 });
    const layout = layoutEngine().layout(score, 390);
    for (const staff of layout.staffs) {
      const lyrics = layout.lyrics
        .filter((lyric) => lyric.origin.y >= staff.frame.top && lyric.origin.y < staff.frame.top + 94)
        .sort((first, second) => first.origin.x - second.origin.x);
      for (const [first, second] of zipWithNext(lyrics)) {
        expect(first.origin.x + first.width).toBeLessThanOrEqual(second.origin.x + 0.5);
      }
    }
  });

  it("testAccentedLyricReservesItsBoldAdvanceBeforeFollowingSyllable", () => {
    const syllables = ["con", "fun", "*dán*", "tur"];
    const timeline: GregorianTimeline = {
      events: syllables.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: index,
      })),
    };
    const accentedScore = GregorianScoreParser.parse("name: accented-spacing; %% (c4) con(f)fun(g)*dán*(h)tur(i) (::)", {
      timeline,
    });
    const regularScore = GregorianScoreParser.parse("name: regular-spacing; %% (c4) con(f)fun(g)dán(h)tur(i) (::)", {
      timeline,
    });
    const engine = layoutEngine();
    const accentedLayout = engine.layout(accentedScore, 760);
    const regularLayout = engine.layout(regularScore, 760);
    const accentedLyric = unwrap(accentedLayout.lyrics.find((lyric) => lyric.text === "dán"));
    const regularLyric = unwrap(regularLayout.lyrics.find((lyric) => lyric.text === "dán"));
    const followingLyric = unwrap(accentedLayout.lyrics.find((lyric) => lyric.text === "tur"));

    expect(accentedLyric.style).toBe("accented");
    expect(accentedLyric.width).toBeGreaterThan(regularLyric.width);
    expect(accentedLyric.origin.x + accentedLyric.width).toBeLessThanOrEqual(followingLyric.origin.x + 0.5);
  });

  it("testSyllablesStayGroupedWithinWords", () => {
    const syllables = ["San", "cte", "no", "bis", "Spí", "ri", "tus"];
    const timeline: GregorianTimeline = {
      events: syllables.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: index % 4,
      })),
    };
    const score = GregorianScoreParser.parse("name: word-spacing; %% (c4) San(f)cte(g) no(h)bis(i) Spí(j)ri(i)tus(h) (::)", {
      timeline,
    });
    const layout = layoutEngine().layout(score, 760);
    const lyricsById = uniqueKeysWithValues(layout.lyrics.map((lyric) => [lyric.neumeId, lyric] as const));
    const neumes = score.neumes;

    const gap = ({ after, before }: { after: number; before: number }): number => {
      const first = unwrap(lyricsById.get(neumes[after]!.id));
      const second = unwrap(lyricsById.get(neumes[before]!.id));
      return second.origin.x - (first.origin.x + first.width);
    };

    expect(gap({ after: 0, before: 1 })).toBeLessThanOrEqual(2);
    expect(gap({ after: 1, before: 2 })).toBeGreaterThanOrEqual(4);
    expect(gap({ after: 1, before: 2 })).toBeLessThanOrEqual(7);
    expect(gap({ after: 2, before: 3 })).toBeLessThanOrEqual(2);
    expect(gap({ after: 3, before: 4 })).toBeGreaterThanOrEqual(4);
    expect(gap({ after: 3, before: 4 })).toBeLessThanOrEqual(7);
    expect(gap({ after: 4, before: 5 })).toBeLessThanOrEqual(2);
    expect(gap({ after: 5, before: 6 })).toBeLessThanOrEqual(2);
  });

  it("testAutomaticHyphensClarifyWideSyllableGapsWithinAWord", () => {
    const syllables = ["De", "us", "in"];
    const timeline: GregorianTimeline = {
      events: syllables.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: index,
      })),
    };
    const score = GregorianScoreParser.parse("name: hyphens; %% (c3) De(h)us(h) in(h) (::)", { timeline });
    const layout = layoutEngine().layout(score, 760);
    const lyricTexts = layout.lyrics.map((lyric) => lyric.text);

    expect(lyricTexts.includes("-")).toBe(true);
    expect(layout.lyrics.some((lyric) => lyric.neumeId === "lyric-hyphen-event-0-event-1")).toBe(true);
    const firstSyllable = unwrap(layout.lyrics.find((lyric) => lyric.neumeId === "event-0"));
    const hyphen = unwrap(layout.lyrics.find((lyric) => lyric.neumeId === "lyric-hyphen-event-0-event-1"));
    assert.closeTo(
      hyphen.origin.y,
      firstSyllable.origin.y + ((firstSyllable.fontSize - hyphen.fontSize) * 1.23) / 2,
      0.001,
      "The smaller hyphen should be vertically centered in the lyric line.",
    );
    expect(
      layout.lyrics.some((lyric) => lyric.neumeId === "lyric-hyphen-event-1-event-2"),
      "A GABC word space must not receive a lyric hyphen.",
    ).toBe(false);
  });

  it("testNearbyGABCFragmentsJoinWithoutAutomaticHyphens", () => {
    const fragments = ["Qui", "fe", "ci", "t cæ", "lu", "m e", "t te", "rram."];
    const timeline: GregorianTimeline = {
      events: fragments.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: index === fragments.length - 1 ? -2 : 0,
      })),
    };
    const score = GregorianScoreParser.parse(
      "name: compact-fragments; %% (c3) R/.() Qui(h) fe(h)ci(h)t cæ(h)lu(h)m e(h)t te(h)rram.(f.) (::)",
      { timeline },
    );
    const layout = layoutEngine().layout(score, 760);

    expect(
      layout.lyrics.some((lyric) => lyric.text === "-"),
      "Adjacent musical attachments should read as continuous words, including et rather than e-t.",
    ).toBe(false);
    const fragmentBeforeT = unwrap(layout.lyrics.find((lyric) => lyric.neumeId === "event-5"));
    const fragmentBeginningWithT = unwrap(layout.lyrics.find((lyric) => lyric.neumeId === "event-6"));
    expect(
      fragmentBeginningWithT.origin.x - (fragmentBeforeT.origin.x + fragmentBeforeT.width),
      "The e and t in et should remain visually joined.",
    ).toBeLessThanOrEqual(2);
  });

  it("testReferencePrecesUseConnectorsOnlyWhereMusicalSpacingNeedsThem", () => {
    const fragments = ["De", "us", "in", "ad", "ju", "tó", "ri", "um", "me", "um", "in", "tén", "de."];
    const timeline: GregorianTimeline = {
      events: fragments.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: 0,
      })),
    };
    const score = GregorianScoreParser.parse(
      "name: reference-preces; %% (c3) V/.() De(h)us(h) ✠(,) in(h) ad(h)ju(h)tó(i)ri(h)um(h) me(h)um(h) in(h)tén(g)de.(h.) (::)",
      { timeline },
    );
    const layout = layoutEngine().layout(score, 760);
    const actualHyphenIds = new Set(layout.lyrics.filter((lyric) => lyric.text === "-").map((lyric) => lyric.neumeId));

    expect(
      actualHyphenIds,
      "Match Exsurge's De-us, adjutóri-um, and me-um connectors without hyphenating every syllable.",
    ).toEqual(new Set(["lyric-hyphen-event-0-event-1", "lyric-hyphen-event-6-event-7", "lyric-hyphen-event-8-event-9"]));

    const adjutoriumFragments = ["A", "dju", "tó", "ri", "u", "m nó", "strum", "i", "n nó", "mi", "ne", "Dó", "mi", "ni."];
    const adjutoriumTimeline: GregorianTimeline = {
      events: adjutoriumFragments.map((_, index) => ({
        id: `adjutorium-event-${index}`,
        syllableId: `adjutorium-syllable-${index}`,
        relativePitch: 0,
      })),
    };
    const adjutoriumScore = GregorianScoreParser.parse(
      "name: reference-adjutorium; %% (c3) V/.() A(h)dju(h)tó(h)ri(h)u(h)m nó(h)strum(h) ✠(,) i(h)n nó(h)mi(h)ne(h) Dó(h)mi(h)ni.(f.) (::)",
      { timeline: adjutoriumTimeline },
    );
    const adjutoriumLayout = layoutEngine().layout(adjutoriumScore, 760);

    expect(
      new Set(adjutoriumLayout.lyrics.filter((lyric) => lyric.text === "-").map((lyric) => lyric.neumeId)),
      "Match Exsurge's single adjutóri-um connector.",
    ).toEqual(new Set(["lyric-hyphen-adjutorium-event-3-adjutorium-event-4"]));
  });

  it("testReferenceDoxologyRetainsItsSelectiveConnectors", () => {
    const fragments = [
      "Gló", "ri", "a", "Pa", "tri,", "et", "Fí", "li", "o,", "et",
      "Spi", "rí", "tu", "i", "San", "cto.", "Sic", "ut", "e", "rat",
      "in", "prin", "cí", "pi", "o,", "et", "nunc,",
    ];
    const timeline: GregorianTimeline = {
      events: fragments.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: 0,
      })),
    };
    const score = GregorianScoreParser.parse(
      "name: reference-doxology; %% (c3) Gló(h)ri(h)a(h) Pa(h)tri,(h) et(h) Fí(h)li(h)o,(h) et(h) Spi(h)rí(h)tu(h)i(h) San(g)cto.(h.) (:) Sic(h)ut(h) e(h)rat(h) in(h) prin(h)cí(h)pi(h)o,(h) et(h) nunc,(h) (::)",
      { timeline },
    );
    const layout = layoutEngine().layout(score, 760);
    const actualHyphenIds = new Set(layout.lyrics.filter((lyric) => lyric.text === "-").map((lyric) => lyric.neumeId));
    const expectedReferenceConnectors = new Set([
      "lyric-hyphen-event-1-event-2", // Glóri-a
      "lyric-hyphen-event-6-event-7", // Fí-li-o
      "lyric-hyphen-event-7-event-8",
      "lyric-hyphen-event-12-event-13", // Spíritu-i
      "lyric-hyphen-event-23-event-24", // princípi-o
    ]);

    const missing = [...expectedReferenceConnectors].filter((id) => !actualHyphenIds.has(id));
    expect(
      [...expectedReferenceConnectors].every((id) => actualHyphenIds.has(id)),
      `Missing reference connectors: [${missing.join(", ")}]`,
    ).toBe(true);
    expect(
      actualHyphenIds.has("lyric-hyphen-event-0-event-1"),
      "Glóri should remain joined before the selective ri-a connector.",
    ).toBe(false);
    expect(
      actualHyphenIds.has("lyric-hyphen-event-18-event-19"),
      "The compact e and rat fragments should read as erat, matching the reference engraving.",
    ).toBe(false);
  });

  it("testAutomaticHyphenMarksAWordContinuedOnTheNextStaff", () => {
    const syllables = ["Su", "per", "ca", "li", "fra", "gi", "lis", "ti", "ce", "ex", "pi", "a", "li", "do", "cious"];
    const timeline: GregorianTimeline = {
      events: syllables.map((_, index) => ({
        id: `event-${index}`,
        syllableId: `syllable-${index}`,
        relativePitch: index % 5,
      })),
    };
    const body = zip(syllables, [..."fghijifghijifgh"])
      .map(([syllable, pitch]) => `${syllable}(${pitch})`)
      .join("");
    const score = GregorianScoreParser.parse(`name: line-end-hyphen; %% (c4) ${body} (::)`, { timeline });
    const layout = layoutEngine().layout(score, 240);
    const neumesById = uniqueKeysWithValues(layout.neumes.map((neume) => [neume.id, neume] as const));
    const splitPair = unwrap(
      zipWithNext(score.neumes).find(([previous, next]) => {
        const previousPlacement = neumesById.get(previous.id);
        const nextPlacement = neumesById.get(next.id);
        if (previousPlacement == null || nextPlacement == null) {
          return false;
        } else {
          return previousPlacement.lineIndex !== nextPlacement.lineIndex && !next.startsWord;
        }
      }),
    );

    const precedingLyric = unwrap(layout.lyrics.find((lyric) => lyric.neumeId === splitPair[0].id));
    const hyphen = unwrap(
      layout.lyrics.find((lyric) => lyric.neumeId === `lyric-hyphen-${splitPair[0].id}-${splitPair[1].id}`),
    );
    assert.closeTo(
      hyphen.origin.y,
      precedingLyric.origin.y + ((precedingLyric.fontSize - hyphen.fontSize) * 1.23) / 2,
      0.001,
      "A line-end hyphen should be vertically centered in the lyric line.",
    );
  });

  it("testLedgerLinesAppearForNotesOutsideTheStaff", () => {
    const timeline: GregorianTimeline = {
      events: [
        { id: "low", syllableId: "s1", relativePitch: -7 },
        { id: "high", syllableId: "s2", relativePitch: 7 },
      ],
    };
    const score = GregorianScoreParser.parse("name: ledger; %% (c4) Low(a) high(m) (::)", { timeline });
    const layout = layoutEngine().layout(score, 390);
    expect(layout.strokes.some((stroke) => stroke.kind.kind === "ledger")).toBe(true);
  });

  it("testLedgerLinesFollowEachNotesPitch", () => {
    const engine = layoutEngine();
    const layout = (gabc: string) => engine.layout(GregorianScoreParser.parse(gabc), 390);
    const ledgers = (placed: GregorianLayout) => placed.strokes.filter((stroke) => stroke.kind.kind === "ledger");
    const staffGap = 200 / 16;

    // A virga on the first ledger line above has its line, as a punctum there does, though its stem reaches
    // down into the staff.
    for (const gabc of ["%% (c4) a(l) (::)", "%% (c4) a(lv) (::)"]) {
      const placed = layout(gabc);
      const lines = ledgers(placed);
      expect(lines.length, gabc).toBe(1);
      assert.closeTo(lines[0]!.start.y, placed.staffs[0]!.frame.top - staffGap, 0.001, gabc);
    }

    // The low note of a porrectus, which its swash engraves, has its line below the staff, under that
    // note's head.
    const placed = layout("%% (c4) a(gbg) (::)");
    const lines = ledgers(placed);
    expect(lines.length).toBe(1);
    const line = lines[0]!;
    assert.closeTo(line.start.y, placed.staffs[0]!.frame.bottom + staffGap, 0.001);
    const low = placed.events.find((event) => event.eventId === "note-1")!;
    expect(line.start.x).toBeLessThan(low.frame.center.x);
    expect(line.end.x).toBeGreaterThan(low.frame.center.x);
  });

  it("testFlatCClefSetsItsFlatOnTheBItFlattens", () => {
    const engine = layoutEngine();
    const staffGap = 200 / 16;
    const flatDefinition = GregorianGlyphCatalog.definition("Flat");
    for (let line = 1; line <= 4; line += 1) {
      const layout = engine.layout(GregorianScoreParser.parse(`%% (cb${line}) a(g) (::)`), 390);
      const staff = layout.staffs[0]!.frame;
      const clef = layout.glyphs.find((glyph) => glyph.kind.kind === "cClef")!;
      const flat = layout.glyphs.find((glyph) => glyph.kind.kind === "flat")!;
      const note = layout.glyphs.find((glyph) => glyph.eventId === "note-0")!;

      // On B, a step below the clef's line,
      const flatScale = flat.frame.height / flatDefinition.bounds.height;
      const clefLineY = staff.top + (4 - line) * staffGap;
      assert.closeTo(flat.frame.top + flatDefinition.origin.y * flatScale, clefLineY + staffGap / 2, 0.001, `cb${line}`);
      // after the clef and before the first note.
      expect(flat.frame.left > clef.frame.right, `cb${line}`).toBe(true);
      expect(flat.frame.right < note.frame.left, `cb${line}`).toBe(true);
    }
  });

  it("testCustosThatEndsAStaffStaysWithinItsSystem", () => {
    // Each staff's custos shows the next staff's first note, here high above the staff, and its stem rises
    // higher still.
    const score = GregorianScoreParser.parse("%% (c4) A(g) (z) B(m) (z) C(g) (z) D(l) (::)");
    const layout = layoutEngine().layout(score, 390);
    const custodes = custosGlyphs(layout);
    expect(custodes.length).toBe(3);
    for (const custos of custodes) {
      expect(
        layout.staffs.some((staff) => staff.systemFrame.contains(custos.frame)),
        `${JSON.stringify(custos.frame)} outside ${JSON.stringify(layout.staffs.map((staff) => staff.systemFrame))}`,
      ).toBe(true);
    }
  });

  it("testWrittenCustosIsDrawnAsACustosAndOffersNoPlaceToBeginSinging", () => {
    const score = parsedCustosFixture({
      body: "tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
      custosIndex: 2,
      eventCount: 5,
    });
    const layout = layoutEngine().layout(score, 390);
    const custosEventId = score.eventIds[2]!;

    // The event keeps its place in the engraving,
    expect(layout.events.map((event) => event.eventId)).toEqual(score.eventIds);
    // but is drawn as a custos, and not as a note the guide may light.
    const custodes = custosGlyphs(layout);
    expect(custodes.length).toBe(1);
    const custos = unwrap(custodes[0]);
    expect(custos.eventId).toBeNull();
    expect(layout.glyphs.some((glyph) => glyph.eventId === custosEventId)).toBe(false);
    expect(layout.strokes.some((stroke) => stroke.eventId === custosEventId)).toBe(false);
    const placement = unwrap(layout.events.find((event) => event.eventId === custosEventId));
    expect(placement.frame.contains(custos.frame)).toBe(true);

    // It stands at its own pitch: that of the note before it, here.
    const glyphScale = 1 / 16;
    const note = unwrap(
      layout.glyphs.find(
        (glyph) => glyph.eventId === score.eventIds[1] && sameGlyphKind(glyph.kind, catalogGlyph("PunctumQuadratum")),
      ),
    );
    assert.closeTo(
      custos.frame.top + GregorianGlyphCatalog.definition("CustosShort").origin.y * glyphScale,
      note.frame.top + GregorianGlyphCatalog.definition("PunctumQuadratum").origin.y * glyphScale,
      0.001,
    );
    expect(custos.frame.left).toBeGreaterThan(note.frame.right);

    // The cantor guide cannot begin from it.
    expect(layout.neumes.map((neume) => neume.id)).toEqual([0, 1, 3, 4].map((index) => score.eventIds[index]));
    expect(layout.neumeContaining(custosEventId)).toBeNull();
    expect(layout.neumes.every((neume) => neume.noteCount === 1)).toBe(true);
  });

  it("testStaffEndingWithAWrittenCustosIsNotGivenAnother", () => {
    const engine = layoutEngine();
    const written = parsedCustosFixture({
      body: "tó(h)ris.(f.) (f+::z) (c4) O(h)rémus.(h.) (::)",
      custosIndex: 2,
      eventCount: 5,
    });
    const layout = engine.layout(written, 390);
    expect(layout.staffs.length).toBe(2);
    const custodes = custosGlyphs(layout);
    expect(custodes.length).toBe(1);
    // The one custos is the written one, in its place after the note.
    const placement = unwrap(layout.events.find((event) => event.eventId === written.eventIds[2]));
    expect(placement.lineIndex).toBe(0);
    expect(placement.frame.contains(unwrap(custodes[0]).frame)).toBe(true);

    // Without one, the staff is given its own as before.
    const unwritten = parsedCustosFixture({
      body: "tó(h)ris.(f.) (::z) (c4) O(h)rémus.(h.) (::)",
      custosIndex: null,
      eventCount: 4,
    });
    const plain = engine.layout(unwritten, 390);
    expect(plain.staffs.length).toBe(2);
    expect(custosGlyphs(plain).length).toBe(1);
  });

  it("testWrittenCustosStaysOnTheStaffOfTheNoteItFollows", () => {
    const syllables = Array.from({ length: 14 }, (_, index) => `syl${index}(h)`).join(" ");
    const score = parsedCustosFixture({
      body: `${syllables} end.(f.) (f+::) (c4) O(h)ré(g)mus.(h.) (::)`,
      custosIndex: 15,
      eventCount: 19,
    });
    const engine = layoutEngine();
    for (let width = 240; width <= 640; width += 5) {
      const layout = engine.layout(score, width);
      const lines = uniqueKeysWithValues(layout.events.map((event) => [event.eventId, event.lineIndex] as const));
      expect(lines.get(score.eventIds[15]!), `width ${width}`).toBe(lines.get(score.eventIds[14]!));
      expect(
        layout.events.map((event) => event.eventId),
        `width ${width}`,
      ).toEqual(score.eventIds);
    }
  });

  it("testLastInkOfAStaffStopsShortOfItsCustos", () => {
    // A psalm verse is short syllables, hyphens within its words and
    // dotted cadences: little of such a staff can be closed up to fit
    // it, and a dot reaches beyond the room its syllable takes.
    const score = parsedCustosFixture({
      body: [
        "Mi(f)se(h)ri(h)cór(h)di(h)as(g) Dó(h)mi(f)ni(f.) *(,)",
        "in(h) æ(h)tér(h)num(f) can(h)tá(g)bo.(f.) (::)",
        "In(h) ge(h)ne(h)ra(h)ti(h)ó(h)nem(h) et(h) ge(h)ne(h)ra(h)ti(h)ó(g)nem(h) *(,)",
        "an(h)nun(h)ti(h)á(h)bo(h) ve(h)ri(h)tá(h)tem(h) tu(h)am(h)",
        "in(h) o(f)re(h) me(g)o.(f.) (::)",
      ].join(" "),
      custosIndex: null,
      eventCount: 46,
    });
    const engine = layoutEngine();
    let custosCount = 0;
    for (let width = 240; width <= 800; width += 1) {
      const layout = engine.layout(score, width);
      expect(
        layout.events.map((event) => event.eventId),
        `width ${width}`,
      ).toEqual(score.eventIds);
      const lines = uniqueKeysWithValues(layout.events.map((event) => [event.eventId, event.lineIndex] as const));
      for (const custos of custosGlyphs(layout)) {
        const staff = unwrap(
          layout.staffs.find(
            (each) => each.systemFrame.top <= custos.frame.center.y && custos.frame.center.y <= each.systemFrame.bottom,
          ),
          `width ${width}`,
        );
        const glyphInk = layout.glyphs
          .filter((glyph) => (glyph.eventId != null ? lines.get(glyph.eventId) : undefined) === staff.index)
          .map((glyph) => glyph.frame.right);
        const strokeInk = layout.strokes
          .filter((stroke) => (stroke.eventId != null ? lines.get(stroke.eventId) : undefined) === staff.index)
          .map((stroke) => Math.max(stroke.start.x, stroke.end.x));
        const allInk = [...glyphInk, ...strokeInk];
        const ink = unwrap(allInk.length > 0 ? Math.max(...allInk) : null, `width ${width}`);
        expect(ink, `width ${width}, staff ${staff.index}`).toBeLessThan(custos.frame.left);
        expect(custos.frame.right, `width ${width}, staff ${staff.index}`).toBeLessThanOrEqual(staff.frame.right);
        custosCount += 1;
      }
    }
    expect(custosCount).toBeGreaterThan(0);
  });

  it("testRepeatedNoteIsDrawnAsOftenAsItIsWritten", () => {
    const events = ["bi", "tri", "di", "men"].map((_, index) => ({
      id: `event-${index}`,
      syllableId: `syllable-${index}`,
      relativePitch: 0,
    }));
    const score = GregorianScoreParser.parse("name: repeated; %% (c4) bi(hvv) tri(hsss) di(hss.) men(g) (::)", {
      timeline: { events },
    });
    const layout = layoutEngine().layout(score, 390);
    // Repeated notes stand apart, as Exsurge sets them: a virga's head a quarter staff space after the last one's
    // stem, which stands inside its head; a stropha an eighth of one after the last.
    const staffSpace = 200 * (1 / 16);
    const stemWidth = 18 / 16;
    const gaps = new Map<GregorianGlyphName, number>([
      ["PunctumQuadratum", 0.25 * staffSpace - stemWidth],
      ["Stropha", 0.125 * staffSpace],
    ]);

    expect(layout.events.map((event) => event.eventId)).toEqual(score.eventIds);
    expect(layout.neumes.map((neume) => neume.eventIds.length)).toEqual([1, 1, 1, 1]);
    expect(layout.neumes.map((neume) => neume.noteCount)).toEqual([2, 3, 2, 1]);

    const expected: [name: GregorianGlyphName, count: number][] = [
      ["PunctumQuadratum", 2],
      ["Stropha", 3],
      ["Stropha", 2],
      ["PunctumQuadratum", 1],
    ];
    for (const [index, [name, count]] of expected.entries()) {
      const eventId = score.eventIds[index]!;
      const notes = layout.glyphs
        .filter((glyph) => glyph.eventId === eventId && sameGlyphKind(glyph.kind, catalogGlyph(name)))
        .sort((first, second) => first.frame.left - second.frame.left);
      expect(notes.length, eventId).toBe(count);
      for (const [previous, next] of zipWithNext(notes)) {
        assert.closeTo(next.frame.left - previous.frame.right, gaps.get(name) ?? 0, 0.001);
        assert.closeTo(next.frame.top, previous.frame.top, 0.001);
        expect(next.frame.size).toEqual(previous.frame.size);
      }
      // Each head of a virga has its stem, inside its right edge.
      if (index === 0) {
        const stems = layout.strokes.filter((stroke) => stroke.eventId === eventId && stroke.kind.kind === "connector");
        expect(stems.length).toBe(2);
        for (const [head, stem] of zip(
          notes,
          [...stems].sort((first, second) => first.start.x - second.start.x),
        )) {
          assert.closeTo(stem.start.x, head.frame.right - stemWidth / 2, 0.001);
          expect(stem.end.y).toBeGreaterThan(head.frame.bottom);
        }
      }
      // The event answers to all of them together.
      const placement = unwrap(layout.events.find((event) => event.eventId === eventId));
      expect(notes.every((note) => placement.frame.contains(note.frame))).toBe(true);
      const neume = unwrap(layout.neumeContaining(eventId));
      expect(notes.every((note) => neume.inkFrame.contains(note.frame))).toBe(true);
    }

    // The dot written after a repeated note follows the last of them.
    const dotted = score.eventIds[2]!;
    const mora = unwrap(layout.glyphs.find((glyph) => glyph.eventId === dotted && glyph.kind.kind === "mora"));
    const strophae = layout.glyphs.filter(
      (glyph) => glyph.eventId === dotted && sameGlyphKind(glyph.kind, catalogGlyph("Stropha")),
    );
    const lastStropha = unwrap(
      strophae.length > 0 ? strophae.reduce((most, glyph) => (glyph.frame.right > most.frame.right ? glyph : most)) : null,
    );
    expect(mora.frame.left).toBeGreaterThan(lastStropha.frame.right);

    // A repeated note takes the room of as many notes written out.
    const writtenOut = GregorianScoreParser.parse("name: repeated; %% (c4) bi(hvhv) men(g) (::)", {
      timeline: {
        events: ["bi", "bi", "men"].map((syllable, index) => ({
          id: `event-${index}`,
          syllableId: `syllable-${syllable}`,
          relativePitch: 0,
        })),
      },
    });
    const abbreviated = GregorianScoreParser.parse("name: repeated; %% (c4) bi(hvv) men(g) (::)", {
      timeline: {
        events: ["bi", "men"].map((syllable, index) => ({
          id: `event-${index}`,
          syllableId: `syllable-${syllable}`,
          relativePitch: 0,
        })),
      },
    });
    const engine = layoutEngine();
    const writtenOutVirgae = engine
      .layout(writtenOut, 390)
      .glyphs.filter(
        (glyph) =>
          sameGlyphKind(glyph.kind, catalogGlyph("PunctumQuadratum")) &&
          (glyph.eventId === "event-0" || glyph.eventId === "event-1"),
      )
      .map((glyph) => glyph.frame);
    const abbreviatedVirgae = engine
      .layout(abbreviated, 390)
      .glyphs.filter((glyph) => sameGlyphKind(glyph.kind, catalogGlyph("PunctumQuadratum")) && glyph.eventId === "event-0")
      .map((glyph) => glyph.frame);
    expect(writtenOutVirgae.length).toBe(2);
    expect(abbreviatedVirgae.length).toBe(2);
    for (const [lhs, rhs] of zip(writtenOutVirgae, abbreviatedVirgae)) {
      assert.closeTo(lhs.left, rhs.left, 0.001);
      assert.closeTo(lhs.top, rhs.top, 0.001);
    }
  });
});

function custosGlyphs(layout: GregorianLayout): GregorianPlacedGlyph[] {
  return layout.glyphs.filter((glyph) => glyph.kind.kind === "custos");
}

/**
 * A score in the third-line C clef whose events are one to a syllable,
 * except that the custos has a syllable to itself.
 */
function parsedCustosFixture({
  body,
  eventCount,
}: {
  readonly body: string;
  readonly custosIndex: number | null;
  readonly eventCount: number;
}): GregorianScore {
  const events = Array.from({ length: eventCount }, (_, index) => ({
    id: `event-${index}`,
    syllableId: `syllable-${index}`,
    relativePitch: 0,
  }));
  return GregorianScoreParser.parse(`name: custos; %% (c3) ${body}`, { timeline: { events } });
}

function parsedFixture({ noteCount }: { readonly noteCount: number }): GregorianScore {
  const syllables = Array.from({ length: noteCount }, (_, index) => `s${index}`);
  const body = syllables.map((syllable, index) => `${syllable}(${String.fromCharCode(102 + (index % 7))})`).join(" ");
  const events = syllables.map((_, index) => ({
    id: `event-${index}`,
    syllableId: `syllable-${index}`,
    relativePitch: index % 7,
  }));
  return GregorianScoreParser.parse(`name: layout; %% (c4) ${body} (::)`, { timeline: { events } });
}

// XCTest's assertions that vitest lacks, with the values in their messages as XCTest gives them; its comparisons are
// vitest's own.

/** XCTUnwrap: fails the test when the value is null, and returns it otherwise. */
function unwrap<T>(value: T | null | undefined, message = ""): T {
  const failure = [message, "expected a value, found null"].filter((part) => part.length > 0).join(": ");
  expect(value, failure).toBeDefined();
  expect(value, failure).not.toBeNull();
  return value as T;
}

/** Swift's `Dictionary(uniqueKeysWithValues:)`, which traps on a key given twice. */
function uniqueKeysWithValues<K, V>(pairs: readonly (readonly [K, V])[]): Map<K, V> {
  const result = new Map<K, V>();
  for (const [key, value] of pairs) {
    expect(result.has(key), `Dictionary literal contains duplicate key ${String(key)}`).toBe(false);
    result.set(key, value);
  }
  return result;
}
