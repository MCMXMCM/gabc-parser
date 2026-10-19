package io.github.mcmxmcm.gabc.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * @param mode The score's mode, shown above a large initial when it names a conclusion (`8g`, `7c2`).
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
    highlightedEventId: String? = null,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)? = null,
) {
    ScoreView(modifier, Source.Gabc(gabc, scoreId), metrics, mode, highlightedEventId, onTapNeume)
}

/** Engraves and draws a parsed score across the width it is offered; see the GABC overload. */
@Composable
public fun GregorianScoreView(
    score: GregorianScore,
    modifier: Modifier = Modifier,
    metrics: GregorianLayoutMetrics = GregorianLayoutMetrics(),
    mode: String? = null,
    highlightedEventId: String? = null,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)? = null,
) {
    ScoreView(modifier, Source.Score(score), metrics, mode, highlightedEventId, onTapNeume)
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
    highlightedEventId: String?,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)?,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (!constraints.hasBoundedWidth) return@BoxWithConstraints
        val width = maxWidth.value
        val prepared = remember(source, metrics, mode, width) {
            runCatching {
                val score = when (source) {
                    is Source.Gabc -> GregorianScoreParser.parse(source.gabc, source.scoreId)
                    is Source.Score -> source.score
                }
                GregorianPreparedScore.prepare(context, score, width, metrics, mode)
            }
        }
        prepared.fold(
            onSuccess = { GregorianScoreCanvas(it, highlightedEventId = highlightedEventId, onTapNeume = onTapNeume) },
            onFailure = { error ->
                BasicText(
                    "Notation unavailable\n${error.message.orEmpty()}",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    style = TextStyle(color = (LocalGregorianScoreStyle.current ?: GregorianScoreStyle.standard()).ink, textAlign = TextAlign.Center),
                )
            },
        )
    }
}
