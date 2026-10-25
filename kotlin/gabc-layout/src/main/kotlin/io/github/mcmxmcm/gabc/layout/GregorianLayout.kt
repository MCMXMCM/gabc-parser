package io.github.mcmxmcm.gabc.layout

import io.github.mcmxmcm.gabc.parser.GregorianAccidental
import io.github.mcmxmcm.gabc.parser.GregorianClef
import io.github.mcmxmcm.gabc.parser.GregorianClefKind
import io.github.mcmxmcm.gabc.parser.GregorianDivision
import io.github.mcmxmcm.gabc.parser.GregorianEpisemaPosition
import io.github.mcmxmcm.gabc.parser.GregorianLiquescence
import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle
import io.github.mcmxmcm.gabc.parser.GregorianNeume
import io.github.mcmxmcm.gabc.parser.GregorianNeumeForm
import io.github.mcmxmcm.gabc.parser.GregorianNeumeSpace
import io.github.mcmxmcm.gabc.parser.GregorianNotationElement
import io.github.mcmxmcm.gabc.parser.GregorianNote
import io.github.mcmxmcm.gabc.parser.GregorianNoteShape
import io.github.mcmxmcm.gabc.parser.GregorianScore
import java.text.BreakIterator
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The engraving of a score: its units measured and broken into staffs, then each note, word and mark set in place,
// in points of the width it is laid out at. A port of the Swift package's GregorianLayout.swift.

/** The pixel grid of the screen a layout is drawn on, if one is given. */
private class PixelGrid(
    /** Pixels in a point; null sets everything where it falls. */
    val scale: Float?,
) {
    /** The nearest length of whole pixels, one at least. */
    fun wholePixels(length: Float): Float = scale?.let { max(1f, rounded(length * it)) / it } ?: length

    /** A length rounded up to whole pixels. */
    fun roundedUp(length: Float): Float = scale?.let { ceil(length * it - 0.001f) / it } ?: length

    /** A line's width in whole pixels, two at least. */
    fun lineWidth(width: Float): Float = scale?.let { max(2f, rounded(width * it)) / it } ?: width

    /**
     * Where a line of [width], already whole pixels, is centred near [position] so that its edges fall between pixels:
     * on the edge of a pixel if it is an even number of pixels wide, on the middle of one if odd.
     */
    fun centre(position: Float, width: Float, roundingUp: Boolean = false): Float {
        val scale = scale ?: return position
        val offset = if (rounded(width * scale) % 2f == 0f) 0f else 0.5f
        val pixels = position * scale - offset
        return ((if (roundingUp) ceil(pixels - 0.001f) else rounded(pixels)) + offset) / scale
    }

    /** Strokes snapped to the grid: each whole pixels wide, and set across or up and down where its edges fall between pixels. */
    fun snap(strokes: List<GregorianPlacedStroke>): List<GregorianPlacedStroke> {
        if (scale == null) return strokes
        return strokes.map { stroke ->
            val width = lineWidth(stroke.lineWidth)
            when {
                abs(stroke.start.y - stroke.end.y) < 0.001f -> {
                    val y = centre(stroke.start.y, width)
                    stroke.copy(start = GregorianPoint(stroke.start.x, y), end = GregorianPoint(stroke.end.x, y), lineWidth = width)
                }
                abs(stroke.start.x - stroke.end.x) < 0.001f -> {
                    val x = centre(stroke.start.x, width)
                    stroke.copy(start = GregorianPoint(x, stroke.start.y), end = GregorianPoint(x, stroke.end.y), lineWidth = width)
                }
                else -> stroke.copy(lineWidth = width)
            }
        }
    }

    private companion object {
        /** Rounded half away from zero, as Swift rounds. */
        fun rounded(value: Float): Float = if (value >= 0) floor(value + 0.5f) else -floor(-value + 0.5f)
    }
}

public data class GregorianLayoutMetrics(
    val notationScale: Double = 1.0,
    val lyricScale: Double = 1.0,
    /** The PostScript name of the font lyrics are measured in. Whatever draws the layout should draw its lyrics in the font each one names. */
    val lyricFontName: String = GregorianLyricFont.DEFAULT_NAME,
    /**
     * The pixels in a point of the screen the layout is drawn on. Given, the staff space is a whole number of pixels,
     * and the staff and ledger lines, stems, episemata and bars are whole pixels wide, two at least, and set where their
     * edges fall between pixels, so that they draw crisply; null, as for print, sets them where they fall.
     */
    val displayScale: Double? = null,
) {
    public companion object {
        public const val MINIMUM_NOTATION_SCALE: Double = 0.6
        public const val MAXIMUM_NOTATION_SCALE: Double = 1.6
        public val notationScaleRange: ClosedFloatingPointRange<Double> = MINIMUM_NOTATION_SCALE..MAXIMUM_NOTATION_SCALE

        public fun clampedNotationScale(scale: Double): Double = scale.coerceIn(MINIMUM_NOTATION_SCALE, MAXIMUM_NOTATION_SCALE)
    }
}

public sealed interface GregorianGlyphKind {
    // Stable cases kept for tests and accessibility consumers.
    public data object CClef : GregorianGlyphKind
    public data object FClef : GregorianGlyphKind
    public data class Notehead(val shape: GregorianNoteShape) : GregorianGlyphKind
    public data object Flat : GregorianGlyphKind
    public data object Natural : GregorianGlyphKind
    public data object Sharp : GregorianGlyphKind
    public data object Mora : GregorianGlyphKind
    public data object Custos : GregorianGlyphKind
    public data class Catalog(val name: GregorianGlyphName) : GregorianGlyphKind

    public val catalogName: GregorianGlyphName
        get() = when (this) {
            CClef -> GregorianGlyphName.DoClef
            FClef -> GregorianGlyphName.FaClef
            is Notehead -> when (shape) {
                GregorianNoteShape.Punctum -> GregorianGlyphName.PunctumQuadratum
                GregorianNoteShape.Virga -> GregorianGlyphName.VirgaShort
                GregorianNoteShape.Quilisma -> GregorianGlyphName.Quilisma
                GregorianNoteShape.Liquescent -> GregorianGlyphName.PunctumQuadratumLiquescent
                GregorianNoteShape.Inclinatum -> GregorianGlyphName.PunctumInclinatum
                GregorianNoteShape.Oriscus -> GregorianGlyphName.OriscusAsc
                GregorianNoteShape.Stropha -> GregorianGlyphName.Stropha
                GregorianNoteShape.Custos -> GregorianGlyphName.CustosShort
            }
            Flat -> GregorianGlyphName.Flat
            Natural -> GregorianGlyphName.Natural
            Sharp -> GregorianGlyphName.Sharp
            Mora -> GregorianGlyphName.Mora
            Custos -> GregorianGlyphName.CustosShort
            is Catalog -> name
        }
}

public sealed interface GregorianStrokeKind {
    public data object Staff : GregorianStrokeKind
    public data object Ledger : GregorianStrokeKind
    public data object Connector : GregorianStrokeKind
    public data object Episema : GregorianStrokeKind
    public data class Division(val division: GregorianDivision) : GregorianStrokeKind
}

public data class GregorianPlacedGlyph(val kind: GregorianGlyphKind, val frame: GregorianRect, val eventId: String? = null)

public data class GregorianPlacedStroke(
    val kind: GregorianStrokeKind,
    val start: GregorianPoint,
    val end: GregorianPoint,
    val lineWidth: Float,
    val eventId: String? = null,
)

/** A lyric, set from the top left of its line. */
public data class GregorianPlacedLyric(
    val text: String,
    val style: GregorianLyricStyle = GregorianLyricStyle.Regular,
    val origin: GregorianPoint,
    val width: Float,
    val fontSize: Float,
    /** The PostScript name of the font the lyric was measured in. */
    val fontName: String = GregorianLyricFont.DEFAULT_NAME,
    val neumeId: String,
)

/** The large initial a score opens with, its mode above it. */
public data class GregorianPlacedInitial(
    val text: String,
    val annotation: String? = null,
    val annotationOrigin: GregorianPoint? = null,
    val annotationFontSize: Float = 0f,
    val origin: GregorianPoint,
    val width: Float,
    val fontSize: Float,
    /** The PostScript name of the font the initial and its annotation were measured in. */
    val fontName: String = GregorianLyricFont.DEFAULT_NAME,
    val neumeId: String,
    /** The style the initial and its annotation were measured in, and must be drawn in: bold, as the chant books set them. */
    val style: GregorianLyricStyle = GregorianLyricStyle.Regular,
)

public data class GregorianEventPlacement(val eventId: String, val neumeId: String, val frame: GregorianRect, val lineIndex: Int) {
    val id: String get() = eventId
}

public data class GregorianNeumePlacement(
    val id: String,
    val eventIds: List<String>,
    /** The notes the neume draws: more than its events when a note is written twice or thrice (`hvv`, `hsss`). */
    val noteCount: Int = eventIds.size,
    val lyric: String,
    val inkFrame: GregorianRect,
    val hitFrame: GregorianRect,
    val lineIndex: Int,
)

public data class GregorianStaffLayout(
    val index: Int,
    val frame: GregorianRect,
    val systemFrame: GregorianRect = frame,
    val firstEventId: String?,
) {
    val id: Int get() = index
}

public class GregorianLayout(
    public val size: GregorianSize,
    public val staffs: List<GregorianStaffLayout>,
    public val glyphs: List<GregorianPlacedGlyph>,
    public val strokes: List<GregorianPlacedStroke>,
    public val lyrics: List<GregorianPlacedLyric>,
    public val initials: List<GregorianPlacedInitial> = emptyList(),
    public val events: List<GregorianEventPlacement>,
    public val neumes: List<GregorianNeumePlacement>,
) {
    public fun neumeContaining(eventId: String): GregorianNeumePlacement? = neumes.firstOrNull { eventId in it.eventIds }
}

/**
 * Native Gregorian engraving with the same three-stage shape as Exsurge: semantic units, intrinsic measurement and
 * line breaking, then positioned ink. Lyrics are measured by the [measurer], in the font each layout's metrics name.
 */
public class GregorianEngravingLayoutEngine(private val measurer: GregorianLyricMeasurer) {
    /**
     * Lays out a score at a width.
     *
     * @param openingLabel The label printed above the initial; the initial is drawn where it is a psalm tone's (`8g`),
     *   or where the score opens a piece.
     * @param opensPiece Whether the score opens a piece, a hymn, a responsory or a part of the Mass, which takes an
     *   initial, with its mode alone above it if it has no psalm tone.
     */
    public fun layout(
        score: GregorianScore,
        width: Float,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        openingLabel: String? = null,
        opensPiece: Boolean = false,
    ): GregorianLayout {
        val requestedWidth = width
        val requestedNotationScale = GregorianLayoutMetrics.clampedNotationScale(metrics.notationScale).toFloat()
        val grid = PixelGrid(metrics.displayScale?.toFloat()?.takeIf { it > 0f })
        // On a screen, a staff space of whole pixels sets every line of the staff alike.
        val notationScale = grid.wholePixels(12.5f * requestedNotationScale) / 12.5f
        val lyricScale = (metrics.lyricScale.toFloat() * requestedNotationScale).coerceIn(0.5f, 3.5f)
        val lyricFontName = metrics.lyricFontName
        val columnWidth = max(240f, requestedWidth)
        val glyphScale = notationScale / 16
        val staffStep = 100 * glyphScale // one GABC pitch step, half a staff space
        val staffGap = staffStep * 2
        val staffLineWidth = grid.lineWidth(max(0.72f, 0.78f * notationScale))
        val sidePadding = 8 * notationScale
        val clefColumn = 18 * notationScale
        val custosColumn = 10 * notationScale
        val lyricFontSize = 21 * lyricScale
        val lyricHeight = ceil(lyricFontSize * LYRIC_LINE_HEIGHT_MULTIPLIER)
        val openingInitial = openingInitial(score, openingLabel(openingLabel, opensPiece), opensPiece)
        val initialScale = max(notationScale, min(1.35f, lyricScale))
        val initialFontSize = 56 * initialScale
        val initialAnnotationFontSize = 13 * max(notationScale, min(1.25f, lyricScale))
        // The initial and its annotation are bold, as the books set them.
        val initialWidth = openingInitial?.let {
            measureLyric(it.text, GregorianLyricStyle.Accented, fontSize = initialFontSize, fontName = lyricFontName)
        } ?: 0f
        val initialAnnotationWidth = openingInitial?.annotation?.let {
            measureLyric(it, GregorianLyricStyle.Accented, fontSize = initialAnnotationFontSize, fontName = lyricFontName)
        } ?: 0f
        val ascent = measurer.ascent(lyricFontName)
        val capHeight = measurer.capHeight(lyricFontName)
        val initialColumn = if (openingInitial != null) {
            max(38 * notationScale, max(initialWidth, initialAnnotationWidth) + 8 * notationScale)
        } else {
            0f
        }

        val hyphenWidth = measureLyric("-", fontSize = lyricFontSize, fontName = lyricFontName)
        val spacing = Spacing(staffGap, hyphenWidth)
        val units = makeUnits(score, glyphScale, notationScale, lyricFontSize, lyricFontName, openingInitial)
        fun lineFrame(lineIndex: Int): LineFrame {
            val staffLeft = sidePadding + if (lineIndex == 0) initialColumn else 0f
            return LineFrame(
                staffLeft = staffLeft,
                notationStart = staffLeft + clefColumn + 4 * notationScale,
                notationEnd = columnWidth - sidePadding - custosColumn,
                textEnd = columnWidth - sidePadding,
            )
        }
        val wordContinues = wordContinuation(units)
        val lineRanges = breakLines(units, ::lineFrame, spacing, wordContinues)
        val lines = lineRanges.map { units.subList(it.first, it.last + 1) }
        val placements = lineRanges.mapIndexed { lineIndex, range ->
            placeLine(
                units, range, lineFrame(lineIndex), spacing, wordContinues[range.last + 1],
                endsParagraph = range.isEmpty() || range.last + 1 == units.size || units[range.last].forcesBreak,
            )
        }
        val lineVerticalMetrics = lines.mapIndexed { lineIndex, lineUnits ->
            verticalMetrics(
                lineUnits, custosPitch(lineIndex, lines), glyphScale, staffStep, staffGap, notationScale, lyricHeight,
                if (lineIndex == 0 && openingInitial != null) initialFontSize else null, grid, staffLineWidth,
            )
        }
        val lineOrigins = ArrayList<Float>(lines.size)
        var contentHeight = 0f
        for (lineMetrics in lineVerticalMetrics) {
            lineOrigins += contentHeight
            contentHeight += lineMetrics.height
        }

        val staffs = mutableListOf<GregorianStaffLayout>()
        val glyphs = mutableListOf<GregorianPlacedGlyph>()
        var strokes = mutableListOf<GregorianPlacedStroke>()
        val lyrics = mutableListOf<GregorianPlacedLyric>()
        val initials = mutableListOf<GregorianPlacedInitial>()
        val events = mutableListOf<GregorianEventPlacement>()
        val rawNeumes = mutableListOf<RawNeumePlacement>()
        var activeClef = GregorianClef(GregorianClefKind.C, 4)
        val punctumWidth = 100 * glyphScale

        for ((lineIndex, lineUnits) in lines.withIndex()) {
            val lineOriginY = lineOrigins[lineIndex]
            val lineMetrics = lineVerticalMetrics[lineIndex]
            val staffTop = lineOriginY + lineMetrics.topInset
            val staffBottom = staffTop + staffGap * 3
            val staffCenter = (staffTop + staffBottom) / 2
            val frame = lineFrame(lineIndex)
            val staffLeft = frame.staffLeft
            val lyricY = staffBottom + lineMetrics.lyricOffset
            val placement = placements[lineIndex]
            val firstEventId = lineUnits.firstNotNullOfOrNull { it.neume?.id }
            staffs += GregorianStaffLayout(
                index = lineIndex,
                frame = GregorianRect.of(staffLeft, staffTop, columnWidth - sidePadding - staffLeft, staffGap * 3),
                systemFrame = GregorianRect.of(0f, lineOriginY, columnWidth, lineMetrics.height),
                firstEventId = firstEventId,
            )

            for (staffLine in 0 until 4) {
                val y = staffTop + staffLine * staffGap
                strokes += GregorianPlacedStroke(
                    GregorianStrokeKind.Staff,
                    GregorianPoint(staffLeft, y),
                    GregorianPoint(columnWidth - sidePadding, y),
                    lineWidth = staffLineWidth,
                )
            }

            var leadingClefCount = 0
            for (unit in lineUnits) {
                val clef = (unit.element as? GregorianNotationElement.Clef)?.clef ?: break
                activeClef = clef
                leadingClefCount += 1
            }
            appendClef(activeClef, staffLeft + 2 * notationScale, staffTop, staffGap, glyphScale, notationScale, glyphs)

            if (lineIndex == 0 && openingInitial != null) {
                // The initial stands on the words' baseline, and its annotation is centred over it, its capitals about
                // the top line, as in the Liber.
                val lyricBaseline = lyricY + ascent * lyricFontSize
                val annotationBaseline = staffTop - 0.14f * staffGap + capHeight * initialAnnotationFontSize / 2
                initials += GregorianPlacedInitial(
                    text = openingInitial.text,
                    annotation = openingInitial.annotation,
                    annotationOrigin = openingInitial.annotation?.let {
                        GregorianPoint(
                            sidePadding + (initialColumn - initialAnnotationWidth) / 2,
                            max(0f, annotationBaseline - ascent * initialAnnotationFontSize),
                        )
                    },
                    annotationFontSize = initialAnnotationFontSize,
                    origin = GregorianPoint(sidePadding + (initialColumn - initialWidth) / 2, lyricBaseline - ascent * initialFontSize),
                    width = initialWidth,
                    fontSize = initialFontSize,
                    fontName = lyricFontName,
                    neumeId = openingInitial.neumeId,
                    style = GregorianLyricStyle.Accented,
                )
            }

            for ((unitIndex, unit) in lineUnits.withIndex()) {
                val x = placement.x[unitIndex]
                when (val element = unit.element) {
                    is GregorianNotationElement.Clef -> {
                        // The clefs a staff opens with are set at its head, and take no room in it.
                        if (unitIndex < leadingClefCount) continue
                        activeClef = element.clef
                        appendClef(element.clef, x, staffTop, staffGap, glyphScale, notationScale, glyphs)
                    }
                    is GregorianNotationElement.Accidental -> {
                        val y = staffCenter - (element.mark.pitch - 6) * staffStep
                        glyphs += placedGlyph(accidentalKind(element.mark.kind), GregorianPoint(x, y), glyphScale * 0.9f)
                    }
                    is GregorianNotationElement.Division ->
                        appendDivision(element.division, x + unit.inkRight / 2, staffTop, staffGap, notationScale, strokes)
                    GregorianNotationElement.ForcedBreak -> Unit
                    is GregorianNotationElement.LyricMark -> lyrics += GregorianPlacedLyric(
                        text = element.mark.text,
                        style = element.mark.style,
                        origin = GregorianPoint(placement.lyricX[unitIndex] ?: x, lyricY),
                        width = unit.lyricWidth,
                        fontSize = lyricFontSize,
                        fontName = lyricFontName,
                        neumeId = element.mark.id,
                    )
                    is GregorianNotationElement.Neume -> {
                        val neume = element.neume
                        val composition = compose(
                            neume, unit.context, x - punctumWidth / 2, staffCenter, staffTop, staffBottom, staffStep, glyphScale, notationScale, lineIndex,
                        )
                        glyphs += composition.glyphs
                        strokes += composition.strokes
                        events += composition.events

                        val lyricX = placement.lyricX[unitIndex]
                        if (unit.hasLyric && lyricX != null) {
                            lyrics += GregorianPlacedLyric(
                                text = unit.lyricText,
                                style = neume.lyricStyle,
                                origin = GregorianPoint(lyricX, lyricY),
                                width = unit.lyricWidth,
                                fontSize = lyricFontSize,
                                fontName = lyricFontName,
                                neumeId = neume.id,
                            )
                        }
                        // A custos is drawn and keeps its event's place, but it is not sung: the cantor guide cannot
                        // begin from it.
                        if (neume.writtenNoteCount > 0) {
                            rawNeumes += RawNeumePlacement(
                                neume, composition.inkFrame ?: GregorianRect.Zero, lineIndex, staffTop,
                                staffBottom + lineMetrics.lyricOffset + lyricHeight,
                            )
                        }
                    }
                }
            }

            // The hyphen is the lyrics' own, set just after its syllable, whatever the gap that follows; one at the
            // staff's end divides a word that goes on in the next.
            for (unitIndex in placement.hyphenated) {
                val unit = lineUnits[unitIndex]
                val nextId = units.subList(lineRanges[lineIndex].first + unitIndex + 1, units.size)
                    .firstOrNull { it.hasLyric }?.lyricId
                val lyricX = placement.lyricX[unitIndex]
                val id = unit.lyricId
                if (lyricX == null || id == null || nextId == null) continue
                lyrics += GregorianPlacedLyric(
                    text = "-",
                    origin = GregorianPoint(lyricX + unit.lyricWidth, lyricY),
                    width = hyphenWidth,
                    fontSize = lyricFontSize,
                    fontName = lyricFontName,
                    neumeId = "lyric-hyphen-$id-$nextId",
                )
            }

            val custosPitch = custosPitch(lineIndex, lines)
            if (custosPitch != null) {
                val nextY = staffCenter - (custosPitch - 6) * staffStep
                // The custos stands flush with the staff's end.
                val custosWidth = GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).bounds.width * glyphScale
                val custosX = columnWidth - sidePadding - custosWidth
                glyphs += placedGlyph(GregorianGlyphKind.Custos, GregorianPoint(custosX, nextY), glyphScale)
            }
        }

        strokes = grid.snap(mergeLedgerLines(strokes, notationScale)).toMutableList()
        val neumes = makeHitFrames(rawNeumes, columnWidth, notationScale)
        return GregorianLayout(
            size = GregorianSize(columnWidth, max(64f, contentHeight)),
            staffs = staffs,
            glyphs = glyphs,
            strokes = strokes,
            lyrics = lyrics,
            initials = initials,
            events = events,
            neumes = neumes,
        )
    }

    /** How a syllable's lyric meets the lyric before it. */
    private enum class LyricJoin {
        /** It begins a word, or follows a rubric: a word's space parts them. */
        Word,

        /** It is the next syllable of the word: joined to the one before, or parted from it by one hyphen. */
        Syllable,

        /** One of the two writes its own hyphen at the join: they may touch, and take no other. */
        Written,
    }

    private class LayoutUnit(
        val element: GregorianNotationElement,
        /**
         * The unit's ink, reckoned from where it is placed: a neume's from the centre of its first note, a clef's, an
         * accidental's or a division's from the left of the room it takes, which runs to [inkRight].
         */
        val inkLeft: Float,
        val inkRight: Float,
        val lyricText: String,
        val lyricWidth: Float,
        /** From the lyric's left edge to the centre of its sung vowel. */
        val lyricFocus: Float,
        val join: LyricJoin,
        val forcesBreak: Boolean,
        /** The notes beside a neume that its engraving needs. */
        val context: NeumeContext = NeumeContext(),
        /** How far the unit's ink reaches below the staff's bottom line. */
        val inkBelowStaff: Float = 0f,
    ) {
        val neume: GregorianNeume? get() = (element as? GregorianNotationElement.Neume)?.neume

        val hasLyric: Boolean get() = lyricText.isNotEmpty()

        val lyricId: String?
            get() = when (element) {
                is GregorianNotationElement.Neume -> element.neume.id
                is GregorianNotationElement.LyricMark -> element.mark.id
                else -> null
            }
    }

    /**
     * The room a staff gives its notes and words: where the first note may stand, clear of the clef; where the notes
     * must end, short of the custos; and where the words must end, which may run on under the custos.
     */
    private class LineFrame(val staffLeft: Float, val notationStart: Float, val notationEnd: Float, val textEnd: Float)

    /**
     * A staff's units, set in place: each unit's place, from which its `inkLeft` and `inkRight` are reckoned; the left
     * edge of each unit's lyric; and the units whose syllable a hyphen follows, within the staff or at its end.
     */
    private class LinePlacement(
        val x: FloatArray,
        val lyricX: Array<Float?>,
        var notationRight: Float,
        var textRight: Float,
    ) {
        val hyphenated: MutableList<Int> = mutableListOf()

        /** The words, bars, rubrics and clefs the staff may stretch before. */
        var stretchPoints = 0

        /** The share of the staff's note spaces that it may give up. */
        var spare = 0f

        fun fits(frame: LineFrame): Boolean =
            notationRight <= frame.notationEnd + 0.01f && textRight <= frame.textEnd + 0.01f

        /**
         * The room the staff leaves at its end: by the notes, short of the custos, or by the words, whichever leaves less.
         * It is reckoned to a hundredth of a point, so that no choice of breaks turns on how the sums were rounded.
         */
        fun slack(frame: LineFrame): Float {
            val slack = min(frame.notationEnd - notationRight, frame.textEnd - textRight)
            return floor(slack * 100 + 0.5f) / 100
        }
    }

    /**
     * The spaces a staff keeps between its notes and between its words, in points: Exsurge's, reckoned from the staff
     * space. [word] is the least space between two words' texts; [hyphen], a hyphen's
     * advance, the least space between two syllables of a word that do not touch.
     */
    private class Spacing(staffSpace: Float, val hyphen: Float) {
        /** Between the neumes of one syllable. */
        val withinSyllable = 0.25f * staffSpace

        /** Between one syllable's notes and the next's, within a word. */
        val betweenSyllables = 0.625f * staffSpace

        /** Between one word's notes and the next's. */
        val betweenWords = 0.875f * staffSpace

        /** After a bar, beyond the half of its room that follows its line. */
        val afterDivision = 0.25f * staffSpace

        /** Before a clef within the staff. */
        val beforeClef = 0.5f * staffSpace

        val word = max(hyphen, 0.5f * staffSpace)

        /**
         * The most a staff that ends a paragraph is stretched at each word or bar, as Exsurge stretches one; the
         * measure of the stretch of any other.
         */
        val maximumStretch = 0.5f * staffSpace
    }

    private class RawNeumePlacement(
        val neume: GregorianNeume,
        val inkFrame: GregorianRect,
        val lineIndex: Int,
        val staffTop: Float,
        val staffBottom: Float,
    )

    private class Composition {
        val glyphs = mutableListOf<GregorianPlacedGlyph>()
        val strokes = mutableListOf<GregorianPlacedStroke>()
        val events = mutableListOf<GregorianEventPlacement>()
        var inkFrame: GregorianRect? = null

        fun ink(frame: GregorianRect) {
            inkFrame = inkFrame?.union(frame) ?: frame
        }
    }

    private class LineVerticalMetrics(val topInset: Float, val lyricOffset: Float, val height: Float)

    private class OpeningInitial(val text: String, val annotation: String?, val remainder: String, val neumeId: String)

    private fun openingInitial(score: GregorianScore, annotation: String?, opensPiece: Boolean): OpeningInitial? {
        if (annotation == null && !opensPiece) return null
        val neume = score.neumes.firstOrNull { it.lyric.isNotEmpty() } ?: return null
        val characters = graphemes(neume.lyric)
        val initialIndex = characters.indexOfFirst { cluster -> cluster.any(Char::isUpperCase) }
        if (initialIndex < 0) return null
        val prefixIsSilent = characters.take(initialIndex).all { cluster ->
            cluster.all { it.isWhitespace() || isNumber(it) || it == '.' }
        }
        if (!prefixIsSilent) return null
        // The rest of the word the initial begins is small, however the GABC writes it (PU-ER).
        return OpeningInitial(
            text = characters[initialIndex],
            annotation = annotation,
            remainder = characters.drop(initialIndex + 1).joinToString("").lowercase(),
            neumeId = neume.id,
        )
    }

    /**
     * The pitch of the custos that ends a staff: the next staff's first note. The last staff has none, and a custos
     * written in the score (`f+`, as before a change of clef) already guides the eye to what follows, so the staff
     * that ends with one is not given another.
     */
    private fun custosPitch(lineIndex: Int, lines: List<List<LayoutUnit>>): Int? {
        if (lineIndex + 1 >= lines.size) return null
        if (lines[lineIndex].mapNotNull { it.neume }.lastOrNull()?.notes?.lastOrNull()?.isCustos == true) return null
        return lines[lineIndex + 1].firstNotNullOfOrNull { it.neume }?.notes?.firstOrNull()?.pitch
    }

    private fun verticalMetrics(
        units: List<LayoutUnit>,
        custosPitch: Int?,
        glyphScale: Float,
        staffStep: Float,
        staffGap: Float,
        notationScale: Float,
        lyricHeight: Float,
        openingInitialFontSize: Float?,
        grid: PixelGrid,
        staffLineWidth: Float,
    ): LineVerticalMetrics {
        val notes = units.mapNotNull { it.neume }.flatMap { it.notes }
        val accidentalPitches = units.mapNotNull { (it.element as? GregorianNotationElement.Accidental)?.mark?.pitch }
        val pitches = notes.map { it.pitch } + accidentalPitches + listOfNotNull(custosPitch)
        val highestPitch = pitches.maxOrNull() ?: 9
        var topInset = max(11 * notationScale, max(0, highestPitch - 9) * staffStep + 10 * notationScale)
        // A custos's stem rises farther above its pitch than a note's head: the staff is set low enough to hold it.
        val highestCustos = (notes.filter { it.isCustos }.map { it.pitch } + listOfNotNull(custosPitch)).maxOrNull()
        if (highestCustos != null) {
            val stem = GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).origin.y * glyphScale
            topInset = max(topInset, (highestCustos - 9) * staffStep + stem + 2 * notationScale)
        }
        // The words stand as far below the lowest ink as they stand below a note on the bottom line, and no nearer the
        // staff.
        var inkBelow = units.maxOfOrNull { it.inkBelowStaff } ?: 0f
        if (custosPitch != null) {
            val custos = GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort)
            val y = staffGap * 1.5f - (custosPitch - 6) * staffStep
            inkBelow = max(inkBelow, y + (custos.bounds.height - custos.origin.y) * glyphScale - staffGap * 3)
        }
        val punctum = GregorianGlyphCatalog.definition(GregorianGlyphName.PunctumQuadratum)
        val headBelowLine = (punctum.bounds.height - punctum.origin.y) * glyphScale
        val lyricOffset = max(2 * notationScale, inkBelow - headBelowLine + 2 * notationScale)
        // On a screen, each staff begins on a whole pixel, and its top line is set where its edges fall between pixels;
        // the others follow it a whole number of pixels apart.
        topInset = grid.centre(topInset, staffLineWidth, roundingUp = true)
        val systemGap = 4 * notationScale
        val notationHeight = topInset + staffGap * 3 + lyricOffset + lyricHeight + systemGap
        val initialHeight = openingInitialFontSize?.let { topInset + ceil(it * 1.15f) + systemGap } ?: 0f
        return LineVerticalMetrics(topInset, lyricOffset, grid.roundedUp(max(notationHeight, initialHeight)))
    }

    private fun makeUnits(
        score: GregorianScore,
        glyphScale: Float,
        notationScale: Float,
        lyricFontSize: Float,
        lyricFontName: String,
        openingInitial: OpeningInitial?,
    ): List<LayoutUnit> {
        // The last lyric written, and the syllable it was sung on: none for a rubric, which ends any word before it.
        var previousLyricText: String? = null
        var previousLyricSyllableId: String? = null
        val punctum = 100 * glyphScale
        val staffSpace = 200 * glyphScale

        fun unit(
            element: GregorianNotationElement,
            inkRight: Float,
            inkLeft: Float = 0f,
            lyricText: String = "",
            lyricWidth: Float = 0f,
            lyricFocus: Float = 0f,
            join: LyricJoin = LyricJoin.Word,
            forcesBreak: Boolean = false,
            context: NeumeContext = NeumeContext(),
            inkBottom: Float = 0f,
        ) = LayoutUnit(
            element, inkLeft, inkRight, lyricText, lyricWidth, lyricFocus, join, forcesBreak, context,
            // Reckoned on a staff whose top line is at 0.
            inkBelowStaff = max(0f, inkBottom - 3 * staffSpace),
        )

        // A first syllable that the initial takes whole leaves a hyphen under its notes where its word goes on, as
        // Gregorio and Exsurge set it.
        val openingHyphen = run {
            if (openingInitial == null || openingInitial.remainder.isNotEmpty()) return@run false
            val neumes = score.neumes
            val first = neumes.indexOfFirst { it.id == openingInitial.neumeId }
            if (first < 0) return@run false
            val next = neumes.drop(first + 1).firstOrNull { it.lyric.isNotEmpty() } ?: return@run false
            !next.startsWord && next.syllableId != neumes[first].syllableId
        }
        // And so are its other syllables.
        val openingWord = mutableSetOf<String>()
        // A verse number written before the initial's syllable is left out: the initial marks the first verse. The
        // others keep theirs.
        val omittedMarks = mutableSetOf<Int>()
        if (openingInitial != null) {
            val first = score.elements.indexOfFirst { (it as? GregorianNotationElement.Neume)?.neume?.id == openingInitial.neumeId }
            if (first >= 0) {
                for (element in score.elements.drop(first + 1)) {
                    if (element is GregorianNotationElement.LyricMark) break
                    val neume = (element as? GregorianNotationElement.Neume)?.neume ?: continue
                    if (neume.lyric.isEmpty()) continue
                    if (neume.startsWord) break
                    openingWord += neume.id
                }
                for (index in 0 until first) {
                    val mark = (score.elements[index] as? GregorianNotationElement.LyricMark)?.mark ?: continue
                    if (VERSE_NUMBER.matches(mark.text)) omittedMarks += index
                }
            }
        }

        // Each neume's context: the last note of the neume of its syllable that it follows with no space (`!`), and
        // the next neume's first note.
        val elements = score.elements
        val contexts = arrayOfNulls<NeumeContext>(elements.size)
        var nextNote: GregorianNote? = null
        for (index in elements.indices.reversed()) {
            val neume = (elements[index] as? GregorianNotationElement.Neume)?.neume ?: continue
            val previous = (elements.getOrNull(index - 1) as? GregorianNotationElement.Neume)?.neume
            val joinedFrom = if (neume.spaceBefore == GregorianNeumeSpace.Joined && previous?.syllableId == neume.syllableId) {
                previous.notes.lastOrNull()
            } else {
                null
            }
            contexts[index] = NeumeContext(joinedFrom = joinedFrom, next = nextNote)
            val first = neume.notes.firstOrNull()
            if (first != null && !first.isCustos) nextNote = first
        }

        return elements.mapIndexedNotNull { elementIndex, element ->
            when (element) {
                is GregorianNotationElement.Clef -> unit(element, inkRight = 24 * notationScale)
                is GregorianNotationElement.Accidental -> {
                    val definition = GregorianGlyphCatalog.definition(accidentalKind(element.mark.kind).catalogName)
                    val y = staffSpace * 1.5f - (element.mark.pitch - 6) * punctum
                    // The accidental, and the space that parts it from its note: about a quarter of a staff space, as
                    // Gregorio's.
                    unit(
                        element,
                        inkRight = definition.bounds.width * glyphScale * 0.9f + 0.27f * staffSpace,
                        inkBottom = y + (definition.bounds.height - definition.origin.y) * glyphScale * 0.9f,
                    )
                }
                is GregorianNotationElement.Division ->
                    unit(element, inkRight = (if (element.division == GregorianDivision.Final) 17 else 12) * notationScale)
                GregorianNotationElement.ForcedBreak -> unit(element, inkRight = 0f, forcesBreak = true)
                is GregorianNotationElement.LyricMark -> {
                    if (elementIndex in omittedMarks) return@mapIndexedNotNull null
                    previousLyricText = element.mark.text
                    previousLyricSyllableId = null
                    unit(
                        element,
                        inkRight = 0f,
                        lyricText = element.mark.text,
                        lyricWidth = measureLyric(element.mark.text, element.mark.style, lyricFontSize, lyricFontName),
                    )
                }
                is GregorianNotationElement.Neume -> {
                    val neume = element.neume
                    val opensScore = openingInitial != null && openingInitial.neumeId == neume.id
                    val lyricText = when {
                        !opensScore -> if (neume.id in openingWord) neume.lyric.lowercase() else neume.lyric
                        openingHyphen -> "-"
                        else -> openingInitial.remainder
                    }
                    // The opening initial takes the syllable's first letter.
                    val centeredRange = if (opensScore) {
                        neume.centeredRange?.let { if (it.first >= 1) (it.first - 1)..(it.last - 1) else null }
                    } else {
                        neume.centeredRange
                    }
                    val lyric = lyricMetrics(lyricText, neume.lyricStyle, centeredRange, lyricFontSize, lyricFontName)
                    val previousSyllable = previousLyricSyllableId
                    val join = if (lyricText.isNotEmpty() && !neume.startsWord && previousSyllable != null &&
                        previousSyllable != neume.syllableId
                    ) {
                        if (previousLyricText?.endsWith("-") == true || lyricText.startsWith("-")) LyricJoin.Written else LyricJoin.Syllable
                    } else {
                        LyricJoin.Word
                    }
                    if (lyricText.isNotEmpty()) {
                        previousLyricText = lyricText
                        previousLyricSyllableId = neume.syllableId
                    }
                    val context = contexts[elementIndex] ?: NeumeContext()
                    val ink = notationInk(neume, context, glyphScale, notationScale)
                    unit(
                        element,
                        // A neume is placed by its first note's centre, under which its syllable's vowel stands.
                        inkLeft = ink.left - punctum / 2,
                        inkRight = ink.right - punctum / 2,
                        lyricText = lyricText,
                        lyricWidth = lyric.width,
                        lyricFocus = lyric.focus,
                        join = join,
                        context = context,
                        inkBottom = ink.bottom,
                    )
                }
            }
        }
    }

    /** A neume's ink, from where its notation begins and to where it ends, and its lowest. */
    private class NotationInk(val left: Float, val right: Float, val bottom: Float)

    /**
     * A neume's ink, from where its notation begins: its notes, with their dots, episemata and ledger lines; and its
     * lowest, on a staff whose top line is at 0.
     */
    private fun notationInk(neume: GregorianNeume, context: NeumeContext, glyphScale: Float, notationScale: Float): NotationInk {
        val staffStep = 100 * glyphScale
        val staffGap = staffStep * 2
        val composition = compose(
            neume, context, originX = 0f, staffCenter = staffGap * 1.5f, staffTop = 0f, staffBottom = staffGap * 3, staffStep = staffStep,
            glyphScale = glyphScale, notationScale = notationScale, lineIndex = 0,
        )
        val left = inkLeft(composition)
        val right = inkRight(composition)
        val bottom = max(
            composition.inkFrame?.bottom ?: 0f,
            composition.strokes.maxOfOrNull { max(it.start.y, it.end.y) + it.lineWidth / 2 } ?: 0f,
        )
        if (left == Float.MAX_VALUE || right == -Float.MAX_VALUE || right <= left) return NotationInk(0f, staffStep, bottom)
        return NotationInk(left, right, bottom)
    }

    /**
     * What a break before each unit costs, as TeX reckons penalties: lower is better, and null where no staff may break.
     * A staff best ends with a bar, then with a word; a word's syllables may be divided at a cost, and beside a
     * syllable of one letter only as a last resort; a melisma at a bar, or where four of its notes or more stand on
     * either side. No break falls between an accidental and its note, before a bar or a custos, before a rubric that
     * closes the words before it or an asterisk however it is written, or after a rubric that heads the words after it.
     * One more than the units: the end of the score.
     */
    private fun breakPenalties(units: List<LayoutUnit>): Array<Int?> {
        // The notes each syllable writes: a melisma of ten or more may be divided between staffs, as Gregorio divides
        // one.
        val syllableNotes = HashMap<String, Int>()
        for (unit in units) {
            val neume = unit.neume ?: continue
            syllableNotes[neume.syllableId] = (syllableNotes[neume.syllableId] ?: 0) + neume.writtenNoteCount
        }

        fun letterCount(text: String): Int = text.count { it.isLetter() }

        fun neumePenalty(index: Int, afterDivision: Boolean): Int? {
            val unit = units[index]
            val neume = unit.neume ?: return null
            if (neume.writtenNoteCount <= 0) return null
            if (!unit.hasLyric) {
                // A bar within a melisma divides it as well as any.
                if (afterDivision) return -50
                // Elsewhere a melisma is divided only where it leaves four notes or more on either staff, short of a
                // bar or a word.
                var before = 0
                for (earlier in index - 1 downTo 0) {
                    val earlierUnit = units[earlier]
                    if (earlierUnit.element is GregorianNotationElement.Division) break
                    before += earlierUnit.neume?.writtenNoteCount ?: 0
                    if (earlierUnit.hasLyric) break
                }
                var after = 0
                for (later in index until units.size) {
                    val laterUnit = units[later]
                    if (laterUnit.element is GregorianNotationElement.Division) break
                    if (laterUnit.hasLyric) break
                    after += laterUnit.neume?.writtenNoteCount ?: 0
                }
                if (before < 4 || after < 4) return null
                val previous = (index - 1 downTo 0).firstNotNullOfOrNull { units[it].neume }
                if (previous?.syllableId == neume.syllableId) {
                    return if ((syllableNotes[neume.syllableId] ?: 0) >= 10) 300 else null
                }
                // The notes of a group without words go on the melisma of the syllable before them.
                return 200
            }
            return when (unit.join) {
                LyricJoin.Word -> if (afterDivision) -50 else 0
                LyricJoin.Syllable, LyricJoin.Written -> {
                    val previousLyric = (index - 1 downTo 0).firstOrNull { units[it].hasLyric }?.let { units[it].lyricText } ?: ""
                    // A letter alone at a staff's end or start is a last resort.
                    if (letterCount(previousLyric) <= 1 || letterCount(unit.lyricText) <= 1) 10_000 else 100
                }
            }
        }

        val result = arrayOfNulls<Int>(units.size + 1)
        result[units.size] = 0
        for (index in 1 until units.size) {
            val previous = units[index - 1].element
            if (previous is GregorianNotationElement.Accidental) continue
            if (previous is GregorianNotationElement.LyricMark && previous.mark.beginsWhatFollows) continue
            val afterDivision = previous is GregorianNotationElement.Division
            result[index] = when (val element = units[index].element) {
                is GregorianNotationElement.Division, GregorianNotationElement.ForcedBreak -> null
                // An asterisk begins no staff, though it is written with the word after it (`*~ab`).
                is GregorianNotationElement.LyricMark ->
                    if (element.mark.beginsWhatFollows && !element.mark.text.startsWith("*")) (if (afterDivision) -50 else 0) else null
                is GregorianNotationElement.Clef -> 50
                // A break before an accidental is a break before its note.
                is GregorianNotationElement.Accidental -> if (index + 1 < units.size) neumePenalty(index + 1, afterDivision) else null
                is GregorianNotationElement.Neume -> neumePenalty(index, afterDivision)
            }
        }
        return result
    }

    /**
     * Sets a staff's units in place. Each is set after the notation before it, by the space its syllable and word call
     * for, and moved on as far as its lyric must go to clear the lyric before it: by a word's space, or, within a word,
     * to touch it. A syllable that the notes would part from the one before is joined to it if the note spaces since
     * may close up so far, and is otherwise parted from it by one hyphen. A full staff gives up [condensation] of the
     * share of each note space that it may; a staff that is justified adds [stretch] before each word, bar, rubric and
     * clef after its first, never within a word.
     */
    private fun place(
        units: List<LayoutUnit>,
        range: IntRange,
        frame: LineFrame,
        spacing: Spacing,
        condensation: Float,
        endsWithinWord: Boolean,
        stretch: Float = 0f,
    ): LinePlacement {
        val base = range.first
        val count = range.last - range.first + 1
        val result = LinePlacement(FloatArray(count), arrayOfNulls(count), frame.notationStart, frame.staffLeft)
        var previousNotation: Int? = null
        var previousLyric: Int? = null
        var lyricRight: Float? = null
        // The right of the last neume's ink.
        var notesRight: Float? = null
        // Where the next text may begin: the staff's left, a bar's line, or a clef that changes within the staff.
        var textFloor = frame.staffLeft
        var crossedDivision = false
        // The note spaces since the last lyric, each with the share of it still to give up: a syllable may close them
        // to join the one before.
        val run = mutableListOf<Pair<Int, Float>>()
        var leadingClefs = true
        // Whether the next neume's accidental has already stretched, or not, before it.
        var stretchDecided = false
        val kept = 1 - CONDENSABLE * condensation

        fun space(index: Int): Float {
            val previousIndex = previousNotation ?: return 0f
            val previous = units[base + previousIndex].element
            if (previous is GregorianNotationElement.Division) return spacing.afterDivision
            val previousNeume = (previous as? GregorianNotationElement.Neume)?.neume ?: return 0f
            var next = units[base + index].element
            if (next is GregorianNotationElement.Accidental && index + 1 < count) {
                // An accidental takes the space of the neume it governs.
                next = units[base + index + 1].element
            }
            return when (next) {
                is GregorianNotationElement.Neume -> when {
                    next.neume.syllableId != previousNeume.syllableId ->
                        if (next.neume.startsWord) spacing.betweenWords else spacing.betweenSyllables
                    // The neumes of a syllable, as the GABC parts them.
                    else -> when (val written = next.neume.spaceBefore) {
                        // A quilisma is sung from the note before it.
                        GregorianNeumeSpace.Automatic ->
                            if (next.neume.notes.firstOrNull()?.shape == GregorianNoteShape.Quilisma) 0f else spacing.withinSyllable
                        GregorianNeumeSpace.Joined -> 0f
                        is GregorianNeumeSpace.Slashes -> written.count * spacing.withinSyllable
                        GregorianNeumeSpace.Space -> 2 * spacing.withinSyllable
                    }
                }
                is GregorianNotationElement.Clef -> spacing.beforeClef
                is GregorianNotationElement.Accidental -> spacing.betweenSyllables
                else -> 0f
            }
        }

        // Whether the staff stretches before this unit: before each word, bar, rubric and clef after its first.
        fun stretches(unit: LayoutUnit): Boolean {
            if (previousNotation == null && previousLyric == null) return false
            return when (unit.element) {
                is GregorianNotationElement.Division, is GregorianNotationElement.LyricMark, is GregorianNotationElement.Clef -> true
                is GregorianNotationElement.Neume -> unit.hasLyric && unit.join == LyricJoin.Word
                is GregorianNotationElement.Accidental, GregorianNotationElement.ForcedBreak -> false
            }
        }

        for (index in 0 until count) {
            val unit = units[base + index]
            when (unit.element) {
                GregorianNotationElement.ForcedBreak -> continue
                is GregorianNotationElement.Clef, is GregorianNotationElement.Accidental -> {
                    if (unit.element is GregorianNotationElement.Clef && leadingClefs) {
                        // Drawn at the staff's start, with the clef it sets.
                        result.x[index] = frame.staffLeft
                        continue
                    }
                    leadingClefs = false
                    val natural = space(index)
                    var left = if (previousNotation == null) frame.notationStart else result.notationRight + natural * kept
                    left = max(left, frame.notationStart)
                    var stretchesHere = stretches(unit)
                    if (unit.element is GregorianNotationElement.Accidental && index + 1 < count) {
                        // An accidental takes the stretch of the word it begins.
                        stretchesHere = stretches(units[base + index + 1])
                        stretchDecided = true
                    }
                    if (stretchesHere) {
                        left += stretch
                        result.stretchPoints += 1
                    }
                    result.x[index] = left
                    result.notationRight = left + unit.inkRight
                    if (unit.element is GregorianNotationElement.Clef) textFloor = max(textFloor, left)
                    result.spare += natural * CONDENSABLE
                    run += index to natural * CONDENSABLE * (1 - condensation)
                    previousNotation = index
                }
                is GregorianNotationElement.Division -> {
                    leadingClefs = false
                    // A bar follows its syllable's words as well as its notes.
                    var left = max(
                        if (previousNotation == null) frame.notationStart else result.notationRight,
                        lyricRight ?: frame.notationStart,
                    )
                    if (stretches(unit)) {
                        left += stretch
                        result.stretchPoints += 1
                    }
                    result.x[index] = left
                    result.notationRight = left + unit.inkRight
                    textFloor = max(textFloor, left + unit.inkRight / 2)
                    crossedDivision = true
                    run += index to 0f
                    previousNotation = index
                }
                is GregorianNotationElement.LyricMark -> {
                    leadingClefs = false
                    // A rubric clears the notes before it. After a bar or a clef it stands where words would, under the
                    // room they keep for the notes.
                    var left = max(textFloor, notesRight?.let { it + spacing.withinSyllable } ?: frame.staffLeft)
                    lyricRight?.let { left = max(left, it + spacing.word) }
                    if (stretches(unit)) {
                        left += stretch
                        result.stretchPoints += 1
                    }
                    result.x[index] = left
                    result.lyricX[index] = left
                    lyricRight = left + unit.lyricWidth
                    result.notationRight = max(result.notationRight, left + unit.lyricWidth)
                    previousLyric = index
                    previousNotation = index
                    crossedDivision = false
                    run.clear()
                }
                is GregorianNotationElement.Neume -> {
                    leadingClefs = false
                    val natural = space(index)
                    val spare = natural * CONDENSABLE * (1 - condensation)
                    val start = if (previousNotation == null) frame.notationStart else result.notationRight + natural * kept
                    var position = max(start, frame.notationStart) - unit.inkLeft
                    if (unit.hasLyric) {
                        val floor = textFloor + unit.lyricFocus
                        val right = lyricRight
                        val previous = previousLyric
                        if (right != null && previous != null) {
                            val solid = right + unit.lyricFocus
                            when {
                                unit.join == LyricJoin.Word -> position = max(position, max(solid + spacing.word, floor))
                                unit.join == LyricJoin.Written -> position = max(position, max(solid, floor))
                                crossedDivision -> {
                                    // A bar within a word parts its syllables.
                                    position = max(position, max(solid + spacing.hyphen, floor))
                                    result.hyphenated += previous
                                }
                                else -> {
                                    val opening = position - solid
                                    val closable = run.fold(spare) { total, entry -> total + entry.second }
                                    if (opening <= TOUCHING) {
                                        position = max(position, solid)
                                    } else if (opening <= closable + TOUCHING) {
                                        // Close up the notes since the syllable before, each space by its share, till
                                        // the two meet.
                                        val shift = min(opening, closable)
                                        var given = 0f
                                        for ((entryIndex, entrySpare) in run) {
                                            given += entrySpare
                                            result.x[entryIndex] -= shift * given / closable
                                        }
                                        position -= shift
                                    } else {
                                        position = max(position, solid + spacing.hyphen)
                                        result.hyphenated += previous
                                    }
                                }
                            }
                        } else {
                            position = max(position, floor)
                        }
                    }
                    if (!stretchDecided && stretches(unit)) {
                        position += stretch
                        result.stretchPoints += 1
                    }
                    stretchDecided = false
                    result.x[index] = position
                    result.notationRight = position + unit.inkRight
                    notesRight = position + unit.inkRight
                    result.spare += natural * CONDENSABLE
                    // An accidental stands just before the neume it governs.
                    val before = previousNotation
                    if (before != null && units[base + before].element is GregorianNotationElement.Accidental) {
                        result.x[before] = position + unit.inkLeft - units[base + before].inkRight
                    }
                    if (unit.hasLyric) {
                        val left = position - unit.lyricFocus
                        result.lyricX[index] = left
                        lyricRight = left + unit.lyricWidth
                        previousLyric = index
                        crossedDivision = false
                        run.clear()
                    } else {
                        run += index to spare
                    }
                    previousNotation = index
                }
            }
        }

        // A bar between two neumes stands midway between their notes, though not over the words before it.
        for (index in 0 until count) {
            if (units[base + index].element !is GregorianNotationElement.Division || index == 0 || index + 1 >= count) continue
            val before = units[base + index - 1]
            val after = units[base + index + 1]
            if (before.neume == null || after.neume == null) continue
            val width = units[base + index].inkRight
            val middle = (result.x[index - 1] + before.inkRight + result.x[index + 1] + after.inkLeft) / 2
            val wordsEnd = if (before.hasLyric) (result.lyricX[index - 1] ?: 0f) + before.lyricWidth else -Float.MAX_VALUE
            result.x[index] = max(middle, wordsEnd) - width / 2
        }

        // A word that goes on in the next staff ends this one with a hyphen.
        val last = previousLyric
        val right = lyricRight
        if (endsWithinWord && last != null && right != null && units[base + last].neume != null) {
            result.hyphenated += last
            lyricRight = right + spacing.hyphen
        }
        result.textRight = lyricRight ?: frame.staffLeft
        return result
    }

    /**
     * Sets a staff in place: at its natural spacing, closed up by as little as fits it when it is too full, and justified
     * when it is not. The last staff of a paragraph, before a forced break or the score's end, is justified only to
     * bring a closing bar to the staff's end, and then only by a little.
     */
    private fun placeLine(
        units: List<LayoutUnit>,
        range: IntRange,
        frame: LineFrame,
        spacing: Spacing,
        endsWithinWord: Boolean,
        endsParagraph: Boolean,
    ): LinePlacement {
        fun placed(condensation: Float, stretch: Float = 0f) =
            place(units, range, frame, spacing, condensation, endsWithinWord, stretch)
        val natural = placed(0f)
        if (!natural.fits(frame)) {
            var best = placed(1f)
            if (!best.fits(frame)) return best
            var low = 0f
            var high = 1f
            repeat(10) {
                val middle = (low + high) / 2
                val attempt = placed(middle)
                if (attempt.fits(frame)) {
                    high = middle
                    best = attempt
                } else {
                    low = middle
                }
            }
            return best
        }
        val slack = natural.slack(frame)
        if (slack <= 0.5f || natural.stretchPoints == 0) return natural
        val stretch = slack / natural.stretchPoints
        if (endsParagraph) {
            val endsWithDivision = range.reversed().firstOrNull { !units[it].forcesBreak }
                ?.let { units[it].element is GregorianNotationElement.Division } ?: false
            if (!endsWithDivision || stretch > spacing.maximumStretch) return natural
        }
        return placed(0f, stretch)
    }

    /**
     * Breaks the units into staffs, a paragraph at a time between forced breaks, choosing the breaks that best serve the
     * whole paragraph, as TeX and Gregorio choose them: each staff is reckoned by how far it must be stretched or closed
     * up and by what its break costs, and a last staff that holds a lone syllable is avoided.
     */
    private fun breakLines(
        units: List<LayoutUnit>,
        frame: (Int) -> LineFrame,
        spacing: Spacing,
        wordContinues: BooleanArray,
    ): List<IntRange> {
        if (units.isEmpty()) return listOf(0 until 0)
        val penalties = breakPenalties(units)
        val lines = mutableListOf<IntRange>()
        var start = 0
        while (start < units.size) {
            val end = (start until units.size).firstOrNull { units[it].forcesBreak }?.let { it + 1 } ?: units.size
            lines += breakParagraph(
                units,
                start until end,
                { lineStart -> frame(if (lineStart == 0) 0 else 1) },
                spacing,
                wordContinues,
                penalties,
            )
            start = end
        }
        return lines
    }

    private fun breakParagraph(
        units: List<LayoutUnit>,
        paragraph: IntRange,
        frame: (Int) -> LineFrame,
        spacing: Spacing,
        wordContinues: BooleanArray,
        penalties: Array<Int?>,
    ): List<IntRange> {
        val lower = paragraph.first
        val upper = paragraph.last + 1
        val candidates = listOf(lower) + (lower + 1 until upper).filter { penalties[it] != null } + listOf(upper)
        val cost = DoubleArray(candidates.size) { Double.POSITIVE_INFINITY }
        val previous = IntArray(candidates.size) { -1 }
        cost[0] = 0.0

        for (end in 1 until candidates.size) {
            val b = candidates[end]
            val endsParagraph = b == upper
            val penalty = if (endsParagraph) 0.0 else (penalties[b] ?: 0).toDouble()
            for (begin in end - 1 downTo 0) {
                if (cost[begin].isInfinite()) continue
                val a = candidates[begin]
                val lineFrame = frame(a)
                val natural = place(units, a until b, lineFrame, spacing, 0f, wordContinues[b])
                val badness: Double = if (natural.fits(lineFrame)) {
                    if (endsParagraph) {
                        0.0
                    } else {
                        // A staff with nowhere to stretch, a word alone, is reckoned as if stretched in one place.
                        val stretch = natural.slack(lineFrame).toDouble() / max(1, natural.stretchPoints)
                        100 * (stretch / spacing.maximumStretch).pow(3)
                    }
                } else {
                    val condensed = place(units, a until b, lineFrame, spacing, 1f, wordContinues[b])
                    if (condensed.fits(lineFrame)) {
                        val overflow = -natural.slack(lineFrame).toDouble()
                        val share = if (natural.spare > 0) min(1.0, overflow / natural.spare) else 1.0
                        100 * share.pow(3)
                    } else if (begin == end - 1 && previous[end] < 0) {
                        // Nothing shorter fits: the staff is overfull, and worse than any that fits.
                        1e9
                    } else {
                        // A longer staff will not fit either.
                        break
                    }
                }
                var demerits = (10 + badness).pow(2) + if (penalty < 0) -penalty * penalty else penalty * penalty
                if (endsParagraph && begin > 0 && (a until b).count { units[it].hasLyric } <= 1) {
                    // A last staff of one syllable is worse than a loose staff before it.
                    demerits += 1_000_000
                }
                if (cost[begin] + demerits < cost[end]) {
                    cost[end] = cost[begin] + demerits
                    previous[end] = begin
                }
            }
        }

        // Every end has a way back: the staff from the candidate before it either fits or is taken overfull.
        val breaks = mutableListOf<Int>()
        var index = candidates.size - 1
        while (index > 0 && previous[index] >= 0) {
            breaks += candidates[index]
            index = previous[index]
        }
        val lines = mutableListOf<IntRange>()
        var lineStart = lower
        for (lineEnd in breaks.asReversed()) {
            lines += lineStart until lineEnd
            lineStart = lineEnd
        }
        return lines.ifEmpty { listOf(paragraph) }
    }

    /**
     * What a neume's builder needs of the notes beside it: the last note of the neume the GABC joins it to (`!`), from
     * which its first stem may hang, and the first note of the next neume, by which a lone oriscus turns.
     */
    private class NeumeContext(val joinedFrom: GregorianNote? = null, val next: GregorianNote? = null)

    /**
     * Engraves a neume as Exsurge builds one, note by note from the left. Each note is set where the last ended, joined
     * to it by a stem where the two stand more than a step apart. A clivis and a porrectus hang from a stem on their
     * left; a virga's stem stands inside its head's right edge; a pes sets its upper note over the lower, joined on the
     * right; a porrectus sets its last note over the end of its oblique; and the diamonds of a climacus step down from
     * one another unjoined.
     */
    private fun compose(
        neume: GregorianNeume,
        context: NeumeContext,
        originX: Float,
        staffCenter: Float,
        staffTop: Float,
        staffBottom: Float,
        staffStep: Float,
        glyphScale: Float,
        notationScale: Float,
        lineIndex: Int,
    ): Composition {
        val result = Composition()
        val notes = neume.notes
        val punctumWidth = 100 * glyphScale
        // Exsurge's space between the parts of a neume: a quarter staff space.
        val intraNeumeSpacing = staffStep / 2
        // The weight of the stems that bind a neume's notes.
        val stemWidth = max(1f, 18 * glyphScale)

        fun noteY(pitch: Int): Float = staffCenter - (pitch - 6) * staffStep
        fun width(name: GregorianGlyphName): Float = GregorianGlyphCatalog.definition(name).bounds.width * glyphScale

        // Each note's glyph, the left of its first head, and the advance from one head to the next of a note written
        // twice or thrice.
        val glyphs = MutableList(notes.size) { GregorianGlyphName.PunctumQuadratum }
        val lefts = MutableList(notes.size) { originX }
        val advances = MutableList(notes.size) { 0f }
        // The notes drawn with a virga's stem.
        val virgae = mutableSetOf<Int>()

        // The builder's place, and the note the next may be joined to.
        var x = originX
        var lastPitch: Int? = null
        var lastAlignsRight = false
        var lineIsHanging = false
        var minX = originX

        // Exsurge's neume line: from the upper note's centre to the lower's, and on past the lower for a stem that
        // hangs, a clivis's or a porrectus's.
        fun stem(stemX: Float, pitchA: Int, pitchB: Int, hanging: Boolean, eventId: String) {
            val upper = max(pitchA, pitchB) - 6
            var lower = min(pitchA, pitchB) - 6
            var overhang = 0f
            if (hanging) {
                if (upper - lower > 4) lower = upper - 4
                // From a note on a line a step above the next, within the staff, the stem reaches a step further.
                if (upper - lower == 1 && abs(upper) % 2 == 1 && lower > -3) lower -= 1
                overhang = GregorianGlyphCatalog.definition(GregorianGlyphName.PunctumQuadratum).bounds.height * glyphScale / 2.2f
            }
            result.strokes += GregorianPlacedStroke(
                GregorianStrokeKind.Connector,
                GregorianPoint(stemX + stemWidth / 2, noteY(upper + 6)),
                GregorianPoint(stemX + stemWidth / 2, noteY(lower + 6) + overhang),
                lineWidth = stemWidth,
                eventId = eventId,
            )
        }

        fun noteAt(index: Int, glyph: GregorianGlyphName, withLine: Boolean = true) {
            val note = notes[index]
            val alignsRight = GregorianGlyphCatalog.definition(glyph).alignment == GregorianGlyphAlignment.Right
            val previous = lastPitch
            if (withLine && previous != null && (lineIsHanging || lastAlignsRight || abs(previous - note.pitch) > 1)) {
                val stemX = max(minX, x - stemWidth)
                stem(stemX, previous, note.pitch, lineIsHanging, note.id)
                if (!alignsRight) x = stemX
            }
            glyphs[index] = glyph
            if (alignsRight && previous != null) {
                lefts[index] = x - width(glyph)
            } else {
                lefts[index] = x
                // A note written twice or thrice (`hss`) sets a head for each, a quarter staff space apart, an eighth
                // after a stropha.
                advances[index] = width(glyph) + if (note.shape == GregorianNoteShape.Stropha) intraNeumeSpacing / 2 else intraNeumeSpacing
                x += width(glyph) + (note.repetitionCount - 1) * advances[index]
            }
            lastPitch = note.pitch
            lastAlignsRight = alignsRight
            lineIsHanging = false
        }

        fun virgaAt(index: Int) {
            noteAt(index, GregorianGlyphName.PunctumQuadratum)
            // The stem stands inside the head's right edge; a virga written twice or thrice (`hvv`) sets each head a
            // quarter staff space after the last one's stem.
            advances[index] = punctumWidth - stemWidth + intraNeumeSpacing
            x = lefts[index] + (notes[index].repetitionCount - 1) * advances[index] + punctumWidth - stemWidth
            virgae += index
            lastPitch = notes[index].pitch
            lastAlignsRight = false
            lineIsHanging = false
        }

        fun advance(distance: Float) {
            lastPitch = null
            lineIsHanging = false
            x += distance
        }

        // Starts a stem to hang on the left of the next note, down to `lower`, or joins the next note to the neume the
        // GABC joins this one to.
        fun lineFrom(lower: Int) {
            val joined = context.joinedFrom
            if (x == originX && joined != null) {
                lastPitch = joined.pitch
                lastAlignsRight = false
                minX = originX - stemWidth
            } else {
                lastPitch = notes[lower].pitch
                lastAlignsRight = false
                lineIsHanging = true
            }
        }

        // Ends a stem from the last note, inside its right edge, down to `index`'s.
        fun lineEnding(index: Int) {
            val previous = lastPitch ?: return
            x -= stemWidth
            stem(x, previous, notes[index].pitch, true, notes[index].id)
            lastPitch = notes[index].pitch
            lastAlignsRight = false
        }

        fun podatus(lower: Int, upper: Int) {
            var lowerGlyph: GregorianGlyphName
            val upperGlyph: GregorianGlyphName
            when (notes[upper].liquescence) {
                GregorianLiquescence.Small -> {
                    lowerGlyph = GregorianGlyphName.BeginningAscLiquescent
                    upperGlyph = GregorianGlyphName.TerminatingAscLiquescent
                }
                GregorianLiquescence.Ascending -> {
                    lowerGlyph = GregorianGlyphName.PunctumQuadratum
                    upperGlyph = GregorianGlyphName.PunctumQuadratumAscLiquescent
                }
                GregorianLiquescence.Descending -> {
                    lowerGlyph = GregorianGlyphName.PunctumQuadratum
                    upperGlyph = GregorianGlyphName.PunctumQuadratumDesLiquescent
                }
                GregorianLiquescence.None -> {
                    val wide = notes[upper].pitch - notes[lower].pitch > 1
                    lowerGlyph = if (wide) GregorianGlyphName.PodatusLower else GregorianGlyphName.PodatusLowerShort
                    upperGlyph = if (wide) GregorianGlyphName.PodatusUpper else GregorianGlyphName.PodatusUpperShort
                }
            }
            if (notes[lower].shape == GregorianNoteShape.Quilisma) lowerGlyph = GregorianGlyphName.Quilisma
            noteAt(lower, lowerGlyph)
            noteAt(upper, upperGlyph)
            lastPitch = null
        }

        fun clivisUpper(upper: Int, lower: Int?, glyph: GregorianGlyphName = GregorianGlyphName.PunctumQuadratum) {
            if (notes[upper].shape == GregorianNoteShape.Oriscus) {
                noteAt(upper, GregorianGlyphName.OriscusDes, withLine = false)
                return
            }
            var chosen = glyph
            if (lower != null) {
                lineFrom(lower)
                lineIsHanging = notes[lower].pitch < notes[upper].pitch
                if (notes[lower].liquescence == GregorianLiquescence.Small) chosen = GregorianGlyphName.BeginningDesLiquescent
            }
            noteAt(upper, chosen)
        }

        // The glyph of a note that ends a descent: a clivis's lower note, a torculus's last.
        fun descentEnd(index: Int): GregorianGlyphName = when (notes[index].liquescence) {
            GregorianLiquescence.Small -> GregorianGlyphName.TerminatingDesLiquescent
            GregorianLiquescence.Ascending -> GregorianGlyphName.PunctumQuadratumAscLiquescent
            GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
            GregorianLiquescence.None -> GregorianGlyphName.PunctumQuadratum
        }

        fun clivis(upper: Int, lower: Int) {
            clivisUpper(upper, lower)
            noteAt(lower, descentEnd(lower))
            lastPitch = null
        }

        fun inclinata(start: Int) {
            // Each diamond steps from the last by its interval, as Exsurge steps them: the advance of a diamond and a
            // tenth for a unison, two thirds of one for each step.
            val advance = width(GregorianGlyphName.PunctumInclinatum)
            for (index in start until notes.size) {
                if (index > start) {
                    val steps = abs(notes[index - 1].pitch - notes[index].pitch)
                    x += advance * if (steps == 0) 1.1f else steps * 2f / 3
                }
                glyphs[index] = if (notes[index].liquescence == GregorianLiquescence.Small) {
                    GregorianGlyphName.PunctumInclinatumLiquescent
                } else {
                    GregorianGlyphName.PunctumInclinatum
                }
                lefts[index] = x
            }
            lastPitch = null
        }

        fun porrectusSwash(start: Int, end: Int) {
            val previous = lastPitch
            if (previous != null && (lineIsHanging || lastAlignsRight || abs(previous - notes[start].pitch) > 1)) {
                x = max(minX, x - stemWidth)
                stem(x, previous, notes[start].pitch, lineIsHanging, notes[start].id)
            }
            val swash = when ((notes[start].pitch - notes[end].pitch).coerceIn(1, 4)) {
                1 -> GregorianGlyphName.Porrectus1
                2 -> GregorianGlyphName.Porrectus2
                3 -> GregorianGlyphName.Porrectus3
                else -> GregorianGlyphName.Porrectus4
            }
            glyphs[start] = swash
            lefts[start] = x
            x += width(swash)
            // The swash engraves the second note too, at its right end.
            glyphs[end] = GregorianGlyphName.None
            lefts[end] = x - punctumWidth
            lastPitch = notes[end].pitch
            lastAlignsRight = false
            lineIsHanging = false
        }

        // A lone note's glyph, as Exsurge draws a punctum.
        fun punctumGlyph(note: GregorianNote): GregorianGlyphName {
            if (note.liquescence != GregorianLiquescence.None) {
                return when {
                    note.shape == GregorianNoteShape.Inclinatum -> GregorianGlyphName.PunctumInclinatumLiquescent
                    note.shape == GregorianNoteShape.Oriscus -> GregorianGlyphName.OriscusLiquescent
                    note.liquescence == GregorianLiquescence.Ascending -> GregorianGlyphName.PunctumQuadratumAscLiquescent
                    note.liquescence == GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
                    else -> GregorianGlyphName.PunctumQuadratumLiquescent
                }
            }
            if (note.isCavum) return GregorianGlyphName.PunctumCavum
            return when (note.shape) {
                GregorianNoteShape.Inclinatum -> GregorianGlyphName.PunctumInclinatum
                GregorianNoteShape.Quilisma -> GregorianGlyphName.Quilisma
                GregorianNoteShape.Oriscus -> GregorianGlyphName.OriscusDes
                GregorianNoteShape.Stropha -> GregorianGlyphName.Stropha
                else -> GregorianGlyphName.PunctumQuadratum
            }
        }

        // A note of a stropha's form: a stropha only where the GABC writes one (`s`); a plain note repeated is a punctum.
        fun strophaGlyph(note: GregorianNote): GregorianGlyphName {
            if (note.shape == GregorianNoteShape.Stropha) return GregorianGlyphName.Stropha
            return when (note.liquescence) {
                GregorianLiquescence.Ascending -> GregorianGlyphName.PunctumQuadratumAscLiquescent
                GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
                else -> if (note.isCavum) GregorianGlyphName.PunctumCavum else GregorianGlyphName.PunctumQuadratum
            }
        }

        fun plainOrQuilisma(index: Int): GregorianGlyphName =
            if (notes[index].shape == GregorianNoteShape.Quilisma) GregorianGlyphName.Quilisma else GregorianGlyphName.PunctumQuadratum

        fun head(index: Int) {
            if (notes[index].shape == GregorianNoteShape.Virga) virgaAt(index) else noteAt(index, plainOrQuilisma(index))
        }

        // Where the diamonds begin, if all the notes from there are diamonds.
        val inclinataStart = notes.indices.firstOrNull { start ->
            notes.subList(start, notes.size).all { it.shape == GregorianNoteShape.Inclinatum }
        }
        val form = neume.form
        val count = notes.size
        when {
            form == GregorianNeumeForm.PunctaInclinata -> inclinata(0)
            (form == GregorianNeumeForm.Climacus || form == GregorianNeumeForm.Ancus) && inclinataStart == 1 -> {
                head(0)
                advance(intraNeumeSpacing)
                inclinata(1)
            }
            form == GregorianNeumeForm.PesSubpunctis && inclinataStart == 2 -> {
                podatus(0, 1)
                advance(intraNeumeSpacing * 0.68f)
                inclinata(2)
            }
            form == GregorianNeumeForm.Virga || form == GregorianNeumeForm.Bivirga || form == GregorianNeumeForm.Trivirga -> {
                for (index in notes.indices) {
                    if (index > 0) advance(intraNeumeSpacing)
                    virgaAt(index)
                }
            }
            form == GregorianNeumeForm.Apostropha || form == GregorianNeumeForm.Distropha || form == GregorianNeumeForm.Tristropha -> {
                val strophae = notes.take(2).count { it.shape == GregorianNoteShape.Stropha }
                val gap = if (form == GregorianNeumeForm.Tristropha) {
                    if (notes[0].shape == GregorianNoteShape.Stropha) intraNeumeSpacing / 2 else intraNeumeSpacing
                } else {
                    intraNeumeSpacing - strophae * intraNeumeSpacing / 4
                }
                for (index in notes.indices) {
                    if (index > 0) advance(gap)
                    noteAt(index, strophaGlyph(notes[index]))
                }
            }
            form == GregorianNeumeForm.Oriscus && count == 1 -> {
                val rises = context.next?.let { it.pitch > notes[0].pitch } ?: false
                noteAt(
                    0,
                    when {
                        notes[0].liquescence != GregorianLiquescence.None -> GregorianGlyphName.OriscusLiquescent
                        rises -> GregorianGlyphName.OriscusAsc
                        else -> GregorianGlyphName.OriscusDes
                    },
                )
            }
            form == GregorianNeumeForm.Clivis && count == 2 -> clivis(0, 1)
            form == GregorianNeumeForm.Ancus && count == 3 -> {
                clivisUpper(0, 1)
                val middleGlyph = if (notes[2].liquescence == GregorianLiquescence.Small) {
                    GregorianGlyphName.BeginningDesLiquescent
                } else {
                    GregorianGlyphName.PunctumQuadratum
                }
                clivisUpper(1, if (notes[0].pitch - notes[1].pitch > 1) 0 else null, middleGlyph)
                noteAt(2, descentEnd(2))
                lastPitch = null
            }
            form == GregorianNeumeForm.Podatus && count == 2 -> podatus(0, 1)
            form == GregorianNeumeForm.PesQuassus && count == 2 -> {
                noteAt(0, if (notes[0].shape == GregorianNoteShape.Oriscus) GregorianGlyphName.OriscusAsc else GregorianGlyphName.PunctumQuadratum)
                if (notes[1].pitch - notes[0].pitch == 1) {
                    virgaAt(1)
                } else {
                    noteAt(
                        1,
                        if (notes[1].liquescence == GregorianLiquescence.Descending) GregorianGlyphName.PunctumQuadratumDesLiquescent else GregorianGlyphName.PunctumQuadratum,
                    )
                    lineEnding(0)
                }
            }
            form == GregorianNeumeForm.Porrectus && count == 3 -> {
                lineFrom(1)
                porrectusSwash(0, 1)
                noteAt(
                    2,
                    when (notes[2].liquescence) {
                        GregorianLiquescence.Small -> GregorianGlyphName.TerminatingAscLiquescent
                        GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
                        else -> if (notes[2].pitch - notes[1].pitch > 1) GregorianGlyphName.PodatusUpper else GregorianGlyphName.PodatusUpperShort
                    },
                )
            }
            form == GregorianNeumeForm.PorrectusFlexus && count == 4 -> {
                lineFrom(1)
                porrectusSwash(0, 1)
                noteAt(2, if (notes[3].liquescence == GregorianLiquescence.Small) GregorianGlyphName.PunctumQuadratumDesLiquescent else GregorianGlyphName.PunctumQuadratum)
                noteAt(3, descentEnd(3))
            }
            form == GregorianNeumeForm.Torculus && count == 3 -> {
                noteAt(0, plainOrQuilisma(0))
                noteAt(1, GregorianGlyphName.PunctumQuadratum)
                noteAt(2, descentEnd(2))
            }
            form == GregorianNeumeForm.TorculusResupinus && count == 4 -> {
                noteAt(0, plainOrQuilisma(0))
                porrectusSwash(1, 2)
                noteAt(
                    3,
                    when {
                        notes[3].liquescence == GregorianLiquescence.Small -> GregorianGlyphName.TerminatingAscLiquescent
                        notes[2].liquescence == GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
                        else -> GregorianGlyphName.PodatusUpper
                    },
                )
            }
            form == GregorianNeumeForm.TorculusResupinusFlexus && count == 5 -> {
                noteAt(0, plainOrQuilisma(0))
                porrectusSwash(1, 2)
                noteAt(3, if (notes[4].liquescence == GregorianLiquescence.Small) GregorianGlyphName.PunctumQuadratumDesLiquescent else GregorianGlyphName.PunctumQuadratum)
                noteAt(4, descentEnd(4))
            }
            form == GregorianNeumeForm.Scandicus && count == 3 -> {
                if (notes[2].shape == GregorianNoteShape.Virga) {
                    podatus(0, 1)
                    virgaAt(2)
                } else {
                    noteAt(0, plainOrQuilisma(0))
                    podatus(1, 2)
                }
            }
            form == GregorianNeumeForm.ScandicusFlexus && count == 4 -> {
                if (notes[2].shape == GregorianNoteShape.Virga) {
                    podatus(0, 1)
                    advance(intraNeumeSpacing)
                    clivis(2, 3)
                } else {
                    noteAt(0, GregorianGlyphName.PunctumQuadratum)
                    podatus(1, 2)
                    advance(intraNeumeSpacing)
                    noteAt(
                        3,
                        when (notes[3].liquescence) {
                            GregorianLiquescence.Ascending -> GregorianGlyphName.PunctumQuadratumAscLiquescent
                            GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
                            else -> GregorianGlyphName.PunctumQuadratum
                        },
                    )
                }
            }
            form == GregorianNeumeForm.Salicus && count == 3 -> {
                noteAt(0, GregorianGlyphName.PunctumQuadratum)
                advance(intraNeumeSpacing)
                noteAt(1, GregorianGlyphName.OriscusAsc)
                when (notes[2].liquescence) {
                    GregorianLiquescence.Small -> noteAt(2, GregorianGlyphName.TerminatingAscLiquescent)
                    GregorianLiquescence.Ascending -> noteAt(2, GregorianGlyphName.PunctumQuadratumAscLiquescent)
                    GregorianLiquescence.Descending -> noteAt(2, GregorianGlyphName.PunctumQuadratumDesLiquescent)
                    GregorianLiquescence.None -> virgaAt(2)
                }
            }
            form == GregorianNeumeForm.SalicusFlexus && count == 4 -> {
                noteAt(0, GregorianGlyphName.PunctumQuadratum)
                advance(intraNeumeSpacing)
                noteAt(1, GregorianGlyphName.OriscusAsc)
                noteAt(2, if (notes[3].liquescence == GregorianLiquescence.Small) GregorianGlyphName.PunctumQuadratumDesLiquescent else GregorianGlyphName.PunctumQuadratum)
                noteAt(3, descentEnd(3))
            }
            else -> {
                // A note alone, or a run the forms above do not name: its notes one after another, joined where they
                // leap.
                for (index in notes.indices) {
                    if (notes[index].shape == GregorianNoteShape.Virga) virgaAt(index) else noteAt(index, punctumGlyph(notes[index]))
                }
            }
        }

        // The head each note's signs are set by, the last of a note written twice or thrice; null for a custos.
        val signFrames = arrayOfNulls<GregorianRect>(notes.size)
        for ((index, note) in notes.withIndex()) {
            val y = noteY(note.pitch)
            val glyph = glyphs[index]
            // Where the note's head is, whatever glyph engraves it.
            val headFrame = GregorianRect.of(lefts[index], y - punctumWidth / 2, punctumWidth, punctumWidth)
            if (note.isCustos) {
                // Drawn as the custos that ends a staff is drawn: without signs, and not as a note that the cantor
                // guide may light.
                val custos = placedGlyph(
                    GregorianGlyphKind.Custos,
                    GregorianPoint(lefts[index] + GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).origin.x * glyphScale, y),
                    glyphScale,
                )
                result.glyphs += custos
                appendEvent(note, neume, custos.frame, lineIndex, result)
                continue
            }
            if (glyph == GregorianGlyphName.None) {
                // The second note of a porrectus, which its swash engraves.
                signFrames[index] = headFrame
                appendLedgerLines(headFrame, y, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes)
                appendEvent(note, neume, headFrame, lineIndex, result)
                continue
            }
            val isSwash = glyph in SWASHES

            val definition = GregorianGlyphCatalog.definition(glyph)
            val first = placedGlyph(
                GregorianGlyphKind.Catalog(glyph),
                GregorianPoint(lefts[index] + definition.origin.x * glyphScale, y),
                glyphScale,
                note.id,
            )
            // The same glyph again for each further time the note is written.
            val heads = listOf(first) + (1 until max(1, note.repetitionCount)).map { repetition ->
                GregorianPlacedGlyph(first.kind, first.frame.translate(repetition * advances[index], 0f), note.id)
            }
            // Signs written after a repeated note belong to the last of them.
            val placed = heads.last()
            var noteFrame = heads.fold(first.frame) { frame, head -> frame.union(head.frame) }
            if (index in virgae) {
                // A virga's stem falls nearly two steps from a note in a space, nearly three from one on a line.
                val length = (if (abs(note.pitch - 6) % 2 == 0) 1.8f else 2.7f) * staffStep
                for (head in heads) {
                    val stemX = head.frame.left + punctumWidth - stemWidth / 2
                    result.strokes += GregorianPlacedStroke(
                        GregorianStrokeKind.Connector,
                        GregorianPoint(stemX, y),
                        GregorianPoint(stemX, y + length),
                        lineWidth = stemWidth,
                        eventId = note.id,
                    )
                    noteFrame = noteFrame.union(GregorianRect.of(stemX - stemWidth / 2, y, stemWidth, length))
                }
            }

            note.accidental?.let { accidental ->
                val kind = accidentalKind(accidental)
                val accidentalScale = glyphScale * 0.9f
                val accidentalDefinition = GregorianGlyphCatalog.definition(kind.catalogName)
                val accidentalMinX = first.frame.left - accidentalDefinition.bounds.width * accidentalScale - 2 * notationScale
                val accidentalGlyph = placedGlyph(
                    kind,
                    GregorianPoint(accidentalMinX + accidentalDefinition.origin.x * accidentalScale, y),
                    accidentalScale,
                    note.id,
                )
                result.glyphs += accidentalGlyph
                result.ink(accidentalGlyph.frame)
            }

            result.glyphs += heads
            result.ink(noteFrame)
            signFrames[index] = placed.frame

            // A swash's first head; any other glyph's whole frame.
            appendLedgerLines(if (isSwash) headFrame else noteFrame, y, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes)
            appendEvent(note, neume, noteFrame, lineIndex, result)
        }

        appendSigns(neume, signFrames, glyphs, staffCenter, staffStep, glyphScale, notationScale, result)
        return result
    }

    /**
     * Sets a neume's dots, episemata and ictus as Exsurge places them, the dot at the Liber's size: on the side the GABC
     * writes, or else on the side the neume gives each note. A dot stands just after its note, in its space, or in the
     * space above a note on a line; a double dot after a neume's last two notes gives each its own. An episema keeps
     * clear of its note and of the staff's lines, and runs on over the next note's on the same side. An ictus stands a
     * step beyond its note, off the line there.
     */
    private fun appendSigns(
        neume: GregorianNeume,
        frames: Array<GregorianRect?>,
        glyphs: List<GregorianGlyphName>,
        staffCenter: Float,
        staffStep: Float,
        glyphScale: Float,
        notationScale: Float,
        result: Composition,
    ) {
        val notes = neume.notes
        fun noteY(pitch: Int): Float = staffCenter - (pitch - 6) * staffStep
        fun onLine(note: GregorianNote): Boolean = abs(note.pitch - 6) % 2 == 1
        val form = neume.form

        // Dots. A double dot on a neume's last note dots the note before it too, set after the last.
        val dots = notes.map { it.moraCount }.toMutableList()
        val dotAfterNext = MutableList(notes.size) { false }
        val last = notes.lastIndex
        if (last > 0 && notes[last].moraCount > 1) {
            dots[last] -= 1
            dots[last - 1] += 1
            dotAfterNext[last - 1] = true
        }
        val dotSides = notes.map { it.moraPosition }.toMutableList()
        fun lowerDot(index: Int) {
            if (dotSides[index] == null && dots[index] > 0) dotSides[index] = GregorianEpisemaPosition.Below
        }
        when (form) {
            // The lower note of a pes, on a line, has its dot below it.
            GregorianNeumeForm.Podatus, GregorianNeumeForm.PesQuassus, GregorianNeumeForm.PesSubpunctis ->
                if (notes.size > 1 && onLine(notes[0])) lowerDot(0)
            // A clivis's lower note, on a line a step down, has its dot below it.
            GregorianNeumeForm.Clivis, GregorianNeumeForm.Torculus, GregorianNeumeForm.Porrectus -> {
                val upper = if (form == GregorianNeumeForm.Clivis) 0 else 1
                val lower = upper + 1
                if (notes.size > lower && notes[upper].pitch - notes[lower].pitch == 1 && onLine(notes[lower])) lowerDot(lower)
            }
            else -> Unit
        }
        if ((form == GregorianNeumeForm.Climacus || form == GregorianNeumeForm.PunctaInclinata || form == GregorianNeumeForm.PesSubpunctis) &&
            notes.size > 1
        ) {
            // So has the last diamond, on a line a step below the one before.
            if (notes[last - 1].pitch - notes[last].pitch == 1 && onLine(notes[last])) lowerDot(last)
        }
        val dotDefinition = GregorianGlyphCatalog.definition(GregorianGlyphName.Mora)
        // The Liber's dot: a third of a staff space across, a tenth of one after its note.
        val dotScale = 0.33f * staffStep * 2 / dotDefinition.bounds.width
        val dotWidth = dotDefinition.bounds.width * dotScale
        val dotGap = 0.1f * staffStep * 2
        for ((index, note) in notes.withIndex()) {
            if (dots[index] <= 0) continue
            val frame = frames[index] ?: continue
            var y = noteY(note.pitch)
            when (dotSides[index]) {
                GregorianEpisemaPosition.Above -> y -= (if (onLine(note)) 0.75f else 1.75f) * staffStep
                GregorianEpisemaPosition.Below -> y += (if (onLine(note)) 0.75f else 1.75f) * staffStep
                null -> if (onLine(note)) {
                    y -= 0.75f * staffStep
                } else if (index + 1 < notes.size && notes[index + 1].pitch == note.pitch - 1) {
                    y -= 0.25f * staffStep
                }
            }
            var left = frame.right + dotGap
            val next = frames.getOrNull(index + 1)
            if (next != null) {
                if (dotAfterNext[index]) {
                    left = next.right + dotGap
                } else if (next.right > frame.right) {
                    // Between the note and the end of the next.
                    left = frame.right + (next.right - frame.right - dotWidth) / 2
                }
            }
            for (dot in 0 until dots[index]) {
                val mora = placedGlyph(
                    GregorianGlyphKind.Mora,
                    GregorianPoint(left + dot * (dotWidth + dotGap) + dotDefinition.origin.x * dotScale, y),
                    dotScale,
                    note.id,
                )
                result.glyphs += mora
                result.ink(mora.frame)
            }
        }

        // Episemata, on the side the neume gives each note unless the GABC writes one.
        val episemaSides = notes.map { it.episemaPosition }.toMutableList()
        fun side(index: Int, position: GregorianEpisemaPosition) {
            if (index < notes.size && episemaSides[index] == null) episemaSides[index] = position
        }
        fun clivisSides(upper: Int, lower: Int): Boolean {
            side(upper, GregorianEpisemaPosition.Above)
            val hasTop = upper < notes.size && notes[upper].hasEpisema
            side(lower, if (hasTop) GregorianEpisemaPosition.Above else GregorianEpisemaPosition.Below)
            return hasTop
        }
        fun podatusSides(lower: Int, upper: Int) {
            side(lower, GregorianEpisemaPosition.Below)
            side(upper, GregorianEpisemaPosition.Above)
        }
        when (form) {
            GregorianNeumeForm.Clivis -> clivisSides(0, 1)
            GregorianNeumeForm.Ancus -> {
                clivisSides(0, 2)
                clivisSides(1, 2)
            }
            GregorianNeumeForm.Podatus, GregorianNeumeForm.PesQuassus, GregorianNeumeForm.PesSubpunctis -> podatusSides(0, 1)
            GregorianNeumeForm.Porrectus -> {
                clivisSides(0, 1)
                podatusSides(1, 2)
            }
            GregorianNeumeForm.PorrectusFlexus -> {
                side(0, GregorianEpisemaPosition.Above)
                val hasTop = clivisSides(2, 3) || notes[0].hasEpisema
                side(1, if (hasTop) GregorianEpisemaPosition.Above else GregorianEpisemaPosition.Below)
            }
            GregorianNeumeForm.Torculus -> {
                val hasTop = clivisSides(1, 2)
                side(0, if (hasTop) GregorianEpisemaPosition.Above else GregorianEpisemaPosition.Below)
            }
            GregorianNeumeForm.TorculusResupinus, GregorianNeumeForm.TorculusResupinusFlexus -> {
                clivisSides(1, 2)
                podatusSides(2, 3)
                clivisSides(1, 0)
            }
            GregorianNeumeForm.Scandicus, GregorianNeumeForm.ScandicusFlexus ->
                if (form == GregorianNeumeForm.Scandicus && notes.size > 2 && notes[2].shape == GregorianNoteShape.Virga) {
                    podatusSides(0, 1)
                } else {
                    side(0, GregorianEpisemaPosition.Below)
                    podatusSides(1, 2)
                }
            GregorianNeumeForm.Salicus, GregorianNeumeForm.SalicusFlexus ->
                for (index in notes.indices) side(index, GregorianEpisemaPosition.Below)
            else -> Unit
        }
        val episemaWeight = max(1f, 1.3f * notationScale)
        val staffLines = 4
        class Run(val side: GregorianEpisemaPosition, var y: Float, val left: Float, var right: Float, val eventId: String)
        val runs = mutableListOf<Run>()
        for ((index, note) in notes.withIndex()) {
            if (!note.hasEpisema) continue
            val frame = frames[index] ?: continue
            val position = episemaSides[index] ?: GregorianEpisemaPosition.Above
            // Exsurge's place: a quarter step clear of the note, centred between it and the next line if there is
            // room, else in the nearest third of a space off the line.
            val clearance = staffStep * 0.25f
            var step: Float
            if (position == GregorianEpisemaPosition.Below) {
                var y = frame.bottom + clearance - staffCenter
                if (glyphs[index] == GregorianGlyphName.None) y += staffStep / 2
                step = ceil(y / staffStep)
                if (abs(step % 2) == 0f) {
                    step = (step + 3f / 4 + (y - clearance) / staffStep) / 2
                } else {
                    step = (ceil(1.5f * y / staffStep - 0.5f) * 2 + 1) / 3
                    if (abs(step) % 2 == 1f) step += if (abs(step) < staffLines) 2f / 3 else 1f / 3
                }
            } else {
                val y = frame.top - clearance - staffCenter
                step = floor(y / staffStep)
                if (abs(step % 2) == 0f) {
                    step = (step - 3f / 4 + (y + clearance) / staffStep) / 2
                } else {
                    step = (floor(1.5f * y / staffStep - 0.5f) * 2 + 1) / 3
                    if (abs(step) % 2 == 1f) step -= if (abs(step) < staffLines) 2f / 3 else 1f / 3
                }
            }
            var left = frame.left
            var width = frame.width
            when {
                glyphs[index] in SWASHES -> width = staffStep
                glyphs[index] == GregorianGlyphName.None -> {
                    width = staffStep
                    left = frame.right - width
                }
                glyphs[index] == GregorianGlyphName.PunctumInclinatum && index > 0 &&
                    glyphs[index - 1] == GregorianGlyphName.PunctumInclinatum && notes[index - 1].pitch - note.pitch == 1 -> {
                    width *= 2f / 3
                    left += 0.5f * width
                }
                glyphs[index] == GregorianGlyphName.PunctumInclinatumLiquescent -> {
                    width *= 2f / 3
                    left += 0.25f * width
                }
            }
            val y = staffCenter + step * staffStep
            // An episema runs on over the next note's on the same side, at the height that clears both.
            val previous = runs.lastOrNull()
            if (previous != null && previous.side == position && left - previous.right < staffStep) {
                previous.y = if (position == GregorianEpisemaPosition.Above) min(previous.y, y) else max(previous.y, y)
                previous.right = left + width
            } else {
                runs += Run(position, y, left, left + width, note.id)
            }
        }
        for (run in runs) {
            result.strokes += GregorianPlacedStroke(
                GregorianStrokeKind.Episema,
                GregorianPoint(run.left, run.y),
                GregorianPoint(run.right, run.y),
                lineWidth = episemaWeight,
                eventId = run.eventId,
            )
        }

        // The ictus: above a virga, the top of a rise and the head of a fall, and below any other note, unless the
        // GABC writes its side.
        for ((index, note) in notes.withIndex()) {
            if (!note.hasIctus) continue
            val frame = frames[index] ?: continue
            val above = when {
                note.ictusPosition != null -> note.ictusPosition == GregorianEpisemaPosition.Above
                note.shape == GregorianNoteShape.Virga -> true
                note.shape != GregorianNoteShape.Inclinatum && index + 1 < notes.size -> notes[index + 1].pitch < note.pitch
                else -> index > 0 && index == notes.lastIndex && notes[index - 1].pitch < note.pitch
            }
            val position = note.pitch - 6 + if (above) 1 else -1
            // Off a staff line, or nearer if the space is free; further from an episema on the same side.
            val onStaffLine = abs(position) % 2 == 1 && abs(position) < staffLines
            val episemaBeside = note.hasEpisema &&
                (episemaSides[index] ?: GregorianEpisemaPosition.Above) == (if (above) GregorianEpisemaPosition.Above else GregorianEpisemaPosition.Below)
            var extra = 0f
            if (note.shape == GregorianNoteShape.Inclinatum && !onStaffLine && !episemaBeside) extra = 0.3f
            if (episemaBeside) extra = 0.4f
            val offset = staffStep * (extra + if (onStaffLine) 0.3f else -0.2f) * if (above) -1 else 1
            val centerX = when {
                glyphs[index] in SWASHES -> frame.left + staffStep / 2
                glyphs[index] == GregorianGlyphName.None -> frame.right - staffStep / 2
                else -> frame.center.x
            }
            val ictus = placedGlyph(
                GregorianGlyphKind.Catalog(if (above) GregorianGlyphName.VerticalEpisemaAbove else GregorianGlyphName.VerticalEpisemaBelow),
                GregorianPoint(centerX, noteY(position + 6) + offset),
                glyphScale,
                note.id,
            )
            result.glyphs += ictus
            result.ink(ictus.frame)
        }
    }

    private fun appendEvent(note: GregorianNote, neume: GregorianNeume, frame: GregorianRect, lineIndex: Int, result: Composition) {
        result.events += GregorianEventPlacement(note.id, neume.id, frame.inset(-2f, -4f), lineIndex)
        result.ink(frame)
    }

    private fun appendClef(
        clef: GregorianClef,
        x: Float,
        staffTop: Float,
        staffGap: Float,
        glyphScale: Float,
        notationScale: Float,
        glyphs: MutableList<GregorianPlacedGlyph>,
    ) {
        val lineY = staffTop + (4 - clef.line) * staffGap
        val kind = if (clef.kind == GregorianClefKind.C) GregorianGlyphKind.CClef else GregorianGlyphKind.FClef
        val clefGlyph = placedGlyph(kind, GregorianPoint(x, lineY), glyphScale)
        glyphs += clefGlyph
        if (clef.flattensB) {
            // The flat stands on the B the clef flattens, a step below the clef's line, just after the clef.
            val flatScale = glyphScale * 0.9f
            val flat = GregorianGlyphCatalog.definition(GregorianGlyphName.Flat)
            glyphs += placedGlyph(
                GregorianGlyphKind.Flat,
                GregorianPoint(clefGlyph.frame.right + notationScale + flat.origin.x * flatScale, lineY + staffGap / 2),
                flatScale,
            )
        }
    }

    private fun appendDivision(
        division: GregorianDivision,
        x: Float,
        staffTop: Float,
        staffGap: Float,
        notationScale: Float,
        strokes: MutableList<GregorianPlacedStroke>,
    ) {
        val height = when (division) {
            GregorianDivision.Minima -> staffGap
            GregorianDivision.Minor -> staffGap * 2
            GregorianDivision.Major, GregorianDivision.Final -> staffGap * 3
        }
        // A quarter bar crosses the top line, as the Liber draws it.
        val top = if (division == GregorianDivision.Minima) staffTop - staffGap / 2 else staffTop + (staffGap * 3 - height) / 2
        val count = if (division == GregorianDivision.Final) 2 else 1
        for (index in 0 until count) {
            val barX = x + index * 4 * notationScale
            strokes += GregorianPlacedStroke(
                GregorianStrokeKind.Division(division),
                GregorianPoint(barX, top),
                GregorianPoint(barX, top + height),
                lineWidth = max(1.2f, 1.45f * notationScale),
            )
        }
    }

    private fun placedGlyph(kind: GregorianGlyphKind, anchor: GregorianPoint, scale: Float, eventId: String? = null): GregorianPlacedGlyph {
        val definition = GregorianGlyphCatalog.definition(kind.catalogName)
        return GregorianPlacedGlyph(
            kind,
            GregorianRect.of(
                anchor.x - definition.origin.x * scale,
                anchor.y - definition.origin.y * scale,
                definition.bounds.width * scale,
                definition.bounds.height * scale,
            ),
            eventId,
        )
    }

    private class LyricMetrics(val width: Float, val focus: Float)

    /**
     * A lyric's width, and the middle of the letters its first note stands over: those a brace pair marks, or its sung
     * vowel.
     */
    private fun lyricMetrics(
        lyric: String,
        style: GregorianLyricStyle,
        centeredRange: IntRange?,
        fontSize: Float,
        fontName: String,
    ): LyricMetrics {
        if (lyric.isEmpty()) return LyricMetrics(0f, 0f)
        val width = measureLyric(lyric, style, fontSize, fontName)
        val characters = graphemes(lyric)
        val centre = centeredRange?.takeIf { it.first >= 0 && it.last < characters.size && !it.isEmpty() }
            ?: sungVowel(characters)
        val prefix = characters.take(centre.first).joinToString("")
        val nucleus = characters.subList(centre.first, centre.last + 1).joinToString("")
        val focus = measureLyric(prefix, style, fontSize, fontName) + measureLyric(nucleus, style, fontSize, fontName) / 2
        return LyricMetrics(width, focus)
    }

    // Measured exactly: syllables of a word set solid meet exactly.
    private fun measureLyric(
        lyric: String,
        style: GregorianLyricStyle = GregorianLyricStyle.Regular,
        fontSize: Float,
        fontName: String,
    ): Float = if (lyric.isEmpty()) 0f else measurer.width(lyric, style, fontSize, fontName)

    /**
     * The ledger lines a note at [noteY] needs, as wide as its head at [frame] and a staff line's weight, as the books
     * draw them. They follow the note's pitch, not its glyph, whose stem or swash may reach far from the head.
     */
    private fun appendLedgerLines(
        frame: GregorianRect,
        noteY: Float,
        staffTop: Float,
        staffBottom: Float,
        staffGap: Float,
        scale: Float,
        eventId: String,
        strokes: MutableList<GregorianPlacedStroke>,
    ) {
        if (noteY <= staffTop - staffGap / 2) {
            var y = staffTop - staffGap
            while (y >= noteY - 1) {
                strokes += GregorianPlacedStroke(
                    GregorianStrokeKind.Ledger, GregorianPoint(frame.left - 3 * scale, y), GregorianPoint(frame.right + 3 * scale, y),
                    max(0.72f, 0.78f * scale), eventId,
                )
                y -= staffGap
            }
        } else if (noteY >= staffBottom + staffGap / 2) {
            var y = staffBottom + staffGap
            while (y <= noteY + 1) {
                strokes += GregorianPlacedStroke(
                    GregorianStrokeKind.Ledger, GregorianPoint(frame.left - 3 * scale, y), GregorianPoint(frame.right + 3 * scale, y),
                    max(0.72f, 0.78f * scale), eventId,
                )
                y += staffGap
            }
        }
    }

    private fun mergeLedgerLines(strokes: List<GregorianPlacedStroke>, scale: Float): List<GregorianPlacedStroke> {
        val (unsortedLedgers, nonLedger) = strokes.partition { it.kind == GregorianStrokeKind.Ledger }
        val ledgers = unsortedLedgers.sortedWith { a, b ->
            if (abs(a.start.y - b.start.y) < 0.1f) a.start.x.compareTo(b.start.x) else a.start.y.compareTo(b.start.y)
        }
        val merged = mutableListOf<GregorianPlacedStroke>()
        for (ledger in ledgers) {
            val last = merged.lastOrNull()
            if (last != null && abs(last.start.y - ledger.start.y) < 0.1f && ledger.start.x <= last.end.x + 2 * scale) {
                merged[merged.lastIndex] = GregorianPlacedStroke(
                    GregorianStrokeKind.Ledger,
                    last.start,
                    GregorianPoint(max(last.end.x, ledger.end.x), last.end.y),
                    max(last.lineWidth, ledger.lineWidth),
                    last.eventId,
                )
            } else {
                merged += ledger
            }
        }
        return nonLedger + merged
    }

    private fun makeHitFrames(raw: List<RawNeumePlacement>, width: Float, scale: Float): List<GregorianNeumePlacement> {
        val result = mutableListOf<GregorianNeumePlacement>()
        val grouped = raw.groupBy { it.lineIndex }
        for (lineIndex in grouped.keys.sorted()) {
            val placements = grouped.getValue(lineIndex).sortedBy { it.inkFrame.center.x }
            for ((index, placement) in placements.withIndex()) {
                val midX = placement.inkFrame.center.x
                val previousMid = if (index > 0) placements[index - 1].inkFrame.center.x else 0f
                val nextMid = if (index + 1 < placements.size) placements[index + 1].inkFrame.center.x else width
                val leftBoundary = if (index > 0) (previousMid + midX) / 2 else max(0f, midX - 22 * scale)
                val rightBoundary = if (index + 1 < placements.size) (midX + nextMid) / 2 else min(width, midX + 22 * scale)
                val desiredWidth = max(44f, placement.inkFrame.width + 14 * scale)
                val centeredLeft = midX - desiredWidth / 2
                val hitLeft = max(leftBoundary, centeredLeft)
                val hitRight = min(rightBoundary, hitLeft + desiredWidth)
                result += GregorianNeumePlacement(
                    id = placement.neume.id,
                    eventIds = placement.neume.eventIds,
                    noteCount = placement.neume.writtenNoteCount,
                    lyric = placement.neume.lyric,
                    inkFrame = placement.inkFrame,
                    hitFrame = GregorianRect.of(
                        hitLeft,
                        placement.staffTop - 7 * scale,
                        max(1f, hitRight - hitLeft),
                        max(44f, placement.staffBottom - placement.staffTop + 7 * scale),
                    ),
                    lineIndex = lineIndex,
                )
            }
        }
        return result
    }

    public companion object {
        private const val LYRIC_LINE_HEIGHT_MULTIPLIER = 1.23f

        /** The share of each space between notes that a full staff may give up, as Exsurge's. */
        private const val CONDENSABLE = 0.3f

        /** A gap between two syllables no wider than this is read as none. */
        private const val TOUCHING = 0.25f

        /** The obliques of the porrectus, by the interval of their two notes. */
        private val SWASHES = setOf(
            GregorianGlyphName.Porrectus1,
            GregorianGlyphName.Porrectus2,
            GregorianGlyphName.Porrectus3,
            GregorianGlyphName.Porrectus4,
        )

        /**
         * The mode a psalm tone is headed with beside its initial ("8G"), from its score's mode; null for a mode
         * that names no conclusion, as a hymn's does.
         */
        public fun openingLabel(mode: String?): String? {
            val trimmed = mode?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            val compact = trimmed.lowercase().filterNot { it.isWhitespace() }
            val prefix = MODE_PREFIXES.firstOrNull { compact.startsWith(it) } ?: return null
            var conclusion = compact.drop(prefix.length)
            if (conclusion.startsWith("-")) conclusion = conclusion.drop(1)
            if (conclusion.startsWith("alt-")) conclusion = conclusion.drop(4)
            val concludingLetter = conclusion.firstOrNull() ?: return null
            if (concludingLetter !in 'a'..'g') return null
            val variant = conclusion.drop(1)
            if (!(variant.isEmpty() || variant.all(::isNumber) || variant == "star" || variant == "-")) return null
            return trimmed.uppercase()
        }

        /**
         * The label printed above the initial of a piece that opens a chant: for an antiphon, its mode and the
         * conclusion of its psalm tone, as [openingLabel] gives it; for a piece with no psalm tone, a hymn, a
         * responsory or a part of the Mass, its mode alone ("8", "4").
         */
        public fun openingLabel(mode: String?, opensPiece: Boolean): String? {
            openingLabel(mode)?.let { return it }
            if (!opensPiece) return null
            val compact = mode?.lowercase()?.filterNot { it.isWhitespace() } ?: return null
            val prefix = MODE_PREFIXES.firstOrNull { compact.startsWith(it) } ?: return null
            // A numeral, not the first letters of a word.
            if (compact.drop(prefix.length).firstOrNull()?.isLetter() == true) return null
            val index = MODE_PREFIXES.indexOf(prefix)
            return MODE_PREFIXES[if (index < 8) index + 8 else index]
        }

        /** A verse number: `1.`, `12`. */
        private val VERSE_NUMBER = Regex("^[0-9]+\\.?$")

        /** The modes as the GABC writes them, the longer Roman numerals first and each in the same place as its Arabic numeral eight on. */
        private val MODE_PREFIXES = listOf("viii", "vii", "iii", "vi", "iv", "ii", "v", "i", "8", "7", "3", "6", "4", "2", "5", "1")

        private fun inkRight(composition: Composition): Float {
            val strokes = composition.strokes.maxOfOrNull { max(it.start.x, it.end.x) } ?: -Float.MAX_VALUE
            return max(composition.inkFrame?.right ?: -Float.MAX_VALUE, strokes)
        }

        private fun inkLeft(composition: Composition): Float {
            val strokes = composition.strokes.minOfOrNull { min(it.start.x, it.end.x) } ?: Float.MAX_VALUE
            return min(composition.inkFrame?.left ?: Float.MAX_VALUE, strokes)
        }

        /**
         * For each unit, whether a staff that ends before it divides a word: whether the next lyric is the next syllable
         * of the word the last one began. One more than the units, for a staff that ends the score.
         */
        private fun wordContinuation(units: List<LayoutUnit>): BooleanArray {
            val result = BooleanArray(units.size + 1)
            var continues = false
            for (index in units.indices.reversed()) {
                if (units[index].hasLyric) continues = units[index].join == LyricJoin.Syllable
                result[index] = continues
            }
            return result
        }

        /**
         * The letters a syllable is sung on: the first vowel of its last word (a note may carry the end of one word and
         * the beginning of the next, `t Dó`), with the vowels that follow it (`ae`, `au`), past a consonantal i that
         * begins it (`ius`, `Ie`) or a u after q or g (`qui`, `guis`); the whole syllable, if it has no vowel.
         */
        private fun sungVowel(characters: List<String>): IntRange {
            fun vowel(start: Int): IntRange? {
                var first = (start until characters.size).firstOrNull { isVowel(characters[it]) } ?: return null
                if (first + 1 < characters.size && isVowel(characters[first + 1])) {
                    val letter = baseLetter(characters[first])
                    val before = if (first > start) baseLetter(characters[first - 1]) else null
                    if (letter == 'i' && first == start) {
                        first += 1
                    } else if (letter == 'u' && (before == 'q' || before == 'g')) {
                        first += 1
                    }
                }
                var end = first + 1
                while (end < characters.size && isVowel(characters[end])) end += 1
                return first until end
            }
            val lastWord = characters.indexOfLast { it.isNotEmpty() && it.all(Char::isWhitespace) } + 1
            return (if (lastWord < characters.size) vowel(lastWord) else null) ?: vowel(0) ?: characters.indices
        }

        /** A letter without its accents, in lower case: `ǽ` is `æ`, `Ë` is `e`. */
        private fun baseLetter(character: String): Char? =
            Normalizer.normalize(character, Normalizer.Form.NFD).lowercase().firstOrNull()

        private fun isVowel(character: String): Boolean = baseLetter(character)?.let { it in "aeiouyæœ" } ?: false

        /** A numeral of any script, as Swift's `Character.isNumber` reads one. */
        private fun isNumber(character: Char): Boolean = when (Character.getType(character)) {
            Character.DECIMAL_DIGIT_NUMBER.toInt(), Character.LETTER_NUMBER.toInt(), Character.OTHER_NUMBER.toInt() -> true
            else -> false
        }

        private fun accidentalKind(accidental: GregorianAccidental): GregorianGlyphKind = when (accidental) {
            GregorianAccidental.Flat -> GregorianGlyphKind.Flat
            GregorianAccidental.Natural -> GregorianGlyphKind.Natural
            GregorianAccidental.Sharp -> GregorianGlyphKind.Sharp
        }

        /** A text's characters as a reader counts them, an accent with its letter, as Swift counts them. */
        private fun graphemes(text: String): List<String> {
            val breaks = BreakIterator.getCharacterInstance()
            breaks.setText(text)
            val clusters = mutableListOf<String>()
            var start = breaks.first()
            var end = breaks.next()
            while (end != BreakIterator.DONE) {
                clusters += text.substring(start, end)
                start = end
                end = breaks.next()
            }
            return clusters
        }
    }
}
