package com.navigator.app.ui.feature.replay

import com.navigator.app.ride.RideMerge
import com.navigator.app.ride.ReplayPacing
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint

/** Where playback stands after a step: the fix it is at and the recorded time already earned towards the next. */
data class Advance(val index: Int, val carryMs: Long)

/**
 * The pure arithmetic of the replay: stepping along the recorded timestamps, jumping across a stop between the
 * parts of a merged ride, seeking, and finding the fix nearest a tapped spot. No Android, so it is unit-tested.
 */
object PlaybackEngine {
    /** Playback speeds the stepper walks through. */
    val SPEEDS = listOf(1, 2, 4, 8, 16, 32, 64)

    const val DEFAULT_SPEED = 4

    /** Longest wait honoured between two fixes, so a GPS dropout doesn't freeze the marker for minutes. */
    const val MAX_STEP_MS = 3_000L

    /** A tap further than this from the track is a map tap, not a seek. */
    const val SEEK_TAP_MAX_M = 100.0

    /** The next speed up or down, staying within the range. */
    fun stepSpeed(current: Int, direction: Int): Int {
        val i = SPEEDS.indexOf(current).let { if (it < 0) SPEEDS.indexOf(DEFAULT_SPEED) else it }
        return SPEEDS[(i + direction).coerceIn(0, SPEEDS.lastIndex)]
    }

    /**
     * Spend [addMs] of recorded time (a frame's worth times the speed) walking forward from [index]. A pause or merge
     * seam costs nothing, so playback skips it; the end keeps no leftover time.
     */
    fun advance(points: List<RidePoint>, index: Int, carryMs: Long, addMs: Long): Advance {
        var i = index.coerceIn(0, points.lastIndex.coerceAtLeast(0))
        var carry = carryMs + addMs
        while (i < points.lastIndex) {
            val step = ReplayPacing.stepMs(points, i).coerceAtMost(MAX_STEP_MS)
            if (step > carry) break
            carry -= step
            i++
        }
        return Advance(i, if (i >= points.lastIndex) 0L else carry)
    }

    /** The fix at [fraction] (0..1) of the way along the track, by index (the chart's buckets follow the index too). */
    fun indexAt(fraction: Float, size: Int): Int =
        if (size < 2) 0 else (fraction.coerceIn(0f, 1f) * (size - 1)).toInt().coerceIn(0, size - 1)

    fun fractionOf(index: Int, size: Int): Float =
        if (size < 2) 0f else (index.toFloat() / (size - 1)).coerceIn(0f, 1f)

    /** Where the stops between parts (and pauses) fall along the track, as fractions of it. */
    fun seams(points: List<RidePoint>): List<Float> =
        points.indices.filter { it > 0 && points[it].gapBefore }.map { fractionOf(it, points.size) }

    /** The subset of [seams] that are merge seams (the rest are pauses), as fractions of the track. */
    fun mergeSeams(points: List<RidePoint>, merged: Boolean): List<Float> =
        RideMerge.seamIndices(points, merged).sorted().map { fractionOf(it, points.size) }

    /** The fix nearest the tapped spot, or null when it is further than [maxM] from the whole track. */
    fun nearest(points: List<RidePoint>, lat: Double, lng: Double, maxM: Double = SEEK_TAP_MAX_M): Int? {
        var best = -1
        var bestDistance = Double.MAX_VALUE
        for (i in points.indices) {
            val d = RideMetrics.haversineMeters(lat, lng, points[i].lat, points[i].lng)
            if (d < bestDistance) { bestDistance = d; best = i }
        }
        return best.takeIf { it >= 0 && bestDistance <= maxM }
    }
}

/** The ticks of the chart's axes. */
object ChartAxis {
    /** Round km/h values from 0 up to just above [max]: 0, 20, 40... with 3 to 5 of them. */
    fun speedTicks(max: Float): List<Int> {
        val top = max.coerceAtLeast(10f)
        val step = listOf(5, 10, 20, 25, 50, 100).firstOrNull { ceil(top / it) <= 4 } ?: 100
        val count = ceil(top / step).coerceAtLeast(1)
        return (0..count).map { it * step }
    }

    /** [count] clock times evenly from [startMs] to [endMs] inclusive (the first and last at the ends). */
    fun timeTicks(startMs: Long, endMs: Long, count: Int): List<Long> {
        if (count < 2 || endMs <= startMs) return listOf(startMs)
        return (0 until count).map { startMs + (endMs - startMs) * it / (count - 1) }
    }

    private fun ceil(v: Float) = kotlin.math.ceil(v.toDouble()).toInt()
}
