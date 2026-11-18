package com.navigator.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.navigator.app.logging.AppLogger
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideStore
import com.navigator.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OpenDashApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        ConnectivityMonitor.init(this)
        // Apply the persisted app-font choice before any UI composes so a
        // system-font user doesn't see a first brand-font frame.
        runCatching {
            com.navigator.app.ui.theme.AppFonts.apply(AppSettings(this).fontMode)
        }
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
                val store = RideStore(this@OpenDashApplication)
                store.recoverOrphans(
                    minDistanceMeters = settings.rideMinDistanceMeters,
                    minDurationSeconds = settings.rideMinDurationSeconds,
                )
                store.pruneToLimit(settings.rideHistoryLimit)
            }.onFailure { AppLogger.log("Ride", "!! orphan recovery failed: $it") }
        }
    }

    /**
     * Drive ride recording off the navigation *intent*, not the guidance state:
     * start as soon as a trip is requested (destination set) and stop when it's
     * cleared (arrival / stop / cancel). Keying off [GoogleNavSdkProvider.currentDestination]
     * rather than ENROUTE means the GPS track still records even when the initial
     * route can't be computed offline (guidance never reaches ENROUTE, but the
     * recorder is pure device GPS and works regardless). Runs for the process life.
     */
    private fun observeNavForRecording() {
        appScope.launch {
            var recording = false
            var recordingTripId = -1L
            // Keyed on the trip id, not the destination value: A -> B replacements and
            // same-destination restarts are separate rides.
            GoogleNavSdkProvider.activeTrip.collect { trip ->
                if (recording && (trip == null || trip.id != recordingTripId)) {
                    RideRecordingService.stop(this@OpenDashApplication)
                    recording = false
                    // Let the service finalise the old ride before the next one starts.
                    if (trip != null) {
                        delay(RIDE_RESTART_GAP_MS)
                        // The old ride has been finalised: drop its result so the new
                        // trip's finish screen can't pick it up.
                        com.navigator.app.ride.RideEvents.clear()
                    }
                }
                if (trip != null && !recording) {
                    val settings = AppSettings(this@OpenDashApplication)
                    if (settings.rideRecordingEnabled && hasLocationPermission()) {
                        recording = RideRecordingService.start(
                            this@OpenDashApplication,
                            destLabel = trip.dest.label,
                            destLat = trip.dest.lat,
                            destLng = trip.dest.lng,
                        )
                        if (recording) recordingTripId = trip.id
                    }
                }
            }
        }
    }

    private companion object {
        const val RIDE_RESTART_GAP_MS = 600L
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
