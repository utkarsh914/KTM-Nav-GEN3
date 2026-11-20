package com.navigator.app.ride

/** "7:05" / "1:02:09" for a recorded duration. */
fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** What happened when the rider asked to record a ride with no destination. */
enum class RecordStartResult { Started, NeedsLocation, Failed }

object RecordStartPolicy {
    fun result(locationGranted: Boolean, start: () -> Boolean): RecordStartResult = when {
        !locationGranted -> RecordStartResult.NeedsLocation
        start() -> RecordStartResult.Started
        else -> RecordStartResult.Failed
    }
}
