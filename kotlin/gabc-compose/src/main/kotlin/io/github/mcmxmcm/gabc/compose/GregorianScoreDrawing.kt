package io.github.mcmxmcm.gabc.compose

import android.content.Context
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import io.github.mcmxmcm.gabc.layout.GregorianEngravingLayoutEngine
import io.github.mcmxmcm.gabc.layout.GregorianLayout
import io.github.mcmxmcm.gabc.layout.GregorianLayoutMetrics
import io.github.mcmxmcm.gabc.layout.GregorianLyricFont
import io.github.mcmxmcm.gabc.layout.GregorianLyricMeasurer
import io.github.mcmxmcm.gabc.layout.GregorianPlacedGlyph
import io.github.mcmxmcm.gabc.layout.GregorianPlacedInitial
import io.github.mcmxmcm.gabc.layout.GregorianPlacedLyric
import io.github.mcmxmcm.gabc.layout.GregorianPlacedStroke
import io.github.mcmxmcm.gabc.layout.GregorianRect
import io.github.mcmxmcm.gabc.lyricfont.measurer
import io.github.mcmxmcm.gabc.parser.GregorianScore

/**
 * A score laid out at one width, with its drawing prepared. Neither needs the main thread: prepare them in the
 * background for long documents, then draw them with [GregorianScoreCanvas].
 */
public class GregorianPreparedScore(public val score: GregorianScore, public val layout: GregorianLayout) {
    public val drawing: GregorianScoreDrawing = GregorianScoreDrawing(layout)

    public companion object {
        /**
         * Lays a score out at a width and prepares its drawing, measuring its lyrics with [measurer].
         *
         * @param mode The score's mode, shown above a large initial when it names a conclusion (`8g`, `7c2`); see
         *   [GregorianEngravingLayoutEngine.openingLabel].
         */
        public fun prepare(
            score: GregorianScore,
            width: Float,
            measurer: GregorianLyricMeasurer,
            metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
            mode: String? = null,
        ): GregorianPreparedScore = GregorianPreparedScore(
            score,
            GregorianEngravingLayoutEngine(measurer).layout(score, width, metrics, openingLabel = mode),
        )

        /** Lays a score out at a width and prepares its drawing, measuring its lyrics in the bundled EB Garamond. */
        public fun prepare(
            context: Context,
            score: GregorianScore,
            width: Float,
            metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
            mode: String? = null,
        ): GregorianPreparedScore = prepare(score, width, GregorianLyricFont.measurer(context), metrics, mode)
    }
}

/** A layout's static ink, gathered into one tile for each staff system. */
public class GregorianScoreDrawing(layout: GregorianLayout) {
    /** Strokes of one width, drawn as one path. */
    public class StrokeBatch(public val lineWidth: Float, public val strokes: List<GregorianPlacedStroke>) {
        public val path: Path = Path().apply {
            for (stroke in strokes) {
                moveTo(stroke.start.x, stroke.start.y)
                lineTo(stroke.end.x, stroke.end.y)
            }
        }
    }

    /**
     * Glyphs drawn as one path. Even-odd fill is required by some catalog glyphs, so glyphs that intersect are kept
     * in separate batches and cannot cancel each other's fill, while the usual glyphs that don't are batched.
     */
    public class GlyphBatch(public val glyphs: List<GregorianPlacedGlyph>) {
        public val path: Path = Path().apply {
            fillType = PathFillType.EvenOdd
            for (glyph in glyphs) addPath(GregorianGlyphLibrary.path(glyph))
        }
    }

    public class Tile(
        public val frame: GregorianRect,
        strokes: List<GregorianPlacedStroke>,
        glyphs: List<GregorianPlacedGlyph>,
        public val lyrics: List<GregorianPlacedLyric>,
        public val initials: List<GregorianPlacedInitial>,
    ) {
        public val strokeBatches: List<StrokeBatch> =
            strokes.groupBy { it.lineWidth }.toSortedMap().map { (lineWidth, batch) -> StrokeBatch(lineWidth, batch) }

        public val glyphBatches: List<GlyphBatch> = run {
            val batches = mutableListOf<Pair<MutableList<GregorianPlacedGlyph>, MutableList<GregorianRect>>>()
            for (glyph in glyphs) {
                val occupied = glyph.frame.inset(-0.5f, -0.5f)
                val batch = batches.firstOrNull { (_, frames) -> frames.none { it.overlaps(occupied) } }
                    ?: (mutableListOf<GregorianPlacedGlyph>() to mutableListOf<GregorianRect>()).also { batches += it }
                batch.first += glyph
                batch.second += occupied
            }
            batches.map { GlyphBatch(it.first) }
        }

        public val glyphPaths: List<Path> get() = glyphBatches.map { it.path }
    }

    public val tiles: List<Tile>

    init {
        val frames = if (layout.staffs.isEmpty()) {
            listOf(GregorianRect(0f, 0f, layout.size.width, layout.size.height))
        } else {
            layout.staffs.map { it.systemFrame }
        }
        val strokes = List(frames.size) { mutableListOf<GregorianPlacedStroke>() }
        val glyphs = List(frames.size) { mutableListOf<GregorianPlacedGlyph>() }
        val lyrics = List(frames.size) { mutableListOf<GregorianPlacedLyric>() }
        val initials = List(frames.size) { mutableListOf<GregorianPlacedInitial>() }
        fun tileIndex(y: Float): Int = frames.indexOfFirst { y < it.bottom }.takeIf { it >= 0 } ?: frames.lastIndex
        for (stroke in layout.strokes) strokes[tileIndex((stroke.start.y + stroke.end.y) / 2)] += stroke
        for (glyph in layout.glyphs) glyphs[tileIndex(glyph.frame.center.y)] += glyph
        for (lyric in layout.lyrics) lyrics[tileIndex(lyric.origin.y)] += lyric
        for (initial in layout.initials) initials[tileIndex(initial.origin.y)] += initial
        tiles = frames.indices.map { Tile(frames[it], strokes[it], glyphs[it], lyrics[it], initials[it]) }
    }
}
