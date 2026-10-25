// A port of the Kotlin library's LyricFontTest. The Swift tests first register the bundled EB Garamond
// (`GregorianLyricFont.registerBundledFont()`, in setUp and as an assertion of its own). Registering the face, and
// finding it by name through the platform, is the Android font library's (gabc-lyric-font) and has no counterpart in
// Node: here the face is read from its file, as the measuring fixture reads it.

import * as fontkit from "fontkit";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { GregorianEngravingLayoutEngine } from "../src/GregorianLayout.js";
import { GregorianLyricFont, type GregorianLyricMeasurer } from "../src/GregorianLyricFont.js";
import { GregorianScoreParser } from "../src/GregorianNotation.js";
import { ebGaramondMeasurer } from "./ebGaramondMeasurer.js";

const fontPath = fileURLToPath(
  new URL("../../Sources/GABCLyricFont/Resources/EBGaramond-VariableFont_wght.ttf", import.meta.url),
);

describe("LyricFontTest", () => {
  it("bundledEBGaramondCanBeFoundByName", () => {
    const font = fontkit.openSync(fontPath) as fontkit.Font;
    // CoreText finds the face by the default name; here the face must be the one the default name names.
    expect(font.postscriptName).toBe(GregorianLyricFont.defaultName);
    expect(font.postscriptName).toBe("EBGaramond-Regular");
    expect(font.familyName).toBe("EB Garamond");
  });

  it("lyricsAndInitialsNameTheFontTheyWereMeasuredIn", () => {
    const score = GregorianScoreParser.parse("%% (c4) Cla(f)ma(g)vé(h)runt(i) iu(j)sti(i) (::)");
    // The Swift engine measures with CoreText, in whatever face the metrics name; the fixture's engine measures
    // every lyric in EB Garamond, whatever is named. This one measures each face as itself, as CoreText does.
    const engine = new GregorianEngravingLayoutEngine(namedFaceMeasurer);
    const garamond = engine.layout(score, 390, { openingLabel: "8g" });
    expect(garamond.lyrics.length === 0).toBe(false);
    expect(garamond.initials.length).toBe(1);
    expect(garamond.lyrics.every((lyric) => lyric.fontName === "EBGaramond-Regular")).toBe(true);
    expect(garamond.initials.every((initial) => initial.fontName === "EBGaramond-Regular")).toBe(true);

    const georgia = engine.layout(score, 390, {
      metrics: { lyricFontName: "Georgia" },
      openingLabel: "8g",
    });
    expect(georgia.lyrics.every((lyric) => lyric.fontName === "Georgia")).toBe(true);
    expect(georgia.initials.every((initial) => initial.fontName === "Georgia")).toBe(true);
    // Each face is measured as itself.
    expect(georgia.lyrics.map((lyric) => lyric.width)).not.toEqual(garamond.lyrics.map((lyric) => lyric.width));
  });
});

/**
 * Measures a lyric in the face named, as CoreText does for the Swift package's engine: EB Garamond as the fixture
 * measures it. The Kotlin tests measure any other face as the JDK finds it by name, falling back to the JDK's own
 * default, which is not EB Garamond either; Node has no platform faces to find by name, so here any other face is a
 * stand-in that plainly is not EB Garamond, half an em to a character.
 */
const namedFaceMeasurer: GregorianLyricMeasurer = {
  width(text, style, fontSize, fontName) {
    if (fontName === GregorianLyricFont.defaultName) return ebGaramondMeasurer.width(text, style, fontSize, fontName);
    if (text.length === 0) return 0;
    return [...text].length * 0.5 * fontSize;
  },
};
