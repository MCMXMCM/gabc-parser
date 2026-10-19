package io.github.mcmxmcm.gabc.layout

import kotlin.math.max
import kotlin.math.min

// The layout's geometry, in points of the width it is laid out at, from the top left with y running down: the
// counterpart of the CGPoint, CGSize and CGRect the Swift package lays out in. Its names follow Compose's Offset,
// Size and Rect, which the Compose module converts them to.

/** A point in the layout. */
public data class GregorianPoint(val x: Float, val y: Float) {
    public companion object {
        public val Zero: GregorianPoint = GregorianPoint(0f, 0f)
    }
}

/** A size in points. */
public data class GregorianSize(val width: Float, val height: Float)

/** A rectangle by its edges. */
public data class GregorianRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val size: GregorianSize get() = GregorianSize(width, height)
    val topLeft: GregorianPoint get() = GregorianPoint(left, top)
    val center: GregorianPoint get() = GregorianPoint((left + right) / 2, (top + bottom) / 2)

    /** True when the rectangle encloses no area. */
    val isEmpty: Boolean get() = left >= right || top >= bottom

    /** The smallest rectangle that holds both. */
    public fun union(other: GregorianRect): GregorianRect =
        GregorianRect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    /** True when the two share some area. */
    public fun overlaps(other: GregorianRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** True when the point lies within the rectangle, its left and top edges included and its right and bottom not. */
    public operator fun contains(point: GregorianPoint): Boolean =
        point.x >= left && point.x < right && point.y >= top && point.y < bottom

    /** True when the other rectangle lies wholly within this one. */
    public operator fun contains(other: GregorianRect): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    /** The rectangle moved by an offset. */
    public fun translate(dx: Float, dy: Float): GregorianRect = GregorianRect(left + dx, top + dy, right + dx, bottom + dy)

    /** The rectangle drawn in by `dx` at each side and `dy` at top and bottom; negative values draw it out. */
    public fun inset(dx: Float, dy: Float): GregorianRect = GregorianRect(left + dx, top + dy, right - dx, bottom - dy)

    public companion object {
        public val Zero: GregorianRect = GregorianRect(0f, 0f, 0f, 0f)

        /** A rectangle by its origin and size, as CGRect is given. */
        public fun of(x: Float, y: Float, width: Float, height: Float): GregorianRect = GregorianRect(x, y, x + width, y + height)
    }
}
