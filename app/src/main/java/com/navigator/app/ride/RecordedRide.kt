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
 * @param avgSpeedKmh average speed across fixes that carried a speed value
 * @param maxSpeedKmh peak recorded speed
 * @param pointCount number of recorded fixes
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
    val maxSpeedKmh: Float = 0f,
    val pointCount: Int = 0,
)
