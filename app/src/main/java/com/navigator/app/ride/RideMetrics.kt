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

    /** Speed bucket for colouring the replay track. */
    enum class SpeedBand { SLOW, MEDIUM, FAST, VERY_FAST }

    /** Classify a speed (km/h) into a colour band for the replay polyline. */
    fun band(speedKmh: Float?): SpeedBand = when {
        speedKmh == null -> SpeedBand.SLOW
        speedKmh < 20f -> SpeedBand.SLOW
        speedKmh < 45f -> SpeedBand.MEDIUM
        speedKmh < 80f -> SpeedBand.FAST
        else -> SpeedBand.VERY_FAST
    }
}
