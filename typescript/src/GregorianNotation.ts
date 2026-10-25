// Gregorian notation as the engraver knows it: clefs, accidentals, divisions, lyric marks and the notes of each
// syllable grouped into the named neumes of Exsurge's compositor; and the parser that reads it from GABC. A port of
// the Swift package's GregorianNotation.swift, by way of the Kotlin port.

import type { GregorianTimeline } from "./GregorianTimeline.js";

/** A stretch of a text, from its lower bound up to and not including its upper bound. */
export interface GregorianSourceRange {
  readonly lowerBound: number;
  readonly upperBound: number;
}

export type GregorianClefKind = "c" | "f";

export interface GregorianClef {
  readonly kind: GregorianClefKind;
  readonly line: number;
  readonly flattensB: boolean;
}

export type GregorianAccidental = "flat" | "natural" | "sharp";

export type GregorianDivision = "minima" | "minor" | "major" | "final";

/**
 * A note's shape. A custos (`custos`) is written in the score itself (`f+`): a guide to the pitch that follows,
 * usually before a change of clef. The compiled timeline holds an event for it, as for every pitch letter, but it is
 * drawn as a custos and is never sung.
 */
export type GregorianNoteShape =
  | "punctum"
  | "virga"
  | "quilisma"
  | "liquescent"
  | "inclinatum"
  | "oriscus"
  | "stropha"
  | "custos";

export type GregorianLiquescence = "none" | "small" | "ascending" | "descending";

export type GregorianEpisemaPosition = "above" | "below";

export interface GregorianAccidentalMark {
  readonly kind: GregorianAccidental;
  readonly pitch: number;
  readonly sourceRange: GregorianSourceRange;
}

/**
 * Semantic neume families used by the native compositor. These deliberately mirror Exsurge's named-neume layer
 * rather than treating a neume as an arbitrary row of noteheads.
 */
export type GregorianNeumeForm =
  | "apostropha"
  | "bivirga"
  | "trivirga"
  | "climacus"
  | "clivis"
  | "ancus"
  | "distropha"
  | "oriscus"
  | "pesQuassus"
  | "pesSubpunctis"
  | "podatus"
  | "porrectus"
  | "porrectusFlexus"
  | "punctaInclinata"
  | "punctum"
  | "salicus"
  | "salicusFlexus"
  | "scandicus"
  | "scandicusFlexus"
  | "torculus"
  | "torculusResupinus"
  | "torculusResupinusFlexus"
  | "tristropha"
  | "virga";

export type GregorianLyricStyle = "regular" | "preparatory" | "accented" | "rubric";

/** The fields of a note; the parser gives every one. */
export interface GregorianNoteFields {
  readonly id: string;
  readonly pitch: number;
  readonly relativePitch: number;
  readonly shape: GregorianNoteShape;
  readonly accidental?: GregorianAccidental | null;
  readonly hasMora?: boolean;
  readonly hasEpisema?: boolean;
  readonly moraCount?: number;
  readonly moraPosition?: GregorianEpisemaPosition | null;
  readonly episemaPosition?: GregorianEpisemaPosition | null;
  readonly liquescence?: GregorianLiquescence;
  readonly isCavum?: boolean;
  readonly hasIctus?: boolean;
  readonly ictusPosition?: GregorianEpisemaPosition | null;
  readonly repetitionCount?: number;
  readonly sourceRange: GregorianSourceRange;
}

/**
 * A written note. `moraCount` is at least one when the note `hasMora`, and `repetitionCount` at least one, as the
 * parser writes them.
 */
export class GregorianNote {
  readonly id: string;
  readonly pitch: number;
  readonly relativePitch: number;
  readonly shape: GregorianNoteShape;
  readonly accidental: GregorianAccidental | null;
  readonly hasMora: boolean;
  readonly hasEpisema: boolean;
  readonly moraCount: number;
  /** The side of the note the GABC sets its dot on (`.1` above, `.0` below); null where it leaves the side to the engraver. */
  readonly moraPosition: GregorianEpisemaPosition | null;
  /** The side the GABC sets the horizontal episema on (`_1`, `_0`); null where it leaves the side to the engraver. */
  readonly episemaPosition: GregorianEpisemaPosition | null;
  readonly liquescence: GregorianLiquescence;
  readonly isCavum: boolean;
  readonly hasIctus: boolean;
  /** The side the GABC sets the ictus on (`'1`, `'0`); null where it leaves the side to the engraver. */
  readonly ictusPosition: GregorianEpisemaPosition | null;
  /**
   * How many notes the one pitch letter writes. GABC abbreviates two virgae or three strophae on a pitch as `hvv`
   * and `hsss`; the compiled timeline holds a single event for the group, which is drawn and sung as that many notes.
   */
  readonly repetitionCount: number;
  readonly sourceRange: GregorianSourceRange;

  constructor(fields: GregorianNoteFields) {
    this.id = fields.id;
    this.pitch = fields.pitch;
    this.relativePitch = fields.relativePitch;
    this.shape = fields.shape;
    this.accidental = fields.accidental ?? null;
    this.hasMora = fields.hasMora ?? false;
    this.hasEpisema = fields.hasEpisema ?? false;
    this.moraCount = fields.moraCount ?? (this.hasMora ? 1 : 0);
    this.moraPosition = fields.moraPosition ?? null;
    this.episemaPosition = fields.episemaPosition ?? null;
    this.liquescence = fields.liquescence ?? "none";
    this.isCavum = fields.isCavum ?? false;
    this.hasIctus = fields.hasIctus ?? false;
    this.ictusPosition = fields.ictusPosition ?? null;
    this.repetitionCount = fields.repetitionCount ?? 1;
    this.sourceRange = fields.sourceRange;
  }

  /** True for a custos, which guides the eye and is not sung. */
  get isCustos(): boolean {
    return this.shape === "custos";
  }

  /** The notes this one event draws and sings: as many as it is written, and none for a custos. */
  get writtenNoteCount(): number {
    return this.isCustos ? 0 : this.repetitionCount;
  }

  /** A custos, or a note written more than once, is a neume in itself. */
  get standsAlone(): boolean {
    return this.isCustos || this.repetitionCount > 1;
  }
}

/**
 * The space a GABC group writes before a neume, after the neume before it in the same group: `automatic` where none
 * is written, the engraver's own, between the neumes it divides a run of notes into; `joined` for `!`; `slashes` for
 * `/` and `//`, a small space for each slash; `space` for a space within the group, a neumatic cut.
 */
export type GregorianNeumeSpace =
  | { readonly kind: "automatic" }
  | { readonly kind: "joined" }
  | { readonly kind: "slashes"; readonly count: number }
  | { readonly kind: "space" };

const automaticSpace: GregorianNeumeSpace = { kind: "automatic" };
const joinedSpace: GregorianNeumeSpace = { kind: "joined" };

/** The fields of a neume; the parser gives every one. */
export interface GregorianNeumeFields {
  readonly id: string;
  readonly syllableId: string;
  readonly lyric: string;
  readonly lyricStyle?: GregorianLyricStyle;
  readonly startsWord: boolean;
  readonly notes: readonly GregorianNote[];
  readonly form?: GregorianNeumeForm;
  readonly sourceRange: GregorianSourceRange;
  readonly centeredRange?: GregorianSourceRange | null;
  readonly spaceBefore?: GregorianNeumeSpace;
}

export class GregorianNeume {
  readonly id: string;
  readonly syllableId: string;
  readonly lyric: string;
  readonly lyricStyle: GregorianLyricStyle;
  readonly startsWord: boolean;
  readonly notes: readonly GregorianNote[];
  readonly form: GregorianNeumeForm;
  readonly sourceRange: GregorianSourceRange;
  /**
   * The letters of `lyric`, as character offsets, that a brace pair in the GABC marks to be centred under the first
   * note (`{ia}.`); null when none is marked, and the sung vowel is centred.
   */
  readonly centeredRange: GregorianSourceRange | null;
  /** The space written before the neume, after the one before it in its group: `automatic` for a group's first neume. */
  readonly spaceBefore: GregorianNeumeSpace;

  constructor(fields: GregorianNeumeFields) {
    this.id = fields.id;
    this.syllableId = fields.syllableId;
    this.lyric = fields.lyric;
    this.lyricStyle = fields.lyricStyle ?? "regular";
    this.startsWord = fields.startsWord;
    this.notes = fields.notes;
    this.form = fields.form ?? GregorianNeumeClassifier.classify(fields.notes);
    this.sourceRange = fields.sourceRange;
    this.centeredRange = fields.centeredRange ?? null;
    this.spaceBefore = fields.spaceBefore ?? automaticSpace;
  }

  get eventIds(): string[] {
    return this.notes.map((note) => note.id);
  }

  /** The notes the neume draws and sings: one for each note, or as many as a doubled note is written, and none for a custos. */
  get writtenNoteCount(): number {
    return this.notes.reduce((sum, note) => sum + note.writtenNoteCount, 0);
  }
}

export class GregorianLyricMark {
  readonly text: string;
  readonly style: GregorianLyricStyle;
  readonly startsWord: boolean;
  readonly sourceRange: GregorianSourceRange;
  /** The identifier of the score the mark was parsed in, if one was given. */
  readonly scoreId: string | null;
  /**
   * Whether the mark heads the words after it, as ℣., ℟., a verse number or any sign written before a syllable's words
   * does, rather than closing the words before it, as an asterisk, a flex or a repeat mark written after them or alone
   * does.
   */
  readonly beginsWhatFollows: boolean;

  constructor(fields: {
    readonly text: string;
    readonly style: GregorianLyricStyle;
    readonly startsWord: boolean;
    readonly sourceRange: GregorianSourceRange;
    readonly scoreId?: string | null;
    readonly beginsWhatFollows?: boolean;
  }) {
    this.text = fields.text;
    this.style = fields.style;
    this.startsWord = fields.startsWord;
    this.sourceRange = fields.sourceRange;
    this.scoreId = fields.scoreId ?? null;
    this.beginsWhatFollows = fields.beginsWhatFollows ?? false;
  }

  /** Named by where it is written, after its score, as the score's notes are: `lyric-mark-5-7`, or `kyrie-lyric-mark-5-7`. */
  get id(): string {
    return `${this.scoreId != null ? `${this.scoreId}-` : ""}lyric-mark-${this.sourceRange.lowerBound}-${this.sourceRange.upperBound}`;
  }
}

export type GregorianNotationElement =
  | { readonly kind: "clef"; readonly clef: GregorianClef }
  | { readonly kind: "accidental"; readonly mark: GregorianAccidentalMark }
  | { readonly kind: "neume"; readonly neume: GregorianNeume }
  | { readonly kind: "lyricMark"; readonly mark: GregorianLyricMark }
  | { readonly kind: "division"; readonly division: GregorianDivision }
  | { readonly kind: "forcedBreak" };

export class GregorianScore {
  constructor(readonly elements: readonly GregorianNotationElement[]) {}

  get neumes(): GregorianNeume[] {
    return this.elements.flatMap((element) => (element.kind === "neume" ? [element.neume] : []));
  }

  get eventIds(): string[] {
    return this.neumes.flatMap((neume) => neume.eventIds);
  }

  /** The words the score sings, as they read. */
  get lyricText(): string {
    let result = "";
    let separatesFollowingLyric = false;
    const append = (text: string, startsWord: boolean, isLyricMark: boolean) => {
      if (text.length === 0) return;
      if (result.length > 0 && (startsWord || separatesFollowingLyric) && !isWhitespace(result[result.length - 1]!)) {
        result += " ";
      }
      result += text;
      separatesFollowingLyric = isLyricMark;
    };
    for (const element of this.elements) {
      if (element.kind === "neume") append(element.neume.lyric, element.neume.startsWord, false);
      else if (element.kind === "lyricMark") append(element.mark.text, element.mark.startsWord, true);
    }
    return result.replace(spaceBeforePunctuation, "$1");
  }
}

/** Unicode's spaces, as Swift's `isWhitespace` reads them, as a character class. */
const unicodeSpace = "[\\t\\n\\u000B\\u000C\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]";
const whitespacePattern = new RegExp(`^${unicodeSpace}$`, "u");

function isWhitespace(character: string): boolean {
  return whitespacePattern.test(character);
}

/** Spaces before punctuation, which a score's lyric text closes up. */
const spaceBeforePunctuation = new RegExp(`${unicodeSpace}+([,.;:!?])`, "gu");

/** Why a score could not be parsed, with the offset in its notation, in characters, where the parse stopped. */
export type GregorianScoreParserErrorReason =
  | { readonly kind: "unterminatedNotation"; readonly offset: number }
  | { readonly kind: "invalidClef"; readonly token: string; readonly offset: number }
  | { readonly kind: "unsupportedToken"; readonly token: string; readonly offset: number }
  | { readonly kind: "tooManyNotationNotes"; readonly offset: number }
  | { readonly kind: "timelineMismatch"; readonly expected: number; readonly parsed: number }
  | { readonly kind: "eventSyllableMismatch"; readonly eventId: string };

function errorMessage(reason: GregorianScoreParserErrorReason): string {
  switch (reason.kind) {
    case "unterminatedNotation":
      return `The GABC notation beginning at offset ${reason.offset} is not closed.`;
    case "invalidClef":
      return `Invalid clef ${reason.token} at offset ${reason.offset}.`;
    case "unsupportedToken":
      return `Unsupported GABC token ${reason.token} at offset ${reason.offset}.`;
    case "tooManyNotationNotes":
      return `The notation contains more notes than its compiled timeline near offset ${reason.offset}.`;
    case "timelineMismatch":
      return `The compiled timeline contains ${reason.expected} notes but the notation contains ${reason.parsed}.`;
    case "eventSyllableMismatch":
      return `Timeline event ${reason.eventId} is assigned to a different syllable than its neume.`;
  }
}

export class GregorianScoreParserError extends Error {
  constructor(readonly reason: GregorianScoreParserErrorReason) {
    super(errorMessage(reason));
    this.name = "GregorianScoreParserError";
  }
}

const comment = /%[^\r\n]*/g;
const tag = /<[^>]+>/g;
const preparatory = /_([^_]+)_/g;
const accented = /\*([^*]+)\*/g;
const hasPreparatory = /_([^_]+)_/;
const hasAccented = /\*([^*]+)\*/;
const spaces = new RegExp(`${unicodeSpace}+`, "u");

/** The signs and repeat marks set as rubrics, beside the verse numbers. */
const rubrics = new Set(["*", "**", "†", "‡", "+", "✠", "℣.", "℟.", "V/.", "R/.", "ij.", "iij.", "ij", "iij"]);
const verseNumber = /^[0-9]+\.?$/;

interface ResolvedEvent {
  readonly id: string;
  readonly syllableId: string;
  readonly relativePitch: number;
}

type ParsedToken =
  | { readonly kind: "clef"; readonly clef: GregorianClef }
  | { readonly kind: "accidental"; readonly mark: GregorianAccidentalMark }
  | { readonly kind: "notes"; readonly notes: ParsedNote[]; readonly spaceBefore: GregorianNeumeSpace }
  | { readonly kind: "division"; readonly division: GregorianDivision }
  | { readonly kind: "forcedBreak" };

interface ParsedNote {
  readonly pitch: number;
  readonly shape: GregorianNoteShape;
  readonly accidental: GregorianAccidental | null;
  readonly hasMora: boolean;
  hasEpisema: boolean;
  readonly moraCount: number;
  readonly moraPosition: GregorianEpisemaPosition | null;
  episemaPosition: GregorianEpisemaPosition | null;
  /** The notes its episema covers, counting back from this one: as many as the GABC writes underscores (`kj__`). */
  readonly episemaReach: number;
  readonly liquescence: GregorianLiquescence;
  readonly isCavum: boolean;
  readonly hasIctus: boolean;
  readonly ictusPosition: GregorianEpisemaPosition | null;
  readonly repetitionCount: number;
  readonly sourceRange: GregorianSourceRange;
}

/** A group's lyric, and the letters a brace pair in it marks to be centred, as character offsets into the lyric. */
interface CleanLyric {
  readonly text: string;
  readonly centeredRange: GregorianSourceRange | null;
}

/** A sung syllable's lyric, and the rubrics written before and after it. */
interface SungLyric {
  readonly leading: readonly string[];
  readonly text: string;
  readonly centeredRange: GregorianSourceRange | null;
  readonly trailing: readonly string[];
}

function sungLyricOf(lyric: CleanLyric): SungLyric {
  return { leading: [], text: lyric.text, centeredRange: lyric.centeredRange, trailing: [] };
}

export const GregorianScoreParser = {
  /**
   * Parses a GABC score.
   *
   * Without a timeline, the notes are numbered as events in the order written, `note-0`, `note-1` and so on, and the
   * syllables `syllable-0`, `syllable-1`: one for each notation group that writes a note, whether or not the group
   * carries lyric text. These are the names a content compiler gives the events of a compiled timeline. A score
   * identifier prefixes both (`kyrie-note-0`), which keeps the names of several scores apart.
   *
   * With a timeline, its events name the notes, one for each written note in order. The parse fails if the notation
   * writes more or fewer notes than the timeline holds, or if a group that carries lyric text spans more than one of
   * its syllables.
   *
   * The notation follows the `%%` that ends a GABC file's header; text without one is read as notation alone.
   *
   * @throws GregorianScoreParserError
   */
  parse(gabc: string, options: { readonly scoreId?: string | null; readonly timeline?: GregorianTimeline } = {}): GregorianScore {
    const compiled = options.timeline?.events.map((event) => ({
      id: event.id,
      syllableId: event.syllableId,
      relativePitch: event.relativePitch,
    }));
    return parseScore(gabc, compiled ?? null, compiled ? null : (options.scoreId ?? null));
  },
};

function parseScore(gabc: string, compiledEvents: readonly ResolvedEvent[] | null, scoreId: string | null): GregorianScore {
  const headerEnd = gabc.indexOf("%%");
  const bodyStart = headerEnd < 0 ? 0 : headerEnd + 2;
  // In GABC, a single percent sign begins a comment through the end of its line. The newline stays, so the lyric
  // fragments around it can't be joined.
  const body = gabc.substring(bodyStart).replace(comment, "");
  const offsets = characterOffsets(body);
  const eventPrefix = scoreId != null ? `${scoreId}-` : "";
  // The syllable of each compiled event, by its name; the first event given a name keeps it.
  let compiledSyllables: Map<string, string> | null = null;
  if (compiledEvents) {
    compiledSyllables = new Map();
    for (const event of compiledEvents) {
      if (!compiledSyllables.has(event.id)) compiledSyllables.set(event.id, event.syllableId);
    }
  }
  const elements: GregorianNotationElement[] = [];
  let cursor = 0;
  let eventIndex = 0;
  // Numbered events give each group that writes a note a syllable.
  let syllableIndex = 0;
  // A word begins where a space stands before a group, and a clef or a bar that stands there alone does not use the
  // beginning up: in "mus.(h.) (:)De(g)us" the bar ends one word and "De" begins the next, though no space follows
  // the bar. Notes without words are the melisma of the syllable before them, and leave it a word unfinished.
  let wordStartIsPending = false;

  for (;;) {
    const open = body.indexOf("(", cursor);
    if (open < 0) break;
    // The lyric is read without its markup tags (`<i>`, `<sp>`).
    const lyricSource = body.substring(cursor, open).replace(tag, "");
    const close = body.indexOf(")", open);
    if (close < 0) throw new GregorianScoreParserError({ kind: "unterminatedNotation", offset: offsets(open) });

    const notationStart = open + 1;
    const notation = body.substring(notationStart, close);
    const parsedLyricStyle = lyricStyle(lyricSource);
    const sourceStartsWord = lyricStartsWord(lyricSource, elements.length === 0);

    const parsed = parseNotationGroup(notation, (index) => offsets(notationStart + index));
    const hasNotes = parsed.some((token) => token.kind === "notes");
    // The rubrics written with a sung syllable stand apart from it: a sign or a verse number before it, a flex after it.
    const cleaned = cleanLyric(lyricSource);
    const sung = hasNotes ? sungLyric(cleaned) : sungLyricOf(cleaned);
    const lyric = sung.text;
    const startsWord = sourceStartsWord || sung.leading.length > 0 || (wordStartIsPending && lyric.length > 0);
    const leadingStartsWord = sourceStartsWord || wordStartIsPending;
    if (hasNotes) {
      // A rubric written after the syllable ends its word.
      wordStartIsPending = sung.trailing.length > 0;
    } else if (lyric.length > 0) {
      // A rubric that stands alone ends the word before it, and begins none: `ne,(f.) R/.(::)In(f)` begins a word at "In".
      wordStartIsPending = true;
    } else if (sourceStartsWord && parsed.length > 0) {
      wordStartIsPending = true;
    }
    sung.leading.forEach((mark, index) => {
      elements.push({
        kind: "lyricMark",
        mark: new GregorianLyricMark({
          text: standaloneLyricText(mark),
          style: "rubric",
          startsWord: index === 0 ? leadingStartsWord : true,
          sourceRange: { lowerBound: offsets(cursor) + index, upperBound: offsets(cursor) + index + 1 },
          scoreId,
          beginsWhatFollows: true,
        }),
      });
    });
    // A lyric written with a bar or a clef and no notes is a rubric. One that heads what follows stands after the
    // bar, at the head of its words: `me(g.)um.(f.) R/.(::)In(f)` reads `um. ‖ ℟. In`.
    let standaloneMark: GregorianLyricMark | null = null;
    if (lyric.length > 0 && !hasNotes) {
      const displayLyric = standaloneLyricText(lyric);
      standaloneMark = new GregorianLyricMark({
        text: displayLyric,
        style: standaloneLyricStyle(displayLyric, parsedLyricStyle),
        startsWord,
        sourceRange: { lowerBound: offsets(cursor), upperBound: offsets(open) },
        scoreId,
        beginsWhatFollows: headsWhatFollows(displayLyric),
      });
    }
    if (standaloneMark && !standaloneMark.beginsWhatFollows) {
      elements.push({ kind: "lyricMark", mark: standaloneMark });
    }
    let appliedLyric = false;

    for (const token of parsed) {
      switch (token.kind) {
        case "clef":
          elements.push({ kind: "clef", clef: token.clef });
          break;
        case "accidental":
          elements.push({ kind: "accidental", mark: token.mark });
          break;
        case "division":
          elements.push({ kind: "division", division: token.division });
          break;
        case "forcedBreak":
          elements.push({ kind: "forcedBreak" });
          break;
        case "notes": {
          const mappedNotes: GregorianNote[] = [];
          const mappedEvents: ResolvedEvent[] = [];
          for (const note of token.notes) {
            let event: ResolvedEvent;
            if (compiledEvents) {
              const compiledEvent = compiledEvents[eventIndex];
              if (!compiledEvent) {
                throw new GregorianScoreParserError({ kind: "tooManyNotationNotes", offset: note.sourceRange.lowerBound });
              }
              event = compiledEvent;
            } else {
              event = {
                id: `${eventPrefix}note-${eventIndex}`,
                syllableId: `${eventPrefix}syllable-${syllableIndex}`,
                // Pitch letters run from `a` (0); `h` is 7.
                relativePitch: note.pitch - 7,
              };
            }
            mappedEvents.push(event);
            mappedNotes.push(
              new GregorianNote({
                id: event.id,
                pitch: note.pitch,
                relativePitch: event.relativePitch,
                shape: note.shape,
                accidental: note.accidental,
                hasMora: note.hasMora,
                hasEpisema: note.hasEpisema,
                moraCount: note.moraCount,
                moraPosition: note.moraPosition,
                episemaPosition: note.episemaPosition,
                liquescence: note.liquescence,
                isCavum: note.isCavum,
                hasIctus: note.hasIctus,
                ictusPosition: note.ictusPosition,
                repetitionCount: note.repetitionCount,
                sourceRange: note.sourceRange,
              }),
            );
            eventIndex += 1;
          }

          const first = mappedNotes[0];
          if (!first) continue;
          const event = mappedEvents[0]!;
          const neumeLyric = appliedLyric ? "" : lyric;
          if (
            neumeLyric.length > 0 &&
            compiledSyllables &&
            mappedNotes.some((note) => compiledSyllables!.get(note.id) !== event.syllableId)
          ) {
            throw new GregorianScoreParserError({ kind: "eventSyllableMismatch", eventId: first.id });
          }
          const segments = GregorianNeumeSegmenter.segment(mappedNotes);
          segments.forEach((segment, segmentIndex) => {
            const segmentFirst = segment[0];
            if (!segmentFirst) return;
            // A punctum that begins a pressus abuts the neume it repeats the first note of (`hhg`, `ffg`).
            const previousSegment = segments[segmentIndex - 1];
            const previous = previousSegment?.[previousSegment.length - 1];
            const joinsPressus =
              segment.length > 1 &&
              previousSegment?.length === 1 &&
              previous !== undefined &&
              previous.pitch === segmentFirst.pitch &&
              previous.shape === "punctum" &&
              previous.liquescence === "none" &&
              previous.moraCount === 0;
            elements.push({
              kind: "neume",
              neume: new GregorianNeume({
                id: segmentFirst.id,
                syllableId: event.syllableId,
                lyric: segmentIndex === 0 ? neumeLyric : "",
                lyricStyle: segmentIndex === 0 && neumeLyric.length > 0 ? parsedLyricStyle : "regular",
                startsWord: segmentIndex === 0 && !appliedLyric && startsWord,
                notes: segment,
                sourceRange: {
                  lowerBound: segment[0]!.sourceRange.lowerBound,
                  upperBound: segment[segment.length - 1]!.sourceRange.upperBound,
                },
                centeredRange: segmentIndex === 0 && neumeLyric.length > 0 ? sung.centeredRange : null,
                spaceBefore: segmentIndex === 0 ? token.spaceBefore : joinsPressus ? joinedSpace : automaticSpace,
              }),
            });
          });
          appliedLyric = true;
          break;
        }
      }
    }
    if (standaloneMark && standaloneMark.beginsWhatFollows) {
      elements.push({ kind: "lyricMark", mark: standaloneMark });
    }
    sung.trailing.forEach((mark, index) => {
      const lowerBound = offsets(open) - sung.trailing.length + index;
      elements.push({
        kind: "lyricMark",
        mark: new GregorianLyricMark({
          text: standaloneLyricText(mark),
          style: "rubric",
          startsWord: true,
          sourceRange: { lowerBound, upperBound: lowerBound + 1 },
          scoreId,
        }),
      });
    });
    if (hasNotes) syllableIndex += 1;

    cursor = close + 1;
  }

  if (compiledEvents && eventIndex !== compiledEvents.length) {
    throw new GregorianScoreParserError({ kind: "timelineMismatch", expected: compiledEvents.length, parsed: eventIndex });
  }
  return new GregorianScore(elements);
}

/** Reads one group of notation; `at` gives the offset in the body of a position in the group. */
function parseNotationGroup(characters: string, at: (index: number) => number): ParsedToken[] {
  const result: ParsedToken[] = [];
  let currentNotes: ParsedNote[] = [];
  let index = 0;
  // The separator written since the last run of notes, which the next run takes as its space before: the first, as
  // Exsurge reads it, with `//` counted as one.
  let spaceBefore: GregorianNeumeSpace = automaticSpace;

  const flushNotes = () => {
    if (currentNotes.length > 0) {
      result.push({ kind: "notes", notes: currentNotes, spaceBefore });
      currentNotes = [];
      spaceBefore = automaticSpace;
    }
  };

  const separate = (space: GregorianNeumeSpace) => {
    const current = spaceBefore;
    if (current.kind === "automatic") spaceBefore = space;
    else if (current.kind === "slashes" && space.kind === "slashes") spaceBefore = { kind: "slashes", count: current.count + space.count };
  };

  const isClefAt = (position: number): boolean => {
    const character = characters[position];
    if (character !== "c" && character !== "f") return false;
    if (position + 1 < characters.length && isNumber(characters[position + 1]!)) return true;
    return character === "c" && position + 2 < characters.length && characters[position + 1] === "b" && isNumber(characters[position + 2]!);
  };

  // A bracketed sign (`[alt:...]`) is passed over; one left open runs to the end of the group.
  const skipBracket = (from: number): number => {
    const close = characters.indexOf("]", from + 1);
    return close >= 0 ? close + 1 : characters.length;
  };

  while (index < characters.length) {
    const character = characters[index]!;

    if (character === "[") {
      flushNotes();
      index = skipBracket(index);
      continue;
    }
    if (isWhitespace(character) || character === "/" || character === "!") {
      flushNotes();
      separate(character === "/" ? { kind: "slashes", count: 1 } : character === "!" ? joinedSpace : { kind: "space" });
      index += 1;
      continue;
    }

    if (isClefAt(index)) {
      flushNotes();
      const isFlatCClef = characters[index + 1] === "b";
      const lineIndex = index + (isFlatCClef ? 2 : 1);
      const token = characters.substring(index, lineIndex + 1);
      const lineCharacter = characters[lineIndex]!;
      const line = lineCharacter >= "0" && lineCharacter <= "9" ? Number(lineCharacter) : null;
      if (line == null || line < 1 || line > 4) throw new GregorianScoreParserError({ kind: "invalidClef", token, offset: at(index) });
      result.push({ kind: "clef", clef: { kind: character === "c" ? "c" : "f", line, flattensB: isFlatCClef } });
      index = lineIndex + 1;
      continue;
    }

    if (character === ":" || character === ";" || character === "," || character === "`") {
      flushNotes();
      if (character === ":" && index + 1 < characters.length && characters[index + 1] === ":") {
        result.push({ kind: "division", division: "final" });
        index += 2;
      } else {
        result.push({
          kind: "division",
          division: character === "," || character === "`" ? "minima" : character === ";" ? "minor" : "major",
        });
        index += 1;
      }
      continue;
    }

    if (character === "z" || character === "Z") {
      flushNotes();
      // `z0` writes a custos for the next note, as before a change of clef, and breaks no line: a staff's end has its own.
      if (character === "z" && index + 1 < characters.length && characters[index + 1] === "0") {
        index += 2;
        continue;
      }
      result.push({ kind: "forcedBreak" });
      index += 1;
      continue;
    }

    const pitch = pitchValue(character);
    if (pitch == null) {
      if (isIgnorableControl(character)) {
        index += 1;
        continue;
      }
      throw new GregorianScoreParserError({ kind: "unsupportedToken", token: character, offset: at(index) });
    }

    const noteStart = index;
    if (index + 1 < characters.length) {
      const sign = characters[index + 1];
      const accidental: GregorianAccidental | null = sign === "x" ? "flat" : sign === "y" ? "natural" : sign === "#" ? "sharp" : null;
      if (accidental) {
        flushNotes();
        result.push({
          kind: "accidental",
          mark: { kind: accidental, pitch, sourceRange: { lowerBound: at(noteStart), upperBound: at(noteStart + 2) } },
        });
        index += 2;
        continue;
      }
    }
    index += 1;
    let suffix = "";
    while (index < characters.length) {
      const next = characters[index]!;
      if (next === "[") {
        index = skipBracket(index);
        continue;
      }
      if (isWhitespace(next) || "/:;,`zZ".includes(next) || pitchValue(next) != null || isClefAt(index)) break;
      suffix += next;
      index += 1;
    }

    const liquescence: GregorianLiquescence = suffix.includes("<")
      ? "ascending"
      : suffix.includes(">")
        ? "descending"
        : suffix.includes("~")
          ? "small"
          : "none";

    // A pitch letter whose signs end in `+` is a custos, as in Exsurge: it stands apart from the notes on either side.
    const isCustos = suffix.endsWith("+");

    let shape: GregorianNoteShape;
    if (isCustos) shape = "custos";
    else if (suffix.includes("w") || suffix.includes("W")) shape = "quilisma";
    // A capital is an inclinatum, liquescent or not: `F~` is the diminished inclinatum that ends an ancus.
    else if (isUppercase(character)) shape = "inclinatum";
    else if (suffix.includes("<") || suffix.includes(">") || suffix.includes("~")) shape = "liquescent";
    else if (suffix.includes("v") || suffix.includes("V")) shape = "virga";
    else if (suffix.includes("o") || suffix.includes("O")) shape = "oriscus";
    else if (suffix.includes("s") || suffix.includes("S")) shape = "stropha";
    else shape = "punctum";

    const accidental: GregorianAccidental | null = suffix.includes("x")
      ? "flat"
      : suffix.includes("y")
        ? "natural"
        : suffix.includes("#")
          ? "sharp"
          : null;

    for (const sign of suffix) {
      if (!isSupportedSuffix(sign)) throw new GregorianScoreParserError({ kind: "unsupportedToken", token: sign, offset: at(noteStart + 1) });
    }

    if (isCustos) flushNotes();
    currentNotes.push({
      pitch,
      shape,
      accidental,
      hasMora: suffix.includes("."),
      hasEpisema: suffix.includes("_"),
      moraCount: count(suffix, "."),
      moraPosition: signPosition(".", suffix),
      episemaPosition: signPosition("_", suffix),
      episemaReach: count(suffix, "_"),
      liquescence,
      // r0 is a hollow note within lines; r1 to r5 are signs written above the note (an accent, a circle), not a
      // hollow note.
      isCavum: isCavum(suffix),
      hasIctus: suffix.includes("'"),
      ictusPosition: signPosition("'", suffix),
      repetitionCount: isCustos ? 1 : repeatedNoteCount(suffix),
      sourceRange: { lowerBound: at(noteStart), upperBound: at(index) },
    });
    if (isCustos || suffix.includes("!")) {
      flushNotes();
      if (suffix.includes("!")) separate(joinedSpace);
    }
  }

  flushNotes();

  // An episema written with more than one underscore (`kj__`) reaches back over as many notes of the group.
  const groupNotes = result.flatMap((token) => (token.kind === "notes" ? token.notes : []));
  groupNotes.forEach((note, order) => {
    for (let back = 1; back < note.episemaReach; back += 1) {
      const earlier = groupNotes[order - back];
      if (!earlier) break;
      earlier.hasEpisema = true;
      if (earlier.episemaPosition == null) earlier.episemaPosition = note.episemaPosition;
    }
  });
  return result;
}

function count(text: string, character: string): number {
  let result = 0;
  for (const each of text) if (each === character) result += 1;
  return result;
}

/** The side a sign's digit sets it on: `1` above, `0` below. */
function signPosition(sign: string, suffix: string): GregorianEpisemaPosition | null {
  for (let index = 0; index < suffix.length - 1; index += 1) {
    if (suffix[index] !== sign) continue;
    if (suffix[index + 1] === "1") return "above";
    if (suffix[index + 1] === "0") return "below";
  }
  return null;
}

/** GABC's quick virga and stropha: each repeated sign is another note on the same pitch (`hvv` is two virgae, `hsss` three strophae). */
function repeatedNoteCount(suffix: string): number {
  let result = 1;
  let shape: string | null = null;
  for (const sign of suffix) {
    if (sign !== "s" && sign !== "v") continue;
    if (shape === sign) result += 1;
    shape = sign;
  }
  return result;
}

function isCavum(suffix: string): boolean {
  for (let index = 0; index < suffix.length; index += 1) {
    if (suffix[index] !== "r" && suffix[index] !== "R") continue;
    // A sign with no whole-number value leaves the r alone, a hollow note.
    const next = suffix[index + 1];
    const sign = next !== undefined && isNumber(next) ? (next >= "0" && next <= "9" ? Number(next) : -1) : -1;
    if (sign < 0 || sign === 0) return true;
  }
  return false;
}

/** A note's place on the staff by its letter, `a` (0) to `m` (12). */
function pitchValue(character: string): number | null {
  const lower = character.toLowerCase();
  if (lower.length !== 1) return null;
  return lower >= "a" && lower <= "m" ? lower.charCodeAt(0) - 97 : null;
}

const numberPattern = /^\p{N}$/u;
const uppercasePattern = /^\p{Uppercase}$/u;

/** A numeral of any script, as Swift's `Character.isNumber` reads one. */
function isNumber(character: string): boolean {
  return numberPattern.test(character);
}

function isUppercase(character: string): boolean {
  return uppercasePattern.test(character);
}

function isSupportedSuffix(character: string): boolean {
  return isNumber(character) || "._wW<>~xy#vVoOqQsSrR'!@-+*†(".includes(character);
}

function isIgnorableControl(character: string): boolean {
  return isNumber(character) || "'!@]-+_*†(.".includes(character);
}

/** A group's lyric as sung, from its source without tags. */
function cleanLyric(source: string): CleanLyric {
  // Psalm-tone sources use paired underscores for preparatory syllables and paired asterisks for accented syllables.
  // These delimiters describe pointing and style and are not lyric text. The paired patterns leave a standalone
  // liturgical asterisk unchanged.
  let text = source.replace(preparatory, "$1");
  text = text.replace(accented, "$1");
  // A tilde is a space that no line break may take.
  text = text
    .replaceAll("~", " ")
    .replaceAll("\n", " ")
    .split(spaces)
    .filter((word) => word.length > 0)
    .join(" ")
    .trim();
  // Braces name the letters to centre under the note, in place of the sung vowel: `{ia}.`, `p{a}u`.
  const characters: string[] = [];
  let braceStart: number | null = null;
  let braceEnd: number | null = null;
  for (const character of graphemes(text)) {
    if (character === "{") {
      if (braceStart == null) braceStart = characters.length;
    } else if (character === "}") {
      if (braceStart != null && braceEnd == null) braceEnd = characters.length;
    } else {
      characters.push(character);
    }
  }
  const centeredRange =
    braceStart != null && braceEnd != null && braceEnd > braceStart ? { lowerBound: braceStart, upperBound: braceEnd } : null;
  return { text: characters.join(""), centeredRange };
}

/**
 * A sung syllable's lyric, without the rubrics written before or after its words: `V/. Dó`, `2. Pro`, `* ab`,
 * `hæc, †`. A lyric made of rubrics alone is left whole.
 */
function sungLyric(lyric: CleanLyric): SungLyric {
  const words = lyric.text.split(" ").filter((word) => word.length > 0);
  let first = 0;
  let last = words.length;
  while (first < last && isRubricMark(words[first]!)) first += 1;
  while (last > first && isRubricMark(words[last - 1]!)) last -= 1;
  if (first >= last || (first === 0 && last === words.length)) return sungLyricOf(lyric);
  const text = words.slice(first, last).join(" ");
  const dropped = words.slice(0, first).reduce((sum, word) => sum + graphemes(word).length + 1, 0);
  const textLength = graphemes(text).length;
  let centeredRange: GregorianSourceRange | null = null;
  if (lyric.centeredRange) {
    const lower = lyric.centeredRange.lowerBound - dropped;
    const upper = lyric.centeredRange.upperBound - dropped;
    if (lower >= 0 && upper <= textLength) centeredRange = { lowerBound: lower, upperBound: upper };
  }
  return { leading: words.slice(0, first), text, centeredRange, trailing: words.slice(last) };
}

/** Whether a word of a lyric is a rubric: a sign, a repeat mark, or a verse number. */
function isRubricMark(word: string): boolean {
  return rubrics.has(word) || verseNumber.test(word);
}

/**
 * Whether a rubric written alone heads the words after it: ℣., ℟., the cross signed at a canticle's first word, or a
 * verse number. The others close the words before them.
 */
function headsWhatFollows(text: string): boolean {
  return text === "℣." || text === "℟." || text === "✠" || verseNumber.test(text);
}

const graphemeSegmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });

/** A text's characters as a reader counts them, an accent with its letter, as Swift counts them. */
export function graphemes(text: string): string[] {
  return Array.from(graphemeSegmenter.segment(text), (segment) => segment.segment);
}

function lyricStyle(source: string): GregorianLyricStyle {
  if (hasAccented.test(source)) return "accented";
  if (hasPreparatory.test(source)) return "preparatory";
  return "regular";
}

function standaloneLyricText(lyric: string): string {
  if (lyric === "V/.") return "℣.";
  if (lyric === "R/.") return "℟.";
  return lyric;
}

function standaloneLyricStyle(lyric: string, parsedStyle: GregorianLyricStyle): GregorianLyricStyle {
  if (parsedStyle !== "regular") return parsedStyle;
  return isRubricMark(lyric) ? "rubric" : "regular";
}

function lyricStartsWord(source: string, isFirst: boolean): boolean {
  if (isFirst) return true;
  const first = source[0];
  return first !== undefined && isWhitespace(first);
}

/**
 * Offsets in a text as Swift counts a String's: in characters as a reader counts them, a CR LF, or a letter with its
 * accent written after it, being one. A position within such a character is its start.
 */
function characterOffsets(text: string): (index: number) => number {
  // Most scores are plain ASCII without CR LF, where the two counts agree.
  // eslint-disable-next-line no-control-regex
  if (/^[\x00-\x7f]*$/.test(text) && !text.includes("\r\n")) return (index) => index;
  const offsets = new Int32Array(text.length + 1);
  let characterCount = 0;
  let start = 0;
  for (const segment of graphemeSegmenter.segment(text)) {
    for (let index = start; index < start + segment.segment.length; index += 1) offsets[index] = characterCount;
    characterCount += 1;
    start += segment.segment.length;
  }
  offsets[text.length] = characterCount;
  return (index) => offsets[index] ?? index;
}

/** Whether a melody rises (1), falls (-1) or keeps its pitch (0) from one note to the next. */
function direction(from: number, to: number): number {
  return to > from ? 1 : to < from ? -1 : 0;
}

function sameDirections(directions: readonly number[], expected: readonly number[]): boolean {
  return directions.length === expected.length && directions.every((value, index) => value === expected[index]);
}

export const GregorianNeumeClassifier = {
  classify(notes: readonly GregorianNote[]): GregorianNeumeForm {
    const first = notes[0];
    if (!first) return "punctum";
    const pitches = notes.map((note) => note.pitch);
    const directions = pitches.slice(1).map((to, index) => direction(pitches[index]!, to));
    const allEqual = new Set(pitches).size === 1;
    const allInclinata = notes.every((note) => note.shape === "inclinatum");
    const allVirga = notes.every((note) => note.shape === "virga");
    // A note written twice or thrice (`hvv`, `hsss`) counts as so many.
    const writtenCount = notes.reduce((sum, note) => sum + note.repetitionCount, 0);

    if (allInclinata) return "punctaInclinata";
    if (writtenCount === 1) {
      switch (first.shape) {
        case "virga":
          return "virga";
        case "stropha":
          return "apostropha";
        case "oriscus":
          return "oriscus";
        default:
          return "punctum";
      }
    }
    if (allEqual && allVirga) return writtenCount >= 3 ? "trivirga" : "bivirga";
    if (allEqual) return writtenCount >= 3 ? "tristropha" : "distropha";
    if (notes.length === 2) {
      if (directions[0]! > 0) return first.shape === "oriscus" ? "pesQuassus" : "podatus";
      return directions[0]! < 0 ? "clivis" : "punctum";
    }

    // A pes and the diamonds that fall from it.
    if (
      directions[0] === 1 &&
      notes.length >= 3 &&
      notes.slice(2).every((note) => note.shape === "inclinatum") &&
      directions.slice(1).every((value) => value < 0)
    ) {
      return "pesSubpunctis";
    }

    const firstTwo = directions.slice(0, 2);
    const rest = directions.slice(2);
    if (sameDirections(firstTwo, [1, 1])) {
      if (notes.slice(1).some((note) => note.shape === "oriscus")) return rest.includes(-1) ? "salicusFlexus" : "salicus";
      return rest.includes(-1) ? "scandicusFlexus" : "scandicus";
    }
    if (sameDirections(firstTwo, [-1, -1])) {
      return notes[notes.length - 1]!.liquescence !== "none" ? "ancus" : "climacus";
    }
    if (sameDirections(firstTwo, [1, -1])) {
      if (sameDirections(rest.slice(0, 2), [1, -1])) return "torculusResupinusFlexus";
      if (rest[0] === 1) return "torculusResupinus";
      return "torculus";
    }
    if (sameDirections(firstTwo, [-1, 1])) {
      return rest[0] === -1 ? "porrectusFlexus" : "porrectus";
    }
    return "punctum";
  },
};

/**
 * Splits a continuous GABC note run into the named forms accepted by the native compositor. This is the native
 * counterpart of Exsurge's `createNeumesFromNotes` state machine.
 */
export const GregorianNeumeSegmenter = {
  segment(notes: readonly GregorianNote[]): GregorianNote[][] {
    // A note written twice or thrice (`hvv`, `hsss`) is a bivirga or a tristropha in itself, and a custos belongs to
    // no neume. Each stands alone, and the notes on either side group as they would without it.
    if (!notes.some((note) => note.standsAlone)) return segmentRun(notes);
    const result: GregorianNote[][] = [];
    let run: GregorianNote[] = [];
    for (const note of notes) {
      if (note.standsAlone) {
        result.push(...segmentRun(run));
        run = [];
        result.push([note]);
      } else {
        run.push(note);
      }
    }
    result.push(...segmentRun(run));
    return result;
  },
};

function segmentRun(notes: readonly GregorianNote[]): GregorianNote[][] {
  if (notes.length <= 1) return notes.length === 0 ? [] : [[...notes]];
  const result: GregorianNote[][] = [];
  let index = 0;
  while (index < notes.length) {
    const remaining = notes.length - index;
    if (remaining <= 1) {
      result.push([notes[index]!]);
      break;
    }
    const first = notes[index]!;
    const second = notes[index + 1]!;
    const firstDirection = direction(first.pitch, second.pitch);
    const pitchAt = (position: number) => notes[position]!.pitch;

    if (first.shape === "virga" || first.shape === "stropha") {
      const repeated = repeatedShapeCount(notes, index, first.shape, first.pitch, 3);
      result.push(notes.slice(index, index + repeated));
      index += repeated;
      continue;
    }
    if (first.shape === "inclinatum") {
      let end = index + 1;
      while (end < notes.length && notes[end]!.shape === "inclinatum") end += 1;
      result.push(notes.slice(index, end));
      index = end;
      continue;
    }

    let length: number;
    if (firstDirection > 0) {
      if (remaining >= 3 && notes[index + 2]!.shape === "inclinatum" && direction(second.pitch, pitchAt(index + 2)) < 0) {
        length = 3;
        while (
          index + length < notes.length &&
          notes[index + length]!.shape === "inclinatum" &&
          direction(pitchAt(index + length - 1), pitchAt(index + length)) < 0
        ) {
          length += 1;
        }
      } else if (remaining >= 3) {
        const secondDirection = direction(second.pitch, pitchAt(index + 2));
        if (secondDirection > 0) {
          length = 3;
          if (remaining >= 4 && direction(pitchAt(index + 2), pitchAt(index + 3)) < 0) length = 4;
        } else if (secondDirection < 0) {
          length = 3;
          if (remaining >= 4 && direction(pitchAt(index + 2), pitchAt(index + 3)) > 0) {
            length = 4;
            if (remaining >= 5 && direction(pitchAt(index + 3), pitchAt(index + 4)) < 0) length = 5;
          }
        } else {
          length = 2;
        }
      } else {
        length = 2;
      }
    } else if (firstDirection < 0) {
      if (second.shape === "inclinatum") {
        length = 2;
        while (
          index + length < notes.length &&
          notes[index + length]!.shape === "inclinatum" &&
          direction(pitchAt(index + length - 1), pitchAt(index + length)) < 0
        ) {
          length += 1;
        }
      } else if (remaining >= 3 && direction(second.pitch, pitchAt(index + 2)) < 0 && notes[index + 2]!.liquescence !== "none") {
        length = 3;
      } else if (remaining >= 3 && direction(second.pitch, pitchAt(index + 2)) > 0) {
        length = 3;
        if (remaining >= 4 && direction(pitchAt(index + 2), pitchAt(index + 3)) < 0) length = 4;
      } else {
        length = 2;
      }
    } else {
      length = unisonLength(notes, index);
    }
    result.push(notes.slice(index, index + length));
    index += length;
  }
  return result;
}

function repeatedShapeCount(notes: readonly GregorianNote[], index: number, shape: GregorianNoteShape, pitch: number, maximum: number): number {
  let result = 0;
  while (
    index + result < notes.length &&
    result < maximum &&
    notes[index + result]!.shape === shape &&
    notes[index + result]!.pitch === pitch
  ) {
    result += 1;
  }
  return Math.max(1, result);
}

/**
 * How many plain notes repeated on one pitch make a neume, as Exsurge groups them: two a distropha and three a
 * tristropha only where the run ends with them. Where the melody goes on, the last of them begins the next neume, a
 * pressus (`ffg` is a punctum and a pes), and a dotted or diminished note stands alone.
 */
function unisonLength(notes: readonly GregorianNote[], index: number): number {
  const first = notes[index]!;
  const second = notes[index + 1];
  if (!second || second.pitch !== first.pitch || !isPlain(second) || first.moraCount > 0 || first.liquescence === "small") {
    return 1;
  }
  const third = notes[index + 2];
  if (!third) return 2;
  if (third.pitch !== first.pitch) return 1;
  if (second.moraCount > 0) return 2;
  return index + 3 < notes.length ? 2 : 3;
}

/** A note of no shape of its own: a punctum, liquescent or not. */
function isPlain(note: GregorianNote): boolean {
  return note.shape === "punctum" || note.shape === "liquescent";
}
