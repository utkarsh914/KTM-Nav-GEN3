package com.navigator.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one live speed (km/h) for the UI: GPS at 1 Hz, on only while something holds it ([acquire]):
 * the riding screen or a recording. Independent of the BLE service, so it works during standalone
 * navigation. Null until a fix with a speed arrives, and again 5 s after the last one (tunnel, GPS
 * off) so a frozen number never stays on screen. The overspeed alarm stays in [SpeedMonitor].
 * Main thread.
 */
class SpeedSource(private val context: Context) {
    private val _speedKmh = MutableStateFlow<Float?>(null)
    val speedKmh: StateFlow<Float?> = _speedKmh.asStateFlow()

    private var holders = 0
    private var manager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val staleClear = Runnable { _speedKmh.value = null }

    // A full listener (not a SAM lambda): before API 30 the other callbacks are abstract.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            _speedKmh.value = location.speed * MS_TO_KMH
            handler.removeCallbacks(staleClear)
            handler.postDelayed(staleClear, STALE_MS)
        }

        @Deprecated("Deprecated in API 29 but still abstract on older devices")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { _speedKmh.value = null }
    }

    fun acquire() {
        holders++
        register()
    }

    /** Only with the permission, and again on the next acquire if it was missing before (the rider may have granted it since). */
    private fun register() {
        if (manager != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val registered = runCatching {
            @Suppress("MissingPermission")
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }.isSuccess
        if (registered) manager = lm
    }

    fun release() {
        if (holders == 0 || --holders > 0) return
        handler.removeCallbacks(staleClear)
        runCatching { manager?.removeUpdates(listener) }
        manager = null
        _speedKmh.value = null
    }

    private companion object {
        const val MS_TO_KMH = 3.6f
        const val STALE_MS = 5_000L
    }
}
