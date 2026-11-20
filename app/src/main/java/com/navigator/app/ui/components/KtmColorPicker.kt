package com.navigator.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** A colour as hue (0..360), saturation and value (0..1). */
data class Hsv(val hue: Float, val sat: Float, val value: Float) {
    fun toColor(): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

    companion object {
        fun of(color: Color): Hsv {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), out)
            return Hsv(out[0], out[1], out[2])
        }
    }
}

/**
 * Shade panel (saturation across, brightness up) over a hue slider, with 28 dp handles. Drag or tap either.
 * The hex field next to it is the keyboard and screen-reader route to the same colour.
 */
@Composable
fun KtmColorPicker(
    hsv: Hsv,
    onChange: (Hsv) -> Unit,
    panelDescription: String,
    hueDescription: String,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    val c = KtmTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        Box(
            Modifier.fillMaxWidth().height(KtmLayout.colorPanel).clip(KtmShapes.Control)
                .semantics { contentDescription = panelDescription }
                .pointerInput(Unit) {
                    detectTapGestures { o -> change(current.copy(sat = (o.x / size.width).coerceIn(0f, 1f), value = 1f - (o.y / size.height).coerceIn(0f, 1f))) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { pointer, _ ->
                        change(current.copy(sat = (pointer.position.x / size.width).coerceIn(0f, 1f), value = 1f - (pointer.position.y / size.height).coerceIn(0f, 1f)))
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Hsv(current.hue, 1f, 1f).toColor())))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val r = KtmLayout.colorHandle.toPx() / 2
                val centre = Offset((current.sat * size.width).coerceIn(r, size.width - r), ((1f - current.value) * size.height).coerceIn(r, size.height - r))
                drawCircle(current.toColor(), r, centre)
                drawCircle(Color.White, r, centre, style = Stroke(KtmLayout.colorHandleRing.toPx()))
            }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest)
                .semantics { contentDescription = hueDescription }
                .pointerInput(Unit) { detectTapGestures { o -> change(current.copy(hue = (o.x / size.width * 360f).coerceIn(0f, 360f))) } }
                .pointerInput(Unit) { detectDragGestures { pointer, _ -> change(current.copy(hue = (pointer.position.x / size.width * 360f).coerceIn(0f, 360f))) } },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val track = KtmLayout.hueTrack.toPx()
                val top = (size.height - track) / 2
                drawRoundRect(
                    Brush.horizontalGradient((0..6).map { Hsv(it * 60f, 1f, 1f).toColor() }),
                    topLeft = Offset(0f, top), size = Size(size.width, track), cornerRadius = CornerRadius(track / 2),
                )
                val r = KtmLayout.colorHandle.toPx() / 2
                val centre = Offset((current.hue / 360f * size.width).coerceIn(r, size.width - r), size.height / 2)
                drawCircle(Hsv(current.hue, 1f, 1f).toColor(), r, centre)
                drawCircle(c.fg, r, centre, style = Stroke(KtmLayout.colorHandleRing.toPx()))
            }
        }
    }
}
