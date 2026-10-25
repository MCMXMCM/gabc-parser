import type { GregorianLyricStyle } from "./GregorianNotation.js";

/**
 * The face lyrics are measured in, and must be drawn in.
 *
 * The layout engine measures lyrics in the face named by `GregorianLayoutMetrics.lyricFontName`, EB Garamond unless
 * another is named, through the `GregorianLyricMeasurer` it is given. In a browser, a canvas measures them in the face
 * the page has loaded; the package carries EB Garamond for that.
 */
export const GregorianLyricFont = {
  /** The PostScript name of EB Garamond's regular weight. */
  defaultName: "EBGaramond-Regular",
  /** How far EB Garamond rises above its baseline, in ems: where a lyric drawn from the top of its line sets its baseline. */
  ascent: 1.007,
  /** How tall EB Garamond sets its capitals, in ems. */
  capHeight: 0.65,
} as const;

/**
 * How wide a lyric is set, in points at its size, in the face named and the style given: italic for a preparatory
 * syllable, bold for an accented one. CoreText measures it in the Swift package, the platform's text in the Kotlin
 * libraries, and a canvas here; `ascent` and `capHeight` give the face's, in ems, where it is not EB Garamond.
 */
export interface GregorianLyricMeasurer {
  width(text: string, style: GregorianLyricStyle, fontSize: number, fontName: string): number;
  ascent?(fontName: string): number;
  capHeight?(fontName: string): number;
}
