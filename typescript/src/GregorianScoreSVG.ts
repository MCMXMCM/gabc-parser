// Draws a layout as SVG, and measures lyrics with a browser's canvas: what a web page needs of the engraving, as the
// Swift package's SwiftUI canvas and the Kotlin libraries' Compose canvas are what an app needs.

import { glyphPathData } from "./GregorianGlyphPath.js";
import type { GregorianLayout, GregorianPlacedInitial, GregorianPlacedLyric, GregorianPlacedStroke } from "./GregorianLayout.js";
import { GregorianLyricFont, type GregorianLyricMeasurer } from "./GregorianLyricFont.js";
import type { GregorianLyricStyle } from "./GregorianNotation.js";

/** The CSS family each lyric font is drawn and measured in, by the PostScript name the layout names it by. */
export type GregorianFontFamilies = Readonly<Record<string, string>>;

const defaultFamilies: GregorianFontFamilies = { [GregorianLyricFont.defaultName]: "'EB Garamond', serif" };

function family(families: GregorianFontFamilies, fontName: string): string {
  return families[fontName] ?? defaultFamilies[fontName] ?? "serif";
}

/** The CSS font of a lyric in its style: bold for an accented syllable, italic for a preparatory one. */
export function gregorianCSSFont(style: GregorianLyricStyle, fontSize: number, fontName: string, families: GregorianFontFamilies = {}): string {
  const weight = style === "accented" ? "700" : "400";
  const slant = style === "preparatory" ? "italic" : "normal";
  return `${slant} ${weight} ${fontSize}px ${family(families, fontName)}`;
}

/**
 * Measures lyrics with a canvas, in the fonts the page has loaded: load them before a score is laid out, or its lines
 * are broken by the widths of a fallback face. Text is measured kerned, with its ligatures, as the platforms set it.
 */
export function createCanvasLyricMeasurer(
  options: { readonly families?: GregorianFontFamilies; readonly context?: CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D } = {},
): GregorianLyricMeasurer {
  const families = options.families ?? {};
  let context = options.context ?? null;
  const canvas = () => {
    if (context) return context;
    const made =
      typeof OffscreenCanvas !== "undefined" ? new OffscreenCanvas(1, 1).getContext("2d") : document.createElement("canvas").getContext("2d");
    if (!made) throw new Error("A canvas is needed to measure lyrics.");
    context = made;
    return made;
  };
  return {
    width(text, style, fontSize, fontName) {
      if (text.length === 0) return 0;
      const drawing = canvas();
      drawing.font = gregorianCSSFont(style, fontSize, fontName, families);
      return drawing.measureText(text).width;
    },
  };
}

/** How a layout is drawn as SVG. */
export interface GregorianScoreSVGOptions {
  /** The colour of the notes, the staff and the words: the text's own colour by default. */
  readonly ink?: string;
  /** The colour of rubrics: the asterisk, the flex, ℣. and ℟., verse numbers. */
  readonly rubric?: string;
  readonly families?: GregorianFontFamilies;
  /** How far the lyric face rises above its baseline, in ems: EB Garamond's unless another is drawn. */
  readonly ascent?: number;
}

function number(value: number): string {
  return Number(value.toFixed(3)).toString();
}

function escape(text: string): string {
  return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
}

function strokeClass(stroke: GregorianPlacedStroke): string {
  return stroke.kind.kind === "division" ? `gregorian-division gregorian-division-${stroke.kind.division}` : `gregorian-${stroke.kind.kind}`;
}

/**
 * The layout drawn as an SVG document, a group for each staff and, within it, a group for each neume, so that a page
 * may light a neume or print a staff by itself. Each element names what it draws: `data-staff`, `data-neume` (a
 * neume's id, or a lyric's), `data-event` (a note's). A neume's group lists all its notes in `data-events`, separated
 * by spaces, for a note may be drawn within another's glyph, as the second note of a porrectus is within its oblique.
 */
export function gregorianScoreSVG(layout: GregorianLayout, options: GregorianScoreSVGOptions = {}): string {
  const ink = options.ink ?? "currentColor";
  const rubric = options.rubric ?? "#b3261e";
  const families = options.families ?? {};
  const ascent = options.ascent ?? GregorianLyricFont.ascent;
  const { width, height } = layout.size;
  const neumeOfEvent = new Map(layout.events.map((event) => [event.eventId, event.neumeId]));
  // The neumes are drawn in the order they are sung, each naming its notes.
  const neumeOrder = new Map<string, number>();
  const neumeEvents = new Map<string, string[]>();
  for (const event of layout.events) {
    if (!neumeOrder.has(event.neumeId)) neumeOrder.set(event.neumeId, neumeOrder.size);
    const events = neumeEvents.get(event.neumeId);
    if (events) events.push(event.eventId);
    else neumeEvents.set(event.neumeId, [event.eventId]);
  }
  const staffAt = (y: number): number => {
    const index = layout.staffs.findIndex((staff) => y < staff.systemFrame.bottom);
    return index < 0 ? Math.max(0, layout.staffs.length - 1) : index;
  };

  interface StaffContent {
    lines: string[];
    neumes: Map<string, string[]>;
    loose: string[];
    words: string[];
  }
  const staffs: StaffContent[] = (layout.staffs.length > 0 ? layout.staffs : [null]).map(() => ({
    lines: [],
    neumes: new Map(),
    loose: [],
    words: [],
  }));
  const place = (staffIndex: number, eventId: string | null, element: string) => {
    const content = staffs[staffIndex]!;
    const neumeId = eventId != null ? neumeOfEvent.get(eventId) : undefined;
    if (neumeId === undefined) {
      content.loose.push(element);
      return;
    }
    const group = content.neumes.get(neumeId);
    if (group) group.push(element);
    else content.neumes.set(neumeId, [element]);
  };

  for (const stroke of layout.strokes) {
    const element =
      `<line class="${strokeClass(stroke)}" x1="${number(stroke.start.x)}" y1="${number(stroke.start.y)}" ` +
      `x2="${number(stroke.end.x)}" y2="${number(stroke.end.y)}" stroke-width="${number(stroke.lineWidth)}"` +
      `${stroke.eventId != null ? ` data-event="${escape(stroke.eventId)}"` : ""}/>`;
    const staffIndex = staffAt((stroke.start.y + stroke.end.y) / 2);
    if (stroke.kind.kind === "staff") staffs[staffIndex]!.lines.push(element);
    else place(staffIndex, stroke.eventId, element);
  }
  for (const glyph of layout.glyphs) {
    const data = glyphPathData(glyph);
    if (data.length === 0) continue;
    const element = `<path d="${data}"${glyph.eventId != null ? ` data-event="${escape(glyph.eventId)}"` : ""}/>`;
    place(staffAt(glyph.frame.center.y), glyph.eventId, element);
  }
  const text = (lyric: GregorianPlacedLyric) =>
    `<text class="gregorian-lyric${lyric.style === "rubric" ? " gregorian-rubric" : ""}" x="${number(lyric.origin.x)}" ` +
    `y="${number(lyric.origin.y + ascent * lyric.fontSize)}" style="font: ${escape(gregorianCSSFont(lyric.style, lyric.fontSize, lyric.fontName, families))}"` +
    `${lyric.style === "rubric" ? ` fill="${escape(rubric)}"` : ""} data-neume="${escape(lyric.neumeId)}">${escape(lyric.text)}</text>`;
  for (const lyric of layout.lyrics) staffs[staffAt(lyric.origin.y)]!.words.push(text(lyric));
  const initial = (placed: GregorianPlacedInitial) => {
    const font = escape(gregorianCSSFont(placed.style, placed.fontSize, placed.fontName, families));
    const annotation =
      placed.annotation != null && placed.annotationOrigin
        ? `<text class="gregorian-annotation" x="${number(placed.annotationOrigin.x)}" ` +
          `y="${number(placed.annotationOrigin.y + ascent * placed.annotationFontSize)}" ` +
          `style="font: ${escape(gregorianCSSFont(placed.style, placed.annotationFontSize, placed.fontName, families))}">${escape(placed.annotation)}</text>`
        : "";
    return (
      `${annotation}<text class="gregorian-initial" x="${number(placed.origin.x)}" y="${number(placed.origin.y + ascent * placed.fontSize)}" ` +
      `style="font: ${font}" data-neume="${escape(placed.neumeId)}">${escape(placed.text)}</text>`
    );
  };
  for (const placed of layout.initials) staffs[staffAt(placed.origin.y + placed.fontSize / 2)]!.words.push(initial(placed));

  const groups = staffs.map((content, index) => {
    const neumes = [...content.neumes.entries()]
      .sort(([first], [second]) => neumeOrder.get(first)! - neumeOrder.get(second)!)
      .map(
        ([neumeId, elements]) =>
          `<g class="gregorian-neume" data-neume="${escape(neumeId)}" data-events="${escape((neumeEvents.get(neumeId) ?? []).join(" "))}">` +
          `${elements.join("")}</g>`,
      );
    return `<g class="gregorian-staff" data-staff="${index}">${content.lines.join("")}${content.loose.join("")}${neumes.join("")}${content.words.join("")}</g>`;
  });
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${number(width)}" height="${number(height)}" viewBox="0 0 ${number(width)} ${number(height)}" ` +
    `fill="${escape(ink)}" stroke="${escape(ink)}" stroke-linecap="butt">` +
    `<style>.gregorian-score path{fill-rule:evenodd;stroke:none}.gregorian-score text{stroke:none}</style>` +
    `<g class="gregorian-score">${groups.join("")}</g></svg>`
  );
}
