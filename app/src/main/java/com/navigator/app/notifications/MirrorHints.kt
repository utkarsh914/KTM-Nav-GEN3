package com.navigator.app.notifications

/**
 * What can silently stop the notification mirror, as pure decisions (JVM-testable). The mirror
 * home shows the highest-priority problems and a freshness line, because "no turns on the dash"
 * otherwise gives the rider no clue which link in the chain is broken.
 */
object MirrorHints {

    enum class Kind {
        /** Notification access was never granted or was revoked. */
        NO_ACCESS,
        /** Access is granted but the listener isn't bound (OEM killed it); can be re-bound. */
        LISTENER_DISCONNECTED,
        /** A trip is showing but nothing has arrived from the nav app for a while. */
        STALE,
        /** Idle tip: battery optimisation can stop the listener in the background. */
        BATTERY,
    }

    /** No update for this long while guidance is showing counts as stale. */
    const val STALE_AFTER_MS = 30_000L

    fun compute(
        accessGranted: Boolean,
        listenerConnected: Boolean,
        paused: Boolean,
        hasNav: Boolean,
        ageMs: Long?,
        ignoringBatteryOptimizations: Boolean,
    ): List<Kind> {
        if (!accessGranted) return listOf(Kind.NO_ACCESS)
        val out = ArrayList<Kind>(2)
        if (!listenerConnected) out += Kind.LISTENER_DISCONNECTED
        if (hasNav && !paused && listenerConnected && ageMs != null && ageMs > STALE_AFTER_MS) out += Kind.STALE
        if (!hasNav && !ignoringBatteryOptimizations) out += Kind.BATTERY
        return out
    }
}
