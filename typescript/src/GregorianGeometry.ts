// The layout's geometry, in points of the width it is laid out at, from the top left with y running down: the
// counterpart of the CGPoint, CGSize and CGRect the Swift package lays out in, by edges as the Kotlin port has it.

/** A point in the layout. */
export interface GregorianPoint {
  readonly x: number;
  readonly y: number;
}

/** A size in points. */
export interface GregorianSize {
  readonly width: number;
  readonly height: number;
}

export function point(x: number, y: number): GregorianPoint {
  return { x, y };
}

/**
 * A rectangle by its edges. One given by its origin and size keeps that size exactly, as a CGRect does, moved or drawn
 * in: in floating point the difference of two edges need not give back the size they were made from.
 */
export class GregorianRect {
  readonly width: number;
  readonly height: number;

  constructor(
    readonly left: number,
    readonly top: number,
    readonly right: number,
    readonly bottom: number,
    size: GregorianSize = { width: right - left, height: bottom - top },
  ) {
    this.width = size.width;
    this.height = size.height;
  }

  static readonly zero = new GregorianRect(0, 0, 0, 0);

  /** A rectangle by its origin and size, as CGRect is given. */
  static of(x: number, y: number, width: number, height: number): GregorianRect {
    return new GregorianRect(x, y, x + width, y + height, { width, height });
  }

  get size(): GregorianSize {
    return { width: this.width, height: this.height };
  }

  get topLeft(): GregorianPoint {
    return { x: this.left, y: this.top };
  }

  get center(): GregorianPoint {
    return { x: (this.left + this.right) / 2, y: (this.top + this.bottom) / 2 };
  }

  /** True when the rectangle encloses no area. */
  get isEmpty(): boolean {
    return this.left >= this.right || this.top >= this.bottom;
  }

  /** The smallest rectangle that holds both. */
  union(other: GregorianRect): GregorianRect {
    return new GregorianRect(
      Math.min(this.left, other.left),
      Math.min(this.top, other.top),
      Math.max(this.right, other.right),
      Math.max(this.bottom, other.bottom),
    );
  }

  /** True when the two share some area. */
  overlaps(other: GregorianRect): boolean {
    return this.left < other.right && other.left < this.right && this.top < other.bottom && other.top < this.bottom;
  }

  /** True when the point lies within the rectangle, its left and top edges included and its right and bottom not. */
  containsPoint(point: GregorianPoint): boolean {
    return point.x >= this.left && point.x < this.right && point.y >= this.top && point.y < this.bottom;
  }

  /** True when the other rectangle lies wholly within this one. */
  contains(other: GregorianRect): boolean {
    return other.left >= this.left && other.right <= this.right && other.top >= this.top && other.bottom <= this.bottom;
  }

  /** The rectangle moved by an offset. */
  translate(dx: number, dy: number): GregorianRect {
    return GregorianRect.of(this.left + dx, this.top + dy, this.width, this.height);
  }

  /** The rectangle drawn in by `dx` at each side and `dy` at top and bottom; negative values draw it out. */
  inset(dx: number, dy: number): GregorianRect {
    return GregorianRect.of(this.left + dx, this.top + dy, this.width - 2 * dx, this.height - 2 * dy);
  }
}
