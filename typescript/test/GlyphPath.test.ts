// A port of the Kotlin library's GlyphPathTest.

import { assert, describe, expect, it } from "vitest";
import { GregorianRect, type GregorianPoint } from "../src/GregorianGeometry.js";
import { GregorianGlyphCatalog, gregorianGlyphNames, type GregorianPathCommand } from "../src/GregorianGlyphCatalog.js";
import { glyphPath } from "../src/GregorianGlyphPath.js";
import { catalogGlyph } from "../src/GregorianLayout.js";

describe("GlyphPathTest", () => {
  it("everyCatalogGlyphHasAnOutline", () => {
    for (const name of gregorianGlyphNames.filter((each) => each !== "None")) {
      const bounds = boundingBoxOfPath(GregorianGlyphCatalog.definition(name).paths);
      // A path with no points has the null rectangle for its box, which is empty.
      expect(bounds == null || bounds.isEmpty, name).toBe(false);
    }
  });

  it("placedOutlineMapsTheGlyphsBoundsOntoItsFrame", () => {
    // The catalog's bounds are those of the notehead: a stem (the podatus's, say) may reach beyond them, and
    // beyond the frame.
    const frame = GregorianRect.of(12.5, 40, 6.25, 7.5);
    for (const name of gregorianGlyphNames.filter((each) => each !== "None")) {
      const definition = GregorianGlyphCatalog.definition(name);
      // The accents have no width in the catalog, and are not drawn.
      if (!(definition.bounds.width > 0 && definition.bounds.height > 0)) {
        // A CGPath is empty when it holds no elements: here, no commands.
        expect(
          glyphPath({ kind: catalogGlyph(name), frame, eventId: null }).every((commands) => commands.length === 0),
          name,
        ).toBe(true);
        continue;
      }
      const outline = checkNotNull(boundingBoxOfPath(definition.paths), name);
      const scaleX = frame.width / definition.bounds.width;
      const scaleY = frame.height / definition.bounds.height;
      const expected = GregorianRect.of(
        frame.left + (outline.left + definition.origin.x) * scaleX,
        frame.top + (outline.top + definition.origin.y) * scaleY,
        outline.width * scaleX,
        outline.height * scaleY,
      );

      const placed = checkNotNull(boundingBoxOfPath(glyphPath({ kind: catalogGlyph(name), frame, eventId: null })), name);
      assert.closeTo(placed.left, expected.left, 0.0001, name);
      assert.closeTo(placed.top, expected.top, 0.0001, name);
      assert.closeTo(placed.width, expected.width, 0.0001, name);
      assert.closeTo(placed.height, expected.height, 0.0001, name);
    }
  });
});

/**
 * The box of a path as CGPath's `boundingBoxOfPath` measures it: every point the outline passes through, the extremes
 * of its curves included, and not their control points. Null for a path with no points, where CoreGraphics gives the
 * null rectangle. Measured in doubles, as CoreGraphics measures in CGFloat.
 */
function boundingBoxOfPath(path: readonly (readonly GregorianPathCommand[])[]): GregorianRect | null {
  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;
  const include = (x: number, y: number) => {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
  };
  const includePoint = (point: GregorianPoint) => include(point.x, point.y);

  for (const commands of path) {
    // Each outline is a path of its own, added to the glyph's, as the Swift package builds it.
    let current: GregorianPoint | null = null;
    let subpathStart: GregorianPoint | null = null;
    for (const command of commands) {
      switch (command.kind) {
        case "move":
          includePoint(command.point);
          current = command.point;
          subpathStart = command.point;
          break;
        case "line":
          // CoreGraphics ignores a segment with no current point.
          if (current == null) continue;
          includePoint(command.point);
          current = command.point;
          break;
        case "quadratic": {
          const start = current;
          if (start == null) continue;
          includePoint(command.end);
          const xs = [start.x, command.control.x, command.end.x];
          const ys = [start.y, command.control.y, command.end.y];
          for (const t of [...quadraticExtremes(xs), ...quadraticExtremes(ys)]) {
            include(quadratic(xs, t), quadratic(ys, t));
          }
          current = command.end;
          break;
        }
        case "cubic": {
          const start = current;
          if (start == null) continue;
          includePoint(command.end);
          const xs = [start.x, command.control1.x, command.control2.x, command.end.x];
          const ys = [start.y, command.control1.y, command.control2.y, command.end.y];
          for (const t of [...cubicExtremes(xs), ...cubicExtremes(ys)]) {
            include(cubic(xs, t), cubic(ys, t));
          }
          current = command.end;
          break;
        }
        case "close":
          current = subpathStart;
          break;
      }
    }
  }
  if (minX > maxX || minY > maxY) return null;
  return new GregorianRect(minX, minY, maxX, maxY);
}

/** Where, strictly between its ends, a quadratic Bézier coordinate turns. */
function quadraticExtremes(p: readonly number[]): number[] {
  const denominator = p[0]! - 2 * p[1]! + p[2]!;
  if (denominator === 0) return [];
  return [(p[0]! - p[1]!) / denominator].filter((t) => t > 0 && t < 1);
}

/** Where, strictly between its ends, a cubic Bézier coordinate turns: the roots of its derivative. */
function cubicExtremes(p: readonly number[]): number[] {
  const a = -p[0]! + 3 * p[1]! - 3 * p[2]! + p[3]!;
  const b = 2 * (p[0]! - 2 * p[1]! + p[2]!);
  const c = p[1]! - p[0]!;
  let roots: number[];
  if (a === 0) {
    roots = b === 0 ? [] : [-c / b];
  } else {
    const discriminant = b * b - 4 * a * c;
    if (discriminant < 0) {
      roots = [];
    } else {
      // The stable form of the quadratic formula.
      const q = -0.5 * (b + copySign(Math.sqrt(discriminant), b));
      roots = q === 0 ? [0] : [q / a, c / q];
    }
  }
  return roots.filter((t) => t > 0 && t < 1);
}

function quadratic(p: readonly number[], t: number): number {
  const u = 1 - t;
  return u * u * p[0]! + 2 * u * t * p[1]! + t * t * p[2]!;
}

function cubic(p: readonly number[], t: number): number {
  const u = 1 - t;
  return u * u * u * p[0]! + 3 * u * u * t * p[1]! + 3 * u * t * t * p[2]! + t * t * t * p[3]!;
}

/** Java's `Math.copySign`: the magnitude of the first, with the sign of the second, negative zero's included. */
function copySign(magnitude: number, sign: number): number {
  return sign < 0 || Object.is(sign, -0) ? -Math.abs(magnitude) : Math.abs(magnitude);
}

/** Kotlin's `checkNotNull`: the value, and an error with the message where it is null. */
function checkNotNull<T>(value: T | null | undefined, message: string): T {
  if (value == null) throw new Error(message);
  return value;
}
