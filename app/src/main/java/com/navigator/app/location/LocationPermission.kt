package com.navigator.app.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** The one place that knows how location is asked for and checked. */
object LocationPermission {
    /** Always asked together: on Android 12+ a request with FINE alone is silently ignored. */
    val REQUEST = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Precise location: recording, speed, the route origin the Navigation SDK must match. */
    fun hasPrecise(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Approximate is enough: where to search from, a place to pick. */
    fun hasAny(context: Context): Boolean = REQUEST.any { granted(context, it) }

    /** After a refusal: true when Android will not show the dialog again (so only the app's settings are left). */
    fun cannotAskAgain(activity: Activity): Boolean =
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
