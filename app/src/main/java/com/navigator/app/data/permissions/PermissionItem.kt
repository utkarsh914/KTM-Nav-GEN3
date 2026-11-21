package com.navigator.app.data.permissions

import androidx.annotation.StringRes
import com.navigator.app.R
import com.navigator.app.data.settings.NavEngine

/** [consequenceRes] says what stops working while it is off (the Permissions banner, ST7). */
enum class PermissionId(@StringRes val titleRes: Int, @StringRes val whyRes: Int, @StringRes val consequenceRes: Int) {
    NEARBY_DEVICES(R.string.permission_nearby_title, R.string.permission_nearby_why, R.string.permission_nearby_cost),
    LOCATION(R.string.permission_location_title, R.string.permission_location_why, R.string.permission_location_cost),
    LOCATION_ALWAYS(R.string.permission_location_always_title, R.string.permission_location_always_why, R.string.permission_location_always_cost),
    NOTIFICATIONS(R.string.permission_notifications_title, R.string.permission_notifications_why, R.string.permission_notifications_cost),
    NOTIFICATION_ACCESS(R.string.permission_notification_access_title, R.string.permission_notification_access_why, R.string.permission_notification_access_cost),
    BATTERY(R.string.permission_battery_title, R.string.permission_battery_why, R.string.permission_battery_cost),
}

data class PermissionItem(val id: PermissionId, val granted: Boolean, val required: Boolean)

/** What Android currently grants, read in one go. */
data class PermissionGrants(
    val nearbyDevices: Boolean,
    val location: Boolean,
    val locationAlways: Boolean,
    val notifications: Boolean,
    val notificationAccess: Boolean,
    val batteryUnrestricted: Boolean,
)

data class PermissionsState(val items: List<PermissionItem>) {
    val requiredMissing: List<PermissionItem> get() = items.filter { it.required && !it.granted }

    /** Drives the dot on the Settings tab: something the app needs is switched off. */
    val attentionNeeded: Boolean get() = requiredMissing.isNotEmpty()

    /** Anything off at all, optional rows included: for the "what's missing" banner in Permissions. */
    val anyMissing: Boolean get() = items.any { !it.granted }

    operator fun get(id: PermissionId): PermissionItem = items.first { it.id == id }
}

object PermissionPolicy {
    /** Rows in the order they are requested: "all the time" location can only follow plain location. */
    fun build(grants: PermissionGrants, engine: NavEngine): PermissionsState = PermissionsState(
        listOf(
            PermissionItem(PermissionId.NEARBY_DEVICES, grants.nearbyDevices, required = true),
            PermissionItem(PermissionId.LOCATION, grants.location, required = true),
            PermissionItem(PermissionId.LOCATION_ALWAYS, grants.locationAlways, required = false),
            PermissionItem(PermissionId.NOTIFICATIONS, grants.notifications, required = true),
            // Only the mirror engine reads other apps' notifications.
            PermissionItem(PermissionId.NOTIFICATION_ACCESS, grants.notificationAccess, required = engine == NavEngine.MIRROR),
            PermissionItem(PermissionId.BATTERY, grants.batteryUnrestricted, required = false),
        ),
    )
}
