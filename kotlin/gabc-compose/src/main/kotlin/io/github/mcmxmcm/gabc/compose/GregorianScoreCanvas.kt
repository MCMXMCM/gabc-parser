package io.github.mcmxmcm.gabc.compose

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mcmxmcm.gabc.layout.GregorianLayout
import io.github.mcmxmcm.gabc.layout.GregorianLyricFont
import io.github.mcmxmcm.gabc.layout.GregorianNeumePlacement
import io.github.mcmxmcm.gabc.layout.GregorianPlacedInitial
import io.github.mcmxmcm.gabc.layout.GregorianPlacedLyric
import io.github.mcmxmcm.gabc.layout.GregorianPlacedStroke
import io.github.mcmxmcm.gabc.layout.GregorianScoreHighlight
import io.github.mcmxmcm.gabc.layout.neumeAt
import io.github.mcmxmcm.gabc.lyricfont.typeface
import io.github.mcmxmcm.gabc.parser.GregorianLyricStyle

/**
 * The face each lyric is drawn in, by the font it was measured in and its style. The layout names the font in each
 * lyric; Android cannot find a face by its PostScript name, so the app that names another gives it here.
 */
public fun interface GregorianLyricTypefaces {
    public fun typeface(fontName: String, style: GregorianLyricStyle): Typeface

    public companion object {
        /** The bundled EB Garamond, whatever the font named: the face the layout measures in by default. */
        public fun bundled(context: Context): GregorianLyricTypefaces {
            // The application's context, which typefaces kept for long can hold without keeping an activity alive.
            val appContext = context.applicationContext
            return GregorianLyricTypefaces { _, style -> GregorianLyricFont.typeface(appContext, style) }
        }
    }
}

/**
 * Draws a prepared score at its laid-out size, a layout point to a density-independent pixel.
 *
 * TalkBack reads the score neume by neume while touch exploration is on; when [onTapNeume] is given, each neume is
 * a button. The neumes are given to it only then: a long chant has hundreds.
 *
 * @param highlightedEventId A note to light, with the rest of its neume and the lyric of its syllable; the light
 *   leaves a note gently as it moves to the next.
 * @param onTapNeume Called with the neume nearest a tap. A custos is never offered.
 */
@Composable
public fun GregorianScoreCanvas(
    prepared: GregorianPreparedScore,
    modifier: Modifier = Modifier,
    highlightedEventId: String? = null,
    onTapNeume: ((GregorianNeumePlacement) -> Unit)? = null,
    typefaces: GregorianLyricTypefaces? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val style = currentScoreStyle()
    val faces = typefaces ?: remember(context) { GregorianLyricTypefaces.bundled(context) }
    val paints = remember(faces) { LyricPaints(faces) }
    val layout = prepared.layout
    val highlight = remember(highlightedEventId, prepared) {
        highlightedEventId?.let { GregorianScoreHighlight.of(it, prepared.score, layout) }
    }
    var shown by remember { mutableStateOf<GregorianScoreHighlight?>(null) }
    var fading by remember { mutableStateOf<GregorianScoreHighlight?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        val previous = shown
        shown = highlight
        if (previous != null && previous.neume.id != highlight?.neume?.id) {
            fading = previous
            fade.snapTo(1f)
            fade.animateTo(0f, tween(400))
            fading = null
        }
    }
    val accessible = rememberTouchExploration(context)
    val tap by rememberUpdatedState(onTapNeume)

    Box(
        modifier
            .size(layout.size.width.dp, layout.size.height.dp)
            .then(
                if (onTapNeume == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(layout) {
                        detectTapGestures { location ->
                            layout.neumeAt((location / density).toGregorianPoint())?.let { tap?.invoke(it) }
                        }
                    }
                },
            )
            .then(if (accessible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        Canvas(Modifier.size(layout.size.width.dp, layout.size.height.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                for (tile in prepared.drawing.tiles) drawTile(tile, style, paints)
            }
        }
        Spacer(
            Modifier.size(layout.size.width.dp, layout.size.height.dp).drawWithCache {
                // Each highlight's ink is gathered once, not on every frame of its fade.
                val fadingInk = fading?.let { HighlightInk(it, layout) }
                val shownInk = shown?.let { HighlightInk(it, layout) }
                onDrawBehind {
                    scale(density, density, pivot = Offset.Zero) {
                        fadingInk?.let { drawHighlight(it, style.highlight.copy(alpha = fade.value), paints) }
                        shownInk?.let { drawHighlight(it, style.highlight, paints) }
                    }
                }
            },
        )
        if (accessible) {
            for (neume in layout.neumes) {
                Box(
                    Modifier
                        .offset(neume.hitFrame.left.dp, neume.hitFrame.top.dp)
                        .size(neume.hitFrame.width.dp, neume.hitFrame.height.dp)
                        .semantics {
                            contentDescription = accessibilityLabel(neume)
                            if (onTapNeume != null) {
                                role = Role.Button
                                onClick("Start here") {
                                    tap?.invoke(neume)
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
}

internal fun accessibilityLabel(neume: GregorianNeumePlacement): String {
    val lyric = neume.lyric.ifEmpty { "Chant" }
    return if (neume.noteCount == 1) "$lyric, one-note neume" else "$lyric, ${neume.noteCount}-note neume"
}

/** Whether TalkBack's touch exploration is on, following it as it's turned on and off. */
@Composable
private fun rememberTouchExploration(context: Context): Boolean {
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** The paints lyrics are drawn with, a face to a style, sized and colored as each is drawn. */
private class LyricPaints(private val faces: GregorianLyricTypefaces) {
    // By font name, then style: a lookup allocates nothing, as lyrics are drawn on every frame of a highlight's fade.
    private val paints = HashMap<String, HashMap<GregorianLyricStyle, Paint>>()

    fun paint(fontName: String, style: GregorianLyricStyle, fontSize: Float, color: Color): Paint =
        paints.getOrPut(fontName) { HashMap() }.getOrPut(style) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = faces.typeface(fontName, style) }
        }.apply {
            textSize = fontSize
            this.color = color.toArgb()
        }
}

/** Text set from the top of its line, as SwiftUI draws a Text from its top-left corner. */
private fun DrawScope.drawText(text: String, x: Float, y: Float, paint: Paint) {
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(text, x, y - paint.ascent(), paint) }
}

private fun DrawScope.drawTile(tile: GregorianScoreDrawing.Tile, style: GregorianScoreStyle, paints: LyricPaints) {
    for (batch in tile.strokeBatches) drawPath(batch.path, style.ink, style = Stroke(batch.lineWidth))
    for (batch in tile.glyphBatches) drawPath(batch.path, style.ink)
    for (lyric in tile.lyrics) {
        val color = if (lyric.style == GregorianLyricStyle.Rubric) style.rubric else style.ink
        drawText(lyric.text, lyric.origin.x, lyric.origin.y, paints.paint(lyric.fontName, lyric.style, lyric.fontSize, color))
    }
    for (initial in tile.initials) {
        val annotation = initial.annotation
        val annotationOrigin = initial.annotationOrigin
        if (annotation != null && annotationOrigin != null) {
            drawText(
                annotation, annotationOrigin.x, annotationOrigin.y,
                paints.paint(initial.fontName, GregorianLyricStyle.Regular, initial.annotationFontSize, style.ink),
            )
        }
        drawText(initial.text, initial.origin.x, initial.origin.y, paints.paint(initial.fontName, GregorianLyricStyle.Regular, initial.fontSize, style.ink))
    }
}

/** The ink a highlight lights, in the order the layout gives it, its glyphs' outlines built. */
private class HighlightInk(highlight: GregorianScoreHighlight, layout: GregorianLayout) {
    val strokes: List<GregorianPlacedStroke> =
        layout.strokes.filter { it.eventId != null && it.eventId in highlight.eventIds }
    val glyphPaths: List<Path> =
        layout.glyphs.filter { it.eventId != null && it.eventId in highlight.eventIds }.map { GregorianGlyphLibrary.path(it) }
    val lyrics: List<GregorianPlacedLyric> = layout.lyrics.filter { it.neumeId in highlight.syllableNeumeIds }
    val initials: List<GregorianPlacedInitial> = layout.initials.filter { it.neumeId in highlight.syllableNeumeIds }
}

private fun DrawScope.drawHighlight(ink: HighlightInk, color: Color, paints: LyricPaints) {
    for (stroke in ink.strokes) drawLine(color, stroke.start.toOffset(), stroke.end.toOffset(), stroke.lineWidth)
    for (path in ink.glyphPaths) {
        drawPath(path, color)
        drawPath(path, color, style = Stroke(1.1f))
    }
    for (lyric in ink.lyrics) {
        drawText(lyric.text, lyric.origin.x, lyric.origin.y, paints.paint(lyric.fontName, lyric.style, lyric.fontSize, color))
    }
    for (initial in ink.initials) {
        drawText(initial.text, initial.origin.x, initial.origin.y, paints.paint(initial.fontName, GregorianLyricStyle.Regular, initial.fontSize, color))
    }
}
