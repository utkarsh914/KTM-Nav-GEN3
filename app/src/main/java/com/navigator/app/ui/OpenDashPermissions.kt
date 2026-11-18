package com.navigator.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Single source of truth for the runtime permissions OpenDash needs and
 * whether they're currently granted. Centralized so onboarding, the pairing
 * screen, and MainActivity all agree — and so scanning can be gated on the
 * Bluetooth permission instead of crashing when it's missing.
 */
object OpenDashPermissions {

    /** Runtime permissions to request together. */
    fun runtimePermissions(): Array<String> {
        val perms = mutableListOf<String>()
        perms += Manifest.permission.BLUETOOTH_CONNECT
        perms += Manifest.permission.BLUETOOTH_SCAN
        // GPS features (overspeed, waypoint, route recording). FINE must be
        // requested TOGETHER WITH COARSE: on Android 12+ a request containing
        // FINE alone is silently ignored (no dialog, nothing granted).
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        perms += Manifest.permission.ACCESS_COARSE_LOCATION
        perms += Manifest.permission.POST_NOTIFICATIONS
        return perms.toTypedArray()
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** True when the app may scan/connect over BLE (the permission that, if missing, crashed the scan). */
    fun bluetoothGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
            isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)

    fun notificationsPostGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    /** Special access (not a runtime permission): notification listener, granted from system settings. */
    fun notificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
