package com.navigator.app.ride

/**
 * Metadata for one recorded ride. The point track itself lives in a per-ride
 * NDJSON file (see [RideStore]); this is the lightweight index entry shown in
 * the history list and used to drive replay.
 *
 * @param id stable id (ride start epoch millis, as a string) — also the track filename
 * @param startMs first fix time (epoch millis)
 * @param endMs last fix time (epoch millis)
 * @param distanceMeters total track distance
 * @param durationSeconds wall-clock span from first to last fix
 * @param destinationLabel human label of where the ride was headed, if known
 * @param destLat destination latitude, if known
 * @param destLng destination longitude, if known
 * @param avgSpeedKmh average speed = distance / MOVING time (stops excluded). Rides recorded before this definition used the mean of the fix speeds, stops included
 * @param maxSpeedKmh peak recorded speed
 * @param pointCount number of recorded fixes
 * @param saved pinned to keep permanently; excluded from the history-limit prune
 */
data class RecordedRide(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val destinationLabel: String? = null,
    val destLat: Double? = null,
    val destLng: Double? = null,
    val avgSpeedKmh: Float = 0f,
    /** Seconds spent moving; [MOVING_UNKNOWN] for rides recorded before it was stored (backfilled at launch). */
    val movingSeconds: Int = MOVING_UNKNOWN,
    /** distance / total duration (stops included); 0 when not yet computed. */
    val avgOverallSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val pointCount: Int = 0,
    val saved: Boolean = false,
    /** Total climb / descent in metres; [ELEV_UNKNOWN] for rides recorded without altitude. */
    val elevGainM: Int = ELEV_UNKNOWN,
    val elevLossM: Int = ELEV_UNKNOWN,
)

const val MOVING_UNKNOWN = -1
const val ELEV_UNKNOWN = -1
