package com.navigator.app.ride

/** Which average speed the UI shows for a ride. Both are always recorded. */
enum class AvgSpeedMode(val id: String, val label: String, val shortLabel: String) {
    /** distance / time actually moving (stops excluded). */
    MOVING("moving", "Moving (stops excluded)", "AVG MOVING"),
    /** distance / total duration (stops included). */
    OVERALL("overall", "Overall (includes stops)", "AVG OVERALL");

    companion object {
        fun fromId(id: String?): AvgSpeedMode = entries.firstOrNull { it.id == id } ?: MOVING
    }
}

/**
 * The average to display for [this] ride in [mode]. Rides recorded before both averages were
 * stored (or whose backfill hasn't run yet) fall back to distance / duration for OVERALL, and
 * to the stored average for MOVING.
 */
fun RecordedRide.displayAvgKmh(mode: AvgSpeedMode): Float = when (mode) {
    AvgSpeedMode.MOVING ->
        if (movingSeconds >= 0 && avgSpeedKmh > 0f) avgSpeedKmh else overallOrDerived()
    AvgSpeedMode.OVERALL -> overallOrDerived()
}

private fun RecordedRide.overallOrDerived(): Float = when {
    avgOverallSpeedKmh > 0f -> avgOverallSpeedKmh
    durationSeconds > 0 -> distanceMeters.toFloat() / durationSeconds * 3.6f
    else -> avgSpeedKmh
}
