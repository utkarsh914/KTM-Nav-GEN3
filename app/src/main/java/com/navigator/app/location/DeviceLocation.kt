package com.navigator.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * A fresh current-location fix (falls back to last-known). Used for the route
 * preview so the Routes API route-token origin matches the SDK's GPS at Start,
 * which is required for the SDK to honour the exact selected route.
 */
suspend fun freshLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return lastKnownLocation(context)
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return lastKnownLocation(context)
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return lastKnownLocation(context)
    }
    return try {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                cont.resume(loc?.let { it.latitude to it.longitude } ?: lastKnownLocation(context))
            }
            cont.invokeOnCancellation { signal.cancel() }
        }
    } catch (e: SecurityException) {
        lastKnownLocation(context)
    }
}

/** Best-effort last-known location for the initial camera / distances / recenter. */
fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        // Newest fix across providers (the first non-null one could be hours stale).
        providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (e: SecurityException) {
        null
    }
}
