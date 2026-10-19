package io.github.mcmxmcm.gabc.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The colors a score is drawn in. */
@Immutable
public data class GregorianScoreStyle(
    /** Staff lines, neumes, clefs, divisions and lyrics. */
    val ink: Color,
    /** Lyric marks that are rubrics: `*`, `†`, ℣. and ℟. */
    val rubric: Color,
    /** The neume and syllable lit by a highlight. */
    val highlight: Color,
) {
    public companion object {
        /** Warm black ink and red rubrics, as in a printed chant book. */
        public val Light: GregorianScoreStyle = GregorianScoreStyle(
            ink = Color(red = 0.11f, green = 0.10f, blue = 0.08f),
            rubric = Color(red = 0.54f, green = 0.16f, blue = 0.15f),
            highlight = Color(red = 0.48f, green = 0.05f, blue = 0.04f),
        )

        /** The same, lightened for a dark theme. */
        public val Dark: GregorianScoreStyle = GregorianScoreStyle(
            ink = Color(red = 0.85f, green = 0.84f, blue = 0.82f),
            rubric = Color(red = 0.68f, green = 0.12f, blue = 0.09f),
            highlight = Color(red = 0.78f, green = 0.20f, blue = 0.16f),
        )

        /** [Light] or [Dark], as the system's theme is. */
        @Composable
        public fun standard(): GregorianScoreStyle = if (isSystemInDarkTheme()) Dark else Light
    }
}

/** The colors scores below are drawn in; null draws them in [GregorianScoreStyle.standard]. */
public val LocalGregorianScoreStyle: ProvidableCompositionLocal<GregorianScoreStyle?> = staticCompositionLocalOf { null }

/** Sets the colors the scores in [content] are drawn in. */
@Composable
public fun GregorianScoreStyleProvider(style: GregorianScoreStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGregorianScoreStyle provides style, content = content)
}
