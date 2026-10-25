// A port of the Kotlin library's GregorianScoreParserTest, itself a port of the Swift package's
// Tests/GABCParserTests/GregorianScoreParserTests.swift.

import { describe, expect, it } from "vitest";
import type { GregorianGlyphName } from "../src/GregorianGlyphCatalog.js";
import { catalogGlyph, catalogName, sameGlyphKind } from "../src/GregorianLayout.js";
import { GregorianScoreParser, GregorianScoreParserError } from "../src/GregorianNotation.js";
import type { GregorianTimeline, GregorianTimelineEvent } from "../src/GregorianTimeline.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";
import { SharedFixture } from "./sharedFixture.js";

describe("GregorianScoreParserTest", () => {
  it("testSharedCompilerEngravingFixtures", () => {
    for (const fixture of SharedFixture.all()) {
      let noteIndex = 0;
      const events: GregorianTimelineEvent[] = [];
      for (const [syllableIndex, neumeSize] of fixture.neumeSizes.entries()) {
        for (let repetition = 0; repetition < neumeSize; repetition += 1) {
          events.push({
            id: `${fixture.scoreId}-note-${noteIndex}`,
            syllableId: `${fixture.scoreId}-syllable-${syllableIndex}`,
            relativePitch: 0,
          });
          noteIndex += 1;
        }
      }
      const score = GregorianScoreParser.parse(fixture.gabc, { timeline: { events } });
      expect(score.eventIds, fixture.name).toEqual(events.map((event) => event.id));
      expect(
        score.neumes.map((neume) => neume.notes.length),
        fixture.name,
      ).toEqual(fixture.neumeSizes);
    }
  });

  it("testLiquescentInclinatumEndsAnAncus", () => {
    // `F~` is a diminished inclinatum, not a note of its own: `hGF~` is the ancus, a climacus whose last
    // inclinatum is liquescent.
    const score = GregorianScoreParser.parse("%% (c4) a(hGF~) b(hGF) (::)");

    expect(score.neumes.map((neume) => neume.form)).toEqual(["ancus", "climacus"]);
    const ancus = score.neumes[0]!;
    expect(ancus.notes.map((note) => note.shape)).toEqual(["punctum", "inclinatum", "inclinatum"]);
    expect(ancus.notes.at(-1)!.liquescence).toBe("small");

    const layout = layoutEngine().layout(score, 390);
    expect(
      layout.glyphs.some(
        (glyph) =>
          glyph.eventId === ancus.notes.at(-1)!.id &&
          sameGlyphKind(glyph.kind, catalogGlyph("PunctumInclinatumLiquescent")),
      ),
    ).toBe(true);
  });

  it("testOnlyRAndR0MarkAHollowNote", () => {
    // r and r0 are hollow notes; r1 to r5 are signs written above a note.
    const gabc = "name: test; %% (c3) no(hr1)bis(hr) ló(hr0)rum(h.r3) (::)";
    const timeline = makeTimeline({ pitches: [9, 9, 9, 9], syllables: ["no", "bis", "ló", "rum"] });
    const score = GregorianScoreParser.parse(gabc, { timeline });
    expect(score.neumes.flatMap((neume) => neume.notes.map((note) => note.isCavum))).toEqual([
      false,
      true,
      true,
      false,
    ]);
  });

  it("testMapsCompilerEventsAndPreservesNeumeGrouping", () => {
    const gabc = "name: test; %% (c4) Ky(fg_)ri(h.w)e(ixj~) (;) test(k) (::)";
    const timeline = makeTimeline({
      pitches: [5, 6, 7, 9, 10],
      syllables: ["Ky", "Ky", "ri", "e", "test"],
    });

    const score = GregorianScoreParser.parse(gabc, { timeline });
    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
    expect(score.neumes.length).toBe(4);
    expect(score.neumes[0]?.notes.length).toBe(2);
    expect(score.neumes[0]?.notes.at(-1)?.hasEpisema).toBe(true);
    expect(score.neumes[1]!.notes[0]?.shape).toBe("quilisma");
    expect(score.neumes[1]!.notes[0]?.hasMora).toBe(true);
    expect(score.neumes[2]!.notes[0]?.shape).toBe("liquescent");
    const accidental = unwrap(
      score.elements.flatMap((element) => (element.kind === "accidental" ? [element.mark] : []))[0],
    );
    expect(accidental.kind).toBe("flat");
    expect(accidental.pitch).toBe(8);

    const clefs = score.elements.flatMap((element) => (element.kind === "clef" ? [element.clef] : []));
    expect(clefs).toStrictEqual([{ kind: "c", line: 4, flattensB: false }]);

    const divisions = score.elements.flatMap((element) => (element.kind === "division" ? [element.division] : []));
    expect(divisions).toEqual(["minor", "final"]);
  });

  it("testInManusAccidentalIsNotEngravedOrMappedAsAnExtraNote", () => {
    const timeline = makeTimeline({
      pitches: [7, 7, 7, 8, 7],
      syllables: ["Red", "e", "mí", "mí", "sti"],
    });
    const score = GregorianScoreParser.parse("name: In manus; %% (c4) Red(h)e(h)mí(ixhi)sti(h) (::)", { timeline });

    const mi = unwrap(score.neumes.find((neume) => neume.lyric === "mí"));
    expect(mi.notes.map((note) => note.pitch)).toEqual([7, 8]);
    expect(mi.form).toBe("podatus");
    const accidental = unwrap(
      score.elements.flatMap((element) => (element.kind === "accidental" ? [element.mark] : []))[0],
    );
    expect(accidental.kind).toBe("flat");
    expect(accidental.pitch).toBe(8);

    const layout = layoutEngine().layout(score, 390);
    expect(layout.glyphs.some((glyph) => sameGlyphKind(glyph.kind, catalogGlyph("PodatusLowerShort")))).toBe(true);
    expect(layout.glyphs.some((glyph) => sameGlyphKind(glyph.kind, catalogGlyph("PodatusUpperShort")))).toBe(true);
    const porrectus: GregorianGlyphName[] = ["Porrectus1", "Porrectus2", "Porrectus3", "Porrectus4"];
    expect(layout.glyphs.some((glyph) => porrectus.includes(catalogName(glyph.kind)))).toBe(false);
  });

  it("testClefAndDivisionsDoNotConsumeTimelineEvents", () => {
    const timeline = makeTimeline({ pitches: [5, 6, 7], syllables: ["Ky", "ri", "e"] });
    const score = GregorianScoreParser.parse("name: clef; %% Ky(c4f)ri(g)e.(h.) (::)", { timeline });
    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["Ky", "ri", "e."]);
  });

  it("testFlatCClefDoesNotBecomeTimelineNotesAndIsEngraved", () => {
    const timeline = makeTimeline({ pitches: [5, 6], syllables: ["Ky", "Ky"] });
    const score = GregorianScoreParser.parse("name: flat clef; %% (cb4) Ky(fg) (::)", { timeline });

    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
    const clef = unwrap(score.elements.flatMap((element) => (element.kind === "clef" ? [element.clef] : []))[0]);
    expect(clef).toStrictEqual({ kind: "c", line: 4, flattensB: true });

    const layout = layoutEngine().layout(score, 390);
    expect(layout.glyphs.some((glyph) => glyph.kind.kind === "flat")).toBe(true);
  });

  it("testClassifiesNamedNeumeFormsFromPitchContours", () => {
    const timeline = makeTimeline({
      pitches: Array.from({ length: 10 }, (_, index) => index),
      syllables: ["po", "po", "cli", "cli", "scan", "scan", "scan", "tor", "tor", "tor"],
    });
    const score = GregorianScoreParser.parse("name: forms; %% (c4) po(fg) cli(gf) scan(fgh) tor(fhg) (::)", {
      timeline,
    });

    expect(score.neumes.map((neume) => neume.form)).toEqual(["podatus", "clivis", "scandicus", "torculus"]);
  });

  it("testPreservesDetailedNoteModifiers", () => {
    const timeline = makeTimeline({ pitches: [5], syllables: ["Ky"] });
    const score = GregorianScoreParser.parse("name: modifiers; %% (c4) Ky(f.._0#') (::)", { timeline });
    const note = unwrap(score.neumes[0]?.notes[0]);

    expect(note.accidental).toBe("sharp");
    expect(note.moraCount).toBe(2);
    expect(note.episemaPosition).toBe("below");
    expect(note.hasIctus).toBe(true);
  });

  it("testRemovesPsalmTonePointingDelimitersFromLyrics", () => {
    const timeline = makeTimeline({
      pitches: Array.from({ length: 8 }, (_, index) => index),
      syllables: ["ta", "ber", "ná", "_cu_", "_la_", "*Ja*", "cob.", "*"],
    });
    const score = GregorianScoreParser.parse(
      "name: Psalmus 86; %% (c4) ta(j)ber(j)ná(j)_cu_(h)_la_(j) *Ja*(k)cob.(j.) *(h) (::)",
      { timeline },
    );

    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["ta", "ber", "ná", "cu", "la", "Ja", "cob.", "*"]);
    expect(score.neumes.map((neume) => neume.lyricStyle)).toEqual([
      "regular",
      "regular",
      "regular",
      "preparatory",
      "preparatory",
      "accented",
      "regular",
      "regular",
    ]);
  });

  it("testIgnoresGABCLineCommentsInsteadOfRenderingPercentSigns", () => {
    const timeline = makeTimeline({
      pitches: [7, 8],
      syllables: ["Hó", "die"],
    });
    const score = GregorianScoreParser.parse(
      "name: Invitatorium; %% (c3)\r\n%\r\nHó(h)% source note\r\ndie(i) (::)",
      { timeline },
    );

    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["Hó", "die"]);
    expect(score.neumes.some((neume) => neume.lyric.includes("%"))).toBe(false);
    expect(score.neumes.some((neume) => neume.lyric.includes("source note"))).toBe(false);
  });

  it("testPreservesNoteLessLiturgicalAsteriskAsRubric", () => {
    const timeline = makeTimeline({
      pitches: [7, 6, 5],
      syllables: ["fac", "fac", "Dó"],
    });
    const score = GregorianScoreParser.parse("name: Psalmus 6; %% (c4) fac(gh) *()Dó(f) (::)", { timeline });
    const mark = unwrap(
      score.elements.flatMap((element) => (element.kind === "lyricMark" ? [element.mark] : []))[0],
    );

    expect(mark.text).toBe("*");
    expect(mark.style).toBe("rubric");
    expect(mark.startsWord).toBe(true);
    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["fac", "Dó"]);
    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));

    const layout = layoutEngine().layout(score, 390);
    expect(layout.lyrics.map((lyric) => lyric.text)).toEqual(["fac", "*", "Dó"]);
    expect(layout.lyrics.map((lyric) => lyric.style)).toEqual(["regular", "rubric", "regular"]);

    const fac = unwrap(layout.lyrics.find((lyric) => lyric.text === "fac"));
    const asterisk = unwrap(layout.lyrics.find((lyric) => lyric.text === "*"));
    const domine = unwrap(layout.lyrics.find((lyric) => lyric.text === "Dó"));
    expect(asterisk.origin.x).toBeGreaterThan(fac.origin.x + fac.width);
    expect(domine.origin.x).toBeGreaterThan(asterisk.origin.x + asterisk.width);
  });

  it("testProjectsCompleteLatinWithoutLosingWordBoundaries", () => {
    const timeline = makeTimeline({
      pitches: [5, 6, 7, 8, 9, 10, 9],
      syllables: ["Sal", "ve,", "Re", "gí", "na", "Dó", "mi"],
    });
    const score = GregorianScoreParser.parse(
      "name: Salve; %% (c4) Sal(f)ve,(g) Re(h)gí(i)na(j) *()Dó(k)mi(j) (::)",
      { timeline },
    );

    expect(score.lyricText).toBe("Salve, Regína * Dómi");
  });

  it("testUsesVersicleAndResponseGlyphsInsteadOfAsciiSlashes", () => {
    const timeline = makeTimeline({
      pitches: [5, 6],
      syllables: ["Jube", "Amen"],
    });
    const score = GregorianScoreParser.parse("name: fallback marks; %% (c4) V/.()Jube(f) R/.()Amen(g) (::)", {
      timeline,
    });
    const marks = score.elements.flatMap((element) => (element.kind === "lyricMark" ? [element.mark] : []));

    expect(marks.map((mark) => mark.text)).toEqual(["℣.", "℟."]);
    expect(marks.map((mark) => mark.style)).toEqual(["rubric", "rubric"]);

    const layout = layoutEngine().layout(score, 390);
    expect(layout.lyrics.map((lyric) => lyric.text)).toEqual(["℣.", "Jube", "℟.", "Amen"]);
    expect(layout.lyrics.some((lyric) => lyric.text.includes("/"))).toBe(false);
  });

  it("testZeroSpaceBoundarySplitsNamedNeumesWithoutLosingLyricIdentity", () => {
    const timeline = makeTimeline({
      pitches: [5, 6, 7, 8],
      syllables: ["Ky", "Ky", "Ky", "Ky"],
    });
    const score = GregorianScoreParser.parse("name: boundary; %% (c4) Ky(fg!hi) (::)", { timeline });

    expect(score.neumes.length).toBe(2);
    expect(score.neumes.map((neume) => neume.form)).toEqual(["podatus", "podatus"]);
    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["Ky", ""]);
    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
  });

  it("testWrittenCustosKeepsItsEventButIsNotANote", () => {
    // A versicle before its collect: the custos, the double bar, and
    // the clef of the collect.
    const timeline = makeTimeline({
      pitches: [9, 5, 5, 9, 9],
      syllables: ["tó", "ris.", "", "O", "rémus."],
    });
    const score = GregorianScoreParser.parse(
      "name: custos; %% (c3) tó(h)ris.(f.) (f+::) (c4) O(h)rémus.(h.) (::)",
      { timeline },
    );

    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
    expect(score.neumes.map((neume) => neume.notes.map((note) => note.shape))).toEqual([
      ["punctum"],
      ["punctum"],
      ["custos"],
      ["punctum"],
      ["punctum"],
    ]);
    expect(score.neumes.map((neume) => neume.writtenNoteCount)).toEqual([1, 1, 0, 1, 1]);
    const custos = unwrap(score.neumes[2]!.notes[0]);
    expect(custos.isCustos).toBe(true);
    expect(custos.pitch).toBe(5);
    expect(custos.repetitionCount).toBe(1);
    expect(score.neumes[1]!.notes[0]!.isCustos).toBe(false);
  });

  it("testCustosStandsApartFromTheNotesBesideIt", () => {
    const timeline = makeTimeline({
      pitches: [5, 6, 7, 8, 9],
      syllables: ["Ky", "Ky", "Ky", "Ky", "Ky"],
    });
    const score = GregorianScoreParser.parse("name: custos; %% (c4) Ky(fgh+ij) (::)", { timeline });

    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
    expect(score.neumes.map((neume) => neume.notes.length)).toEqual([2, 1, 2]);
    expect(score.neumes.map((neume) => neume.form)).toEqual(["podatus", "punctum", "podatus"]);
    expect(score.neumes.map((neume) => neume.notes[0]!.isCustos)).toEqual([false, true, false]);
    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["Ky", "", ""]);
  });

  it("testPlusAmongOtherSignsIsNotACustos", () => {
    // Only a pitch whose signs end in `+` is a custos, as in Exsurge.
    const timeline = makeTimeline({ pitches: [5], syllables: ["Ky"] });
    const score = GregorianScoreParser.parse("name: custos; %% (c4) Ky(f+.) (::)", { timeline });

    const note = unwrap(score.neumes[0]?.notes[0]);
    expect(note.shape).toBe("punctum");
    expect(note.hasMora).toBe(true);
  });

  it("testZZeroWritesACustosAndBreaksNoLine", () => {
    // A respond's end before the verse's change of clef, as GABC sources write it: the custos, the double bar
    // and the new clef.
    const score = GregorianScoreParser.parse(
      "name: z0; %% (c4) cis.(f_c) (z0::c3) V/.() Pro(d)pter(d) (z) quod(d) (::Z)",
    );
    expect(score.elements.filter((element) => element.kind === "forcedBreak").length).toBe(2);
    expect(
      score.elements.filter((element) => element.kind === "division" && element.division === "final").length,
    ).toBe(2);
    expect(score.lyricText).toBe("cis. ℣. Propter quod");
  });

  it("testRepeatedVirgaAndStrophaAreOneEventWrittenSeveralTimes", () => {
    const timeline = makeTimeline({
      pitches: [7, 7, 7, 7, 7],
      syllables: ["bi", "tri", "di", "tris", "one"],
    });
    const score = GregorianScoreParser.parse(
      "name: repeated; %% (c4) bi(hvv) tri(hvvv) di(hss.) tris(hsss) one(hv) (::)",
      { timeline },
    );

    expect(score.eventIds).toEqual(timeline.events.map((event) => event.id));
    expect(score.neumes.map((neume) => neume.notes.length)).toEqual([1, 1, 1, 1, 1]);
    expect(score.neumes.map((neume) => neume.notes[0]!.repetitionCount)).toEqual([2, 3, 2, 3, 1]);
    expect(score.neumes.map((neume) => neume.writtenNoteCount)).toEqual([2, 3, 2, 3, 1]);
    expect(score.neumes.map((neume) => neume.form)).toEqual([
      "bivirga",
      "trivirga",
      "distropha",
      "tristropha",
      "virga",
    ]);
    expect(score.neumes.map((neume) => neume.notes[0]!.shape)).toEqual([
      "virga",
      "virga",
      "stropha",
      "stropha",
      "virga",
    ]);
    expect(score.neumes[2]!.notes[0]!.moraCount).toBe(1);
  });

  it("testRepeatedNoteIsANeumeOfItsOwnWithinAGroup", () => {
    // From the corpus: a bivirga before descending diamonds, and a
    // distropha between a clivis and a torculus.
    const bivirga = GregorianScoreParser.parse("name: repeated; %% (c4) et(gvvF'ED) (::)", {
      timeline: makeTimeline({ pitches: [6, 5, 4, 3], syllables: ["et", "et", "et", "et"] }),
    });
    expect(bivirga.neumes.map((neume) => neume.notes.length)).toEqual([1, 3]);
    expect(bivirga.neumes.map((neume) => neume.form)).toEqual(["bivirga", "punctaInclinata"]);
    expect(bivirga.neumes.map((neume) => neume.writtenNoteCount)).toEqual([2, 3]);

    const distropha = GregorianScoreParser.parse("name: repeated; %% (c4) ri(igfssded) (::)", {
      timeline: makeTimeline({
        pitches: [8, 6, 5, 3, 4, 3],
        syllables: ["ri", "ri", "ri", "ri", "ri", "ri"],
      }),
    });
    expect(distropha.neumes.map((neume) => neume.notes.length)).toEqual([2, 1, 3]);
    expect(distropha.neumes.map((neume) => neume.form)).toEqual(["clivis", "distropha", "torculus"]);

    // A repeated note that ends a rising figure still stands alone.
    const scandicus = GregorianScoreParser.parse("name: repeated; %% (c4) Ky(fghvv) (::)", {
      timeline: makeTimeline({ pitches: [5, 6, 7], syllables: ["Ky", "Ky", "Ky"] }),
    });
    expect(scandicus.neumes.map((neume) => neume.notes.length)).toEqual([2, 1]);
    expect(scandicus.neumes.map((neume) => neume.form)).toEqual(["podatus", "bivirga"]);
  });

  it("testVirgaAndStrophaSignsOfDifferentKindsDoNotRepeatANote", () => {
    const timeline = makeTimeline({ pitches: [7, 7], syllables: ["a", "b"] });
    const score = GregorianScoreParser.parse("name: repeated; %% (c4) a(hsv) b(hvsv) (::)", { timeline });

    expect(score.neumes.map((neume) => neume.notes[0]!.repetitionCount)).toEqual([1, 1]);
  });

  it("testWordBeginsAfterAClefOrBarThatNoSpaceFollows", () => {
    // As many collects are written: a space before the bar, none after.
    const timeline = makeTimeline({
      pitches: [7, 6, 7, 7, 6, 7, 7],
      syllables: ["O", "ré", "ré", "mus.", "De", "us,", "qui"],
    });
    const score = GregorianScoreParser.parse(
      "name: words; %% (c4)O(h)ré(gh)mus.(h.) (:)De(g)us,(h) (,)qui(h) (::)",
      { timeline },
    );

    expect(score.neumes.map((neume) => neume.lyric)).toEqual(["O", "ré", "mus.", "De", "us,", "qui"]);
    expect(score.neumes.map((neume) => neume.startsWord)).toEqual([true, false, false, true, false, true]);
    expect(score.lyricText).toBe("Orémus. Deus, qui");

    // No hyphen joins the end of one word to the beginning of the next.
    const layout = layoutEngine().layout(score, 390);
    expect(
      layout.lyrics.some(
        (lyric) =>
          lyric.neumeId === `lyric-hyphen-${score.neumes[2]!.id}-${score.neumes[3]!.id}` ||
          lyric.neumeId === `lyric-hyphen-${score.neumes[4]!.id}-${score.neumes[5]!.id}`,
      ),
    ).toBe(false);

    // A versicle sign after the clef, and the word after a clef that
    // follows the sign, each begin a word.
    const versicle = GregorianScoreParser.parse("name: words; %% (c3)V/.() (c4)Tu(h) es(h) (::)", {
      timeline: makeTimeline({ pitches: [7, 7], syllables: ["Tu", "es"] }),
    });
    const marks = versicle.elements.flatMap((element) => (element.kind === "lyricMark" ? [element.mark] : []));
    expect(marks.map((mark) => mark.startsWord)).toEqual([true]);
    expect(versicle.neumes.map((neume) => neume.startsWord)).toEqual([true, true]);
    expect(versicle.lyricText).toBe("℣. Tu es");
  });

  it("testWordContinuesThroughAMelismaAndTheBarsWithinIt", () => {
    // A bar inside a word, with no space on either side.
    const breath = GregorianScoreParser.parse("name: words; %% (c4) al(f)le(g)lú(gh)(,)ia.(g) (::)", {
      timeline: makeTimeline({
        pitches: [5, 6, 6, 7, 6],
        syllables: ["al", "le", "lú", "lú", "ia."],
      }),
    });
    expect(breath.neumes.map((neume) => neume.startsWord)).toEqual([true, false, false, false]);
    expect(breath.lyricText).toBe("allelúia.");

    // From the corpus: a melisma carried over bars and spaces before
    // the word's last syllable. Notes without words belong to the
    // syllable before them, and the word is still unfinished.
    const melisma = GregorianScoreParser.parse(
      "name: words; %% (c4) co(f)ró(gh) (;) (ij) (,)(hg)na(g) (;) stel(h)lá(g)rum(f) (::)",
      {
        timeline: makeTimeline({
          pitches: [5, 6, 7, 8, 9, 7, 6, 6, 7, 6, 5],
          syllables: ["co", "ró", "ró", "x", "x", "y", "y", "na", "stel", "lá", "rum"],
        }),
      },
    );
    expect(melisma.lyricText).toBe("coróna stellárum");
    expect(
      melisma.neumes.filter((neume) => neume.lyric.length > 0).map((neume) => neume.startsWord),
    ).toEqual([true, false, false, true, false, false]);

    // An elided vowel written after an empty group stays with its word.
    const elision = GregorianScoreParser.parse("name: words; %% (c4) Cum(k) Patr(k) ()_e_(i) () et(i) (::)", {
      timeline: makeTimeline({ pitches: [10, 10, 8, 8], syllables: ["Cum", "Patr", "e", "et"] }),
    });
    expect(elision.lyricText).toBe("Cum Patre et");
  });

  it("testTimelineMismatchIsAValidationFailure", () => {
    const timeline = makeTimeline({ pitches: [5], syllables: ["Ky"] });
    const error = assertThrows(() => GregorianScoreParser.parse("name: mismatch; %% Ky(c4fg)", { timeline }));
    expect(error, `Unexpected error: ${error}`).toBeInstanceOf(GregorianScoreParserError);
    expect((error as GregorianScoreParserError).reason, `Unexpected error: ${error}`).toMatchObject({
      kind: "tooManyNotationNotes",
    });
  });

  it("testUnsupportedTokenIsAValidationFailure", () => {
    const timeline = makeTimeline({ pitches: [5], syllables: ["Ky"] });
    const error = assertThrows(() => GregorianScoreParser.parse("name: unsupported; %% Ky(c4f?)", { timeline }));
    expect(error, `Unexpected error: ${error}`).toBeInstanceOf(GregorianScoreParserError);
    expect((error as GregorianScoreParserError).reason, `Unexpected error: ${error}`).toMatchObject({
      kind: "unsupportedToken",
      token: "?",
    });
  });
});

function makeTimeline({
  pitches,
  syllables,
}: {
  readonly pitches: readonly number[];
  readonly syllables: readonly string[];
}): GregorianTimeline {
  if (pitches.length !== syllables.length) throw new Error("A timeline needs a syllable for each pitch.");
  let syllableNumber = 0;
  let previousSyllable: string | null = null;
  return {
    events: pitches.map((pitch, index) => {
      const syllable = syllables[index]!;
      if (syllable !== previousSyllable) {
        syllableNumber += 1;
        previousSyllable = syllable;
      }
      return {
        id: `fixture-note-${index}`,
        syllableId: `fixture-syllable-${syllableNumber}`,
        relativePitch: pitch - 7,
      };
    }),
  };
}

/** XCTUnwrap: fails the test when the value is null or undefined, and returns it otherwise. */
function unwrap<T>(value: T | null | undefined): T {
  expect(value).toBeDefined();
  expect(value).not.toBeNull();
  return value as T;
}

/** JUnit's `assertThrows(Throwable::class.java)`: fails the test unless the block throws, and returns what it threw. */
function assertThrows(block: () => unknown): unknown {
  try {
    block();
  } catch (error) {
    return error;
  }
  return expect.unreachable("Expected the block to throw");
}
