package io.github.mcmxmcm.gabc.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mcmxmcm.gabc.layout.GregorianLayoutMetrics
import io.github.mcmxmcm.gabc.layout.GregorianNeumePlacement
import io.github.mcmxmcm.gabc.parser.GregorianScore
import io.github.mcmxmcm.gabc.parser.GregorianScoreParser

/**
 * Parses, engraves and draws a score across the width it is offered, as tall as its staffs need.
 *
 * The score is laid out in composition, when the view first learns its width, and again when the width or the score
 * changes. For long documents, prepare [GregorianPreparedScore] values in the background and draw them with
 * [GregorianScoreCanvas].
 *
 * @param gabc A GABC file, or its notation alone.
 * @param scoreId A prefix for the names the parser gives the notes; see [GregorianScoreParser.parse].
 * @param metrics The score's sizes. Without a display scale, the view snaps the staff to the pixels of the screen it is
 *   drawn on.
 * @param mode The score's mode, shown above a large initial when it names a conclusion (`8g`, `7c2`), or when the
 *   score opens a piece.
 * @param opensPiece Whether the score opens a piece, a hymn, a responsory or a part of the Mass, which takes an initial.
 * @param highlightedEventId A note to light, with the rest of its neume and the lyric of its syllable.
 * @param onTapNeume Called with the neume nearest a tap.
 */
@Composable
public fun GregorianScoreView(
    gabc: String,
    modifier: Modifier = Modifier,
    scoreId: String? = null,
    metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
    mode: String? = null,
    opensPiece: Boolean = false,
    highlightedEventId: String? = null,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)? = null,
) {
    ScoreView(modifier, Source.Gabc(gabc, scoreId), metrics, mode, opensPiece, highlightedEventId, onTapNeume)
}

/** Engraves and draws a parsed score across the width it is offered; see the GABC overload. */
@Composable
public fun GregorianScoreView(
    score: GregorianScore,
    modifier: Modifier = Modifier,
    metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
    mode: String? = null,
    opensPiece: Boolean = false,
    highlightedEventId: String? = null,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)? = null,
) {
    ScoreView(modifier, Source.Score(score), metrics, mode, opensPiece, highlightedEventId, onTapNeume)
}

private sealed interface Source {
    data class Gabc(val gabc: String, val scoreId: String?) : Source
    data class Score(val score: GregorianScore) : Source
}

@Composable
private fun ScoreView(
    modifier: Modifier,
    source: Source,
    metrics: GregorianLayoutMetrics,
    mode: String?,
    opensPiece: Boolean,
    highlightedEventId: String?,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)?,
) {
    val context = LocalContext.current
    // A layout point is drawn to a density-independent pixel: the staff is snapped to the screen's pixels in them.
    val density = LocalDensity.current.density
    val screenMetrics = if (metrics.displayScale == null) metrics.copy(displayScale = density.toDouble()) else metrics
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (!constraints.hasBoundedWidth) return@BoxWithConstraints
        val width = maxWidth.value
        // The score is parsed once, and laid out again at each width.
        val parsed = remember(source) {
            runCatching {
                when (source) {
                    is Source.Gabc -> GregorianScoreParser.parse(source.gabc, source.scoreId)
                    is Source.Score -> source.score
                }
            }
        }
        val prepared = remember(parsed, screenMetrics, mode, opensPiece, width) {
            parsed.mapCatching { GregorianPreparedScore.prepare(context, it, width, screenMetrics, mode, opensPiece) }
        }
        prepared.fold(
            onSuccess = { GregorianScoreCanvas(it, highlightedEventId = highlightedEventId, onTapNeume = onTapNeume) },
            onFailure = { error ->
                BasicText(
                    "Notation unavailable\n${error.message.orEmpty()}",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    style = TextStyle(color = currentScoreStyle().ink, textAlign = TextAlign.Center),
                )
            },
        )
    }
}
