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
    /** Time spent actually moving (stopped time excluded). */
    val movingSeconds: Int = 0,
    /** distance / total duration (stops included); [avgSpeedKmh] is distance / moving time. */
    val avgOverallSpeedKmh: Float = 0f,
)

/**
 * Pure ride math shared by the live recorder and crash-recovery, so both paths
 * compute identical figures. No Android dependencies → JVM unit-testable.
 *
 * Distance uses the haversine great-circle formula, ignoring GPS noise: fixes that go
 * back in time, jumps implying more than [MAX_PLAUSIBLE_KMH], and sub-[JITTER_HOP_M]
 * hops while stationary don't count. Average speed is distance / MOVING time (stops
 * excluded); max speed is the best 3-fix median so a single spike can't set it.
 */
object RideMetrics {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** A hop implying more than this is a GPS glitch, not riding. */
    const val MAX_PLAUSIBLE_KMH = 300.0
    /** Stationary jitter: hops shorter than this at (near-)zero speed add no distance. */
    const val JITTER_HOP_M = 3.0
    /** Consecutive rejected fixes after which the next one becomes the new baseline. */
    const val RESYNC_AFTER_REJECTS = 5
    /** At/above this speed the rider counts as moving. */
    const val MOVING_KMH = 2.0

    /** Great-circle distance between two lat/lng points, in metres. */
    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = a.coerceIn(0.0, 1.0) // float error can push a just past 1 for antipodal points
        return EARTH_RADIUS_M * 2 * atan2(sqrt(c), sqrt(1 - c))
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
        private var movingMs = 0L
        private var count = 0
        private val recentSpeeds = ArrayDeque<Float>()
        private var rejectStreak = 0
        private var pausedMs = 0L

        /** Feed one fix; returns false when it was rejected as a glitch (and must not be recorded). */
        fun add(p: RidePoint): Boolean {
            var resync = false
            // First fix after a user pause: not a glitch check, not riding.
            val gap = p.gapBefore && started
            if (started && !gap) {
                val dtMs = p.timeMs - lastMs
                // Clock went backwards, or a hop that would need an impossible speed: GPS glitch.
                val glitch = dtMs < 0 || (havePrev && dtMs > 0 &&
                    haversineMeters(prevLat, prevLng, p.lat, p.lng) / (dtMs / 1000.0) * 3.6 > MAX_PLAUSIBLE_KMH)
                if (glitch) {
                    // If the BASELINE was the bad fix, every later good fix would look like a
                    // glitch for the rest of the ride: after a streak, adopt the new fix as
                    // the baseline (no distance for the jump).
                    if (++rejectStreak < RESYNC_AFTER_REJECTS) return false
                    resync = true
                }
            }
            rejectStreak = 0
            if (gap) pausedMs += (p.timeMs - lastMs).coerceAtLeast(0L)
            if (havePrev && !resync && !gap) {
                val dtMs = p.timeMs - lastMs
                val hop = haversineMeters(prevLat, prevLng, p.lat, p.lng)
                val speed = p.speedKmh
                val stationaryJitter = hop < JITTER_HOP_M && (speed == null || speed < MOVING_KMH)
                if (!stationaryJitter) distanceM += hop
                val impliedKmh = if (dtMs > 0) hop / (dtMs / 1000.0) * 3.6 else 0.0
                val moving = !stationaryJitter &&
                    (if (speed != null) speed >= MOVING_KMH else impliedKmh >= MOVING_KMH)
                if (moving && dtMs > 0) movingMs += dtMs
            }
            if (!started || (resync && count <= RESYNC_AFTER_REJECTS)) {
                firstMs = p.timeMs
                pausedMs = 0L
                started = true
            }
            lastMs = p.timeMs
            prevLat = p.lat
            prevLng = p.lng
            havePrev = true
            p.speedKmh?.let { s ->
                recentSpeeds.addLast(s)
                if (recentSpeeds.size > 3) recentSpeeds.removeFirst()
                if (recentSpeeds.size == 3) {
                    val median = recentSpeeds.sorted()[1]
                    if (median > maxSpeed) maxSpeed = median
                }
            }
            count++
            return true
        }

        fun stats(): RideStats {
            val spanMs = lastMs - firstMs - pausedMs
            val durationSec = if (spanMs > 0) (spanMs / 1000).toInt() else 0
            val movingSec = (movingMs / 1000).toInt()
            val avg = if (movingSec > 0) (distanceM / movingSec * 3.6).toFloat() else 0f
            val overall = if (durationSec > 0) (distanceM / durationSec * 3.6).toFloat() else 0f
            return RideStats(
                distanceMeters = distanceM.toInt(),
                durationSeconds = durationSec,
                avgSpeedKmh = avg,
                maxSpeedKmh = maxSpeed,
                pointCount = count,
                startMs = firstMs,
                endMs = lastMs,
                movingSeconds = movingSec,
                avgOverallSpeedKmh = overall,
            )
        }
    }

    /** Whole-list computation (crash recovery, tests) — identical math to live. */
    fun summarize(points: List<RidePoint>): RideStats {
        val acc = Accumulator()
        points.forEach { acc.add(it) }
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
        // Newest by CREATION order (the id is the creation time in millis), not by GPS
        // startMs - a bad clock/fix time must not make a fresh ride look old.
        val unsavedNewestFirst = rides.filterNot { it.saved }
            .sortedByDescending { it.id.toLongOrNull() ?: it.startMs }
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
