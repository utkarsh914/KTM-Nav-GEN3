package com.navigator.app.ui.screens

import com.navigator.app.nav.destination.RouteLeg
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Per-leg summary lines and drag-reorder maths for the multi-stop preview (pure, JVM-testable). */
internal object StopLegs {

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    /** "12 min" / "1 h 05 min". */
    fun duration(seconds: Int): String {
        val m = (seconds.coerceAtLeast(0) / 60.0).roundToInt().coerceAtLeast(1)
        return if (m < 60) "$m min" else "${m / 60} h %02d min".format(m % 60)
    }

    /**
     * One line per leg ("12 min · 8.4 km · arrive 14:32"), the arrival being cumulative from
     * [nowMs]. Empty when the route has no per-leg breakdown for exactly [legCount] legs (a
     * single-destination route, or a stale route that doesn't match the current stops).
     */
    fun lines(
        legs: List<RouteLeg>,
        legCount: Int,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<String> {
        if (legCount < 2 || legs.size != legCount) return emptyList()
        var elapsed = 0L
        return legs.map { leg ->
            elapsed += leg.durationSeconds * 1000L
            val arrive = CLOCK.format(Instant.ofEpochMilli(nowMs + elapsed).atZone(zone))
            duration(leg.durationSeconds) + " \u00b7 " +
                DistanceFormatter.format(leg.distanceMeters, DistanceUnits.METRIC) + " \u00b7 arrive $arrive"
        }
    }

    /** Rows to move for a drag of [dragPx] over rows [slotPx] tall, clamped to the list. */
    fun dragDelta(dragPx: Float, slotPx: Float, index: Int, size: Int): Int {
        if (slotPx <= 0f || size <= 1) return 0
        return (dragPx / slotPx).roundToInt().coerceIn(-index, size - 1 - index)
    }
}
