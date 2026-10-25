import * as fontkit from "fontkit";
import { fileURLToPath } from "node:url";
import { GregorianEngravingLayoutEngine } from "../src/GregorianLayout.js";
import type { GregorianLyricMeasurer } from "../src/GregorianLyricFont.js";

const fontPath = fileURLToPath(new URL("../../Sources/GABCLyricFont/Resources/EBGaramond-VariableFont_wght.ttf", import.meta.url));
const face = fontkit.openSync(fontPath) as fontkit.Font;
const bold = face.getVariation({ wght: 700 });

/**
 * Measures lyrics in the EB Garamond the Swift package carries, with fontkit's text layout: kerned, with ligatures and
 * fractional advances, as CoreText measures them for the Swift package's tests. A bold syllable is the variable face's
 * bold weight; an italic is slanted, and takes the regular's advances.
 */
export const ebGaramondMeasurer: GregorianLyricMeasurer = {
  width(text, style, fontSize) {
    if (text.length === 0) return 0;
    const font = style === "accented" ? bold : face;
    return (font.layout(text).advanceWidth / font.unitsPerEm) * fontSize;
  },
};

/** A layout engine that measures lyrics in EB Garamond, as the Swift package's engine does by default. */
export function layoutEngine(): GregorianEngravingLayoutEngine {
  return new GregorianEngravingLayoutEngine(ebGaramondMeasurer);
}
