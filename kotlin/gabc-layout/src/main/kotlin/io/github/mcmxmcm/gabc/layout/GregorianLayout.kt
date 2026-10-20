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

        val hyphenFontSize = lyricFontSize * AUTOMATIC_HYPHEN_SCALE
        val hyphenWidth = measureLyric("-", fontSize = hyphenFontSize, fontName = lyricFontName)
        val units = makeUnits(score, glyphScale, notationScale, lyricFontSize, lyricFontName, hyphenWidth, openingInitial)
        val continuationAvailableWidth = columnWidth - sidePadding * 2 - clefColumn - custosColumn
        val lines = breakLines(units, continuationAvailableWidth - initialColumn, continuationAvailableWidth, notationScale)
        val lineVerticalMetrics = lines.mapIndexed { lineIndex, lineUnits ->
            verticalMetrics(
                lineUnits, staffStep, staffGap, notationScale, lyricHeight,
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

        for ((lineIndex, lineUnits) in lines.withIndex()) {
            val lineOriginY = lineOrigins[lineIndex]
            val lineMetrics = lineVerticalMetrics[lineIndex]
            val staffTop = lineOriginY + lineMetrics.topInset
            val staffBottom = staffTop + staffGap * 3
            val staffCenter = (staffTop + staffBottom) / 2
            val openingColumn = if (lineIndex == 0) initialColumn else 0f
            val staffLeft = sidePadding + openingColumn
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
            appendClef(activeClef, staffLeft + 2 * notationScale, staffTop, staffGap, glyphScale, glyphs)

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

            val availableWidth = continuationAvailableWidth - openingColumn
            // The staff's last ink must stop short of the custos as well.
            val neededWidth = lineUnits.fold(0f) { total, unit -> total + unit.width } + (lineUnits.lastOrNull()?.trailingInk ?: 0f)
            val gapAdjustments = FloatArray(max(0, lineUnits.size - 1))
            val condensibleGaps = condensibleGaps(lineUnits)
            if (neededWidth > availableWidth && condensibleGaps.isNotEmpty()) {
                val adjustment = max(-MAXIMUM_GAP_CONDENSATION * notationScale, (availableWidth - neededWidth) / condensibleGaps.size)
                for (index in condensibleGaps) gapAdjustments[index] = adjustment
            }
            var x = staffLeft + clefColumn
            var inkRight = x

            for ((unitIndex, unit) in lineUnits.withIndex()) {
                when (val element = unit.element) {
                    is GregorianNotationElement.Clef -> {
                        // The clefs a staff opens with are set at its head, and take no room in it.
                        if (unitIndex < leadingClefCount) continue
                        activeClef = element.clef
                        appendClef(element.clef, x, staffTop, staffGap, glyphScale, glyphs)
                    }
                    is GregorianNotationElement.Accidental -> {
                        val y = staffCenter - (element.mark.pitch - 6) * staffStep
                        glyphs += placedGlyph(accidentalKind(element.mark.kind), GregorianPoint(x + unit.anchorOffset, y), glyphScale * 0.9f)
                    }
                    is GregorianNotationElement.Division ->
                        appendDivision(element.division, x + unit.anchorOffset, staffTop, staffGap, notationScale, strokes)
                    GregorianNotationElement.ForcedBreak -> Unit
                    is GregorianNotationElement.LyricMark -> lyrics += GregorianPlacedLyric(
                        text = element.mark.text,
                        style = element.mark.style,
                        origin = GregorianPoint(x + unit.anchorOffset - unit.lyricFocus, staffBottom + lineMetrics.lyricOffset),
                        width = unit.lyricWidth,
                        fontSize = lyricFontSize,
                        fontName = lyricFontName,
                        neumeId = element.mark.id,
                    )
                    is GregorianNotationElement.Neume -> {
                        val neume = element.neume
                        val notationLeft = x + unit.anchorOffset - unit.notationWidth / 2
                        val composition = compose(neume, notationLeft, staffCenter, staffTop, staffBottom, staffStep, glyphScale, notationScale, lineIndex)
                        glyphs += composition.glyphs
                        strokes += composition.strokes
                        events += composition.events
                        inkRight = max(inkRight, inkRight(composition))

                        if (unit.lyricText.isNotEmpty()) {
                            lyrics += GregorianPlacedLyric(
                                text = unit.lyricText,
                                style = neume.lyricStyle,
                                origin = GregorianPoint(x + unit.anchorOffset - unit.lyricFocus, staffBottom + lineMetrics.lyricOffset),
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
                x += unit.width
                if (unitIndex in gapAdjustments.indices) x += gapAdjustments[unitIndex]
            }

            // A custos written in the score (`f+`, as before a change of clef) already guides the eye to what
            // follows; the staff that ends with one is not given another.
            val endsWithWrittenCustos = lineUnits.mapNotNull { it.neume }.lastOrNull()?.notes?.lastOrNull()?.isCustos ?: false
            val nextNote = lines.getOrNull(lineIndex + 1)?.firstNotNullOfOrNull { it.neume }?.notes?.firstOrNull()
            if (!endsWithWrittenCustos && nextNote != null) {
                val nextY = staffCenter - (nextNote.pitch - 6) * staffStep
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

        lyrics += automaticLyricHyphens(
            lines, lyrics, columnWidth, sidePadding, notationScale, lyricFontSize, lyricFontName, hyphenFontSize, hyphenWidth,
        )
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

    private fun automaticLyricHyphens(
        lines: List<List<LayoutUnit>>,
        lyrics: List<GregorianPlacedLyric>,
        width: Float,
        sidePadding: Float,
        notationScale: Float,
        lyricFontSize: Float,
        lyricFontName: String,
        hyphenFontSize: Float,
        hyphenWidth: Float,
    ): List<GregorianPlacedLyric> {
        class Syllable(val unit: LayoutUnit, val neume: GregorianNeume, val lyric: GregorianPlacedLyric)

        val lyricsByNeumeId = lyrics.associateBy { it.neumeId }
        val syllablesByLine = lines.map { line ->
            line.mapNotNull { unit ->
                val neume = unit.neume ?: return@mapNotNull null
                if (unit.lyricText.isEmpty()) return@mapNotNull null
                val lyric = lyricsByNeumeId[neume.id] ?: return@mapNotNull null
                Syllable(unit, neume, lyric)
            }
        }
        val hyphenText = "-"
        val minimumInlineHyphenGap = hyphenWidth - notationScale
        val minimumUnreservedHyphenGap = hyphenWidth + 1.5f * notationScale
        val lineEndClearance = max(1f, 1.5f * notationScale)
        val hyphens = mutableListOf<GregorianPlacedLyric>()

        fun boundaryWordHasMultipleSyllables(previous: Syllable, next: Syllable): Boolean {
            val left = previous.unit.lyricText.split(SPACES).lastOrNull { it.isNotEmpty() }?.filter(Char::isLetter).orEmpty()
            val right = next.unit.lyricText.split(SPACES).firstOrNull { it.isNotEmpty() }?.filter(Char::isLetter).orEmpty()
            val folded = Normalizer.normalize(left + right, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase()
                .replace("æ", "ae")
                .replace("œ", "oe")
            val vowels = "aeiouy"
            var nuclei = 0
            var index = 0
            while (index < folded.length) {
                val letter = folded[index]
                if (letter !in vowels) {
                    index += 1
                    continue
                }
                if (letter == 'u' && index > 0 && folded[index - 1] == 'q') {
                    index += 1
                    continue
                }
                nuclei += 1
                index += if (index + 1 < folded.length && folded.substring(index, index + 2) in DIPHTHONGS) 2 else 1
            }
            return nuclei > 1
        }

        fun shouldHyphenate(previous: Syllable, next: Syllable): Boolean =
            next.unit.hyphenatesFromPrevious &&
                previous.neume.syllableId != next.neume.syllableId &&
                !previous.unit.lyricText.endsWith("-") &&
                !next.unit.lyricText.startsWith("-") &&
                boundaryWordHasMultipleSyllables(previous, next)

        fun makeHyphen(previous: Syllable, next: Syllable, originX: Float): GregorianPlacedLyric {
            val verticalOffset = (lyricFontSize - hyphenFontSize) * LYRIC_LINE_HEIGHT_MULTIPLIER / 2
            return GregorianPlacedLyric(
                text = hyphenText,
                origin = GregorianPoint(originX, previous.lyric.origin.y + verticalOffset),
                width = hyphenWidth,
                fontSize = hyphenFontSize,
                fontName = lyricFontName,
                neumeId = "lyric-hyphen-${previous.neume.id}-${next.neume.id}",
            )
        }

        for (syllables in syllablesByLine) {
            for ((previous, next) in syllables.zipWithNext()) {
                if (!shouldHyphenate(previous, next)) continue
                val gapStart = previous.lyric.origin.x + previous.lyric.width
                val gapEnd = next.lyric.origin.x
                val availableWidth = gapEnd - gapStart
                // The unit-building pass reserves a connector lane when the intrinsic musical spacing predicts one.
                // The placed lyrics are checked again, since wide neumes can leave more daylight than the intrinsic
                // pair promised.
                val hasReservedLane = next.unit.showsInlineHyphenFromPrevious
                val fits = if (hasReservedLane) availableWidth >= minimumInlineHyphenGap else availableWidth >= minimumUnreservedHyphenGap
                if (!fits) continue
                hyphens += makeHyphen(previous, next, gapStart + (availableWidth - hyphenWidth) / 2)
            }
        }

        if (syllablesByLine.size <= 1) return hyphens
        // The custos occupies the staff, while this mark is on the lyric baseline, so the custos's column is free for it.
        val lineEnd = width - sidePadding
        for (lineIndex in 0 until syllablesByLine.size - 1) {
            val previous = syllablesByLine[lineIndex].lastOrNull() ?: continue
            val next = syllablesByLine[lineIndex + 1].firstOrNull() ?: continue
            if (!shouldHyphenate(previous, next)) continue
            val gapStart = previous.lyric.origin.x + previous.lyric.width
            if (lineEnd - gapStart < hyphenWidth + lineEndClearance) continue
            hyphens += makeHyphen(previous, next, gapStart + lineEndClearance)
        }
        return hyphens
    }

    private class LayoutUnit(
        val element: GregorianNotationElement,
        val width: Float,
        val anchorOffset: Float,
        val notationWidth: Float,
        val lyricText: String,
        val lyricWidth: Float,
        val lyricFocus: Float,
        val hyphenatesFromPrevious: Boolean,
        val showsInlineHyphenFromPrevious: Boolean,
        val breakPenalty: Int,
        val forcesBreak: Boolean,
        /**
         * How far the unit's ink (a dot after its last note, an episema, a ledger line) reaches beyond the room the
         * unit takes. Whatever follows on the staff has room for it; the end of the staff has not.
         */
        val trailingInk: Float = 0f,
    ) {
        val neume: GregorianNeume? get() = (element as? GregorianNotationElement.Neume)?.neume
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

    private fun verticalMetrics(
        units: List<LayoutUnit>,
        staffStep: Float,
        staffGap: Float,
        notationScale: Float,
        lyricHeight: Float,
        openingInitialFontSize: Float?,
    ): LineVerticalMetrics {
        val notes = units.mapNotNull { it.neume }.flatMap { it.notes }
        val accidentalPitches = units.mapNotNull { (it.element as? GregorianNotationElement.Accidental)?.mark?.pitch }
        val pitches = notes.map { it.pitch } + accidentalPitches
        val highestPitch = pitches.maxOrNull() ?: 9
        val lowestPitch = pitches.minOrNull() ?: 3
        val topInset = max(11 * notationScale, max(0, highestPitch - 9) * staffStep + 10 * notationScale)
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
        hyphenWidth: Float,
        openingInitial: OpeningInitial?,
    ): List<LayoutUnit> {
        var hasEngravedContent = false
        var previousWasAccidental = false
        var previousWasLyricMark = false
        var previousLyricSyllableId: String? = null
        var previousLyricText: String? = null
        var previousLyricNotationHalf: Float? = null
        var previousLyricRightExtent: Float? = null
        var interveningNotationWidth = 0f
        var interveningUnitCount = 0
        // Exsurge starts with normal inter-syllabic notation spacing, then joins lyric fragments only when they would
        // collide after modest condensation. The collision test is kept conservative with EB Garamond's metrics; the
        // later line pass can still condense ordinary (non-connector) gaps further. A connector is kept when the
        // musical spacing still leaves daylight between the fragments.
        val interSyllabicSpacing = 7.8125f * notationScale
        val condensedInterSyllabicSpacing = interSyllabicSpacing * 0.8f
        val lyricCollisionTolerance = 0.1f * notationScale

        fun countIntervening(width: Float) {
            if (previousLyricText != null) {
                interveningNotationWidth += width
                interveningUnitCount += 1
            }
        }

        return score.elements.map { element ->
            when (element) {
                is GregorianNotationElement.Clef -> {
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    val width = if (hasEngravedContent) 24 * notationScale else 0f
                    countIntervening(width)
                    LayoutUnit(element, width, 0f, 18 * notationScale, "", 0f, 0f, false, false, breakPenalty = 1000, forcesBreak = false)
                }
                is GregorianNotationElement.Accidental -> {
                    hasEngravedContent = true
                    previousWasAccidental = true
                    previousWasLyricMark = false
                    val definition = GregorianGlyphCatalog.definition(accidentalKind(element.mark.kind).catalogName)
                    val width = definition.bounds.width * glyphScale * 0.9f + 4 * notationScale
                    countIntervening(width)
                    // Wrapping is permitted before an accidental, but never between the accidental and the neume it
                    // governs.
                    LayoutUnit(element, width, width / 2, width, "", 0f, 0f, false, false, breakPenalty = 18, forcesBreak = false)
                }
                is GregorianNotationElement.Division -> {
                    hasEngravedContent = true
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    val width = (if (element.division == GregorianDivision.Final) 17 else 12) * notationScale
                    countIntervening(width)
                    // A division belongs at the end of a phrase, not at the beginning of the next staff. The following
                    // word offers the usable break after the bar.
                    LayoutUnit(element, width, width / 2, width, "", 0f, 0f, false, false, breakPenalty = 1000, forcesBreak = false)
                }
                GregorianNotationElement.ForcedBreak -> {
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    LayoutUnit(element, 0f, 0f, 0f, "", 0f, 0f, false, false, breakPenalty = 0, forcesBreak = true)
                }
                is GregorianNotationElement.LyricMark -> {
                    hasEngravedContent = true
                    previousWasAccidental = false
                    previousWasLyricMark = true
                    val lyric = lyricMetrics(element.mark.text, element.mark.style, lyricFontSize, lyricFontName)
                    val leadingPadding = (if (element.mark.startsWord) 5 else 2) * notationScale
                    val trailingPadding = 5 * notationScale
                    val width = leadingPadding + lyric.width + trailingPadding
                    countIntervening(width)
                    // A rubric without notes belongs between the syllables beside it, and shouldn't begin a staff.
                    LayoutUnit(
                        element, width, leadingPadding + lyric.focus, 0f, element.mark.text, lyric.width, lyric.focus,
                        false, false, breakPenalty = 1000, forcesBreak = false,
                    )
                }
                is GregorianNotationElement.Neume -> {
                    val neume = element.neume
                    hasEngravedContent = true
                    val followsUnbreakableElement = previousWasAccidental || previousWasLyricMark
                    val notationWidth = estimatedNotationWidth(neume, glyphScale)
                    val lyricText = if (openingInitial != null && openingInitial.neumeId == neume.id) openingInitial.remainder else neume.lyric
                    val followsDifferentSyllable = previousLyricSyllableId?.let { it != neume.syllableId } ?: false
                    val followsExplicitHyphen = previousLyricText?.endsWith("-") ?: false
                    val hyphenatesFromPrevious = lyricText.isNotEmpty() && !neume.startsWord && !previousWasLyricMark &&
                        followsDifferentSyllable && !followsExplicitHyphen && !lyricText.startsWith("-")
                    previousWasAccidental = false
                    previousWasLyricMark = false
                    val lyric = lyricMetrics(lyricText, neume.lyricStyle, lyricFontSize, lyricFontName)
                    val notationHalf = notationWidth / 2
                    val leftExtent = max(notationHalf, lyric.focus)
                    val rightExtent = max(notationHalf, lyric.width - lyric.focus)
                    val leadingPadding: Float
                    val showsInlineHyphenFromPrevious: Boolean
                    if (neume.startsWord) {
                        leadingPadding = 5 * notationScale
                        showsInlineHyphenFromPrevious = false
                    } else {
                        val condensedNaturalDistance = previousLyricNotationHalf?.let {
                            it + condensedInterSyllabicSpacing + interveningNotationWidth + notationHalf
                        }
                        val lyricTouchDistance = previousLyricRightExtent?.let { it + leftExtent }
                        val needsConnector = if (hyphenatesFromPrevious && condensedNaturalDistance != null && lyricTouchDistance != null) {
                            condensedNaturalDistance - lyricTouchDistance > lyricCollisionTolerance || interveningUnitCount > 0
                        } else {
                            false
                        }
                        showsInlineHyphenFromPrevious = needsConnector
                        val previousNotationHalf = previousLyricNotationHalf
                        val previousRightExtent = previousLyricRightExtent
                        leadingPadding = if (needsConnector && previousNotationHalf != null && previousRightExtent != null) {
                            val naturalDistance = previousNotationHalf + interSyllabicSpacing + interveningNotationWidth + notationHalf
                            val naturalGap = naturalDistance - previousRightExtent - leftExtent
                            // Line fitting may condense every intervening boundary. That allowance is reserved as part
                            // of the connector's lane, so melismas do not squeeze the hyphen back out once it's chosen.
                            max(hyphenWidth + 2.5f * notationScale * interveningUnitCount, naturalGap)
                        } else {
                            0f
                        }
                    }
                    val contentWidth = leadingPadding + leftExtent + rightExtent
                    val width = if (neume.startsWord) max(24 * notationScale, contentWidth) else contentWidth
                    val centeringInset = (width - contentWidth) / 2
                    val anchorOffset = centeringInset + leadingPadding + leftExtent
                    val trailingInk = max(0f, anchorOffset - notationWidth / 2 + notationInkRight(neume, glyphScale, notationScale) - width)
                    if (lyricText.isNotEmpty()) {
                        previousLyricSyllableId = neume.syllableId
                        previousLyricText = lyricText
                        previousLyricNotationHalf = notationHalf
                        previousLyricRightExtent = rightExtent
                        interveningNotationWidth = 0f
                        interveningUnitCount = 0
                    } else {
                        countIntervening(width)
                    }
                    LayoutUnit(
                        element, width, anchorOffset, notationWidth, lyricText, lyric.width, lyric.focus,
                        hyphenatesFromPrevious, showsInlineHyphenFromPrevious,
                        // Chant may wrap between syllables when that produces a fuller staff (for example "pró-" /
                        // "ximos"). A custos, like a division, belongs at the end of what it follows.
                        breakPenalty = if (followsUnbreakableElement || neume.writtenNoteCount == 0) 1000 else 18,
                        forcesBreak = false,
                        trailingInk = trailingInk,
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

    private fun estimatedNotationWidth(neume: GregorianNeume, glyphScale: Float): Float {
        val punctum = 100 * glyphScale
        if (neume.notes.size == 1 && neume.notes[0].isCustos) {
            return GregorianGlyphCatalog.definition(GregorianGlyphName.CustosShort).bounds.width * glyphScale
        }
        // A note written twice or thrice draws a glyph for each.
        val repeated = neume.notes.sumOf { it.repetitionCount - 1 }
        return formNotationWidth(neume, punctum, glyphScale) + repeated * punctum * REPEATED_NOTE_ADVANCE
    }

    private fun formNotationWidth(neume: GregorianNeume, punctum: Float, glyphScale: Float): Float = when (neume.form) {
        GregorianNeumeForm.Podatus, GregorianNeumeForm.PesQuassus -> punctum * 1.15f
        GregorianNeumeForm.Porrectus, GregorianNeumeForm.PorrectusFlexus -> {
            val swash = GregorianGlyphCatalog.definition(porrectusSwash(neume)).bounds.width * glyphScale
            swash + punctum * max(1, neume.notes.size - 2)
        }
        GregorianNeumeForm.Climacus, GregorianNeumeForm.Ancus, GregorianNeumeForm.PunctaInclinata ->
            punctum + max(0, neume.notes.size - 1) * punctum * 0.72f
        else -> punctum + max(0, neume.notes.size - 1) * punctum * 0.92f
    }

    /** How far a neume's ink reaches to the right of where its notation begins: its notes, with their dots, episemata and ledger lines. */
    private fun notationInkRight(neume: GregorianNeume, glyphScale: Float, notationScale: Float): Float {
        val staffStep = 100 * glyphScale
        val staffGap = staffStep * 2
        val composition = compose(
            neume, originX = 0f, staffCenter = staffGap * 1.5f, staffTop = 0f, staffBottom = staffGap * 3, staffStep = staffStep,
            glyphScale = glyphScale, notationScale = notationScale, lineIndex = 0,
        )
        return max(0f, inkRight(composition))
    }

    private fun breakLines(
        units: List<LayoutUnit>,
        firstAvailableWidth: Float,
        continuationAvailableWidth: Float,
        notationScale: Float,
    ): List<List<LayoutUnit>> {
        if (units.isEmpty()) return listOf(emptyList())
        class Candidate(val index: Int, val penalty: Int)

        val lines = mutableListOf<List<LayoutUnit>>()
        var current = mutableListOf<LayoutUnit>()
        var currentWidth = 0f
        var candidates = mutableListOf<Candidate>()

        fun availableWidth() = if (lines.isEmpty()) firstAvailableWidth else continuationAvailableWidth

        fun commit(count: Int? = null) {
            val split = count ?: current.size
            val prefix = current.take(split).filter { it.element !is GregorianNotationElement.ForcedBreak }
            if (prefix.isNotEmpty()) lines += prefix
            current = current.drop(split).toMutableList()
            currentWidth = current.fold(0f) { total, unit -> total + unit.width }
            candidates = current.withIndex().mapNotNull { (index, unit) ->
                if (index > 0 && current.take(index).any { it.neume != null }) Candidate(index, unit.breakPenalty) else null
            }.toMutableList()
        }

        for (unit in units) {
            current += unit
            currentWidth += unit.width
            val splitBeforeUnit = current.size - 1
            if (splitBeforeUnit > 0 && current.take(splitBeforeUnit).any { it.neume != null }) {
                candidates += Candidate(splitBeforeUnit, unit.breakPenalty)
            }
            // A staff must hold its last ink too: a dot after the final note would otherwise fall beyond the custos.
            val neededWidth = currentWidth + unit.trailingInk
            if (unit.forcesBreak) {
                commit()
            } else if (neededWidth > availableWidth() && current.size > 1) {
                // Modest condensation is permitted before wrapping, as Exsurge permits it, but no more than the staff
                // will in fact be closed up.
                val condensible = condensibleGaps(current).size * MAXIMUM_GAP_CONDENSATION * notationScale
                if (neededWidth - availableWidth() <= condensible) continue
                val fallback = current.size - 1
                val earliest = max(1, fallback / 2)
                val split = candidates
                    .filter { it.index >= earliest && it.index < current.size }
                    .minWithOrNull(compareBy<Candidate> { it.penalty }.thenByDescending { it.index })
                    ?.index ?: fallback
                commit(split)
            }
        }
        commit()
        return lines.ifEmpty { listOf(emptyList()) }
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
                val frame = GregorianRect.of(noteOriginX, y - punctumWidth / 2, punctumWidth, punctumWidth)
                appendEvent(note, neume, frame, lineIndex, result)
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

            appendLedgerLines(noteFrame, staffTop, staffBottom, staffStep * 2, notationScale, note.id, result.strokes)
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
        glyphs: MutableList<GregorianPlacedGlyph>,
    ) {
        val lineY = staffTop + (4 - clef.line) * staffGap
        val kind = if (clef.kind == GregorianClefKind.C) GregorianGlyphKind.CClef else GregorianGlyphKind.FClef
        glyphs += placedGlyph(kind, GregorianPoint(x, lineY), glyphScale)
        if (clef.flattensB) {
            val staffCenter = staffTop + staffGap * 1.5f
            val bPitchY = staffCenter - (1 - 6) * (staffGap / 2)
            glyphs += placedGlyph(GregorianGlyphKind.Flat, GregorianPoint(x + 11 * glyphScale, bPitchY), glyphScale * 0.9f)
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

    /** A lyric's width, and the middle of its vowel, which the neume above it is centred on. */
    private fun lyricMetrics(lyric: String, style: GregorianLyricStyle, fontSize: Float, fontName: String): LyricMetrics {
        if (lyric.isEmpty()) return LyricMetrics(0f, 0f)
        val width = measureLyric(lyric, style, fontSize, fontName)
        val characters = graphemes(lyric)
        fun isVowel(cluster: String) = cluster.any { it in VOWELS }
        val nucleusStart = characters.indexOfFirst(::isVowel).let { if (it < 0) 0 else it }
        var nucleusEnd = nucleusStart
        while (nucleusEnd + 1 < characters.size && isVowel(characters[nucleusEnd + 1])) nucleusEnd += 1
        val prefix = characters.take(nucleusStart).joinToString("")
        val nucleus = characters.subList(nucleusStart, nucleusEnd + 1).joinToString("")
        val focus = measureLyric(prefix, style, fontSize, fontName) + measureLyric(nucleus, style, fontSize, fontName) / 2
        return LyricMetrics(width, focus)
    }

    private fun measureLyric(
        lyric: String,
        style: GregorianLyricStyle = GregorianLyricStyle.Regular,
        fontSize: Float,
        fontName: String,
    ): Float = if (lyric.isEmpty()) 0f else ceil(measurer.width(lyric, style, fontSize, fontName))

    private fun appendLedgerLines(
        frame: GregorianRect,
        staffTop: Float,
        staffBottom: Float,
        staffGap: Float,
        scale: Float,
        eventId: String,
        strokes: MutableList<GregorianPlacedStroke>,
    ) {
        val midY = frame.center.y
        if (midY <= staffTop - staffGap / 2) {
            var y = staffTop - staffGap
            while (y >= midY - 1) {
                strokes += GregorianPlacedStroke(
                    GregorianStrokeKind.Ledger, GregorianPoint(frame.left - 3 * scale, y), GregorianPoint(frame.right + 3 * scale, y), scale, eventId,
                )
                y -= staffGap
            }
        } else if (midY >= staffBottom + staffGap / 2) {
            var y = staffBottom + staffGap
            while (y <= midY + 1) {
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

        /** The size of an automatic hyphen, as a share of the lyric's. */
        private const val AUTOMATIC_HYPHEN_SCALE = 0.72f

        /** The advance, in punctum widths, from one note to the next on the same pitch: the spacing of a bivirga or a tristropha. */
        private const val REPEATED_NOTE_ADVANCE = 0.92f

        /** The most that one gap of a staff is closed to fit the staff. */
        private const val MAXIMUM_GAP_CONDENSATION = 2.5f

        private const val VOWELS = "aeiouyæœáéíóúýAEIOUYÆŒÁÉÍÓÚÝ"
        // Unicode's spaces, as Swift's `isWhitespace` reads them: the JDK's `\s` is ASCII alone, and Android's
        // regular expressions don't take the flag that widens it.
        private val SPACES = Regex("[\t\n\u000B\u000C\r \u0085\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]+")
        private val COMBINING_MARKS = Regex("""\p{M}+""")
        private val DIPHTHONGS = setOf("ae", "au", "oe")
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

        /** The gaps of a staff that may be closed a little to fit it: those between two things that take room, where no hyphen has been given its lane. A staff is broken, and set, by the same count. */
        private fun condensibleGaps(units: List<LayoutUnit>): List<Int> = (0 until units.lastIndex).filter {
            units[it].width > 0 && units[it + 1].width > 0 && !units[it + 1].showsInlineHyphenFromPrevious
        }

        private fun inkRight(composition: Composition): Float {
            val strokes = composition.strokes.maxOfOrNull { max(it.start.x, it.end.x) } ?: -Float.MAX_VALUE
            return max(composition.inkFrame?.right ?: -Float.MAX_VALUE, strokes)
        }

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
