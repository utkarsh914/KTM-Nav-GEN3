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
 * @param source one of [RideSource]; null only on a ride that was not read through [RideStore] (which infers it for old rides)
 * @param parts how many rides were merged into this one (1 for any other; 0 for a merged ride trimmed down to a single part, so its pauses are not mistaken for joins); the replay badge shows it while the track still has that many segments
 * @param deletedAtMs when the rider deleted it (it waits for an undo until purged); null for a live ride
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
    /** Free-text note the rider added in Replay. */
    val notes: String? = null,
    val source: String? = null,
    val deletedAtMs: Long? = null,
    val parts: Int = 1,
) {
    val isDeleted: Boolean get() = deletedAtMs != null
}

const val MOVING_UNKNOWN = -1
const val ELEV_UNKNOWN = -1
