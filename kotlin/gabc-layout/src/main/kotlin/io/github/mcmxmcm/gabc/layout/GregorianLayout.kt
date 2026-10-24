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
import io.github.mcmxmcm.gabc.parser.GregorianNotationElement
import io.github.mcmxmcm.gabc.parser.GregorianNote
import io.github.mcmxmcm.gabc.parser.GregorianNoteShape
import io.github.mcmxmcm.gabc.parser.GregorianScore
import java.text.BreakIterator
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// The engraving of a score: its units measured and broken into staffs, then each note, word and mark set in place,
// in points of the width it is laid out at. A port of the Swift package's GregorianLayout.swift.

public data class GregorianLayoutMetrics(
    val notationScale: Double = 1.0,
    val lyricScale: Double = 1.0,
    /** The PostScript name of the font lyrics are measured in. Whatever draws the layout should draw its lyrics in the font each one names. */
    val lyricFontName: String = GregorianLyricFont.DEFAULT_NAME,
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
    public fun layout(
        score: GregorianScore,
        width: Float,
        metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
        openingLabel: String? = null,
    ): GregorianLayout {
        val requestedWidth = width
        val notationScale = GregorianLayoutMetrics.clampedNotationScale(metrics.notationScale).toFloat()
        val lyricScale = (metrics.lyricScale.toFloat() * notationScale).coerceIn(0.5f, 3.5f)
        val lyricFontName = metrics.lyricFontName
        val columnWidth = max(240f, requestedWidth)
        val glyphScale = notationScale / 16
        val staffStep = 100 * glyphScale // one GABC pitch step, half a staff space
        val staffGap = staffStep * 2
        val sidePadding = 8 * notationScale
        val clefColumn = 18 * notationScale
        val custosColumn = 10 * notationScale
        val lyricFontSize = 21 * lyricScale
        val lyricHeight = ceil(lyricFontSize * LYRIC_LINE_HEIGHT_MULTIPLIER)
        val openingInitial = openingInitial(score, openingLabel(openingLabel))
        val initialScale = max(notationScale, min(1.35f, lyricScale))
        val initialFontSize = 56 * initialScale
        val initialAnnotationFontSize = 13 * max(notationScale, min(1.25f, lyricScale))
        val initialWidth = openingInitial?.let { measureLyric(it.text, fontSize = initialFontSize, fontName = lyricFontName) } ?: 0f
        val initialAnnotationWidth = openingInitial?.annotation?.let {
            measureLyric(it, fontSize = initialAnnotationFontSize, fontName = lyricFontName)
        } ?: 0f
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
            placeFitting(units, range, lineFrame(lineIndex), spacing, wordContinues[range.last + 1])
        }
        val lineVerticalMetrics = lines.mapIndexed { lineIndex, lineUnits ->
            verticalMetrics(
                lineUnits, custosPitch(lineIndex, lines), glyphScale, staffStep, staffGap, notationScale, lyricHeight,
                if (lineIndex == 0 && openingInitial != null) initialFontSize else null,
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
                    lineWidth = max(0.72f, 0.78f * notationScale),
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
                initials += GregorianPlacedInitial(
                    text = openingInitial.text,
                    annotation = openingInitial.annotation,
                    annotationOrigin = openingInitial.annotation?.let {
                        GregorianPoint(sidePadding + (initialColumn - initialWidth) / 2, max(0f, staffTop - initialAnnotationFontSize))
                    },
                    annotationFontSize = initialAnnotationFontSize,
                    origin = GregorianPoint(sidePadding + (initialColumn - initialWidth) / 2, staffTop),
                    width = initialWidth,
                    fontSize = initialFontSize,
                    fontName = lyricFontName,
                    neumeId = openingInitial.neumeId,
                )
            }

            var inkRight = frame.notationStart
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
                        glyphs += placedGlyph(accidentalKind(element.mark.kind), GregorianPoint(x + unit.inkRight / 2, y), glyphScale * 0.9f)
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
                            neume, x - punctumWidth / 2, staffCenter, staffTop, staffBottom, staffStep, glyphScale, notationScale, lineIndex,
                        )
                        glyphs += composition.glyphs
                        strokes += composition.strokes
                        events += composition.events
                        inkRight = max(inkRight, inkRight(composition))

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
                // A staff too short to be closed up (a single long melisma) may still reach the custos, which then
                // stands clear of it as far as the staff allows.
                val custosWidth = GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).bounds.width * glyphScale
                val custosX = min(
                    max(columnWidth - sidePadding - 7 * notationScale, inkRight + 1.5f * notationScale),
                    columnWidth - sidePadding - custosWidth,
                )
                glyphs += placedGlyph(GregorianGlyphKind.Custos, GregorianPoint(custosX, nextY), glyphScale)
            }
        }

        strokes = mergeLedgerLines(strokes, notationScale).toMutableList()
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
        val breakPenalty: Int,
        val forcesBreak: Boolean,
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

        fun fits(frame: LineFrame): Boolean =
            notationRight <= frame.notationEnd + 0.01f && textRight <= frame.textEnd + 0.01f
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

    private fun openingInitial(score: GregorianScore, annotation: String?): OpeningInitial? {
        if (annotation == null) return null
        val neume = score.neumes.firstOrNull { it.lyric.isNotEmpty() } ?: return null
        val characters = graphemes(neume.lyric)
        val initialIndex = characters.indexOfFirst { cluster -> cluster.any(Char::isUpperCase) }
        if (initialIndex < 0) return null
        val prefixIsSilent = characters.take(initialIndex).all { cluster ->
            cluster.all { it.isWhitespace() || isNumber(it) || it == '.' }
        }
        if (!prefixIsSilent) return null
        return OpeningInitial(
            text = characters[initialIndex],
            annotation = annotation,
            remainder = characters.drop(initialIndex + 1).joinToString(""),
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
    ): LineVerticalMetrics {
        val notes = units.mapNotNull { it.neume }.flatMap { it.notes }
        val accidentalPitches = units.mapNotNull { (it.element as? GregorianNotationElement.Accidental)?.mark?.pitch }
        val pitches = notes.map { it.pitch } + accidentalPitches + listOfNotNull(custosPitch)
        val highestPitch = pitches.maxOrNull() ?: 9
        val lowestPitch = pitches.minOrNull() ?: 3
        var topInset = max(11 * notationScale, max(0, highestPitch - 9) * staffStep + 10 * notationScale)
        // A custos's stem rises farther above its pitch than a note's head: the staff is set low enough to hold it.
        val highestCustos = (notes.filter { it.isCustos }.map { it.pitch } + listOfNotNull(custosPitch)).maxOrNull()
        if (highestCustos != null) {
            val stem = GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).origin.y * glyphScale
            topInset = max(topInset, (highestCustos - 9) * staffStep + stem + 2 * notationScale)
        }
        val belowStaff = if (lowestPitch < 3) (3 - lowestPitch) * staffStep + 10 * notationScale else 0f
        val lyricOffset = if (belowStaff > 0) belowStaff + 6 * notationScale else 2 * notationScale
        val systemGap = 4 * notationScale
        val notationHeight = topInset + staffGap * 3 + lyricOffset + lyricHeight + systemGap
        val initialHeight = openingInitialFontSize?.let { topInset + ceil(it * 1.15f) + systemGap } ?: 0f
        return LineVerticalMetrics(topInset, lyricOffset, max(notationHeight, initialHeight))
    }

    private fun makeUnits(
        score: GregorianScore,
        glyphScale: Float,
        notationScale: Float,
        lyricFontSize: Float,
        lyricFontName: String,
        openingInitial: OpeningInitial?,
    ): List<LayoutUnit> {
        var previousWasAccidental = false
        var previousWasLyricMark = false
        // The last lyric written, and the syllable it was sung on: none for a rubric, which ends any word before it.
        var previousLyricText: String? = null
        var previousLyricSyllableId: String? = null
        val punctum = 100 * glyphScale

        fun unit(
            element: GregorianNotationElement,
            inkRight: Float,
            breakPenalty: Int,
            inkLeft: Float = 0f,
            lyricText: String = "",
            lyricWidth: Float = 0f,
            lyricFocus: Float = 0f,
            join: LyricJoin = LyricJoin.Word,
            forcesBreak: Boolean = false,
        ) = LayoutUnit(element, inkLeft, inkRight, lyricText, lyricWidth, lyricFocus, join, breakPenalty, forcesBreak)

        return score.elements.map { element ->
            when (element) {
                is GregorianNotationElement.Clef -> {
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    unit(element, inkRight = 24 * notationScale, breakPenalty = 1000)
                }
                is GregorianNotationElement.Accidental -> {
                    previousWasAccidental = true
                    previousWasLyricMark = false
                    val definition = GregorianGlyphCatalog.definition(accidentalKind(element.mark.kind).catalogName)
                    // Wrapping is permitted before an accidental, but never between the accidental and the neume it
                    // governs.
                    unit(element, inkRight = definition.bounds.width * glyphScale * 0.9f + 4 * notationScale, breakPenalty = 18)
                }
                is GregorianNotationElement.Division -> {
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    // A division belongs at the end of a phrase, not at the beginning of the next staff. The following
                    // word offers the usable break after the bar.
                    unit(element, inkRight = (if (element.division == GregorianDivision.Final) 17 else 12) * notationScale, breakPenalty = 1000)
                }
                GregorianNotationElement.ForcedBreak -> {
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    unit(element, inkRight = 0f, breakPenalty = 0, forcesBreak = true)
                }
                is GregorianNotationElement.LyricMark -> {
                    previousWasAccidental = false
                    previousWasLyricMark = true
                    previousLyricText = element.mark.text
                    previousLyricSyllableId = null
                    // A rubric without notes belongs between the syllables beside it, and shouldn't begin a staff.
                    unit(
                        element,
                        inkRight = 0f,
                        breakPenalty = 1000,
                        lyricText = element.mark.text,
                        lyricWidth = measureLyric(element.mark.text, element.mark.style, lyricFontSize, lyricFontName),
                    )
                }
                is GregorianNotationElement.Neume -> {
                    val neume = element.neume
                    val followsUnbreakableElement = previousWasAccidental || previousWasLyricMark
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    val opensScore = openingInitial != null && openingInitial.neumeId == neume.id
                    val lyricText = if (opensScore) openingInitial!!.remainder else neume.lyric
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
                    val ink = notationInk(neume, glyphScale, notationScale)
                    unit(
                        element,
                        // A neume is placed by its first note's centre, under which its syllable's vowel stands.
                        inkLeft = ink.first - punctum / 2,
                        inkRight = ink.second - punctum / 2,
                        lyricText = lyricText,
                        lyricWidth = lyric.width,
                        lyricFocus = lyric.focus,
                        join = join,
                        // Chant may wrap between syllables when that produces a fuller staff (for example "pró-" /
                        // "ximos"). A custos, like a division, belongs at the end of what it follows.
                        breakPenalty = if (followsUnbreakableElement || neume.writtenNoteCount == 0) 1000 else 18,
                    )
                }
            }
        }
    }

    private fun porrectusSwash(neume: GregorianNeume): GregorianGlyphName {
        val first = neume.notes.firstOrNull()?.pitch ?: 0
        val second = neume.notes.getOrNull(1)?.pitch ?: 0
        return when (abs(first - second).coerceIn(1, 4)) {
            1 -> GregorianGlyphName.Porrectus1
            2 -> GregorianGlyphName.Porrectus2
            3 -> GregorianGlyphName.Porrectus3
            else -> GregorianGlyphName.Porrectus4
        }
    }

    /** A neume's ink, from where its notation begins: its notes, with their dots, episemata and ledger lines. */
    private fun notationInk(neume: GregorianNeume, glyphScale: Float, notationScale: Float): Pair<Float, Float> {
        val staffStep = 100 * glyphScale
        val staffGap = staffStep * 2
        val composition = compose(
            neume, originX = 0f, staffCenter = staffGap * 1.5f, staffTop = 0f, staffBottom = staffGap * 3, staffStep = staffStep,
            glyphScale = glyphScale, notationScale = notationScale, lineIndex = 0,
        )
        val left = inkLeft(composition)
        val right = inkRight(composition)
        if (left == Float.MAX_VALUE || right == -Float.MAX_VALUE || right <= left) return 0f to staffStep
        return left to right
    }

    /**
     * Sets a staff's units in place. Each is set after the notation before it, by the space its syllable and word call
     * for, and moved on as far as its lyric must go to clear the lyric before it: by a word's space, or, within a word,
     * to touch it. A syllable that the notes would part from the one before is joined to it if the note spaces since
     * may close up so far, and is otherwise parted from it by one hyphen. A full staff gives up [condensation] of the
     * share of each note space that it may.
     */
    private fun place(
        units: List<LayoutUnit>,
        range: IntRange,
        frame: LineFrame,
        spacing: Spacing,
        condensation: Float,
        endsWithinWord: Boolean,
    ): LinePlacement {
        val base = range.first
        val count = range.last - range.first + 1
        val result = LinePlacement(FloatArray(count), arrayOfNulls(count), frame.notationStart, frame.staffLeft)
        var previousNotation: Int? = null
        var previousLyric: Int? = null
        var lyricRight: Float? = null
        // Where the next text may begin: the staff's left, or a bar's line.
        var textFloor = frame.staffLeft
        var crossedDivision = false
        // The note spaces since the last lyric, each with the share of it still to give up: a syllable may close them
        // to join the one before.
        val run = mutableListOf<Pair<Int, Float>>()
        var leadingClefs = true
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
                    // A quilisma is sung from the note before it.
                    next.neume.syllableId == previousNeume.syllableId ->
                        if (next.neume.notes.firstOrNull()?.shape == GregorianNoteShape.Quilisma) 0f else spacing.withinSyllable
                    next.neume.startsWord -> spacing.betweenWords
                    else -> spacing.betweenSyllables
                }
                is GregorianNotationElement.Clef -> spacing.beforeClef
                is GregorianNotationElement.Accidental -> spacing.betweenSyllables
                else -> 0f
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
                    val left = if (previousNotation == null) frame.notationStart else result.notationRight + natural * kept
                    result.x[index] = max(left, frame.notationStart)
                    result.notationRight = result.x[index] + unit.inkRight
                    run += index to natural * CONDENSABLE * (1 - condensation)
                    previousNotation = index
                }
                is GregorianNotationElement.Division -> {
                    leadingClefs = false
                    // A bar follows its syllable's words as well as its notes.
                    val left = max(
                        if (previousNotation == null) frame.notationStart else result.notationRight,
                        lyricRight ?: frame.notationStart,
                    )
                    result.x[index] = left
                    result.notationRight = left + unit.inkRight
                    textFloor = max(textFloor, left + unit.inkRight / 2)
                    crossedDivision = true
                    run += index to 0f
                    previousNotation = index
                }
                is GregorianNotationElement.LyricMark -> {
                    leadingClefs = false
                    var left = max(
                        textFloor,
                        if (previousNotation == null) frame.staffLeft else result.notationRight + spacing.withinSyllable,
                    )
                    lyricRight?.let { left = max(left, it + spacing.word) }
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
                    result.x[index] = position
                    result.notationRight = position + unit.inkRight
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

    /** Sets a staff in place with its notes at their natural spacing, or, if they do not fit so, closed up by as little as fits them. */
    private fun placeFitting(
        units: List<LayoutUnit>,
        range: IntRange,
        frame: LineFrame,
        spacing: Spacing,
        endsWithinWord: Boolean,
    ): LinePlacement {
        fun placed(condensation: Float) = place(units, range, frame, spacing, condensation, endsWithinWord)
        val natural = placed(0f)
        if (natural.fits(frame)) return natural
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

    /**
     * Breaks the units into staffs: each takes as many as fit it with its notes closed up as far as they may be, and
     * then breaks where it best may, in its latter half.
     */
    private fun breakLines(
        units: List<LayoutUnit>,
        frame: (Int) -> LineFrame,
        spacing: Spacing,
        wordContinues: BooleanArray,
    ): List<IntRange> {
        if (units.isEmpty()) return listOf(0 until 0)
        val lines = mutableListOf<IntRange>()
        var start = 0
        while (start < units.size) {
            val lineFrame = frame(lines.size)
            var end = start
            var split: Int? = null
            while (end < units.size) {
                val unit = units[end]
                end += 1
                if (unit.forcesBreak) {
                    split = end
                    break
                }
                if (end - start <= 1) continue
                // Condensation is permitted before wrapping, as Exsurge permits it.
                if (place(units, start until end, lineFrame, spacing, 1f, wordContinues[end]).fits(lineFrame)) continue
                val fallback = end - 1
                val earliest = start + max(1, (fallback - start) / 2)
                split = (earliest..fallback)
                    .filter { candidate -> (start until candidate).any { units[it].neume != null } }
                    .minWithOrNull(compareBy<Int> { units[it].breakPenalty }.thenByDescending { it })
                    ?: fallback
                break
            }
            val upper = split ?: end
            lines += start until upper
            start = upper
        }
        return lines
    }

    private fun compose(
        neume: GregorianNeume,
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
        val punctumWidth = 100 * glyphScale
        val notes = neume.notes
        val form = neume.form
        val noteOrigins: MutableList<Float> = when {
            (form == GregorianNeumeForm.Podatus || form == GregorianNeumeForm.PesQuassus) && notes.size >= 2 ->
                MutableList(notes.size) { index -> if (index >= 2) originX + punctumWidth * (index - 1) else originX }
            form == GregorianNeumeForm.Climacus || form == GregorianNeumeForm.Ancus || form == GregorianNeumeForm.PunctaInclinata ->
                MutableList(notes.size) { originX + it * punctumWidth * 0.72f }
            (form == GregorianNeumeForm.Porrectus || form == GregorianNeumeForm.PorrectusFlexus) && notes.size >= 3 -> {
                val swashWidth = GregorianGlyphCatalog.definition(porrectusSwash(neume)).bounds.width * glyphScale
                MutableList(notes.size) { index ->
                    when (index) {
                        0 -> originX
                        1 -> originX + swashWidth - punctumWidth
                        else -> originX + swashWidth + (index - 2) * punctumWidth * 0.92f
                    }
                }
            }
            else -> MutableList(notes.size) { originX + it * punctumWidth * 0.92f }
        }
        // A note written twice or thrice (`hvv`, `hsss`) draws a glyph for each, and moves the notes after it along
        // by as much.
        val repeatedAdvance = punctumWidth * REPEATED_NOTE_ADVANCE
        var repeatedBefore = 0
        for (index in notes.indices) {
            noteOrigins[index] += repeatedBefore * repeatedAdvance
            repeatedBefore += notes[index].repetitionCount - 1
        }
        val isPorrectus = form == GregorianNeumeForm.Porrectus || form == GregorianNeumeForm.PorrectusFlexus

        for ((index, note) in notes.withIndex()) {
            val y = staffCenter - (note.pitch - 6) * staffStep
            val noteOriginX = noteOrigins[index]
            // Where the note's head is, whatever glyph engraves it.
            val headFrame = GregorianRect.of(noteOriginX, y - punctumWidth / 2, punctumWidth, punctumWidth)
            if (note.isCustos) {
                // Drawn as the custos that ends a staff is drawn: without signs, and not as a note that the cantor
                // guide may light.
                val custos = placedGlyph(
                    GregorianGlyphKind.Custos,
                    GregorianPoint(noteOriginX + GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).origin.x * glyphScale, y),
                    glyphScale,
                )
                result.glyphs += custos
                appendEvent(note, neume, custos.frame, lineIndex, result)
                continue
            }
            var glyphName = glyphName(note)
            if (note.shape == GregorianNoteShape.Oriscus && note.liquescence == GregorianLiquescence.None && index + 1 < notes.size) {
                glyphName = if (notes[index + 1].pitch < note.pitch) GregorianGlyphName.OriscusDes else GregorianGlyphName.OriscusAsc
            }

            if (form == GregorianNeumeForm.Apostropha || form == GregorianNeumeForm.Distropha || form == GregorianNeumeForm.Tristropha) {
                glyphName = GregorianGlyphName.Stropha
            } else if (form == GregorianNeumeForm.PesQuassus && notes.size >= 2) {
                glyphName = when (index) {
                    0 -> GregorianGlyphName.OriscusAsc
                    1 -> GregorianGlyphName.PodatusUpper
                    else -> glyphName
                }
            } else if (form == GregorianNeumeForm.Podatus && notes.size >= 2) {
                glyphName = when (index) {
                    0 -> GregorianGlyphName.PodatusLower
                    1 -> GregorianGlyphName.PodatusUpper
                    else -> glyphName
                }
            } else if (isPorrectus && index == 0) {
                glyphName = porrectusSwash(neume)
            } else if (isPorrectus && index == 1) {
                // The porrectus swash already engraves the first two notes.
                appendLedgerLines(headFrame, y, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes)
                appendEvent(note, neume, headFrame, lineIndex, result)
                continue
            }

            val definition = GregorianGlyphCatalog.definition(glyphName)
            val anchorX = noteOriginX + definition.origin.x * glyphScale
            val first = placedGlyph(GregorianGlyphKind.Catalog(glyphName), GregorianPoint(anchorX, y), glyphScale, note.id)
            // The same glyph again for each further time the note is written.
            val repetitions = (1 until max(1, note.repetitionCount)).map { repetition ->
                GregorianPlacedGlyph(first.kind, first.frame.translate(repetition * repeatedAdvance, 0f), note.id)
            }
            // Signs written after a repeated note belong to the last of them.
            val placed = repetitions.lastOrNull() ?: first
            val noteFrame = repetitions.fold(first.frame) { frame, repetition -> frame.union(repetition.frame) }

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

            if (index > 0 && form !in CONNECTED_FORMS) {
                val previous = notes[index - 1]
                if (previous.pitch != note.pitch) {
                    val previousY = staffCenter - (previous.pitch - 6) * staffStep
                    val connectorX = noteOriginX + 0.7f * glyphScale
                    result.strokes += GregorianPlacedStroke(
                        GregorianStrokeKind.Connector,
                        GregorianPoint(connectorX, previousY),
                        GregorianPoint(connectorX, y),
                        lineWidth = max(1f, 18 * glyphScale),
                        eventId = note.id,
                    )
                }
            }

            result.glyphs += first
            result.glyphs += repetitions
            result.ink(noteFrame)

            for (moraIndex in 0 until note.moraCount) {
                val mora = placedGlyph(
                    GregorianGlyphKind.Mora,
                    GregorianPoint(placed.frame.right + (5 + moraIndex * 3.5f) * notationScale, y),
                    glyphScale * 0.72f,
                    note.id,
                )
                result.glyphs += mora
                result.ink(mora.frame)
            }
            if (note.hasEpisema) {
                val above = note.episemaPosition == GregorianEpisemaPosition.Above
                val episemaY = if (above) placed.frame.top - 3 * notationScale else placed.frame.bottom + 3 * notationScale
                result.strokes += GregorianPlacedStroke(
                    GregorianStrokeKind.Episema,
                    GregorianPoint(placed.frame.left - notationScale, episemaY),
                    GregorianPoint(placed.frame.right + notationScale, episemaY),
                    lineWidth = max(1f, 1.3f * notationScale),
                    eventId = note.id,
                )
            }
            if (note.hasIctus) {
                val above = note.episemaPosition == GregorianEpisemaPosition.Above
                val ictusName = if (above) GregorianGlyphName.VerticalEpisemaAbove else GregorianGlyphName.VerticalEpisemaBelow
                val ictus = placedGlyph(
                    GregorianGlyphKind.Catalog(ictusName),
                    GregorianPoint(
                        placed.frame.center.x,
                        if (above) placed.frame.top - 2 * notationScale else placed.frame.bottom + 2 * notationScale,
                    ),
                    glyphScale,
                    note.id,
                )
                result.glyphs += ictus
                result.ink(ictus.frame)
            }

            // The swash's first head; any other glyph's whole frame.
            val ledgerFrame = if (isPorrectus && index == 0) headFrame else noteFrame
            appendLedgerLines(ledgerFrame, y, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes)
            appendEvent(note, neume, noteFrame, lineIndex, result)
        }
        return result
    }

    private fun glyphName(note: GregorianNote): GregorianGlyphName {
        if (note.isCavum) return GregorianGlyphName.PunctumCavum
        return when (note.shape) {
            GregorianNoteShape.Punctum -> when (note.liquescence) {
                GregorianLiquescence.Ascending -> GregorianGlyphName.PunctumQuadratumAscLiquescent
                GregorianLiquescence.Descending -> GregorianGlyphName.PunctumQuadratumDesLiquescent
                GregorianLiquescence.Small -> GregorianGlyphName.PunctumQuadratumLiquescent
                GregorianLiquescence.None -> GregorianGlyphName.PunctumQuadratum
            }
            GregorianNoteShape.Virga -> GregorianGlyphName.VirgaShort
            GregorianNoteShape.Quilisma -> GregorianGlyphName.Quilisma
            GregorianNoteShape.Liquescent ->
                if (note.liquescence == GregorianLiquescence.Ascending) {
                    GregorianGlyphName.TerminatingAscLiquescent
                } else {
                    GregorianGlyphName.TerminatingDesLiquescent
                }
            GregorianNoteShape.Inclinatum ->
                if (note.liquescence == GregorianLiquescence.None) GregorianGlyphName.PunctumInclinatum else GregorianGlyphName.PunctumInclinatumLiquescent
            GregorianNoteShape.Oriscus ->
                if (note.liquescence == GregorianLiquescence.None) GregorianGlyphName.OriscusAsc else GregorianGlyphName.OriscusLiquescent
            GregorianNoteShape.Stropha -> GregorianGlyphName.Stropha
            GregorianNoteShape.Custos -> GregorianGlyphName.CustosShort
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
        val top = if (division == GregorianDivision.Minima) staffTop + staffGap else staffTop + (staffGap * 3 - height) / 2
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
     * The ledger lines a note at [noteY] needs, as wide as its head at [frame]. They follow the note's pitch, not its
     * glyph, whose stem or swash may reach far from the head.
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
                    GregorianStrokeKind.Ledger, GregorianPoint(frame.left - 3 * scale, y), GregorianPoint(frame.right + 3 * scale, y), scale, eventId,
                )
                y -= staffGap
            }
        } else if (noteY >= staffBottom + staffGap / 2) {
            var y = staffBottom + staffGap
            while (y <= noteY + 1) {
                strokes += GregorianPlacedStroke(
                    GregorianStrokeKind.Ledger, GregorianPoint(frame.left - 3 * scale, y), GregorianPoint(frame.right + 3 * scale, y), scale, eventId,
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

        /** The advance, in punctum widths, from one note to the next on the same pitch: the spacing of a bivirga or a tristropha. */
        private const val REPEATED_NOTE_ADVANCE = 0.92f

        /** The share of each space between notes that a full staff may give up, as Exsurge's. */
        private const val CONDENSABLE = 0.3f

        /** A gap between two syllables no wider than this is read as none. */
        private const val TOUCHING = 0.25f

        private val CONNECTED_FORMS = setOf(
            GregorianNeumeForm.Podatus,
            GregorianNeumeForm.PesQuassus,
            GregorianNeumeForm.Porrectus,
            GregorianNeumeForm.PorrectusFlexus,
        )

        /**
         * The mode a psalm tone is headed with beside its initial ("8G"), from its score's mode; null for a mode
         * that names no conclusion, as a hymn's does.
         */
        public fun openingLabel(mode: String?): String? {
            val trimmed = mode?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            val compact = trimmed.lowercase().filterNot { it.isWhitespace() }
            val prefixes = listOf("viii", "vii", "iii", "vi", "iv", "ii", "v", "i", "8", "7", "6", "5", "4", "3", "2", "1")
            val prefix = prefixes.firstOrNull { compact.startsWith(it) } ?: return null
            var conclusion = compact.drop(prefix.length)
            if (conclusion.startsWith("-")) conclusion = conclusion.drop(1)
            if (conclusion.startsWith("alt-")) conclusion = conclusion.drop(4)
            val concludingLetter = conclusion.firstOrNull() ?: return null
            if (concludingLetter !in 'a'..'g') return null
            val variant = conclusion.drop(1)
            if (!(variant.isEmpty() || variant.all(::isNumber) || variant == "star" || variant == "-")) return null
            return trimmed.uppercase()
        }

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
