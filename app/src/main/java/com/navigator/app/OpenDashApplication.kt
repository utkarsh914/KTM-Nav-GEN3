package com.navigator.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideStore
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenDashApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        recoverOrphanRides()
        observeNavForRecording()
    }

    /**
     * Promote or discard any ride tracks left on disk by a crash mid-recording
     * (crash-safe NDJSON streaming means the points survived; the index entry
     * did not). Off the main thread since it reads files.
     */
    private fun recoverOrphanRides() {
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val settings = AppSettings(this@OpenDashApplication)
                RideStore(this@OpenDashApplication).recoverOrphans(
                    minDistanceMeters = settings.rideMinDistanceMeters,
                    minDurationSeconds = settings.rideMinDurationSeconds,
                )
            }.onFailure { AppLogger.log("Ride", "!! orphan recovery failed: $it") }
        }
    }

    /**
     * Drive ride recording off the live navigation session: start when a trip
     * becomes active (and recording is enabled + location granted), stop when it
     * ends. Runs for the whole process lifetime.
     */
    private fun observeNavForRecording() {
        appScope.launch {
            var recording = false
            GoogleNavSdkProvider.state.collect { state ->
                val active = state.sessionState.isActiveNav()
                val ended = state.sessionState == NavSessionState.ARRIVED ||
                    state.sessionState == NavSessionState.STOPPED ||
                    state.sessionState == NavSessionState.IDLE
                when {
                    active && !recording -> {
                        val settings = AppSettings(this@OpenDashApplication)
                        if (settings.rideRecordingEnabled && hasLocationPermission()) {
                            val dest = GoogleNavSdkProvider.currentDestination
                            RideRecordingService.start(
                                this@OpenDashApplication,
                                destLabel = dest?.label,
                                destLat = dest?.lat,
                                destLng = dest?.lng,
                            )
                            recording = true
                        }
                    }
                    ended && recording -> {
                        RideRecordingService.stop(this@OpenDashApplication)
                        recording = false
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
