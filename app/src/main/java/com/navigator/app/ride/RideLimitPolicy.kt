package com.navigator.app.ride

import com.navigator.app.settings.AppSettings

/** Keeps "Rides to keep" from silently deleting rides that arrived by restore or by a phone-to-phone transfer. */
object RideLimitPolicy {
    private const val MAX = AppSettings.RIDE_HISTORY_LIMIT_MAX

    /** The limit to store when [unsaved] rides exceed [limit], else null. */
    fun raisedFor(unsaved: Int, limit: Int): Int? = if (unsaved > limit) unsaved.coerceAtMost(MAX) else null

    /** A new phone has no stored limit: use the rides it already holds instead of pruning to the default. */
    fun initial(stored: Int?, unsaved: Int, default: Int): Int? = if (stored == null) raisedFor(unsaved, default) else null
}
