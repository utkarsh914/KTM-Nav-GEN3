package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.RouteLeg
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.DurationText
import java.time.ZoneId
import kotlin.math.roundToInt

/** Per-leg summary lines and drag-reorder maths for the multi-stop preview (pure, JVM-testable). */
object StopLegs {

    /** "12 min" / "1 h 05 min": [DurationText]'s one rule. */
    fun duration(seconds: Int, format: DurationText.Format = DurationText.Format.English): String = DurationText.format(seconds, format)

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
        words: PlanWords = PlanWords.English,
    ): List<String> {
        if (legCount < 2 || legs.size != legCount) return emptyList()
        var elapsed = 0L
        return legs.map { leg ->
            elapsed += leg.durationSeconds * 1000L
            val arrive = ClockFormat.local(nowMs + elapsed, zone)
            duration(leg.durationSeconds, words.duration) + " \u00b7 " + DistanceFormatter.format(leg.distanceMeters, DistanceUnits.METRIC) +
                " \u00b7 " + words.arrive.format(arrive)
        }
    }

    /** Rows to move for a drag of [dragPx] over rows [slotPx] tall, clamped to the list. */
    fun dragDelta(dragPx: Float, slotPx: Float, index: Int, size: Int): Int {
        if (slotPx <= 0f || size <= 1) return 0
        return (dragPx / slotPx).roundToInt().coerceIn(-index, size - 1 - index)
    }
}
