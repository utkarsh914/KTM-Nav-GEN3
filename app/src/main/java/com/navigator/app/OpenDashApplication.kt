package com.navigator.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import com.navigator.app.app.AppContainer
import com.navigator.app.app.DefaultAppContainer
import com.navigator.app.ble.DashText
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.notifications.NotificationRepository
import com.navigator.app.notify.RideSavedNotice
import com.navigator.app.ride.AutoRecordController
import com.navigator.app.ride.MirrorAutoRecordController
import com.navigator.app.ride.MirrorRecordPolicy
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideSession
import com.navigator.app.ride.RideSource
import com.navigator.app.ride.RideStore
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.theme.AppFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OpenDashApplication : Application() {

    val container: AppContainer by lazy { DefaultAppContainer(this) }

    private val appScope get() = container.appScope

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        ConnectivityMonitor.init(this)
        runCatching { AppSettings(this).migrateRetiredBrand() }
        // Apply the persisted app-font choice before any UI composes so a
        // system-font user doesn't see a first brand-font frame.
        runCatching {
            AppFonts.apply(AppSettings(this).fontMode)
        }
        ClockFormat.use24h = { DateFormat.is24HourFormat(this) }
        // Dash text: ICU transliterator (null = pass-through) + the rider's switch.
        DashText.transliterate = DashText.icuTransliterator()
        DashText.enabled = runCatching { AppSettings(this).latinDashText }.getOrDefault(true)
        recoverOrphanRides()
        container.trip // start watching for arrival even before any screen is shown
        startAutoRecording()
        RideSavedNotice.start(this, appScope)
        appScope.launch(Dispatchers.IO) {
            runCatching { PlacesStore(this@OpenDashApplication).syncShortcuts() }
        }
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
                store.purgeDeleted(System.currentTimeMillis() - RideStore.UNDO_WINDOW_MS)
                store.pruneToLimit(settings.rideHistoryLimit)
                store.backfillMovingStats()
            }.onFailure { AppLogger.log("Ride", "!! orphan recovery failed: $it") }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startAutoRecording() {
        AutoRecordController(
            scope = appScope,
            trips = GoogleNavSdkProvider.activeTrip,
            session = { RideSession.state.value },
            recordingEnabled = { AppSettings(this).rideRecordingEnabled },
            locationGranted = ::hasLocationPermission,
            startRecording = { trip ->
                RideRecordingService.start(this, trip.dest.label, trip.dest.lat, trip.dest.lng)
            },
            stopRecording = { RideRecordingService.stop(this) },
        ).start()
        // Mirror mode: Google Maps' guidance is the trip (Q11).
        val guiding = kotlinx.coroutines.flow.flow {
            var last = false
            combine(
                NotificationRepository.navGuidance, NotificationRepository.currentNavText, NotificationRepository.currentNavPackage,
                NotificationNavProvider.paused,
            ) { g, t, pkg, paused ->
                MirrorRecordPolicy.guiding(pkg != null && (g != null || t != null), paused, last).also { last = it }
            }.collect { emit(it) }
        }
        MirrorAutoRecordController(
            scope = appScope,
            phase = combine(container.settings.snapshot.map { it.engine == NavEngine.MIRROR }, guiding, MirrorRecordPolicy::phase)
                .distinctUntilChanged(),
            session = { RideSession.state.value },
            recordingEnabled = { AppSettings(this).rideRecordingEnabled },
            locationGranted = ::hasLocationPermission,
            startRecording = { RideRecordingService.start(this, null, null, null, RideSource.MIRROR) },
            stopRecording = { RideRecordingService.stop(this) },
        ).start()
    }
}
