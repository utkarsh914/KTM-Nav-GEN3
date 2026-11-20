package com.navigator.app.ui.feature.rides

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ui.map.bandColor

/**
 * Draws a projected route: speed-coloured segments ([neutral] where a point has no speed). A point that starts a
 * gap is joined to the one before it by a dashed [gapColor] link when [gapColor] is set, else the line just breaks.
 */
internal fun DrawScope.drawRoute(
    px: List<ThumbPixel>,
    strokeWidth: Float,
    neutral: Color,
    gapColor: Color? = null,
    bands: RideMetrics.SpeedBands = RideMetrics.SpeedBands.DEFAULT,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 3, strokeWidth * 3))
    for (i in 1 until px.size) {
        val b = px[i]
        val a = px[i - 1]
        if (b.gap) {
            if (gapColor != null) {
                drawLine(gapColor, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = strokeWidth, cap = StrokeCap.Butt, pathEffect = dash)
            }
            continue
        }
        val color = if (b.speedKmh == null) neutral else Color(bandColor(bands.bandOf(b.speedKmh), bands.bandCount))
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}
