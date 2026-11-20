package com.navigator.app.ride

/** How a ride came to exist. Stored as a short string in the index and the track's meta line. */
object RideSource {
    const val NAV = "nav"
    const val MANUAL = "manual"
    const val GPX = "gpx"
    const val MIRROR = "mirror"
    const val MERGED = "merged"

    private val KNOWN = setOf(NAV, MANUAL, GPX, MIRROR, MERGED)

    /** An imported ride's id (import time) is far later than the last fix; a recorded ride's id is its start. */
    private const val IMPORT_LAG_MS = 10 * 60_000L

    fun clean(raw: String?): String? = raw?.takeIf { it in KNOWN }

    /** Source for rides stored before it was recorded. */
    fun infer(ride: RecordedRide): String {
        val id = ride.id.toLongOrNull()
        return when {
            id != null && id - ride.endMs > IMPORT_LAG_MS -> GPX
            ride.destLat != null -> NAV
            else -> MANUAL
        }
    }
}
