package com.navigator.app.nav.model

import com.navigator.app.ride.RideMetrics

/**
 * Which stops of a multi-stop trip are still ahead, from the waypoint the guidance is currently
 * heading to. The trip's own list never shrinks as stops are passed, so the SDK's current
 * destination is the only reliable marker. Pure, JVM-testable.
 */
object RemainingStops {

    /** The SDK may snap a waypoint onto a road: match within this distance. */
    const val MATCH_RADIUS_M = 250.0

    /**
     * The stops still to visit: those from the one being approached onward. All of [stops] when
     * heading to the first one, none when heading to the final destination. Null when
     * [currentLat]/[currentLng] matches no waypoint of the trip, so the caller must not guess.
     */
    fun resolve(stops: List<Stop>, destLat: Double, destLng: Double, currentLat: Double, currentLng: Double): List<Stop>? {
        fun d(lat: Double, lng: Double) = RideMetrics.haversineMeters(lat, lng, currentLat, currentLng)
        // Waypoints in visiting order; the final destination is the last.
        val dists = stops.map { d(it.lat, it.lng) } + d(destLat, destLng)
        var best = -1
        for (i in dists.indices) {
            if (dists[i] <= MATCH_RADIUS_M && (best < 0 || dists[i] < dists[best] - 1.0)) best = i
        }
        if (best < 0) return null
        return stops.drop(best)
    }
}
