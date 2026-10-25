// The engraving of a score: its units measured and broken into staffs, then each note, word and mark set in place, in
// points of the width it is laid out at. A port of the Swift package's GregorianLayout.swift, by way of the Kotlin port.

import { GregorianRect, type GregorianPoint, type GregorianSize } from "./GregorianGeometry.js";
import { GregorianGlyphCatalog, type GregorianGlyphName } from "./GregorianGlyphCatalog.js";
import { GregorianLyricFont, type GregorianLyricMeasurer } from "./GregorianLyricFont.js";
import {
  graphemes,
  type GregorianAccidental,
  type GregorianClef,
  type GregorianDivision,
  type GregorianEpisemaPosition,
  type GregorianLyricStyle,
  type GregorianNeume,
  type GregorianNotationElement,
  type GregorianNote,
  type GregorianNoteShape,
  type GregorianScore,
} from "./GregorianNotation.js";

/** The sizes a score is laid out at. */
export interface GregorianLayoutMetrics {
  readonly notationScale: number;
  readonly lyricScale: number;
  /** The PostScript name of the font lyrics are measured in. Whatever draws the layout should draw its lyrics in the font each one names. */
  readonly lyricFontName: string;
  /**
   * The pixels in a point of the screen the layout is drawn on. Given, the staff space is a whole number of pixels, and
   * the staff and ledger lines, stems, episemata and bars are whole pixels wide, two at least, and set where their edges
   * fall between pixels, so that they draw crisply; null, as for print, sets them where they fall.
   */
  readonly displayScale: number | null;
}

export const GregorianLayoutMetrics = {
  minimumNotationScale: 0.6,
  maximumNotationScale: 1.6,

  clampedNotationScale(scale: number): number {
    return Math.max(GregorianLayoutMetrics.minimumNotationScale, Math.min(GregorianLayoutMetrics.maximumNotationScale, scale));
  },

  /** The default sizes, with those given in place of them. */
  of(metrics: Partial<GregorianLayoutMetrics> = {}): GregorianLayoutMetrics {
    return {
      notationScale: metrics.notationScale ?? 1,
      lyricScale: metrics.lyricScale ?? 1,
      lyricFontName: metrics.lyricFontName ?? GregorianLyricFont.defaultName,
      displayScale: metrics.displayScale ?? null,
    };
  },
};

/** Rounded half away from zero, as Swift rounds. */
function rounded(value: number): number {
  return value >= 0 ? Math.floor(value + 0.5) : -Math.floor(-value + 0.5);
}

/** A count of pixels to a thousandth, so that a place a hair's breadth either side of a pixel's middle rounds alike in every language. */
function steady(pixels: number): number {
  return rounded(pixels * 1000) / 1000;
}

/** The pixel grid of the screen a layout is drawn on, if one is given. */
class PixelGrid {
  /** @param scale Pixels in a point; null sets everything where it falls. */
  constructor(readonly scale: number | null) {}

  /** The nearest length of whole pixels, one at least. */
  wholePixels(length: number): number {
    return this.scale == null ? length : Math.max(1, rounded(steady(length * this.scale))) / this.scale;
  }

  /** A length rounded up to whole pixels. */
  roundedUp(length: number): number {
    return this.scale == null ? length : Math.ceil(steady(length * this.scale) - 0.001) / this.scale;
  }

  /** A line's width in whole pixels, two at least. */
  lineWidth(width: number): number {
    return this.scale == null ? width : Math.max(2, rounded(steady(width * this.scale))) / this.scale;
  }

  /**
   * Where a line of `width`, already whole pixels, is centred near `position` so that its edges fall between pixels:
   * on the edge of a pixel if it is an even number of pixels wide, on the middle of one if odd.
   */
  centre(position: number, width: number, roundingUp = false): number {
    const scale = this.scale;
    if (scale == null) return position;
    const offset = rounded(steady(width * scale)) % 2 === 0 ? 0 : 0.5;
    const pixels = steady(position * scale - offset);
    return ((roundingUp ? Math.ceil(pixels - 0.001) : rounded(pixels)) + offset) / scale;
  }

  /** Strokes snapped to the grid: each whole pixels wide, and set across or up and down where its edges fall between pixels. */
  snap(strokes: readonly GregorianPlacedStroke[]): GregorianPlacedStroke[] {
    if (this.scale == null) return [...strokes];
    return strokes.map((stroke) => {
      const width = this.lineWidth(stroke.lineWidth);
      if (Math.abs(stroke.start.y - stroke.end.y) < 0.001) {
        const y = this.centre(stroke.start.y, width);
        return { ...stroke, start: { x: stroke.start.x, y }, end: { x: stroke.end.x, y }, lineWidth: width };
      }
      if (Math.abs(stroke.start.x - stroke.end.x) < 0.001) {
        const x = this.centre(stroke.start.x, width);
        return { ...stroke, start: { x, y: stroke.start.y }, end: { x, y: stroke.end.y }, lineWidth: width };
      }
      return { ...stroke, lineWidth: width };
    });
  }
}

export type GregorianGlyphKind =
  // Stable cases kept for tests and accessibility consumers.
  | { readonly kind: "cClef" }
  | { readonly kind: "fClef" }
  | { readonly kind: "notehead"; readonly shape: GregorianNoteShape }
  | { readonly kind: "flat" }
  | { readonly kind: "natural" }
  | { readonly kind: "sharp" }
  | { readonly kind: "mora" }
  | { readonly kind: "custos" }
  | { readonly kind: "catalog"; readonly name: GregorianGlyphName };

/** A glyph of the catalog, by its name. */
export function catalogGlyph(name: GregorianGlyphName): GregorianGlyphKind {
  return { kind: "catalog", name };
}

/** Whether two glyph kinds are the same. */
export function sameGlyphKind(first: GregorianGlyphKind, second: GregorianGlyphKind): boolean {
  if (first.kind !== second.kind) return false;
  if (first.kind === "notehead" && second.kind === "notehead") return first.shape === second.shape;
  if (first.kind === "catalog" && second.kind === "catalog") return first.name === second.name;
  return true;
}

/** The catalog glyph a glyph kind is drawn with. */
export function catalogName(kind: GregorianGlyphKind): GregorianGlyphName {
  switch (kind.kind) {
    case "cClef":
      return "DoClef";
    case "fClef":
      return "FaClef";
    case "notehead":
      switch (kind.shape) {
        case "punctum":
          return "PunctumQuadratum";
        case "virga":
          return "VirgaShort";
        case "quilisma":
          return "Quilisma";
        case "liquescent":
          return "PunctumQuadratumLiquescent";
        case "inclinatum":
          return "PunctumInclinatum";
        case "oriscus":
          return "OriscusAsc";
        case "stropha":
          return "Stropha";
        case "custos":
          return "CustosShort";
      }
    // falls through: every shape returns
    case "flat":
      return "Flat";
    case "natural":
      return "Natural";
    case "sharp":
      return "Sharp";
    case "mora":
      return "Mora";
    case "custos":
      return "CustosShort";
    case "catalog":
      return kind.name;
  }
}

export type GregorianStrokeKind =
  | { readonly kind: "staff" }
  | { readonly kind: "ledger" }
  | { readonly kind: "connector" }
  | { readonly kind: "episema" }
  | { readonly kind: "division"; readonly division: GregorianDivision };

const staffStroke: GregorianStrokeKind = { kind: "staff" };
const ledgerStroke: GregorianStrokeKind = { kind: "ledger" };
const connectorStroke: GregorianStrokeKind = { kind: "connector" };
const episemaStroke: GregorianStrokeKind = { kind: "episema" };

export interface GregorianPlacedGlyph {
  readonly kind: GregorianGlyphKind;
  readonly frame: GregorianRect;
  readonly eventId: string | null;
}

export interface GregorianPlacedStroke {
  readonly kind: GregorianStrokeKind;
  readonly start: GregorianPoint;
  readonly end: GregorianPoint;
  readonly lineWidth: number;
  readonly eventId: string | null;
}

/** A lyric, set from the top left of its line. */
export interface GregorianPlacedLyric {
  readonly text: string;
  readonly style: GregorianLyricStyle;
  readonly origin: GregorianPoint;
  readonly width: number;
  readonly fontSize: number;
  /** The PostScript name of the font the lyric was measured in. */
  readonly fontName: string;
  readonly neumeId: string;
}

/** The large initial a score opens with, its mode above it. */
export interface GregorianPlacedInitial {
  readonly text: string;
  readonly annotation: string | null;
  readonly annotationOrigin: GregorianPoint | null;
  readonly annotationFontSize: number;
  readonly origin: GregorianPoint;
  readonly width: number;
  readonly fontSize: number;
  /** The PostScript name of the font the initial and its annotation were measured in. */
  readonly fontName: string;
  readonly neumeId: string;
  /** The style the initial and its annotation were measured in, and must be drawn in: bold, as the chant books set them. */
  readonly style: GregorianLyricStyle;
}

export interface GregorianEventPlacement {
  readonly eventId: string;
  readonly neumeId: string;
  readonly frame: GregorianRect;
  readonly lineIndex: number;
}

export interface GregorianNeumePlacement {
  readonly id: string;
  readonly eventIds: readonly string[];
  /** The notes the neume draws: more than its events when a note is written twice or thrice (`hvv`, `hsss`). */
  readonly noteCount: number;
  readonly lyric: string;
  readonly inkFrame: GregorianRect;
  readonly hitFrame: GregorianRect;
  readonly lineIndex: number;
}

export interface GregorianStaffLayout {
  readonly index: number;
  readonly frame: GregorianRect;
  readonly systemFrame: GregorianRect;
  readonly firstEventId: string | null;
}

export class GregorianLayout {
  constructor(
    readonly size: GregorianSize,
    readonly staffs: readonly GregorianStaffLayout[],
    readonly glyphs: readonly GregorianPlacedGlyph[],
    readonly strokes: readonly GregorianPlacedStroke[],
    readonly lyrics: readonly GregorianPlacedLyric[],
    readonly initials: readonly GregorianPlacedInitial[],
    readonly events: readonly GregorianEventPlacement[],
    readonly neumes: readonly GregorianNeumePlacement[],
  ) {}

  neumeContaining(eventId: string): GregorianNeumePlacement | null {
    return this.neumes.find((neume) => neume.eventIds.includes(eventId)) ?? null;
  }
}

/**
 * How a syllable's lyric meets the lyric before it: `word` where it begins a word, or follows a rubric, and a word's
 * space parts them; `syllable` where it is the next syllable of the word, joined to the one before or parted from it
 * by one hyphen; `written` where one of the two writes its own hyphen at the join, and they may touch, and take no
 * other.
 */
type LyricJoin = "word" | "syllable" | "written";

/** The notes beside a neume that its engraving needs. */
interface NeumeContext {
  /** The last note of the neume of its syllable that it follows with no space (`!`). */
  readonly joinedFrom: GregorianNote | null;
  /** The next neume's first note. */
  readonly next: GregorianNote | null;
}

const noContext: NeumeContext = { joinedFrom: null, next: null };

interface LayoutUnit {
  readonly element: GregorianNotationElement;
  /**
   * The unit's ink, reckoned from where it is placed: a neume's from the centre of its first note, a clef's, an
   * accidental's or a division's from the left of the room it takes, which runs to `inkRight`.
   */
  readonly inkLeft: number;
  readonly inkRight: number;
  readonly lyricText: string;
  readonly lyricWidth: number;
  /** From the lyric's left edge to the centre of its sung vowel. */
  readonly lyricFocus: number;
  readonly join: LyricJoin;
  readonly forcesBreak: boolean;
  /** The notes beside a neume that its engraving needs. */
  readonly context: NeumeContext;
  /** How far the unit's ink reaches below the staff's bottom line. */
  readonly inkBelowStaff: number;
  /** How far the unit's ink reaches above the staff's top line. */
  readonly inkAboveStaff: number;
}

function unitNeume(unit: LayoutUnit): GregorianNeume | null {
  return unit.element.kind === "neume" ? unit.element.neume : null;
}

function hasLyric(unit: LayoutUnit): boolean {
  return unit.lyricText.length > 0;
}

function lyricId(unit: LayoutUnit): string | null {
  if (unit.element.kind === "neume") return unit.element.neume.id;
  if (unit.element.kind === "lyricMark") return unit.element.mark.id;
  return null;
}

/**
 * The room a staff gives its notes and words: where the first note may stand, clear of the clef; where the notes must
 * end, short of the custos; and where the words must end, which may run on under the custos.
 */
interface LineFrame {
  readonly staffLeft: number;
  readonly notationStart: number;
  readonly notationEnd: number;
  readonly textEnd: number;
}

/**
 * A staff's units, set in place: each unit's place, from which its `inkLeft` and `inkRight` are reckoned; the left edge
 * of each unit's lyric; and the units whose syllable a hyphen follows, within the staff or at its end.
 */
class LinePlacement {
  readonly hyphenated: number[] = [];
  /** The words, bars, rubrics and clefs the staff may stretch before. */
  stretchPoints = 0;
  /** The share of the staff's note spaces that it may give up. */
  spare = 0;

  constructor(
    readonly x: number[],
    readonly lyricX: (number | null)[],
    public notationRight: number,
    public textRight: number,
  ) {}

  fits(frame: LineFrame): boolean {
    return this.notationRight <= frame.notationEnd + 0.01 && this.textRight <= frame.textEnd + 0.01;
  }

  /**
   * The room the staff leaves at its end: by the notes, short of the custos, or by the words, whichever leaves less. It
   * is reckoned to a hundredth of a point, so that no choice of breaks turns on how the sums were rounded.
   */
  slack(frame: LineFrame): number {
    const slack = Math.min(frame.notationEnd - this.notationRight, frame.textEnd - this.textRight);
    return Math.floor(slack * 100 + 0.5) / 100;
  }
}

/**
 * The spaces a staff keeps between its notes and between its words, in points: Exsurge's, reckoned from the staff
 * space. `word` is the least space between two words' texts; `hyphen`, a hyphen's advance, the least space between two
 * syllables of a word that do not touch.
 */
class Spacing {
  /** Between the neumes of one syllable. */
  readonly withinSyllable: number;
  /** Between one syllable's notes and the next's, within a word. */
  readonly betweenSyllables: number;
  /** Between one word's notes and the next's. */
  readonly betweenWords: number;
  /** After a bar, beyond the half of its room that follows its line. */
  readonly afterDivision: number;
  /** Before a clef within the staff. */
  readonly beforeClef: number;
  readonly word: number;
  /** The most a staff that ends a paragraph is stretched at each word or bar, as Exsurge stretches one; the measure of the stretch of any other. */
  readonly maximumStretch: number;

  constructor(
    staffSpace: number,
    readonly hyphen: number,
  ) {
    this.withinSyllable = 0.25 * staffSpace;
    this.betweenSyllables = 0.625 * staffSpace;
    this.betweenWords = 0.875 * staffSpace;
    this.afterDivision = 0.25 * staffSpace;
    this.beforeClef = 0.5 * staffSpace;
    this.word = Math.max(hyphen, 0.5 * staffSpace);
    this.maximumStretch = 0.5 * staffSpace;
  }
}

interface RawNeumePlacement {
  readonly neume: GregorianNeume;
  readonly inkFrame: GregorianRect;
  readonly lineIndex: number;
  readonly staffTop: number;
  readonly staffBottom: number;
}

class Composition {
  readonly glyphs: GregorianPlacedGlyph[] = [];
  readonly strokes: GregorianPlacedStroke[] = [];
  readonly events: GregorianEventPlacement[] = [];
  inkFrame: GregorianRect | null = null;

  ink(frame: GregorianRect): void {
    this.inkFrame = this.inkFrame?.union(frame) ?? frame;
  }
}

interface LineVerticalMetrics {
  readonly topInset: number;
  readonly lyricOffset: number;
  readonly height: number;
}

interface OpeningInitial {
  readonly text: string;
  readonly annotation: string | null;
  readonly remainder: string;
  readonly neumeId: string;
}

/** The options a layout is made with. */
export interface GregorianLayoutOptions {
  readonly metrics?: Partial<GregorianLayoutMetrics>;
  /** The label printed above the initial; the initial is drawn where it is a psalm tone's (`8g`), or where the score opens a piece. */
  readonly openingLabel?: string | null;
  /**
   * Whether the score opens a piece, a hymn, a responsory or a part of the Mass, which takes an initial, with its mode
   * alone above it if it has no psalm tone.
   */
  readonly opensPiece?: boolean;
}

const lyricLineHeightMultiplier = 1.23;
/** The share of a note space that a full staff may give up. */
const condensable = 0.3;

/**
 * Native Gregorian engraving with the same three-stage shape as Exsurge: semantic units, intrinsic measurement and line
 * breaking, then positioned ink. Lyrics are measured by the measurer, in the font each layout's metrics name.
 */
export class GregorianEngravingLayoutEngine {
  constructor(private readonly measurer: GregorianLyricMeasurer) {}

  /** Lays out a score at a width. */
  layout(score: GregorianScore, width: number, options: GregorianLayoutOptions = {}): GregorianLayout {
    const metrics = GregorianLayoutMetrics.of(options.metrics);
    const opensPiece = options.opensPiece ?? false;
    const requestedNotationScale = GregorianLayoutMetrics.clampedNotationScale(metrics.notationScale);
    const grid = new PixelGrid(metrics.displayScale != null && metrics.displayScale > 0 ? metrics.displayScale : null);
    // On a screen, a staff space of whole pixels sets every line of the staff alike.
    const notationScale = grid.wholePixels(12.5 * requestedNotationScale) / 12.5;
    const lyricScale = Math.max(0.5, Math.min(3.5, metrics.lyricScale * requestedNotationScale));
    const lyricFontName = metrics.lyricFontName;
    const columnWidth = Math.max(240, width);
    const glyphScale = notationScale / 16;
    const staffStep = 100 * glyphScale; // one GABC pitch step, half a staff space
    const staffGap = staffStep * 2;
    const staffLineWidth = grid.lineWidth(Math.max(0.72, 0.78 * notationScale));
    const sidePadding = 8 * notationScale;
    const clefColumn = 18 * notationScale;
    const custosColumn = 10 * notationScale;
    const lyricFontSize = 21 * lyricScale;
    const lyricHeight = Math.ceil(lyricFontSize * lyricLineHeightMultiplier);
    const openingInitial = this.openingInitial(
      score,
      GregorianEngravingLayoutEngine.openingLabel(options.openingLabel ?? null, opensPiece),
      opensPiece,
    );
    const initialScale = Math.max(notationScale, Math.min(1.35, lyricScale));
    const initialFontSize = 56 * initialScale;
    const initialAnnotationFontSize = 13 * Math.max(notationScale, Math.min(1.25, lyricScale));
    // The initial and its annotation are bold, as the books set them.
    const initialWidth = openingInitial ? this.measureLyric(openingInitial.text, "accented", initialFontSize, lyricFontName) : 0;
    const initialAnnotationWidth =
      openingInitial?.annotation != null ? this.measureLyric(openingInitial.annotation, "accented", initialAnnotationFontSize, lyricFontName) : 0;
    const ascent = this.measurer.ascent?.(lyricFontName) ?? GregorianLyricFont.ascent;
    const capHeight = this.measurer.capHeight?.(lyricFontName) ?? GregorianLyricFont.capHeight;
    const initialColumn = openingInitial
      ? Math.max(38 * notationScale, Math.max(initialWidth, initialAnnotationWidth) + 8 * notationScale)
      : 0;

    const hyphenWidth = this.measureLyric("-", "regular", lyricFontSize, lyricFontName);
    const spacing = new Spacing(staffGap, hyphenWidth);
    const units = this.makeUnits(score, glyphScale, notationScale, lyricFontSize, lyricFontName, openingInitial);
    const lineFrame = (lineIndex: number): LineFrame => {
      const staffLeft = sidePadding + (lineIndex === 0 ? initialColumn : 0);
      return {
        staffLeft,
        notationStart: staffLeft + clefColumn + 4 * notationScale,
        notationEnd: columnWidth - sidePadding - custosColumn,
        textEnd: columnWidth - sidePadding,
      };
    };
    const wordContinues = wordContinuation(units);
    const lineRanges = this.breakLines(units, lineFrame, spacing, wordContinues);
    const lines = lineRanges.map((range) => units.slice(range.first, range.last + 1));
    const placements = lineRanges.map((range, lineIndex) =>
      this.placeLine(
        units,
        range,
        lineFrame(lineIndex),
        spacing,
        wordContinues[range.last + 1]!,
        range.last < range.first || range.last + 1 === units.length || units[range.last]!.forcesBreak,
      ),
    );
    const lineVerticalMetrics = lines.map((lineUnits, lineIndex) =>
      this.verticalMetrics(
        lineUnits,
        custosPitch(lineIndex, lines),
        glyphScale,
        staffStep,
        staffGap,
        notationScale,
        lyricHeight,
        lineIndex === 0 && openingInitial ? initialFontSize : null,
        grid,
        staffLineWidth,
      ),
    );
    const lineOrigins: number[] = [];
    let contentHeight = 0;
    for (const lineMetrics of lineVerticalMetrics) {
      lineOrigins.push(contentHeight);
      contentHeight += lineMetrics.height;
    }

    const staffs: GregorianStaffLayout[] = [];
    const glyphs: GregorianPlacedGlyph[] = [];
    let strokes: GregorianPlacedStroke[] = [];
    const lyrics: GregorianPlacedLyric[] = [];
    const initials: GregorianPlacedInitial[] = [];
    const events: GregorianEventPlacement[] = [];
    const rawNeumes: RawNeumePlacement[] = [];
    let activeClef: GregorianClef = { kind: "c", line: 4, flattensB: false };
    const punctumWidth = 100 * glyphScale;

    lines.forEach((lineUnits, lineIndex) => {
      const lineOriginY = lineOrigins[lineIndex]!;
      const lineMetrics = lineVerticalMetrics[lineIndex]!;
      const staffTop = lineOriginY + lineMetrics.topInset;
      const staffBottom = staffTop + staffGap * 3;
      const staffCenter = (staffTop + staffBottom) / 2;
      const frame = lineFrame(lineIndex);
      const staffLeft = frame.staffLeft;
      const lyricY = staffBottom + lineMetrics.lyricOffset;
      const placement = placements[lineIndex]!;
      const firstEventId = lineUnits.map(unitNeume).find((neume) => neume != null)?.id ?? null;
      staffs.push({
        index: lineIndex,
        frame: GregorianRect.of(staffLeft, staffTop, columnWidth - sidePadding - staffLeft, staffGap * 3),
        systemFrame: GregorianRect.of(0, lineOriginY, columnWidth, lineMetrics.height),
        firstEventId,
      });

      for (let staffLine = 0; staffLine < 4; staffLine += 1) {
        const y = staffTop + staffLine * staffGap;
        strokes.push({
          kind: staffStroke,
          start: { x: staffLeft, y },
          end: { x: columnWidth - sidePadding, y },
          lineWidth: staffLineWidth,
          eventId: null,
        });
      }

      let leadingClefCount = 0;
      for (const unit of lineUnits) {
        if (unit.element.kind !== "clef") break;
        activeClef = unit.element.clef;
        leadingClefCount += 1;
      }
      this.appendClef(activeClef, staffLeft + 2 * notationScale, staffTop, staffGap, glyphScale, notationScale, glyphs);

      if (lineIndex === 0 && openingInitial) {
        // The initial stands on the words' baseline, and its annotation is centred over it, its capitals about the top
        // line, as in the Liber.
        const lyricBaseline = lyricY + ascent * lyricFontSize;
        const annotationBaseline = staffTop - 0.14 * staffGap + (capHeight * initialAnnotationFontSize) / 2;
        initials.push({
          text: openingInitial.text,
          annotation: openingInitial.annotation,
          annotationOrigin:
            openingInitial.annotation != null
              ? {
                  x: sidePadding + (initialColumn - initialAnnotationWidth) / 2,
                  y: Math.max(0, annotationBaseline - ascent * initialAnnotationFontSize),
                }
              : null,
          annotationFontSize: initialAnnotationFontSize,
          origin: { x: sidePadding + (initialColumn - initialWidth) / 2, y: lyricBaseline - ascent * initialFontSize },
          width: initialWidth,
          fontSize: initialFontSize,
          fontName: lyricFontName,
          neumeId: openingInitial.neumeId,
          style: "accented",
        });
      }

      lineUnits.forEach((unit, unitIndex) => {
        const x = placement.x[unitIndex]!;
        const element = unit.element;
        switch (element.kind) {
          case "clef":
            // The clefs a staff opens with are set at its head, and take no room in it.
            if (unitIndex < leadingClefCount) return;
            activeClef = element.clef;
            this.appendClef(element.clef, x, staffTop, staffGap, glyphScale, notationScale, glyphs);
            break;
          case "accidental": {
            const y = staffCenter - (element.mark.pitch - 6) * staffStep;
            glyphs.push(placedGlyph(accidentalKind(element.mark.kind), { x, y }, glyphScale * 0.9));
            break;
          }
          case "division":
            appendDivision(element.division, x + unit.inkRight / 2, staffTop, staffGap, notationScale, strokes);
            break;
          case "forcedBreak":
            break;
          case "lyricMark":
            lyrics.push({
              text: element.mark.text,
              style: element.mark.style,
              origin: { x: placement.lyricX[unitIndex] ?? x, y: lyricY },
              width: unit.lyricWidth,
              fontSize: lyricFontSize,
              fontName: lyricFontName,
              neumeId: element.mark.id,
            });
            break;
          case "neume": {
            const neume = element.neume;
            const composition = this.compose(
              neume,
              unit.context,
              x - punctumWidth / 2,
              staffCenter,
              staffTop,
              staffBottom,
              staffStep,
              glyphScale,
              notationScale,
              lineIndex,
            );
            glyphs.push(...composition.glyphs);
            strokes.push(...composition.strokes);
            events.push(...composition.events);

            const lyricX = placement.lyricX[unitIndex];
            if (hasLyric(unit) && lyricX != null) {
              lyrics.push({
                text: unit.lyricText,
                style: neume.lyricStyle,
                origin: { x: lyricX, y: lyricY },
                width: unit.lyricWidth,
                fontSize: lyricFontSize,
                fontName: lyricFontName,
                neumeId: neume.id,
              });
            }
            // A custos is drawn and keeps its event's place, but it is not sung: the cantor guide cannot begin from it.
            if (neume.writtenNoteCount > 0) {
              rawNeumes.push({
                neume,
                inkFrame: composition.inkFrame ?? GregorianRect.zero,
                lineIndex,
                staffTop,
                staffBottom: staffBottom + lineMetrics.lyricOffset + lyricHeight,
              });
            }
            break;
          }
        }
      });

      // The hyphen is the lyrics' own, set just after its syllable, whatever the gap that follows; one at the staff's
      // end divides a word that goes on in the next.
      for (const unitIndex of placement.hyphenated) {
        const unit = lineUnits[unitIndex]!;
        const next = units.slice(lineRanges[lineIndex]!.first + unitIndex + 1).find(hasLyric);
        const nextId = next ? lyricId(next) : null;
        const lyricX = placement.lyricX[unitIndex];
        const id = lyricId(unit);
        if (lyricX == null || id == null || nextId == null) continue;
        lyrics.push({
          text: "-",
          style: "regular",
          origin: { x: lyricX + unit.lyricWidth, y: lyricY },
          width: hyphenWidth,
          fontSize: lyricFontSize,
          fontName: lyricFontName,
          neumeId: `lyric-hyphen-${id}-${nextId}`,
        });
      }

      const custos = custosPitch(lineIndex, lines);
      if (custos != null) {
        const nextY = staffCenter - (custos - 6) * staffStep;
        // The custos stands flush with the staff's end.
        const custosWidth = GregorianGlyphCatalog.definition("CustosShort").bounds.width * glyphScale;
        const custosX = columnWidth - sidePadding - custosWidth;
        glyphs.push(placedGlyph({ kind: "custos" }, { x: custosX, y: nextY }, glyphScale));
      }
    });

    strokes = grid.snap(mergeLedgerLines(strokes, notationScale));
    const neumes = makeHitFrames(rawNeumes, columnWidth, notationScale);
    return new GregorianLayout(
      { width: columnWidth, height: Math.max(64, contentHeight) },
      staffs,
      glyphs,
      strokes,
      lyrics,
      initials,
      events,
      neumes,
    );
  }

  /**
   * The label printed above a score's initial. For an antiphon, the mode a psalm tone is headed with ("8G"), from its
   * score's mode; null for a mode that names no conclusion, as a hymn's does. For a piece that opens a chant with no
   * psalm tone, a hymn, a responsory or a part of the Mass (`opensPiece`), its mode alone ("8", "4").
   */
  static openingLabel(mode: string | null | undefined, opensPiece = false): string | null {
    const label = psalmToneLabel(mode);
    if (label != null || !opensPiece || mode == null) return label;
    const compact = [...mode.toLowerCase()].filter((character) => !isSpace(character)).join("");
    const prefix = modePrefixes.find((candidate) => compact.startsWith(candidate));
    if (prefix === undefined) return null;
    // A numeral, not the first letters of a word.
    const next = compact[prefix.length];
    if (next !== undefined && letterPattern.test(next)) return null;
    const index = modePrefixes.indexOf(prefix);
    return modePrefixes[index < 8 ? index + 8 : index]!;
  }

  private openingInitial(score: GregorianScore, annotation: string | null, opensPiece: boolean): OpeningInitial | null {
    if (annotation == null && !opensPiece) return null;
    const neume = score.neumes.find((candidate) => candidate.lyric.length > 0);
    if (!neume) return null;
    const characters = graphemes(neume.lyric);
    const initialIndex = characters.findIndex((cluster) => [...cluster].some(isUppercase));
    if (initialIndex < 0) return null;
    const prefixIsSilent = characters
      .slice(0, initialIndex)
      .every((cluster) => [...cluster].every((character) => isSpace(character) || isNumber(character) || character === "."));
    if (!prefixIsSilent) return null;
    // The rest of the word the initial begins is small, however the GABC writes it (PU-ER).
    return {
      text: characters[initialIndex]!,
      annotation,
      remainder: characters
        .slice(initialIndex + 1)
        .join("")
        .toLowerCase(),
      neumeId: neume.id,
    };
  }

  private verticalMetrics(
    units: readonly LayoutUnit[],
    custosPitch: number | null,
    glyphScale: number,
    staffStep: number,
    staffGap: number,
    notationScale: number,
    lyricHeight: number,
    openingInitialFontSize: number | null,
    grid: PixelGrid,
    staffLineWidth: number,
  ): LineVerticalMetrics {
    const notes = units.flatMap((unit) => unitNeume(unit)?.notes ?? []);
    const accidentalPitches = units.flatMap((unit) => (unit.element.kind === "accidental" ? [unit.element.mark.pitch] : []));
    const pitches = [...notes.map((note) => note.pitch), ...accidentalPitches, ...(custosPitch != null ? [custosPitch] : [])];
    const highestPitch = pitches.length > 0 ? Math.max(...pitches) : 9;
    let topInset = Math.max(11 * notationScale, Math.max(0, highestPitch - 9) * staffStep + 10 * notationScale);
    // A custos's stem rises farther above its pitch than a note's head: the staff is set low enough to hold it.
    const custosPitches = [...notes.filter((note) => note.isCustos).map((note) => note.pitch), ...(custosPitch != null ? [custosPitch] : [])];
    if (custosPitches.length > 0) {
      const highestCustos = Math.max(...custosPitches);
      const stem = GregorianGlyphCatalog.definition("CustosShort").origin.y * glyphScale;
      topInset = Math.max(topInset, (highestCustos - 9) * staffStep + stem + 2 * notationScale);
    }
    // A mark that rises higher than the notes leave room for, as an episema set clear of a ledger line may, sets the
    // staff lower still: a staff's band holds all its ink, a point to spare.
    if (units.length > 0) topInset = Math.max(topInset, Math.max(...units.map((unit) => unit.inkAboveStaff)) + notationScale);
    // The words stand as far below the lowest ink as they stand below a note on the bottom line, and no nearer the staff.
    let inkBelow = units.reduce((lowest, unit) => Math.max(lowest, unit.inkBelowStaff), 0);
    if (custosPitch != null) {
      const custos = GregorianGlyphCatalog.definition("CustosShort");
      const y = staffGap * 1.5 - (custosPitch - 6) * staffStep;
      inkBelow = Math.max(inkBelow, y + (custos.bounds.height - custos.origin.y) * glyphScale - staffGap * 3);
    }
    const punctum = GregorianGlyphCatalog.definition("PunctumQuadratum");
    const headBelowLine = (punctum.bounds.height - punctum.origin.y) * glyphScale;
    const lyricOffset = Math.max(2 * notationScale, inkBelow - headBelowLine + 2 * notationScale);
    // On a screen, each staff begins on a whole pixel, and its top line is set where its edges fall between pixels;
    // the others follow it a whole number of pixels apart.
    topInset = grid.centre(topInset, staffLineWidth, true);
    const systemGap = 4 * notationScale;
    const notationHeight = topInset + staffGap * 3 + lyricOffset + lyricHeight + systemGap;
    const initialHeight = openingInitialFontSize != null ? topInset + Math.ceil(openingInitialFontSize * 1.15) + systemGap : 0;
    return { topInset, lyricOffset, height: grid.roundedUp(Math.max(notationHeight, initialHeight)) };
  }

  private makeUnits(
    score: GregorianScore,
    glyphScale: number,
    notationScale: number,
    lyricFontSize: number,
    lyricFontName: string,
    openingInitial: OpeningInitial | null,
  ): LayoutUnit[] {
    // The last lyric written, and the syllable it was sung on: none for a rubric, which ends any word before it.
    let previousLyricText: string | null = null;
    let previousLyricSyllableId: string | null = null;
    const punctum = 100 * glyphScale;
    const staffSpace = 200 * glyphScale;

    const unit = (
      element: GregorianNotationElement,
      fields: {
        readonly inkRight: number;
        readonly inkLeft?: number;
        readonly lyricText?: string;
        readonly lyricWidth?: number;
        readonly lyricFocus?: number;
        readonly join?: LyricJoin;
        readonly forcesBreak?: boolean;
        readonly context?: NeumeContext;
        readonly inkBottom?: number;
        readonly inkTop?: number;
      },
    ): LayoutUnit => ({
      element,
      inkLeft: fields.inkLeft ?? 0,
      inkRight: fields.inkRight,
      lyricText: fields.lyricText ?? "",
      lyricWidth: fields.lyricWidth ?? 0,
      lyricFocus: fields.lyricFocus ?? 0,
      join: fields.join ?? "word",
      forcesBreak: fields.forcesBreak ?? false,
      context: fields.context ?? noContext,
      // Reckoned on a staff whose top line is at 0.
      inkBelowStaff: Math.max(0, (fields.inkBottom ?? 0) - 3 * staffSpace),
      inkAboveStaff: Math.max(0, -(fields.inkTop ?? 0)),
    });

    // A first syllable that the initial takes whole leaves a hyphen under its notes where its word goes on, as
    // Gregorio and Exsurge set it.
    const openingHyphen = (() => {
      if (!openingInitial || openingInitial.remainder.length > 0) return false;
      const neumes = score.neumes;
      const first = neumes.findIndex((neume) => neume.id === openingInitial.neumeId);
      if (first < 0) return false;
      const next = neumes.slice(first + 1).find((neume) => neume.lyric.length > 0);
      if (!next) return false;
      return !next.startsWord && next.syllableId !== neumes[first]!.syllableId;
    })();
    // And so are its other syllables.
    const openingWord = new Set<string>();
    // A verse number written before the initial's syllable is left out: the initial marks the first verse. The others
    // keep theirs.
    const omittedMarks = new Set<number>();
    if (openingInitial) {
      const first = score.elements.findIndex((element) => element.kind === "neume" && element.neume.id === openingInitial.neumeId);
      if (first >= 0) {
        for (const element of score.elements.slice(first + 1)) {
          if (element.kind === "lyricMark") break;
          if (element.kind !== "neume" || element.neume.lyric.length === 0) continue;
          if (element.neume.startsWord) break;
          openingWord.add(element.neume.id);
        }
        score.elements.slice(0, first).forEach((element, index) => {
          if (element.kind === "lyricMark" && verseNumber.test(element.mark.text)) omittedMarks.add(index);
        });
      }
    }

    // Each neume's context: the last note of the neume of its syllable that it follows with no space (`!`), and the
    // next neume's first note.
    const elements = score.elements;
    const contexts: (NeumeContext | null)[] = new Array<NeumeContext | null>(elements.length).fill(null);
    let nextNote: GregorianNote | null = null;
    for (let index = elements.length - 1; index >= 0; index -= 1) {
      const element = elements[index]!;
      if (element.kind !== "neume") continue;
      const neume = element.neume;
      const before = elements[index - 1];
      const previous = before?.kind === "neume" ? before.neume : null;
      const joinedFrom =
        neume.spaceBefore.kind === "joined" && previous?.syllableId === neume.syllableId ? (previous.notes[previous.notes.length - 1] ?? null) : null;
      contexts[index] = { joinedFrom, next: nextNote };
      const first = neume.notes[0];
      if (first && !first.isCustos) nextNote = first;
    }

    return elements.flatMap((element, elementIndex): LayoutUnit[] => {
      switch (element.kind) {
        case "clef":
          return [unit(element, { inkRight: 24 * notationScale })];
        case "accidental": {
          const definition = GregorianGlyphCatalog.definition(catalogName(accidentalKind(element.mark.kind)));
          const y = staffSpace * 1.5 - (element.mark.pitch - 6) * punctum;
          // The accidental, and the space that parts it from its note: about a quarter of a staff space, as Gregorio's.
          return [
            unit(element, {
              inkRight: definition.bounds.width * glyphScale * 0.9 + 0.27 * staffSpace,
              inkBottom: y + (definition.bounds.height - definition.origin.y) * glyphScale * 0.9,
            }),
          ];
        }
        case "division":
          return [unit(element, { inkRight: (element.division === "final" ? 17 : 12) * notationScale })];
        case "forcedBreak":
          return [unit(element, { inkRight: 0, forcesBreak: true })];
        case "lyricMark":
          if (omittedMarks.has(elementIndex)) return [];
          previousLyricText = element.mark.text;
          previousLyricSyllableId = null;
          return [
            unit(element, {
              inkRight: 0,
              lyricText: element.mark.text,
              lyricWidth: this.measureLyric(element.mark.text, element.mark.style, lyricFontSize, lyricFontName),
            }),
          ];
        case "neume": {
          const neume = element.neume;
          const opensScore = openingInitial != null && openingInitial.neumeId === neume.id;
          let lyricText: string;
          if (!opensScore) lyricText = openingWord.has(neume.id) ? neume.lyric.toLowerCase() : neume.lyric;
          else if (openingHyphen) lyricText = "-";
          else lyricText = openingInitial.remainder;
          // The opening initial takes the syllable's first letter.
          const centeredRange = opensScore
            ? neume.centeredRange && neume.centeredRange.lowerBound >= 1
              ? { lowerBound: neume.centeredRange.lowerBound - 1, upperBound: neume.centeredRange.upperBound - 1 }
              : null
            : neume.centeredRange;
          const lyric = this.lyricMetrics(lyricText, neume.lyricStyle, centeredRange, lyricFontSize, lyricFontName);
          const previousSyllable = previousLyricSyllableId;
          let join: LyricJoin = "word";
          if (lyricText.length > 0 && !neume.startsWord && previousSyllable != null && previousSyllable !== neume.syllableId) {
            join = previousLyricText?.endsWith("-") === true || lyricText.startsWith("-") ? "written" : "syllable";
          }
          if (lyricText.length > 0) {
            previousLyricText = lyricText;
            previousLyricSyllableId = neume.syllableId;
          }
          const context = contexts[elementIndex] ?? noContext;
          const ink = this.notationInk(neume, context, glyphScale, notationScale);
          return [
            unit(element, {
              // A neume is placed by its first note's centre, under which its syllable's vowel stands.
              inkLeft: ink.left - punctum / 2,
              inkRight: ink.right - punctum / 2,
              lyricText,
              lyricWidth: lyric.width,
              lyricFocus: lyric.focus,
              join,
              context,
              inkBottom: ink.bottom,
              inkTop: ink.top,
            }),
          ];
        }
      }
    });
  }

  /**
   * A neume's ink, from where its notation begins: its notes, with their dots, episemata and ledger lines; and its
   * lowest and highest, on a staff whose top line is at 0.
   */
  private notationInk(
    neume: GregorianNeume,
    context: NeumeContext,
    glyphScale: number,
    notationScale: number,
  ): { readonly left: number; readonly right: number; readonly bottom: number; readonly top: number } {
    const staffStep = 100 * glyphScale;
    const staffGap = staffStep * 2;
    const composition = this.compose(neume, context, 0, staffGap * 1.5, 0, staffGap * 3, staffStep, glyphScale, notationScale, 0);
    const left = inkLeft(composition);
    const right = inkRight(composition);
    const bottom = Math.max(
      composition.inkFrame?.bottom ?? 0,
      composition.strokes.reduce((lowest, stroke) => Math.max(lowest, Math.max(stroke.start.y, stroke.end.y) + stroke.lineWidth / 2), 0),
    );
    const top = Math.min(
      composition.inkFrame?.top ?? 0,
      composition.strokes.reduce((highest, stroke) => Math.min(highest, Math.min(stroke.start.y, stroke.end.y) - stroke.lineWidth / 2), 0),
    );
    if (!Number.isFinite(left) || !Number.isFinite(right) || right <= left) return { left: 0, right: staffStep, bottom, top };
    return { left, right, bottom, top };
  }

  /**
   * Sets a staff's units in place. Each is set after the notation before it, by the space its syllable and word call
   * for, and moved on as far as its lyric must go to clear the lyric before it: by a word's space, or, within a word,
   * to touch it. A syllable that the notes would part from the one before is joined to it if the note spaces since may
   * close up so far, and is otherwise parted from it by one hyphen. A full staff gives up `condensation` of the share of
   * each note space that it may; a staff that is justified adds `stretch` before each word, bar, rubric and clef after
   * its first, never within a word.
   */
  private place(
    units: readonly LayoutUnit[],
    range: UnitRange,
    frame: LineFrame,
    spacing: Spacing,
    condensation: number,
    endsWithinWord: boolean,
    stretch = 0,
  ): LinePlacement {
    const base = range.first;
    const count = range.last - range.first + 1;
    const result = new LinePlacement(new Array<number>(count).fill(0), new Array<number | null>(count).fill(null), frame.notationStart, frame.staffLeft);
    let previousNotation: number | null = null;
    let previousLyric: number | null = null;
    let lyricRight: number | null = null;
    // The right of the last neume's ink.
    let notesRight: number | null = null;
    // Where the next text may begin: the staff's left, a bar's line, or a clef that changes within the staff.
    let textFloor = frame.staffLeft;
    let crossedDivision = false;
    // The note spaces since the last lyric, each with the share of it still to give up: a syllable may close them to
    // join the one before.
    let run: [number, number][] = [];
    let leadingClefs = true;
    // Whether the next neume's accidental has already stretched, or not, before it.
    let stretchDecided = false;
    const kept = 1 - condensable * condensation;
    const unitAt = (index: number) => units[base + index]!;

    const space = (index: number): number => {
      if (previousNotation == null) return 0;
      const previous = unitAt(previousNotation).element;
      if (previous.kind === "division") return spacing.afterDivision;
      if (previous.kind !== "neume") return 0;
      const previousNeume = previous.neume;
      let next = unitAt(index).element;
      if (next.kind === "accidental" && index + 1 < count) {
        // An accidental takes the space of the neume it governs.
        next = unitAt(index + 1).element;
      }
      switch (next.kind) {
        case "neume": {
          if (next.neume.syllableId !== previousNeume.syllableId) {
            return next.neume.startsWord ? spacing.betweenWords : spacing.betweenSyllables;
          }
          // The neumes of a syllable, as the GABC parts them.
          const written = next.neume.spaceBefore;
          switch (written.kind) {
            // A quilisma is sung from the note before it.
            case "automatic":
              return next.neume.notes[0]?.shape === "quilisma" ? 0 : spacing.withinSyllable;
            case "joined":
              return 0;
            case "slashes":
              return written.count * spacing.withinSyllable;
            case "space":
              return 2 * spacing.withinSyllable;
          }
        }
        // falls through: every space returns
        case "clef":
          return spacing.beforeClef;
        case "accidental":
          return spacing.betweenSyllables;
        default:
          return 0;
      }
    };

    // Whether the staff stretches before this unit: before each word, bar, rubric and clef after its first.
    const stretches = (unit: LayoutUnit): boolean => {
      if (previousNotation == null && previousLyric == null) return false;
      switch (unit.element.kind) {
        case "division":
        case "lyricMark":
        case "clef":
          return true;
        case "neume":
          return hasLyric(unit) && unit.join === "word";
        default:
          return false;
      }
    };

    for (let index = 0; index < count; index += 1) {
      const unit = unitAt(index);
      switch (unit.element.kind) {
        case "forcedBreak":
          continue;
        case "clef":
        case "accidental": {
          if (unit.element.kind === "clef" && leadingClefs) {
            // Drawn at the staff's start, with the clef it sets.
            result.x[index] = frame.staffLeft;
            continue;
          }
          leadingClefs = false;
          const natural = space(index);
          let left = previousNotation == null ? frame.notationStart : result.notationRight + natural * kept;
          left = Math.max(left, frame.notationStart);
          let stretchesHere = stretches(unit);
          if (unit.element.kind === "accidental" && index + 1 < count) {
            // An accidental takes the stretch of the word it begins.
            stretchesHere = stretches(unitAt(index + 1));
            stretchDecided = true;
          }
          if (stretchesHere) {
            left += stretch;
            result.stretchPoints += 1;
          }
          result.x[index] = left;
          result.notationRight = left + unit.inkRight;
          if (unit.element.kind === "clef") textFloor = Math.max(textFloor, left);
          result.spare += natural * condensable;
          run.push([index, natural * condensable * (1 - condensation)]);
          previousNotation = index;
          break;
        }
        case "division": {
          leadingClefs = false;
          // A bar follows its syllable's words as well as its notes.
          let left = Math.max(previousNotation == null ? frame.notationStart : result.notationRight, lyricRight ?? frame.notationStart);
          if (stretches(unit)) {
            left += stretch;
            result.stretchPoints += 1;
          }
          result.x[index] = left;
          result.notationRight = left + unit.inkRight;
          textFloor = Math.max(textFloor, left + unit.inkRight / 2);
          crossedDivision = true;
          run.push([index, 0]);
          previousNotation = index;
          break;
        }
        case "lyricMark": {
          leadingClefs = false;
          // A rubric clears the notes before it. After a bar or a clef it stands where words would, under the room they
          // keep for the notes.
          let left = Math.max(textFloor, notesRight != null ? notesRight + spacing.withinSyllable : frame.staffLeft);
          if (lyricRight != null) left = Math.max(left, lyricRight + spacing.word);
          if (stretches(unit)) {
            left += stretch;
            result.stretchPoints += 1;
          }
          result.x[index] = left;
          result.lyricX[index] = left;
          lyricRight = left + unit.lyricWidth;
          result.notationRight = Math.max(result.notationRight, left + unit.lyricWidth);
          previousLyric = index;
          previousNotation = index;
          crossedDivision = false;
          run = [];
          break;
        }
        case "neume": {
          leadingClefs = false;
          const natural = space(index);
          const spare = natural * condensable * (1 - condensation);
          const start = previousNotation == null ? frame.notationStart : result.notationRight + natural * kept;
          let position = Math.max(start, frame.notationStart) - unit.inkLeft;
          if (hasLyric(unit)) {
            const floor = textFloor + unit.lyricFocus;
            const right = lyricRight;
            const previous = previousLyric;
            if (right != null && previous != null) {
              const solid = right + unit.lyricFocus;
              if (unit.join === "word") {
                position = Math.max(position, Math.max(solid + spacing.word, floor));
              } else if (unit.join === "written") {
                position = Math.max(position, Math.max(solid, floor));
              } else if (crossedDivision) {
                // A bar within a word parts its syllables.
                position = Math.max(position, Math.max(solid + spacing.hyphen, floor));
                result.hyphenated.push(previous);
              } else {
                const opening = position - solid;
                const closable = run.reduce((total, entry) => total + entry[1], spare);
                if (opening <= touching) {
                  position = Math.max(position, solid);
                } else if (opening <= closable + touching) {
                  // Close up the notes since the syllable before, each space by its share, till the two meet.
                  const shift = Math.min(opening, closable);
                  let given = 0;
                  for (const [entryIndex, entrySpare] of run) {
                    given += entrySpare;
                    result.x[entryIndex] = result.x[entryIndex]! - (shift * given) / closable;
                  }
                  position -= shift;
                } else {
                  position = Math.max(position, solid + spacing.hyphen);
                  result.hyphenated.push(previous);
                }
              }
            } else {
              position = Math.max(position, floor);
            }
          }
          if (!stretchDecided && stretches(unit)) {
            position += stretch;
            result.stretchPoints += 1;
          }
          stretchDecided = false;
          result.x[index] = position;
          result.notationRight = position + unit.inkRight;
          notesRight = position + unit.inkRight;
          result.spare += natural * condensable;
          // An accidental stands just before the neume it governs.
          const before = previousNotation;
          if (before != null && unitAt(before).element.kind === "accidental") {
            result.x[before] = position + unit.inkLeft - unitAt(before).inkRight;
          }
          if (hasLyric(unit)) {
            const left = position - unit.lyricFocus;
            result.lyricX[index] = left;
            lyricRight = left + unit.lyricWidth;
            previousLyric = index;
            crossedDivision = false;
            run = [];
          } else {
            run.push([index, spare]);
          }
          previousNotation = index;
          break;
        }
      }
    }

    // A bar between two neumes stands midway between their notes, though not over the words before it.
    for (let index = 0; index < count; index += 1) {
      if (unitAt(index).element.kind !== "division" || index === 0 || index + 1 >= count) continue;
      const before = unitAt(index - 1);
      const after = unitAt(index + 1);
      if (!unitNeume(before) || !unitNeume(after)) continue;
      const width = unitAt(index).inkRight;
      const middle = (result.x[index - 1]! + before.inkRight + result.x[index + 1]! + after.inkLeft) / 2;
      const wordsEnd = hasLyric(before) ? (result.lyricX[index - 1] ?? 0) + before.lyricWidth : -Number.MAX_VALUE;
      result.x[index] = Math.max(middle, wordsEnd) - width / 2;
    }

    // A word that goes on in the next staff ends this one with a hyphen.
    const last = previousLyric;
    const right = lyricRight;
    if (endsWithinWord && last != null && right != null && unitNeume(unitAt(last)) != null) {
      result.hyphenated.push(last);
      lyricRight = right + spacing.hyphen;
    }
    result.textRight = lyricRight ?? frame.staffLeft;
    return result;
  }

  /**
   * Sets a staff in place: at its natural spacing, closed up by as little as fits it when it is too full, and justified
   * when it is not. The last staff of a paragraph, before a forced break or the score's end, is justified only to
   * bring a closing bar to the staff's end, and then only by a little.
   */
  private placeLine(
    units: readonly LayoutUnit[],
    range: UnitRange,
    frame: LineFrame,
    spacing: Spacing,
    endsWithinWord: boolean,
    endsParagraph: boolean,
  ): LinePlacement {
    const placed = (condensation: number, stretch = 0) => this.place(units, range, frame, spacing, condensation, endsWithinWord, stretch);
    const natural = placed(0);
    if (!natural.fits(frame)) {
      let best = placed(1);
      if (!best.fits(frame)) return best;
      let low = 0;
      let high = 1;
      for (let attemptCount = 0; attemptCount < 10; attemptCount += 1) {
        const middle = (low + high) / 2;
        const attempt = placed(middle);
        if (attempt.fits(frame)) {
          high = middle;
          best = attempt;
        } else {
          low = middle;
        }
      }
      return best;
    }
    const slack = natural.slack(frame);
    if (slack <= 0.5 || natural.stretchPoints === 0) return natural;
    const stretch = slack / natural.stretchPoints;
    if (endsParagraph) {
      let endsWithDivision = false;
      for (let index = range.last; index >= range.first; index -= 1) {
        if (units[index]!.forcesBreak) continue;
        endsWithDivision = units[index]!.element.kind === "division";
        break;
      }
      if (!endsWithDivision || stretch > spacing.maximumStretch) return natural;
    }
    return placed(0, stretch);
  }

  /**
   * Breaks the units into staffs, a paragraph at a time between forced breaks, choosing the breaks that best serve the
   * whole paragraph, as TeX and Gregorio choose them: each staff is reckoned by how far it must be stretched or closed up
   * and by what its break costs, and a last staff that holds a lone syllable is avoided.
   */
  private breakLines(
    units: readonly LayoutUnit[],
    frame: (lineIndex: number) => LineFrame,
    spacing: Spacing,
    wordContinues: readonly boolean[],
  ): UnitRange[] {
    if (units.length === 0) return [{ first: 0, last: -1 }];
    const penalties = breakPenalties(units);
    const lines: UnitRange[] = [];
    let start = 0;
    while (start < units.length) {
      let end = units.length;
      for (let index = start; index < units.length; index += 1) {
        if (units[index]!.forcesBreak) {
          end = index + 1;
          break;
        }
      }
      lines.push(
        ...this.breakParagraph(
          units,
          { first: start, last: end - 1 },
          (lineStart) => frame(lineStart === 0 ? 0 : 1),
          spacing,
          wordContinues,
          penalties,
        ),
      );
      start = end;
    }
    return lines;
  }

  private breakParagraph(
    units: readonly LayoutUnit[],
    paragraph: UnitRange,
    frame: (lineStart: number) => LineFrame,
    spacing: Spacing,
    wordContinues: readonly boolean[],
    penalties: readonly (number | null)[],
  ): UnitRange[] {
    const lower = paragraph.first;
    const upper = paragraph.last + 1;
    const candidates = [lower];
    for (let index = lower + 1; index < upper; index += 1) if (penalties[index] != null) candidates.push(index);
    candidates.push(upper);
    const cost = new Array<number>(candidates.length).fill(Number.POSITIVE_INFINITY);
    const previous = new Array<number>(candidates.length).fill(-1);
    cost[0] = 0;

    for (let end = 1; end < candidates.length; end += 1) {
      const b = candidates[end]!;
      const endsParagraph = b === upper;
      const penalty = endsParagraph ? 0 : (penalties[b] ?? 0);
      for (let begin = end - 1; begin >= 0; begin -= 1) {
        if (!Number.isFinite(cost[begin]!)) continue;
        const a = candidates[begin]!;
        const lineFrame = frame(a);
        const range = { first: a, last: b - 1 };
        const natural = this.place(units, range, lineFrame, spacing, 0, wordContinues[b]!);
        let badness: number;
        if (natural.fits(lineFrame)) {
          if (endsParagraph) {
            badness = 0;
          } else {
            // A staff with nowhere to stretch, a word alone, is reckoned as if stretched in one place.
            const stretch = natural.slack(lineFrame) / Math.max(1, natural.stretchPoints);
            badness = 100 * Math.pow(stretch / spacing.maximumStretch, 3);
          }
        } else {
          const condensed = this.place(units, range, lineFrame, spacing, 1, wordContinues[b]!);
          if (condensed.fits(lineFrame)) {
            const overflow = -natural.slack(lineFrame);
            const share = natural.spare > 0 ? Math.min(1, overflow / natural.spare) : 1;
            badness = 100 * Math.pow(share, 3);
          } else if (begin === end - 1 && previous[end]! < 0) {
            // Nothing shorter fits: the staff is overfull, and worse than any that fits.
            badness = 1e9;
          } else {
            // A longer staff will not fit either.
            break;
          }
        }
        let demerits = Math.pow(10 + badness, 2) + (penalty < 0 ? -penalty * penalty : penalty * penalty);
        if (endsParagraph && begin > 0) {
          let syllables = 0;
          for (let index = a; index < b; index += 1) if (hasLyric(units[index]!)) syllables += 1;
          // A last staff of one syllable is worse than a loose staff before it.
          if (syllables <= 1) demerits += 1_000_000;
        }
        if (cost[begin]! + demerits < cost[end]!) {
          cost[end] = cost[begin]! + demerits;
          previous[end] = begin;
        }
      }
    }

    // Every end has a way back: the staff from the candidate before it either fits or is taken overfull.
    const breaks: number[] = [];
    let index = candidates.length - 1;
    while (index > 0 && previous[index]! >= 0) {
      breaks.push(candidates[index]!);
      index = previous[index]!;
    }
    const lines: UnitRange[] = [];
    let lineStart = lower;
    for (const lineEnd of breaks.reverse()) {
      lines.push({ first: lineStart, last: lineEnd - 1 });
      lineStart = lineEnd;
    }
    return lines.length > 0 ? lines : [paragraph];
  }

  /**
   * Engraves a neume as Exsurge builds one, note by note from the left. Each note is set where the last ended, joined to
   * it by a stem where the two stand more than a step apart. A clivis and a porrectus hang from a stem on their left; a
   * virga's stem stands inside its head's right edge; a pes sets its upper note over the lower, joined on the right; a
   * porrectus sets its last note over the end of its oblique; and the diamonds of a climacus step down from one another
   * unjoined.
   */
  private compose(
    neume: GregorianNeume,
    context: NeumeContext,
    originX: number,
    staffCenter: number,
    staffTop: number,
    staffBottom: number,
    staffStep: number,
    glyphScale: number,
    notationScale: number,
    lineIndex: number,
  ): Composition {
    const result = new Composition();
    const notes = neume.notes;
    const punctumWidth = 100 * glyphScale;
    // Exsurge's space between the parts of a neume: a quarter staff space.
    const intraNeumeSpacing = staffStep / 2;
    // The weight of the stems that bind a neume's notes.
    const stemWidth = Math.max(1, 18 * glyphScale);

    const noteY = (pitch: number) => staffCenter - (pitch - 6) * staffStep;
    const width = (name: GregorianGlyphName) => GregorianGlyphCatalog.definition(name).bounds.width * glyphScale;

    // Each note's glyph, the left of its first head, and the advance from one head to the next of a note written twice
    // or thrice.
    const glyphs: GregorianGlyphName[] = notes.map(() => "PunctumQuadratum");
    const lefts: number[] = notes.map(() => originX);
    const advances: number[] = notes.map(() => 0);
    // The notes drawn with a virga's stem.
    const virgae = new Set<number>();

    // The builder's place, and the note the next may be joined to.
    let x = originX;
    let lastPitch: number | null = null;
    let lastAlignsRight = false;
    let lineIsHanging = false;
    let minX = originX;

    // Exsurge's neume line: from the upper note's centre to the lower's, and on past the lower for a stem that hangs, a
    // clivis's or a porrectus's.
    const stem = (stemX: number, pitchA: number, pitchB: number, hanging: boolean, eventId: string) => {
      const upper = Math.max(pitchA, pitchB) - 6;
      let lower = Math.min(pitchA, pitchB) - 6;
      let overhang = 0;
      if (hanging) {
        if (upper - lower > 4) lower = upper - 4;
        // From a note on a line a step above the next, within the staff, the stem reaches a step further.
        if (upper - lower === 1 && Math.abs(upper) % 2 === 1 && lower > -3) lower -= 1;
        overhang = (GregorianGlyphCatalog.definition("PunctumQuadratum").bounds.height * glyphScale) / 2.2;
      }
      result.strokes.push({
        kind: connectorStroke,
        start: { x: stemX + stemWidth / 2, y: noteY(upper + 6) },
        end: { x: stemX + stemWidth / 2, y: noteY(lower + 6) + overhang },
        lineWidth: stemWidth,
        eventId,
      });
    };

    const noteAt = (index: number, glyph: GregorianGlyphName, withLine = true) => {
      const note = notes[index]!;
      const alignsRight = GregorianGlyphCatalog.definition(glyph).alignment === "right";
      const previous = lastPitch;
      if (withLine && previous != null && (lineIsHanging || lastAlignsRight || Math.abs(previous - note.pitch) > 1)) {
        const stemX = Math.max(minX, x - stemWidth);
        stem(stemX, previous, note.pitch, lineIsHanging, note.id);
        if (!alignsRight) x = stemX;
      }
      glyphs[index] = glyph;
      if (alignsRight && previous != null) {
        lefts[index] = x - width(glyph);
      } else {
        lefts[index] = x;
        // A note written twice or thrice (`hss`) sets a head for each, a quarter staff space apart, an eighth after a
        // stropha.
        advances[index] = width(glyph) + (note.shape === "stropha" ? intraNeumeSpacing / 2 : intraNeumeSpacing);
        x += width(glyph) + (note.repetitionCount - 1) * advances[index]!;
      }
      lastPitch = note.pitch;
      lastAlignsRight = alignsRight;
      lineIsHanging = false;
    };

    const virgaAt = (index: number) => {
      noteAt(index, "PunctumQuadratum");
      // The stem stands inside the head's right edge; a virga written twice or thrice (`hvv`) sets each head a quarter
      // staff space after the last one's stem.
      advances[index] = punctumWidth - stemWidth + intraNeumeSpacing;
      x = lefts[index]! + (notes[index]!.repetitionCount - 1) * advances[index]! + punctumWidth - stemWidth;
      virgae.add(index);
      lastPitch = notes[index]!.pitch;
      lastAlignsRight = false;
      lineIsHanging = false;
    };

    const advance = (distance: number) => {
      lastPitch = null;
      lineIsHanging = false;
      x += distance;
    };

    // Starts a stem to hang on the left of the next note, down to `lower`, or joins the next note to the neume the GABC
    // joins this one to.
    const lineFrom = (lower: number) => {
      const joined = context.joinedFrom;
      if (x === originX && joined) {
        lastPitch = joined.pitch;
        lastAlignsRight = false;
        minX = originX - stemWidth;
      } else {
        lastPitch = notes[lower]!.pitch;
        lastAlignsRight = false;
        lineIsHanging = true;
      }
    };

    // Ends a stem from the last note, inside its right edge, down to `index`'s.
    const lineEnding = (index: number) => {
      const previous = lastPitch;
      if (previous == null) return;
      x -= stemWidth;
      stem(x, previous, notes[index]!.pitch, true, notes[index]!.id);
      lastPitch = notes[index]!.pitch;
      lastAlignsRight = false;
    };

    const podatus = (lower: number, upper: number) => {
      let lowerGlyph: GregorianGlyphName;
      let upperGlyph: GregorianGlyphName;
      switch (notes[upper]!.liquescence) {
        case "small":
          lowerGlyph = "BeginningAscLiquescent";
          upperGlyph = "TerminatingAscLiquescent";
          break;
        case "ascending":
          lowerGlyph = "PunctumQuadratum";
          upperGlyph = "PunctumQuadratumAscLiquescent";
          break;
        case "descending":
          lowerGlyph = "PunctumQuadratum";
          upperGlyph = "PunctumQuadratumDesLiquescent";
          break;
        case "none": {
          const wide = notes[upper]!.pitch - notes[lower]!.pitch > 1;
          lowerGlyph = wide ? "PodatusLower" : "PodatusLowerShort";
          upperGlyph = wide ? "PodatusUpper" : "PodatusUpperShort";
          break;
        }
      }
      if (notes[lower]!.shape === "quilisma") lowerGlyph = "Quilisma";
      noteAt(lower, lowerGlyph);
      noteAt(upper, upperGlyph);
      lastPitch = null;
    };

    const clivisUpper = (upper: number, lower: number | null, glyph: GregorianGlyphName = "PunctumQuadratum") => {
      if (notes[upper]!.shape === "oriscus") {
        noteAt(upper, "OriscusDes", false);
        return;
      }
      let chosen = glyph;
      if (lower != null) {
        lineFrom(lower);
        lineIsHanging = notes[lower]!.pitch < notes[upper]!.pitch;
        if (notes[lower]!.liquescence === "small") chosen = "BeginningDesLiquescent";
      }
      noteAt(upper, chosen);
    };

    // The glyph of a note that ends a descent: a clivis's lower note, a torculus's last.
    const descentEnd = (index: number): GregorianGlyphName => {
      switch (notes[index]!.liquescence) {
        case "small":
          return "TerminatingDesLiquescent";
        case "ascending":
          return "PunctumQuadratumAscLiquescent";
        case "descending":
          return "PunctumQuadratumDesLiquescent";
        case "none":
          return "PunctumQuadratum";
      }
    };

    const clivis = (upper: number, lower: number) => {
      clivisUpper(upper, lower);
      noteAt(lower, descentEnd(lower));
      lastPitch = null;
    };

    const inclinata = (start: number) => {
      // Each diamond steps from the last by its interval, as Exsurge steps them: the advance of a diamond and a tenth
      // for a unison, two thirds of one for each step.
      const step = width("PunctumInclinatum");
      for (let index = start; index < notes.length; index += 1) {
        if (index > start) {
          const steps = Math.abs(notes[index - 1]!.pitch - notes[index]!.pitch);
          x += step * (steps === 0 ? 1.1 : (steps * 2) / 3);
        }
        glyphs[index] = notes[index]!.liquescence === "small" ? "PunctumInclinatumLiquescent" : "PunctumInclinatum";
        lefts[index] = x;
      }
      lastPitch = null;
    };

    const porrectusSwash = (start: number, end: number) => {
      const previous = lastPitch;
      if (previous != null && (lineIsHanging || lastAlignsRight || Math.abs(previous - notes[start]!.pitch) > 1)) {
        x = Math.max(minX, x - stemWidth);
        stem(x, previous, notes[start]!.pitch, lineIsHanging, notes[start]!.id);
      }
      const interval = Math.max(1, Math.min(4, notes[start]!.pitch - notes[end]!.pitch));
      const swash: GregorianGlyphName = interval === 1 ? "Porrectus1" : interval === 2 ? "Porrectus2" : interval === 3 ? "Porrectus3" : "Porrectus4";
      glyphs[start] = swash;
      lefts[start] = x;
      x += width(swash);
      // The swash engraves the second note too, at its right end.
      glyphs[end] = "None";
      lefts[end] = x - punctumWidth;
      lastPitch = notes[end]!.pitch;
      lastAlignsRight = false;
      lineIsHanging = false;
    };

    // A lone note's glyph, as Exsurge draws a punctum.
    const punctumGlyph = (note: GregorianNote): GregorianGlyphName => {
      if (note.liquescence !== "none") {
        if (note.shape === "inclinatum") return "PunctumInclinatumLiquescent";
        if (note.shape === "oriscus") return "OriscusLiquescent";
        if (note.liquescence === "ascending") return "PunctumQuadratumAscLiquescent";
        if (note.liquescence === "descending") return "PunctumQuadratumDesLiquescent";
        return "PunctumQuadratumLiquescent";
      }
      if (note.isCavum) return "PunctumCavum";
      switch (note.shape) {
        case "inclinatum":
          return "PunctumInclinatum";
        case "quilisma":
          return "Quilisma";
        case "oriscus":
          return "OriscusDes";
        case "stropha":
          return "Stropha";
        default:
          return "PunctumQuadratum";
      }
    };

    // A note of a stropha's form: a stropha only where the GABC writes one (`s`); a plain note repeated is a punctum.
    const strophaGlyph = (note: GregorianNote): GregorianGlyphName => {
      if (note.shape === "stropha") return "Stropha";
      if (note.liquescence === "ascending") return "PunctumQuadratumAscLiquescent";
      if (note.liquescence === "descending") return "PunctumQuadratumDesLiquescent";
      return note.isCavum ? "PunctumCavum" : "PunctumQuadratum";
    };

    const plainOrQuilisma = (index: number): GregorianGlyphName => (notes[index]!.shape === "quilisma" ? "Quilisma" : "PunctumQuadratum");

    const head = (index: number) => {
      if (notes[index]!.shape === "virga") virgaAt(index);
      else noteAt(index, plainOrQuilisma(index));
    };

    // Where the diamonds begin, if all the notes from there are diamonds.
    let inclinataStart: number | null = null;
    for (let start = 0; start < notes.length; start += 1) {
      if (notes.slice(start).every((note) => note.shape === "inclinatum")) {
        inclinataStart = start;
        break;
      }
    }
    const form = neume.form;
    const count = notes.length;
    if (form === "punctaInclinata") {
      inclinata(0);
    } else if ((form === "climacus" || form === "ancus") && inclinataStart === 1) {
      head(0);
      advance(intraNeumeSpacing);
      inclinata(1);
    } else if (form === "pesSubpunctis" && inclinataStart === 2) {
      podatus(0, 1);
      advance(intraNeumeSpacing * 0.68);
      inclinata(2);
    } else if (form === "virga" || form === "bivirga" || form === "trivirga") {
      for (let index = 0; index < count; index += 1) {
        if (index > 0) advance(intraNeumeSpacing);
        virgaAt(index);
      }
    } else if (form === "apostropha" || form === "distropha" || form === "tristropha") {
      const strophae = notes.slice(0, 2).filter((note) => note.shape === "stropha").length;
      const gap =
        form === "tristropha"
          ? notes[0]!.shape === "stropha"
            ? intraNeumeSpacing / 2
            : intraNeumeSpacing
          : intraNeumeSpacing - (strophae * intraNeumeSpacing) / 4;
      for (let index = 0; index < count; index += 1) {
        if (index > 0) advance(gap);
        noteAt(index, strophaGlyph(notes[index]!));
      }
    } else if (form === "oriscus" && count === 1) {
      const rises = context.next != null && context.next.pitch > notes[0]!.pitch;
      noteAt(0, notes[0]!.liquescence !== "none" ? "OriscusLiquescent" : rises ? "OriscusAsc" : "OriscusDes");
    } else if (form === "clivis" && count === 2) {
      clivis(0, 1);
    } else if (form === "ancus" && count === 3) {
      clivisUpper(0, 1);
      const middleGlyph: GregorianGlyphName = notes[2]!.liquescence === "small" ? "BeginningDesLiquescent" : "PunctumQuadratum";
      clivisUpper(1, notes[0]!.pitch - notes[1]!.pitch > 1 ? 0 : null, middleGlyph);
      noteAt(2, descentEnd(2));
      lastPitch = null;
    } else if (form === "podatus" && count === 2) {
      podatus(0, 1);
    } else if (form === "pesQuassus" && count === 2) {
      noteAt(0, notes[0]!.shape === "oriscus" ? "OriscusAsc" : "PunctumQuadratum");
      if (notes[1]!.pitch - notes[0]!.pitch === 1) {
        virgaAt(1);
      } else {
        noteAt(1, notes[1]!.liquescence === "descending" ? "PunctumQuadratumDesLiquescent" : "PunctumQuadratum");
        lineEnding(0);
      }
    } else if (form === "porrectus" && count === 3) {
      lineFrom(1);
      porrectusSwash(0, 1);
      noteAt(
        2,
        notes[2]!.liquescence === "small"
          ? "TerminatingAscLiquescent"
          : notes[2]!.liquescence === "descending"
            ? "PunctumQuadratumDesLiquescent"
            : notes[2]!.pitch - notes[1]!.pitch > 1
              ? "PodatusUpper"
              : "PodatusUpperShort",
      );
    } else if (form === "porrectusFlexus" && count === 4) {
      lineFrom(1);
      porrectusSwash(0, 1);
      noteAt(2, notes[3]!.liquescence === "small" ? "PunctumQuadratumDesLiquescent" : "PunctumQuadratum");
      noteAt(3, descentEnd(3));
    } else if (form === "torculus" && count === 3) {
      noteAt(0, plainOrQuilisma(0));
      noteAt(1, "PunctumQuadratum");
      noteAt(2, descentEnd(2));
    } else if (form === "torculusResupinus" && count === 4) {
      noteAt(0, plainOrQuilisma(0));
      porrectusSwash(1, 2);
      noteAt(
        3,
        notes[3]!.liquescence === "small"
          ? "TerminatingAscLiquescent"
          : notes[2]!.liquescence === "descending"
            ? "PunctumQuadratumDesLiquescent"
            : "PodatusUpper",
      );
    } else if (form === "torculusResupinusFlexus" && count === 5) {
      noteAt(0, plainOrQuilisma(0));
      porrectusSwash(1, 2);
      noteAt(3, notes[4]!.liquescence === "small" ? "PunctumQuadratumDesLiquescent" : "PunctumQuadratum");
      noteAt(4, descentEnd(4));
    } else if (form === "scandicus" && count === 3) {
      if (notes[2]!.shape === "virga") {
        podatus(0, 1);
        virgaAt(2);
      } else {
        noteAt(0, plainOrQuilisma(0));
        podatus(1, 2);
      }
    } else if (form === "scandicusFlexus" && count === 4) {
      if (notes[2]!.shape === "virga") {
        podatus(0, 1);
        advance(intraNeumeSpacing);
        clivis(2, 3);
      } else {
        noteAt(0, "PunctumQuadratum");
        podatus(1, 2);
        advance(intraNeumeSpacing);
        noteAt(
          3,
          notes[3]!.liquescence === "ascending"
            ? "PunctumQuadratumAscLiquescent"
            : notes[3]!.liquescence === "descending"
              ? "PunctumQuadratumDesLiquescent"
              : "PunctumQuadratum",
        );
      }
    } else if (form === "salicus" && count === 3) {
      noteAt(0, "PunctumQuadratum");
      advance(intraNeumeSpacing);
      noteAt(1, "OriscusAsc");
      switch (notes[2]!.liquescence) {
        case "small":
          noteAt(2, "TerminatingAscLiquescent");
          break;
        case "ascending":
          noteAt(2, "PunctumQuadratumAscLiquescent");
          break;
        case "descending":
          noteAt(2, "PunctumQuadratumDesLiquescent");
          break;
        case "none":
          virgaAt(2);
          break;
      }
    } else if (form === "salicusFlexus" && count === 4) {
      noteAt(0, "PunctumQuadratum");
      advance(intraNeumeSpacing);
      noteAt(1, "OriscusAsc");
      noteAt(2, notes[3]!.liquescence === "small" ? "PunctumQuadratumDesLiquescent" : "PunctumQuadratum");
      noteAt(3, descentEnd(3));
    } else {
      // A note alone, or a run the forms above do not name: its notes one after another, joined where they leap.
      for (let index = 0; index < count; index += 1) {
        if (notes[index]!.shape === "virga") virgaAt(index);
        else noteAt(index, punctumGlyph(notes[index]!));
      }
    }

    // The head each note's signs are set by, the last of a note written twice or thrice; null for a custos.
    const signFrames: (GregorianRect | null)[] = notes.map(() => null);
    notes.forEach((note, index) => {
      const y = noteY(note.pitch);
      const glyph = glyphs[index]!;
      // Where the note's head is, whatever glyph engraves it.
      const headFrame = GregorianRect.of(lefts[index]!, y - punctumWidth / 2, punctumWidth, punctumWidth);
      if (note.isCustos) {
        // Drawn as the custos that ends a staff is drawn: without signs, and not as a note that the cantor guide may
        // light.
        const custos = placedGlyph(
          { kind: "custos" },
          { x: lefts[index]! + GregorianGlyphCatalog.definition("CustosShort").origin.x * glyphScale, y },
          glyphScale,
        );
        result.glyphs.push(custos);
        appendEvent(note, neume, custos.frame, lineIndex, result);
        return;
      }
      if (glyph === "None") {
        // The second note of a porrectus, which its swash engraves.
        signFrames[index] = headFrame;
        appendLedgerLines(headFrame, y, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes);
        appendEvent(note, neume, headFrame, lineIndex, result);
        return;
      }
      const isSwash = swashes.has(glyph);

      const definition = GregorianGlyphCatalog.definition(glyph);
      const first = placedGlyph(catalogGlyph(glyph), { x: lefts[index]! + definition.origin.x * glyphScale, y }, glyphScale, note.id);
      // The same glyph again for each further time the note is written.
      const heads: GregorianPlacedGlyph[] = [first];
      for (let repetition = 1; repetition < Math.max(1, note.repetitionCount); repetition += 1) {
        heads.push({ kind: first.kind, frame: first.frame.translate(repetition * advances[index]!, 0), eventId: note.id });
      }
      // Signs written after a repeated note belong to the last of them.
      const placed = heads[heads.length - 1]!;
      let noteFrame = heads.reduce((frame, each) => frame.union(each.frame), first.frame);
      if (virgae.has(index)) {
        // A virga's stem falls nearly two steps from a note in a space, nearly three from one on a line.
        const length = (Math.abs(note.pitch - 6) % 2 === 0 ? 1.8 : 2.7) * staffStep;
        for (const each of heads) {
          const stemX = each.frame.left + punctumWidth - stemWidth / 2;
          result.strokes.push({
            kind: connectorStroke,
            start: { x: stemX, y },
            end: { x: stemX, y: y + length },
            lineWidth: stemWidth,
            eventId: note.id,
          });
          noteFrame = noteFrame.union(GregorianRect.of(stemX - stemWidth / 2, y, stemWidth, length));
        }
      }

      if (note.accidental) {
        const kind = accidentalKind(note.accidental);
        const accidentalScale = glyphScale * 0.9;
        const accidentalDefinition = GregorianGlyphCatalog.definition(catalogName(kind));
        const accidentalMinX = first.frame.left - accidentalDefinition.bounds.width * accidentalScale - 2 * notationScale;
        const accidentalGlyph = placedGlyph(
          kind,
          { x: accidentalMinX + accidentalDefinition.origin.x * accidentalScale, y },
          accidentalScale,
          note.id,
        );
        result.glyphs.push(accidentalGlyph);
        result.ink(accidentalGlyph.frame);
      }

      result.glyphs.push(...heads);
      result.ink(noteFrame);
      signFrames[index] = placed.frame;

      // A swash's first head; any other glyph's whole frame.
      appendLedgerLines(isSwash ? headFrame : noteFrame, y, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes);
      appendEvent(note, neume, noteFrame, lineIndex, result);
    });

    this.appendSigns(neume, signFrames, glyphs, staffCenter, staffStep, glyphScale, notationScale, result);
    return result;
  }

  /**
   * Sets a neume's dots, episemata and ictus as Exsurge places them, the dot at the Liber's size: on the side the GABC
   * writes, or else on the side the neume gives each note. A dot stands just after its note, in its space, or in the
   * space above a note on a line; a double dot after a neume's last two notes gives each its own. An episema keeps
   * clear of its note and of the staff's lines, and runs on over the next note's on the same side. An ictus stands a
   * step beyond its note, off the line there.
   */
  private appendSigns(
    neume: GregorianNeume,
    frames: readonly (GregorianRect | null)[],
    glyphs: readonly GregorianGlyphName[],
    staffCenter: number,
    staffStep: number,
    glyphScale: number,
    notationScale: number,
    result: Composition,
  ): void {
    const notes = neume.notes;
    const noteY = (pitch: number) => staffCenter - (pitch - 6) * staffStep;
    const onLine = (note: GregorianNote) => Math.abs(note.pitch - 6) % 2 === 1;
    const form = neume.form;

    // Dots. A double dot on a neume's last note dots the note before it too, set after the last.
    const dots = notes.map((note) => note.moraCount);
    const dotAfterNext = notes.map(() => false);
    const last = notes.length - 1;
    if (last > 0 && notes[last]!.moraCount > 1) {
      dots[last] = dots[last]! - 1;
      dots[last - 1] = dots[last - 1]! + 1;
      dotAfterNext[last - 1] = true;
    }
    const dotSides: (GregorianEpisemaPosition | null)[] = notes.map((note) => note.moraPosition);
    const lowerDot = (index: number) => {
      if (dotSides[index] == null && dots[index]! > 0) dotSides[index] = "below";
    };
    if (form === "podatus" || form === "pesQuassus" || form === "pesSubpunctis") {
      // The lower note of a pes, on a line, has its dot below it.
      if (notes.length > 1 && onLine(notes[0]!)) lowerDot(0);
    } else if (form === "clivis" || form === "torculus" || form === "porrectus") {
      // A clivis's lower note, on a line a step down, has its dot below it.
      const upper = form === "clivis" ? 0 : 1;
      const lower = upper + 1;
      if (notes.length > lower && notes[upper]!.pitch - notes[lower]!.pitch === 1 && onLine(notes[lower]!)) lowerDot(lower);
    }
    if ((form === "climacus" || form === "punctaInclinata" || form === "pesSubpunctis") && notes.length > 1) {
      // So has the last diamond, on a line a step below the one before.
      if (notes[last - 1]!.pitch - notes[last]!.pitch === 1 && onLine(notes[last]!)) lowerDot(last);
    }
    const dotDefinition = GregorianGlyphCatalog.definition("Mora");
    // The Liber's dot: a third of a staff space across, a tenth of one after its note.
    const dotScale = (0.33 * staffStep * 2) / dotDefinition.bounds.width;
    const dotWidth = dotDefinition.bounds.width * dotScale;
    const dotGap = 0.1 * staffStep * 2;
    notes.forEach((note, index) => {
      if (dots[index]! <= 0) return;
      const frame = frames[index];
      if (!frame) return;
      let y = noteY(note.pitch);
      const dotSide = dotSides[index];
      if (dotSide === "above") y -= (onLine(note) ? 0.75 : 1.75) * staffStep;
      else if (dotSide === "below") y += (onLine(note) ? 0.75 : 1.75) * staffStep;
      else if (onLine(note)) y -= 0.75 * staffStep;
      else if (index + 1 < notes.length && notes[index + 1]!.pitch === note.pitch - 1) y -= 0.25 * staffStep;
      let left = frame.right + dotGap;
      const next = frames[index + 1];
      if (next) {
        if (dotAfterNext[index]) {
          left = next.right + dotGap;
        } else if (next.right > frame.right) {
          // Between the note and the end of the next.
          left = frame.right + (next.right - frame.right - dotWidth) / 2;
        }
      }
      for (let dot = 0; dot < dots[index]!; dot += 1) {
        const mora = placedGlyph(
          { kind: "mora" },
          { x: left + dot * (dotWidth + dotGap) + dotDefinition.origin.x * dotScale, y },
          dotScale,
          note.id,
        );
        result.glyphs.push(mora);
        result.ink(mora.frame);
      }
    });

    // Episemata, on the side the neume gives each note unless the GABC writes one.
    const episemaSides: (GregorianEpisemaPosition | null)[] = notes.map((note) => note.episemaPosition);
    const side = (index: number, position: GregorianEpisemaPosition) => {
      if (index < notes.length && episemaSides[index] == null) episemaSides[index] = position;
    };
    const clivisSides = (upper: number, lower: number): boolean => {
      side(upper, "above");
      const hasTop = upper < notes.length && notes[upper]!.hasEpisema;
      side(lower, hasTop ? "above" : "below");
      return hasTop;
    };
    const podatusSides = (lower: number, upper: number) => {
      side(lower, "below");
      side(upper, "above");
    };
    switch (form) {
      case "clivis":
        clivisSides(0, 1);
        break;
      case "ancus":
        clivisSides(0, 2);
        clivisSides(1, 2);
        break;
      case "podatus":
      case "pesQuassus":
      case "pesSubpunctis":
        podatusSides(0, 1);
        break;
      case "porrectus":
        clivisSides(0, 1);
        podatusSides(1, 2);
        break;
      case "porrectusFlexus": {
        side(0, "above");
        const hasTop = clivisSides(2, 3) || notes[0]!.hasEpisema;
        side(1, hasTop ? "above" : "below");
        break;
      }
      case "torculus": {
        const hasTop = clivisSides(1, 2);
        side(0, hasTop ? "above" : "below");
        break;
      }
      case "torculusResupinus":
      case "torculusResupinusFlexus":
        clivisSides(1, 2);
        podatusSides(2, 3);
        clivisSides(1, 0);
        break;
      case "scandicus":
      case "scandicusFlexus":
        if (form === "scandicus" && notes.length > 2 && notes[2]!.shape === "virga") {
          podatusSides(0, 1);
        } else {
          side(0, "below");
          podatusSides(1, 2);
        }
        break;
      case "salicus":
      case "salicusFlexus":
        notes.forEach((_, index) => side(index, "below"));
        break;
      default:
        break;
    }
    const episemaWeight = Math.max(1, 1.3 * notationScale);
    const staffLines = 4;
    interface EpisemaRun {
      readonly side: GregorianEpisemaPosition;
      y: number;
      readonly left: number;
      right: number;
      readonly eventId: string;
    }
    const runs: EpisemaRun[] = [];
    notes.forEach((note, index) => {
      if (!note.hasEpisema) return;
      const frame = frames[index];
      if (!frame) return;
      const position = episemaSides[index] ?? "above";
      // Exsurge's place: a quarter step clear of the note, centred between it and the next line if there is room, else
      // in the nearest third of a space off the line.
      const clearance = staffStep * 0.25;
      let step: number;
      if (position === "below") {
        let y = frame.bottom + clearance - staffCenter;
        if (glyphs[index] === "None") y += staffStep / 2;
        step = Math.ceil(y / staffStep);
        if (Math.abs(step % 2) === 0) {
          step = (step + 3 / 4 + (y - clearance) / staffStep) / 2;
        } else {
          step = (Math.ceil((1.5 * y) / staffStep - 0.5) * 2 + 1) / 3;
          if (Math.abs(step) % 2 === 1) step += Math.abs(step) < staffLines ? 2 / 3 : 1 / 3;
        }
      } else {
        const y = frame.top - clearance - staffCenter;
        step = Math.floor(y / staffStep);
        if (Math.abs(step % 2) === 0) {
          step = (step - 3 / 4 + (y + clearance) / staffStep) / 2;
        } else {
          step = (Math.floor((1.5 * y) / staffStep - 0.5) * 2 + 1) / 3;
          if (Math.abs(step) % 2 === 1) step -= Math.abs(step) < staffLines ? 2 / 3 : 1 / 3;
        }
      }
      let left = frame.left;
      let runWidth = frame.width;
      const glyph = glyphs[index]!;
      if (swashes.has(glyph)) {
        runWidth = staffStep;
      } else if (glyph === "None") {
        runWidth = staffStep;
        left = frame.right - runWidth;
      } else if (
        glyph === "PunctumInclinatum" &&
        index > 0 &&
        glyphs[index - 1] === "PunctumInclinatum" &&
        notes[index - 1]!.pitch - note.pitch === 1
      ) {
        runWidth *= 2 / 3;
        left += 0.5 * runWidth;
      } else if (glyph === "PunctumInclinatumLiquescent") {
        runWidth *= 2 / 3;
        left += 0.25 * runWidth;
      }
      const y = staffCenter + step * staffStep;
      // An episema runs on over the next note's on the same side, at the height that clears both.
      const previous = runs[runs.length - 1];
      if (previous && previous.side === position && left - previous.right < staffStep) {
        previous.y = position === "above" ? Math.min(previous.y, y) : Math.max(previous.y, y);
        previous.right = left + runWidth;
      } else {
        runs.push({ side: position, y, left, right: left + runWidth, eventId: note.id });
      }
    });
    for (const run of runs) {
      result.strokes.push({
        kind: episemaStroke,
        start: { x: run.left, y: run.y },
        end: { x: run.right, y: run.y },
        lineWidth: episemaWeight,
        eventId: run.eventId,
      });
    }

    // The ictus: above a virga, the top of a rise and the head of a fall, and below any other note, unless the GABC
    // writes its side.
    notes.forEach((note, index) => {
      if (!note.hasIctus) return;
      const frame = frames[index];
      if (!frame) return;
      let above: boolean;
      if (note.ictusPosition != null) above = note.ictusPosition === "above";
      else if (note.shape === "virga") above = true;
      else if (note.shape !== "inclinatum" && index + 1 < notes.length) above = notes[index + 1]!.pitch < note.pitch;
      else above = index > 0 && index === notes.length - 1 && notes[index - 1]!.pitch < note.pitch;
      const position = note.pitch - 6 + (above ? 1 : -1);
      // Off a staff line, or nearer if the space is free; further from an episema on the same side.
      const onStaffLine = Math.abs(position) % 2 === 1 && Math.abs(position) < staffLines;
      const episemaBeside = note.hasEpisema && (episemaSides[index] ?? "above") === (above ? "above" : "below");
      let extra = 0;
      if (note.shape === "inclinatum" && !onStaffLine && !episemaBeside) extra = 0.3;
      if (episemaBeside) extra = 0.4;
      const offset = staffStep * (extra + (onStaffLine ? 0.3 : -0.2)) * (above ? -1 : 1);
      const glyph = glyphs[index]!;
      const centerX = swashes.has(glyph) ? frame.left + staffStep / 2 : glyph === "None" ? frame.right - staffStep / 2 : frame.center.x;
      const ictus = placedGlyph(
        catalogGlyph(above ? "VerticalEpisemaAbove" : "VerticalEpisemaBelow"),
        { x: centerX, y: noteY(position + 6) + offset },
        glyphScale,
        note.id,
      );
      result.glyphs.push(ictus);
      result.ink(ictus.frame);
    });
  }

  private appendClef(
    clef: GregorianClef,
    x: number,
    staffTop: number,
    staffGap: number,
    glyphScale: number,
    notationScale: number,
    glyphs: GregorianPlacedGlyph[],
  ): void {
    const lineY = staffTop + (4 - clef.line) * staffGap;
    const clefGlyph = placedGlyph({ kind: clef.kind === "c" ? "cClef" : "fClef" }, { x, y: lineY }, glyphScale);
    glyphs.push(clefGlyph);
    if (clef.flattensB) {
      // The flat stands on the B the clef flattens, a step below the clef's line, just after the clef.
      const flatScale = glyphScale * 0.9;
      const flat = GregorianGlyphCatalog.definition("Flat");
      glyphs.push(
        placedGlyph({ kind: "flat" }, { x: clefGlyph.frame.right + notationScale + flat.origin.x * flatScale, y: lineY + staffGap / 2 }, flatScale),
      );
    }
  }

  /** A lyric's width, and the middle of the letters its first note stands over: those a brace pair marks, or its sung vowel. */
  private lyricMetrics(
    lyric: string,
    style: GregorianLyricStyle,
    centeredRange: { readonly lowerBound: number; readonly upperBound: number } | null,
    fontSize: number,
    fontName: string,
  ): { readonly width: number; readonly focus: number } {
    if (lyric.length === 0) return { width: 0, focus: 0 };
    const width = this.measureLyric(lyric, style, fontSize, fontName);
    const characters = graphemes(lyric);
    const centre =
      centeredRange && centeredRange.lowerBound >= 0 && centeredRange.upperBound <= characters.length && centeredRange.upperBound > centeredRange.lowerBound
        ? centeredRange
        : sungVowel(characters);
    const prefix = characters.slice(0, centre.lowerBound).join("");
    const nucleus = characters.slice(centre.lowerBound, centre.upperBound).join("");
    const focus = this.measureLyric(prefix, style, fontSize, fontName) + this.measureLyric(nucleus, style, fontSize, fontName) / 2;
    return { width, focus };
  }

  // Measured exactly: syllables of a word set solid meet exactly.
  private measureLyric(lyric: string, style: GregorianLyricStyle, fontSize: number, fontName: string): number {
    return lyric.length === 0 ? 0 : this.measurer.width(lyric, style, fontSize, fontName);
  }
}

/** A run of units, from the first to the last, which is before the first in an empty run. */
interface UnitRange {
  readonly first: number;
  readonly last: number;
}

/** A gap between two syllables no wider than this is read as none. */
const touching = 0.25;

/** A verse number: `1.`, `12`. */
const verseNumber = /^[0-9]+\.?$/;

/** The obliques of the porrectus, by the interval of their two notes. */
const swashes = new Set<GregorianGlyphName>(["Porrectus1", "Porrectus2", "Porrectus3", "Porrectus4"]);

/** The modes as the GABC writes them, the longer Roman numerals first and each in the same place as its Arabic numeral eight on. */
const modePrefixes = ["viii", "vii", "iii", "vi", "iv", "ii", "v", "i", "8", "7", "3", "6", "4", "2", "5", "1"];

const spacePattern = /^[\t\n\u{B}\u{C}\r \u{85}\u{A0}\u{1680}\u{2000}-\u{200A}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}]$/u;
const numberPattern = /^\p{N}$/u;
const uppercasePattern = /^\p{Uppercase}$/u;
const letterPattern = /^\p{L}$/u;

function isSpace(character: string): boolean {
  return spacePattern.test(character);
}

/** A numeral of any script, as Swift's `Character.isNumber` reads one. */
function isNumber(character: string): boolean {
  return numberPattern.test(character);
}

function isUppercase(character: string): boolean {
  return uppercasePattern.test(character);
}

/** The mode a psalm tone is headed with beside its initial ("8G"), from its score's mode; null for a mode that names no conclusion. */
function psalmToneLabel(mode: string | null | undefined): string | null {
  const trimmed = mode?.trim() ?? "";
  if (trimmed.length === 0) return null;
  const compact = [...trimmed.toLowerCase()].filter((character) => !isSpace(character)).join("");
  const prefix = modePrefixes.find((candidate) => compact.startsWith(candidate));
  if (prefix === undefined) return null;
  let conclusion = compact.substring(prefix.length);
  if (conclusion.startsWith("-")) conclusion = conclusion.substring(1);
  if (conclusion.startsWith("alt-")) conclusion = conclusion.substring(4);
  const concludingLetter = conclusion[0];
  if (concludingLetter === undefined || concludingLetter < "a" || concludingLetter > "g") return null;
  const variant = conclusion.substring(1);
  if (!(variant.length === 0 || [...variant].every(isNumber) || variant === "star" || variant === "*" || variant === "-")) return null;
  const label = trimmed.toUpperCase();
  // The books mark a starred ending with an asterisk (8 G*), which the mode may spell out; a label so marked reads as
  // itself.
  return variant === "star" && label.endsWith("STAR") ? `${label.slice(0, -4).trimEnd()}*` : label;
}

/**
 * The pitch of the custos that ends a staff: the next staff's first note. The last staff has none, and a custos written
 * in the score (`f+`, as before a change of clef) already guides the eye to what follows, so the staff that ends with
 * one is not given another.
 */
function custosPitch(lineIndex: number, lines: readonly (readonly LayoutUnit[])[]): number | null {
  if (lineIndex + 1 >= lines.length) return null;
  const neumes = lines[lineIndex]!.map(unitNeume).filter((neume): neume is GregorianNeume => neume != null);
  const lastNeume = neumes[neumes.length - 1];
  if (lastNeume?.notes[lastNeume.notes.length - 1]?.isCustos === true) return null;
  const next = lines[lineIndex + 1]!.map(unitNeume).find((neume) => neume != null);
  return next?.notes[0]?.pitch ?? null;
}

/**
 * For each unit, whether a staff that ends before it divides a word: whether the next lyric is the next syllable of
 * the word the last one began. One more than the units, for a staff that ends the score.
 */
function wordContinuation(units: readonly LayoutUnit[]): boolean[] {
  const result = new Array<boolean>(units.length + 1).fill(false);
  let continues = false;
  for (let index = units.length - 1; index >= 0; index -= 1) {
    if (hasLyric(units[index]!)) continues = units[index]!.join === "syllable";
    result[index] = continues;
  }
  return result;
}

/**
 * What a break before each unit costs, as TeX reckons penalties: lower is better, and null where no staff may break. A
 * staff best ends with a bar, then with a word; a word's syllables may be divided at a cost, and beside a syllable of
 * one letter only as a last resort; a melisma at a bar, or where four of its notes or more stand on either side. No
 * break falls between an accidental and its note, before a bar or a custos, before a rubric that closes the words
 * before it or an asterisk however it is written, or after a rubric that heads the words after it. One more than the
 * units: the end of the score.
 */
function breakPenalties(units: readonly LayoutUnit[]): (number | null)[] {
  // The notes each syllable writes: a melisma of ten or more may be divided between staffs, as Gregorio divides one.
  const syllableNotes = new Map<string, number>();
  for (const unit of units) {
    const neume = unitNeume(unit);
    if (!neume) continue;
    syllableNotes.set(neume.syllableId, (syllableNotes.get(neume.syllableId) ?? 0) + neume.writtenNoteCount);
  }

  const letterCount = (text: string): number => [...text].filter((character) => letterPattern.test(character)).length;

  const neumePenalty = (index: number, afterDivision: boolean): number | null => {
    const unit = units[index]!;
    const neume = unitNeume(unit);
    if (!neume || neume.writtenNoteCount <= 0) return null;
    if (!hasLyric(unit)) {
      // A bar within a melisma divides it as well as any.
      if (afterDivision) return -50;
      // Elsewhere a melisma is divided only where it leaves four notes or more on either staff, short of a bar or a word.
      let before = 0;
      for (let earlier = index - 1; earlier >= 0; earlier -= 1) {
        const earlierUnit = units[earlier]!;
        if (earlierUnit.element.kind === "division") break;
        before += unitNeume(earlierUnit)?.writtenNoteCount ?? 0;
        if (hasLyric(earlierUnit)) break;
      }
      let after = 0;
      for (let later = index; later < units.length; later += 1) {
        const laterUnit = units[later]!;
        if (laterUnit.element.kind === "division") break;
        if (hasLyric(laterUnit)) break;
        after += unitNeume(laterUnit)?.writtenNoteCount ?? 0;
      }
      if (before < 4 || after < 4) return null;
      let previous: GregorianNeume | null = null;
      for (let earlier = index - 1; earlier >= 0 && !previous; earlier -= 1) previous = unitNeume(units[earlier]!);
      if (previous?.syllableId === neume.syllableId) {
        return (syllableNotes.get(neume.syllableId) ?? 0) >= 10 ? 300 : null;
      }
      // The notes of a group without words go on the melisma of the syllable before them.
      return 200;
    }
    if (unit.join === "word") return afterDivision ? -50 : 0;
    let previousLyric = "";
    for (let earlier = index - 1; earlier >= 0; earlier -= 1) {
      if (hasLyric(units[earlier]!)) {
        previousLyric = units[earlier]!.lyricText;
        break;
      }
    }
    // A letter alone at a staff's end or start is a last resort.
    return letterCount(previousLyric) <= 1 || letterCount(unit.lyricText) <= 1 ? 10_000 : 100;
  };

  const result = new Array<number | null>(units.length + 1).fill(null);
  result[units.length] = 0;
  for (let index = 1; index < units.length; index += 1) {
    const previous = units[index - 1]!.element;
    if (previous.kind === "accidental") continue;
    if (previous.kind === "lyricMark" && previous.mark.beginsWhatFollows) continue;
    const afterDivision = previous.kind === "division";
    const element = units[index]!.element;
    switch (element.kind) {
      case "division":
      case "forcedBreak":
        result[index] = null;
        break;
      // An asterisk begins no staff, though it is written with the word after it (`*~ab`).
      case "lyricMark":
        result[index] = element.mark.beginsWhatFollows && !element.mark.text.startsWith("*") ? (afterDivision ? -50 : 0) : null;
        break;
      case "clef":
        result[index] = 50;
        break;
      // A break before an accidental is a break before its note.
      case "accidental":
        result[index] = index + 1 < units.length ? neumePenalty(index + 1, afterDivision) : null;
        break;
      case "neume":
        result[index] = neumePenalty(index, afterDivision);
        break;
    }
  }
  return result;
}

/**
 * The letters a syllable is sung on: the first vowel of its last word (a note may carry the end of one word and the
 * beginning of the next, `t Dó`), with the vowels that follow it (`ae`, `au`), past a consonantal i that begins it
 * (`ius`, `Ie`) or a u after q or g (`qui`, `guis`); the whole syllable, if it has no vowel.
 */
function sungVowel(characters: readonly string[]): { readonly lowerBound: number; readonly upperBound: number } {
  const vowel = (start: number): { lowerBound: number; upperBound: number } | null => {
    let first = -1;
    for (let index = start; index < characters.length; index += 1) {
      if (isVowel(characters[index]!)) {
        first = index;
        break;
      }
    }
    if (first < 0) return null;
    if (first + 1 < characters.length && isVowel(characters[first + 1]!)) {
      const letter = baseLetter(characters[first]!);
      const before = first > start ? baseLetter(characters[first - 1]!) : null;
      if (letter === "i" && first === start) first += 1;
      else if (letter === "u" && (before === "q" || before === "g")) first += 1;
    }
    let end = first + 1;
    while (end < characters.length && isVowel(characters[end]!)) end += 1;
    return { lowerBound: first, upperBound: end };
  };
  let lastSpace = -1;
  characters.forEach((character, index) => {
    if (character.length > 0 && [...character].every(isSpace)) lastSpace = index;
  });
  const lastWord = lastSpace + 1;
  return (lastWord < characters.length ? vowel(lastWord) : null) ?? vowel(0) ?? { lowerBound: 0, upperBound: characters.length };
}

/** A letter without its accents, in lower case: `ǽ` is `æ`, `Ë` is `e`. */
function baseLetter(character: string): string | null {
  return character.normalize("NFD").toLowerCase()[0] ?? null;
}

function isVowel(character: string): boolean {
  const letter = baseLetter(character);
  return letter != null && "aeiouyæœ".includes(letter);
}

function accidentalKind(accidental: GregorianAccidental): GregorianGlyphKind {
  switch (accidental) {
    case "flat":
      return { kind: "flat" };
    case "natural":
      return { kind: "natural" };
    case "sharp":
      return { kind: "sharp" };
  }
}

function inkRight(composition: Composition): number {
  const strokes = composition.strokes.reduce((right, stroke) => Math.max(right, stroke.start.x, stroke.end.x), -Infinity);
  return Math.max(composition.inkFrame?.right ?? -Infinity, strokes);
}

function inkLeft(composition: Composition): number {
  const strokes = composition.strokes.reduce((left, stroke) => Math.min(left, stroke.start.x, stroke.end.x), Infinity);
  return Math.min(composition.inkFrame?.left ?? Infinity, strokes);
}

function appendEvent(note: GregorianNote, neume: GregorianNeume, frame: GregorianRect, lineIndex: number, result: Composition): void {
  result.events.push({ eventId: note.id, neumeId: neume.id, frame: frame.inset(-2, -4), lineIndex });
  result.ink(frame);
}

function appendDivision(
  division: GregorianDivision,
  x: number,
  staffTop: number,
  staffGap: number,
  notationScale: number,
  strokes: GregorianPlacedStroke[],
): void {
  const height = division === "minima" ? staffGap : division === "minor" ? staffGap * 2 : staffGap * 3;
  // A quarter bar crosses the top line, as the Liber draws it.
  const top = division === "minima" ? staffTop - staffGap / 2 : staffTop + (staffGap * 3 - height) / 2;
  const count = division === "final" ? 2 : 1;
  for (let index = 0; index < count; index += 1) {
    const barX = x + index * 4 * notationScale;
    strokes.push({
      kind: { kind: "division", division },
      start: { x: barX, y: top },
      end: { x: barX, y: top + height },
      lineWidth: Math.max(1.2, 1.45 * notationScale),
      eventId: null,
    });
  }
}

function placedGlyph(kind: GregorianGlyphKind, anchor: GregorianPoint, scale: number, eventId: string | null = null): GregorianPlacedGlyph {
  const definition = GregorianGlyphCatalog.definition(catalogName(kind));
  return {
    kind,
    frame: GregorianRect.of(
      anchor.x - definition.origin.x * scale,
      anchor.y - definition.origin.y * scale,
      definition.bounds.width * scale,
      definition.bounds.height * scale,
    ),
    eventId,
  };
}

/**
 * The ledger lines a note at `noteY` needs, as wide as its head at `frame` and a staff line's weight, as the books draw
 * them. They follow the note's pitch, not its glyph, whose stem or swash may reach far from the head.
 */
function appendLedgerLines(
  frame: GregorianRect,
  noteY: number,
  staffTop: number,
  staffBottom: number,
  staffGap: number,
  scale: number,
  eventId: string,
  strokes: GregorianPlacedStroke[],
): void {
  const ledger = (y: number): GregorianPlacedStroke => ({
    kind: ledgerStroke,
    start: { x: frame.left - 3 * scale, y },
    end: { x: frame.right + 3 * scale, y },
    lineWidth: Math.max(0.72, 0.78 * scale),
    eventId,
  });
  if (noteY <= staffTop - staffGap / 2) {
    for (let y = staffTop - staffGap; y >= noteY - 1; y -= staffGap) strokes.push(ledger(y));
  } else if (noteY >= staffBottom + staffGap / 2) {
    for (let y = staffBottom + staffGap; y <= noteY + 1; y += staffGap) strokes.push(ledger(y));
  }
}

function mergeLedgerLines(strokes: readonly GregorianPlacedStroke[], scale: number): GregorianPlacedStroke[] {
  const ledgers = strokes
    .filter((stroke) => stroke.kind.kind === "ledger")
    .sort((a, b) => (Math.abs(a.start.y - b.start.y) < 0.1 ? a.start.x - b.start.x : a.start.y - b.start.y));
  const others = strokes.filter((stroke) => stroke.kind.kind !== "ledger");
  const merged: GregorianPlacedStroke[] = [];
  for (const ledger of ledgers) {
    const last = merged[merged.length - 1];
    if (last && Math.abs(last.start.y - ledger.start.y) < 0.1 && ledger.start.x <= last.end.x + 2 * scale) {
      merged[merged.length - 1] = {
        kind: ledgerStroke,
        start: last.start,
        end: { x: Math.max(last.end.x, ledger.end.x), y: last.end.y },
        lineWidth: Math.max(last.lineWidth, ledger.lineWidth),
        eventId: last.eventId,
      };
    } else {
      merged.push(ledger);
    }
  }
  return [...others, ...merged];
}

function makeHitFrames(raw: readonly RawNeumePlacement[], width: number, scale: number): GregorianNeumePlacement[] {
  const result: GregorianNeumePlacement[] = [];
  const grouped = new Map<number, RawNeumePlacement[]>();
  for (const placement of raw) {
    const line = grouped.get(placement.lineIndex);
    if (line) line.push(placement);
    else grouped.set(placement.lineIndex, [placement]);
  }
  for (const lineIndex of [...grouped.keys()].sort((a, b) => a - b)) {
    const placements = [...grouped.get(lineIndex)!].sort((a, b) => a.inkFrame.center.x - b.inkFrame.center.x);
    placements.forEach((placement, index) => {
      const midX = placement.inkFrame.center.x;
      const previousMid = index > 0 ? placements[index - 1]!.inkFrame.center.x : 0;
      const nextMid = index + 1 < placements.length ? placements[index + 1]!.inkFrame.center.x : width;
      const leftBoundary = index > 0 ? (previousMid + midX) / 2 : Math.max(0, midX - 22 * scale);
      const rightBoundary = index + 1 < placements.length ? (midX + nextMid) / 2 : Math.min(width, midX + 22 * scale);
      const desiredWidth = Math.max(44, placement.inkFrame.width + 14 * scale);
      const centeredLeft = midX - desiredWidth / 2;
      const hitLeft = Math.max(leftBoundary, centeredLeft);
      const hitRight = Math.min(rightBoundary, hitLeft + desiredWidth);
      result.push({
        id: placement.neume.id,
        eventIds: placement.neume.eventIds,
        noteCount: placement.neume.writtenNoteCount,
        lyric: placement.neume.lyric,
        inkFrame: placement.inkFrame,
        hitFrame: GregorianRect.of(
          hitLeft,
          placement.staffTop - 7 * scale,
          Math.max(1, hitRight - hitLeft),
          Math.max(44, placement.staffBottom - placement.staffTop + 7 * scale),
        ),
        lineIndex,
      });
    });
  }
  return result;
}

// @@HELPERS@@
