package com.navigator.app.notifications

import android.app.Notification

/**
 * Decides whether a posted notification is a turn-by-turn guidance notification.
 *
 * Package alone is not enough for Google Maps: traffic alerts, location sharing
 * and rating prompts come from the same package and were being sent to the dash
 * as turn guidance (and their removal cleared real guidance). Maps' guidance
 * notification is ongoing and/or categorised as navigation. Other nav apps keep
 * the package-only rule: several of them don't set the category reliably (see
 * the regression note in [AppNotificationListener]).
 */
object NavNotificationFilter {

    const val GOOGLE_MAPS = "com.google.android.apps.maps"

    private val STRICT_PACKAGES = setOf(GOOGLE_MAPS)

    fun isNavigation(
        packageName: String,
        flags: Int,
        category: String?,
        navAppOverride: String?,
        knownNavApps: Set<String>,
    ): Boolean {
        val packageMatches = if (navAppOverride != null) packageName == navAppOverride else packageName in knownNavApps
        if (!packageMatches) return false
        if (packageName !in STRICT_PACKAGES) return true
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return flags and Notification.FLAG_ONGOING_EVENT != 0 || category == Notification.CATEGORY_NAVIGATION
    }
}
