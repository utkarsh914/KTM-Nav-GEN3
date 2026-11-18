package com.navigator.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.navigator.app.ride.ChartSeries
import com.navigator.app.ui.theme.Ktm

/** Elevation profile (filled, muted) under a speed line (orange); a cursor marks the replay position. */
@Composable
internal fun RideChartView(series: ChartSeries, progress: Float, modifier: Modifier = Modifier) {
    val fill = Ktm.Muted2.copy(alpha = 0.22f)
    val line = Ktm.Orange
    val cursor = Ktm.TextPrimary
    val description = buildString {
        append("Speed chart, top ${series.maxSpeed.toInt()} km/h")
        if (series.alt != null) append(", elevation ${series.minAlt.toInt()} to ${series.maxAlt.toInt()} metres")
    }
    Box(
        modifier = modifier.fillMaxWidth().height(44.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).drawWithCache {
                // The two paths only depend on size + series: build them once, and let a
                // playback tick redraw just the cursor.
                val w = size.width
                val h = size.height
                val n = series.speed.size
                fun x(i: Int) = w * i / (n - 1).coerceAtLeast(1)
                val elevation = series.alt?.let { alt ->
                    val range = (series.maxAlt - series.minAlt).coerceAtLeast(20f) // flat roads stay flat
                    Path().apply {
                        moveTo(0f, h)
                        for (i in 0 until n) lineTo(x(i), h - (alt[i] - series.minAlt) / range * h * 0.9f)
                        lineTo(w, h)
                        close()
                    }
                }
                val speedLine = Path().apply {
                    for (i in 0 until n) {
                        val y = h - series.speed[i] / series.maxSpeed * (h - 4f) - 2f
                        if (i == 0) moveTo(x(i), y) else lineTo(x(i), y)
                    }
                }
                onDrawBehind {
                    elevation?.let { drawPath(it, fill) }
                    drawPath(speedLine, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    val cx = w * progress.coerceIn(0f, 1f)
                    drawLine(cursor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5.dp.toPx())
                }
            },
        ) {}
    }
}
