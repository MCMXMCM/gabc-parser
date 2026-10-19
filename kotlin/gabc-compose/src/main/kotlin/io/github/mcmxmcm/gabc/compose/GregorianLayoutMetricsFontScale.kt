package io.github.mcmxmcm.gabc.compose

import io.github.mcmxmcm.gabc.layout.GregorianLayoutMetrics

/**
 * A lyric scale that follows the system's font size, as the Swift package's follows Dynamic Type: 1 at the default
 * size, from 0.94 at the smallest to 1.65 at the largest.
 */
public fun GregorianLayoutMetrics.Companion.lyricScale(fontScale: Float): Double = when {
    fontScale <= 0.85f -> 0.94
    fontScale <= 0.95f -> 0.98
    fontScale <= 1.05f -> 1.0
    fontScale <= 1.15f -> 1.08
    fontScale <= 1.3f -> 1.16
    fontScale <= 1.5f -> 1.25
    fontScale <= 1.8f -> 1.5
    else -> 1.65
}
