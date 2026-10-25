import type { GregorianPoint } from "./GregorianGeometry.js";
import { GregorianGlyphCatalog, type GregorianPathCommand } from "./GregorianGlyphCatalog.js";
import { catalogName, type GregorianPlacedGlyph } from "./GregorianLayout.js";

/**
 * How a placed glyph's outlines are scaled from the catalog's units into its frame, then moved to it: a point (x, y) of
 * the outlines is drawn at (x * scaleX + translateX, y * scaleY + translateY).
 */
export interface GregorianGlyphTransform {
  readonly scaleX: number;
  readonly scaleY: number;
  readonly translateX: number;
  readonly translateY: number;
}

function apply(transform: GregorianGlyphTransform, point: GregorianPoint): GregorianPoint {
  return { x: point.x * transform.scaleX + transform.translateX, y: point.y * transform.scaleY + transform.translateY };
}

/** The transform that sets the glyph's outlines in its frame; null for a glyph that has no extent to draw. */
export function glyphTransform(glyph: GregorianPlacedGlyph): GregorianGlyphTransform | null {
  const definition = GregorianGlyphCatalog.definition(catalogName(glyph.kind));
  if (definition.bounds.width <= 0 || definition.bounds.height <= 0) return null;
  // The outlines' box begins at minus the origin; it's scaled to the frame, then set at it.
  const scaleX = glyph.frame.width / definition.bounds.width;
  const scaleY = glyph.frame.height / definition.bounds.height;
  return {
    scaleX,
    scaleY,
    translateX: glyph.frame.left + definition.origin.x * scaleX,
    translateY: glyph.frame.top + definition.origin.y * scaleY,
  };
}

/**
 * The glyph's outlines, scaled into its frame, in layout coordinates: a display list for each. Fill them together with
 * the even-odd rule: some glyphs need it.
 */
export function glyphPath(glyph: GregorianPlacedGlyph): GregorianPathCommand[][] {
  const transform = glyphTransform(glyph);
  if (!transform) return [];
  const definition = GregorianGlyphCatalog.definition(catalogName(glyph.kind));
  return definition.paths.map((commands) =>
    commands.map((command): GregorianPathCommand => {
      switch (command.kind) {
        case "move":
          return { kind: "move", point: apply(transform, command.point) };
        case "line":
          return { kind: "line", point: apply(transform, command.point) };
        case "quadratic":
          return { kind: "quadratic", control: apply(transform, command.control), end: apply(transform, command.end) };
        case "cubic":
          return {
            kind: "cubic",
            control1: apply(transform, command.control1),
            control2: apply(transform, command.control2),
            end: apply(transform, command.end),
          };
        case "close":
          return command;
      }
    }),
  );
}

function number(value: number): string {
  return Number(value.toFixed(3)).toString();
}

/** The glyph's outlines as the data of an SVG path, to fill with the even-odd rule. */
export function glyphPathData(glyph: GregorianPlacedGlyph): string {
  return glyphPath(glyph)
    .flatMap((commands) =>
      commands.map((command) => {
        switch (command.kind) {
          case "move":
            return `M${number(command.point.x)} ${number(command.point.y)}`;
          case "line":
            return `L${number(command.point.x)} ${number(command.point.y)}`;
          case "quadratic":
            return `Q${number(command.control.x)} ${number(command.control.y)} ${number(command.end.x)} ${number(command.end.y)}`;
          case "cubic":
            return `C${number(command.control1.x)} ${number(command.control1.y)} ${number(command.control2.x)} ${number(command.control2.y)} ${number(command.end.x)} ${number(command.end.y)}`;
          case "close":
            return "Z";
        }
      }),
    )
    .join("");
}
