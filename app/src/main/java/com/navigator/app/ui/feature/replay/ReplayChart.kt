package com.navigator.app.ui.feature.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.navigator.app.R
import com.navigator.app.ride.ChartSeries
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlin.math.roundToInt

/**
 * The speed chart with a km/h axis at the left and clock times underneath. The elevation profile fills behind the
 * line when [showElevation] is on and the ride has altitude; a dashed line marks each stop between merged parts and
 * the speed line breaks there. The cursor follows [position]; a tap or drag seeks. The static layers are cached, so
 * a playback frame only moves the cursor.
 */
@Composable
fun ReplayChart(
    series: ChartSeries,
    seams: List<Float>,
    position: State<Int>,
    pointCount: Int,
    startMs: Long,
    endMs: Long,
    showElevation: Boolean,
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.fg3)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrubStart)
    val description = if (series.alt != null) {
        stringResource(R.string.replay_chart_description_elevation, series.maxSpeed.roundToInt(), series.minAlt.roundToInt(), series.maxAlt.roundToInt())
    } else stringResource(R.string.replay_chart_description, series.maxSpeed.roundToInt())
    val unit = stringResource(R.string.replay_chart_axis_kmh)
    val ticks = ChartAxis.speedTicks(series.maxSpeed)
    val times = ChartAxis.timeTicks(startMs, endMs, TIME_LABELS)

    Box(
        modifier.fillMaxWidth().height(KtmLayout.replayChart + KtmLayout.chartAxisHeight)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                val axis = KtmLayout.chartAxisWidth.toPx()
                detectTapGestures { at -> seek(plotFraction(at.x, this.size.width.toFloat(), axis)) }
            }
            .pointerInput(Unit) {
                val axis = KtmLayout.chartAxisWidth.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { at -> scrub(); seek(plotFraction(at.x, this.size.width.toFloat(), axis)) },
                    onHorizontalDrag = { change, _ -> seek(plotFraction(change.position.x, this.size.width.toFloat(), axis)) },
                )
            },
    ) {
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val axisW = KtmLayout.chartAxisWidth.toPx()
                val axisH = KtmLayout.chartAxisHeight.toPx()
                val gap = KtmSpacing.xs.toPx()
                val left = axisW
                val plotW = size.width - axisW
                val plotH = size.height - axisH
                val top = ticks.last().toFloat().coerceAtLeast(1f)
                val n = series.speed.size
                fun x(i: Int) = left + plotW * i / (n - 1).coerceAtLeast(1)
                fun y(kmh: Float) = plotH - (kmh / top).coerceIn(0f, 1f) * plotH
                val breaks = seams.map { (it * (n - 1)).roundToInt() }.toSet()
                val speedLine = Path().apply {
                    for (i in 0 until n) {
                        if (i == 0 || i in breaks) moveTo(x(i), y(series.speed[i])) else lineTo(x(i), y(series.speed[i]))
                    }
                }
                val fill = series.alt?.let { alt ->
                    val range = (series.maxAlt - series.minAlt).coerceAtLeast(20f) // flat roads stay flat
                    Path().apply {
                        moveTo(left, plotH)
                        for (i in 0 until n) lineTo(x(i), plotH - (alt[i] - series.minAlt) / range * plotH * 0.9f)
                        lineTo(left + plotW, plotH)
                        close()
                    }
                }
                val grid = ticks.map { tick -> tick to measurer.measure("$tick", labelStyle) }
                val unitLabel = measurer.measure(unit, labelStyle)
                val timeLabels = times.map { measurer.measure(ReplayFormat.axisTime(it), labelStyle) }
                val hairline = KtmLayout.hairline.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(gap, gap))
                onDrawBehind {
                    if (showElevation && fill != null) drawPath(fill, c.fg3.copy(alpha = ELEVATION_ALPHA))
                    grid.forEach { (tick, label) ->
                        val ty = y(tick.toFloat())
                        drawLine(c.line, Offset(left, ty), Offset(left + plotW, ty), strokeWidth = hairline)
                        drawText(label, topLeft = Offset(axisW - gap - label.size.width, (ty - label.size.height / 2).coerceAtLeast(0f)))
                    }
                    drawText(unitLabel, topLeft = Offset(0f, plotH + gap))
                    seams.forEach { s ->
                        val sx = left + plotW * s
                        drawLine(c.warn, Offset(sx, 0f), Offset(sx, plotH), strokeWidth = hairline * 2, pathEffect = dash)
                    }
                    drawPath(speedLine, a.text, style = Stroke(width = KtmLayout.outline.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    timeLabels.forEachIndexed { i, label ->
                        val lx = when (i) {
                            0 -> left + gap * 2 // clear of the km/h label under the axis
                            timeLabels.lastIndex -> left + plotW - label.size.width
                            else -> left + plotW * i / timeLabels.lastIndex - label.size.width / 2f
                        }
                        drawText(label, topLeft = Offset(lx, plotH + gap))
                    }
                }
            },
        ) {}
        Canvas(Modifier.fillMaxSize()) {
            val axisW = KtmLayout.chartAxisWidth.toPx()
            val plotW = size.width - axisW
            val plotH = size.height - KtmLayout.chartAxisHeight.toPx()
            val cx = axisW + plotW * PlaybackEngine.fractionOf(position.value, pointCount)
            drawLine(c.fg, Offset(cx, 0f), Offset(cx, plotH), strokeWidth = KtmLayout.outline.toPx())
        }
    }
}

/** A touch on the chart maps to the plot area (right of the axis), not the whole width. */
internal fun plotFraction(x: Float, widthPx: Float, axisPx: Float): Float =
    ((x - axisPx) / (widthPx - axisPx).coerceAtLeast(1f)).coerceIn(0f, 1f)

private const val TIME_LABELS = 3
private const val ELEVATION_ALPHA = 0.22f
