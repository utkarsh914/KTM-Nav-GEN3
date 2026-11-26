package com.navigator.app.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
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

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Tells "refused for good" from "not now" for one permission request. Android shows no rationale both before the first
 * prompt and after "don't ask again", so a refusal is final only when a rationale was due at the ask, or Android answered
 * without showing a dialog. Hold one per screen and call [begin] before launching the request.
 */
class PermissionAsk {
    private var askedAt = 0L
    private var rationaleWasDue = false

    fun begin(activity: Activity?, permissions: Collection<String>) {
        askedAt = SystemClock.elapsedRealtime()
        rationaleWasDue = activity != null && permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }

    fun refusedForGood(activity: Activity?, permissions: Collection<String>): Boolean {
        if (activity == null) return false
        val noRationale = permissions.all { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        return noRationale && (rationaleWasDue || SystemClock.elapsedRealtime() - askedAt < NO_DIALOG_MS)
    }

    private companion object { const val NO_DIALOG_MS = 400L }
}
