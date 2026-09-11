package com.navigator.app.ride

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Aggregate figures derived from a ride's point track. */
data class RideStats(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val pointCount: Int,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Pure ride math shared by the live recorder and crash-recovery, so both paths
 * compute identical figures. No Android dependencies → JVM unit-testable.
 *
 * Distance uses the haversine great-circle formula. Average speed is the mean of
 * fixes that carried a GPS speed; if none did, it falls back to distance/time.
 */
object RideMetrics {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two lat/lng points, in metres. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Running accumulator fed one [RidePoint] at a time during recording. Also
     * used to recompute a finished ride from its file (via [summarize]).
     */
    class Accumulator {
        private var started = false
        private var firstMs = 0L
        private var lastMs = 0L
        private var prevLat = 0.0
        private var prevLng = 0.0
        private var havePrev = false
        private var distanceM = 0.0
        private var maxSpeed = 0f
        private var speedSum = 0.0
        private var speedSamples = 0
        private var count = 0

        fun add(p: RidePoint) {
            if (!started) {
                firstMs = p.timeMs
                started = true
            }
            lastMs = p.timeMs
            if (havePrev) {
                distanceM += haversineMeters(prevLat, prevLng, p.lat, p.lng)
            }
            prevLat = p.lat
            prevLng = p.lng
            havePrev = true
            p.speedKmh?.let { s ->
                if (s > maxSpeed) maxSpeed = s
                speedSum += s
                speedSamples++
            }
            count++
        }

        fun stats(): RideStats {
            val durationSec = if (lastMs > firstMs) ((lastMs - firstMs) / 1000).toInt() else 0
            val avg = when {
                speedSamples > 0 -> (speedSum / speedSamples).toFloat()
                durationSec > 0 -> (distanceM / durationSec * 3.6).toFloat()
                else -> 0f
            }
            return RideStats(
                distanceMeters = distanceM.toInt(),
                durationSeconds = durationSec,
                avgSpeedKmh = avg,
                maxSpeedKmh = maxSpeed,
                pointCount = count,
                startMs = firstMs,
                endMs = lastMs,
            )
        }
    }

    /** Whole-list computation (crash recovery, tests) — identical math to live. */
    fun summarize(points: List<RidePoint>): RideStats {
        val acc = Accumulator()
        points.forEach(acc::add)
        return acc.stats()
    }

    /**
     * A ride is worth keeping only if it clears BOTH the distance and duration
     * cutoffs (trivial/aborted trips are short in both).
     */
    fun meetsCutoff(
        distanceMeters: Int,
        durationSeconds: Int,
        minDistanceMeters: Int,
        minDurationSeconds: Int,
    ): Boolean = distanceMeters >= minDistanceMeters && durationSeconds >= minDurationSeconds

    /**
     * Decide which rides to prune to honour a history limit. Saved rides are kept
     * regardless and never count toward the limit; among the unsaved rides only
     * the newest [limit] are kept and the rest are returned for deletion (their
     * ids). A non-positive [limit] keeps all unsaved rides (no pruning).
     */
    fun ridesToPrune(rides: List<RecordedRide>, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val unsavedNewestFirst = rides.filterNot { it.saved }.sortedByDescending { it.startMs }
        if (unsavedNewestFirst.size <= limit) return emptyList()
        return unsavedNewestFirst.drop(limit).map { it.id }
    }

    /**
     * Configurable speed-band scheme for colouring the ride track. The first band
     * (index 0, green) covers 0..[baseKmh]; each subsequent band spans [stepKmh]
     * km/h and shifts toward red, for [count] bands ABOVE the base (so band
     * indices run 0..count, i.e. count+1 total, the last being open-ended).
     */
    data class SpeedBands(
        val baseKmh: Int,
        val stepKmh: Int,
        val count: Int,
    ) {
        /** Total number of distinct colour bands (base + [count] steps). */
        val bandCount: Int get() = count + 1

        /** Classify a speed (km/h) into a band index in 0..[count]. */
        fun bandOf(speedKmh: Float?): Int {
            val s = speedKmh ?: return 0
            if (s < baseKmh) return 0
            val over = s - baseKmh
            val step = if (stepKmh <= 0) 1 else stepKmh
            return (1 + (over / step).toInt()).coerceIn(0, count)
        }

        /** Upper bound (km/h) of band [index], or null for the open-ended top band. */
        fun upperBoundKmh(index: Int): Int? =
            if (index >= count) null else baseKmh + index * stepKmh

        companion object {
            val DEFAULT = SpeedBands(baseKmh = 40, stepKmh = 20, count = 5)
        }
    }
}
