package com.navigator.app.ride

/**
 * A single recorded GPS fix on a ride track.
 *
 * @param lat latitude in degrees
 * @param lng longitude in degrees
 * @param timeMs fix wall-clock time (epoch millis)
 * @param speedKmh GPS ground speed in km/h, or null when the fix carried no speed
 * @param gapBefore true for the first fix after a user pause: the hop from the previous fix is
 *   not riding (no distance, no moving time) and the paused span is excluded from the duration
 */
data class RidePoint(
    val lat: Double,
    val lng: Double,
    val timeMs: Long,
    val speedKmh: Float? = null,
    val gapBefore: Boolean = false,
)
