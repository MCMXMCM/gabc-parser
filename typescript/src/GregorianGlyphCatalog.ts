import { GregorianRect, type GregorianPoint } from "./GregorianGeometry.js";
import { generatedGlyphSources } from "./GeneratedGregorianGlyphCatalog.js";

/**
 * Names from Exsurge's canonical Gregorian SVG glyph set.
 *
 * The generated catalog deliberately contains geometry rather than font characters so engraving is deterministic
 * across platforms and font versions. Each name is Exsurge's own.
 */
export const gregorianGlyphNames = [
  "None",
  "AcuteAccent",
  "GraveAccent",
  "Circle",
  "Semicircle",
  "ReversedSemicircle",
  "Stropha",
  "BeginningAscLiquescent",
  "BeginningDesLiquescent",
  "CustosDescLong",
  "CustosDescShort",
  "CustosLong",
  "CustosShort",
  "DoClef",
  "FaClef",
  "TrebleClef",
  "TrebleClefSmall",
  "ChiRhoClef",
  "ChiRhoClefSans",
  "Flat",
  "Mora",
  "Natural",
  "Sharp",
  "OriscusAsc",
  "OriscusDes",
  "OriscusLiquescent",
  "PodatusLower",
  "PodatusLowerShort",
  "PodatusUpper",
  "PodatusUpperShort",
  "Porrectus1",
  "Porrectus2",
  "Porrectus3",
  "Porrectus4",
  "PunctumCavum",
  "PunctumQuadratum",
  "PunctumQuadratumLiquescent",
  "PunctumQuadratumAscLiquescent",
  "PunctumQuadratumDesLiquescent",
  "PunctumInclinatum",
  "PunctumInclinatumLiquescent",
  "Quilisma",
  "TerminatingAscLiquescent",
  "TerminatingDesLiquescent",
  "VerticalEpisemaAbove",
  "VerticalEpisemaBelow",
  "VirgaLong",
  "VirgaShort",
  "Virgula",
] as const;

export type GregorianGlyphName = (typeof gregorianGlyphNames)[number];

export type GregorianGlyphAlignment = "left" | "center" | "right" | "undefined";

/** A platform-neutral display-list command. SVG parsing and arc flattening happen in the checked-in generator. */
export type GregorianPathCommand =
  | { readonly kind: "move"; readonly point: GregorianPoint }
  | { readonly kind: "line"; readonly point: GregorianPoint }
  | { readonly kind: "quadratic"; readonly control: GregorianPoint; readonly end: GregorianPoint }
  | { readonly kind: "cubic"; readonly control1: GregorianPoint; readonly control2: GregorianPoint; readonly end: GregorianPoint }
  | { readonly kind: "close" };

/** A glyph as the generator writes it: its outlines as text, its box as x, y, width and height, its origin as x and y. */
export interface GregorianGlyphSource {
  readonly outlines: readonly string[];
  readonly bounds: readonly [number, number, number, number];
  readonly origin: readonly [number, number];
  readonly alignment: GregorianGlyphAlignment;
}

/**
 * A glyph of the catalog: its outlines, the box they fill, the point it is set by, and how it aligns. The outlines
 * are in the catalog's units, about the glyph's origin.
 */
export class GregorianGlyphDefinition {
  readonly bounds: GregorianRect;
  readonly origin: GregorianPoint;
  readonly alignment: GregorianGlyphAlignment;
  private readonly outlines: readonly string[];
  private readPaths: GregorianPathCommand[][] | null = null;

  constructor(source: GregorianGlyphSource) {
    this.outlines = source.outlines;
    this.bounds = GregorianRect.of(...source.bounds);
    this.origin = { x: source.origin[0], y: source.origin[1] };
    this.alignment = source.alignment;
  }

  /** The outlines as display lists, one for each of Exsurge's paths; read from the catalog the first time they're asked for. */
  get paths(): GregorianPathCommand[][] {
    this.readPaths ??= this.outlines.map(readOutline);
    return this.readPaths;
  }
}

/** Reads one outline as the generator writes it: `M x y`, `L x y`, `Q cx cy x y`, `C c1x c1y c2x c2y x y` and `Z`. */
function readOutline(outline: string): GregorianPathCommand[] {
  const words = outline.split(" ").filter((word) => word.length > 0);
  const commands: GregorianPathCommand[] = [];
  let index = 0;
  while (index < words.length) {
    const word = words[index]!;
    const command = word[0];
    // A command's first number follows its letter without a space; the rest follow it, a word each.
    const numbers: number[] = [];
    if (word.length > 1) numbers.push(Number(word.substring(1)));
    index += 1;
    const count = command === "M" || command === "L" ? 2 : command === "Q" ? 4 : command === "C" ? 6 : command === "Z" ? 0 : -1;
    if (count < 0) throw new Error(`Unknown glyph command ${command} in the catalog`);
    while (numbers.length < count) numbers.push(Number(words[index++]));
    const at = (offset: number): GregorianPoint => ({ x: numbers[offset]!, y: numbers[offset + 1]! });
    switch (command) {
      case "M":
        commands.push({ kind: "move", point: at(0) });
        break;
      case "L":
        commands.push({ kind: "line", point: at(0) });
        break;
      case "Q":
        commands.push({ kind: "quadratic", control: at(0), end: at(2) });
        break;
      case "C":
        commands.push({ kind: "cubic", control1: at(0), control2: at(2), end: at(4) });
        break;
      default:
        commands.push({ kind: "close" });
    }
  }
  return commands;
}

const definitions = new Map<GregorianGlyphName, GregorianGlyphDefinition>();

export const GregorianGlyphCatalog = {
  definition(name: GregorianGlyphName): GregorianGlyphDefinition {
    let definition = definitions.get(name);
    if (!definition) {
      const source = generatedGlyphSources[name] ?? generatedGlyphSources.None;
      definition = new GregorianGlyphDefinition(source);
      definitions.set(name, definition);
    }
    return definition;
  },
};
