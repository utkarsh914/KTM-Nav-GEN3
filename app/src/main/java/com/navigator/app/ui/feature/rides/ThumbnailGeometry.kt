package com.navigator.app.ui.feature.rides

import com.navigator.app.data.rides.ThumbPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A thumbnail point in pixels. [gap]: the line breaks before it. */
data class ThumbPixel(val x: Float, val y: Float, val speedKmh: Float?, val gap: Boolean)

object ThumbnailGeometry {
    /**
     * Fit the track into [width] x [height] (minus [pad]) keeping its shape: longitude is scaled by cos(latitude)
     * so a loop does not look stretched, and the track is centred.
     */
    fun project(points: List<ThumbPoint>, width: Float, height: Float, pad: Float): List<ThumbPixel> {
        if (points.isEmpty()) return emptyList()
        val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }; val maxLng = points.maxOf { it.lng }
        val k = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)
        val spanX = ((maxLng - minLng) * k).coerceAtLeast(MIN_SPAN)
        val spanY = (maxLat - minLat).coerceAtLeast(MIN_SPAN)
        val availW = max(width - 2 * pad, 1f); val availH = max(height - 2 * pad, 1f)
        val scale = min(availW / spanX, availH / spanY)
        val offX = pad + (availW - (spanX * scale).toFloat()) / 2
        val offY = pad + (availH - (spanY * scale).toFloat()) / 2
        return points.map {
            ThumbPixel(
                x = offX + (((it.lng - minLng) * k) * scale).toFloat(),
                y = offY + (((maxLat - it.lat)) * scale).toFloat(),
                speedKmh = it.speedKmh, gap = it.gap,
            )
        }
    }

    private const val MIN_SPAN = 1e-6
}
