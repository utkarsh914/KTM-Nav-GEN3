package com.navigator.app.ride

/**
 * A single recorded GPS fix on a ride track.
 *
 * @param lat latitude in degrees
 * @param lng longitude in degrees
 * @param timeMs fix wall-clock time (epoch millis)
 * @param speedKmh GPS ground speed in km/h, or null when the fix carried no speed
 */
data class RidePoint(
    val lat: Double,
    val lng: Double,
    val timeMs: Long,
    val speedKmh: Float? = null,
)
