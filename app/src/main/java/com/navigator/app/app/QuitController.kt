package com.navigator.app.app

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.logging.AppLogger
import com.navigator.app.notify.RideSavedNotice
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideSession
import com.navigator.app.settings.AppSettings
import com.navigator.app.trip.TripController
import com.navigator.app.trip.isLive
import com.navigator.app.ui.shell.QuitPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What a hard quit does, one step each, so the order and the "one failure does not stop the rest" rule can be tested. */
interface QuitSteps {
    fun setQuitUntilOpen()
    suspend fun cancelTrip()
    fun recordingActive(): Boolean
    fun stopRecording()

    /** Waits (a few seconds at most) for the ride to be saved. */
    suspend fun awaitRecordingStopped()
    suspend fun stopBike()
    suspend fun stopNavService()
    fun cancelNotifications()
    suspend fun finishActivity()
}

/**
 * The hard quit (D33): the Quit button on a notification or in Settings. It ends the trip, saves the recording, clears the
 * dash and stops the bike link, stops the Navigation SDK's service, removes every notification and closes the app, and
 * leaves a flag that keeps boot, Bluetooth and the bike from starting anything until the rider opens the app again.
 * Leaving by Back, or swiping the app away, stays a light quit that stops nothing.
 */
class QuitController(
    private val tripLive: () -> Boolean,
    private val recording: () -> Boolean,
    private val mirrorGuiding: () -> Boolean,
    private val steps: QuitSteps,
    private val log: (String) -> Unit = { AppLogger.log("Quit", it) },
) {
    fun needsConfirm(): Boolean = QuitPolicy.needsConfirm(tripLive(), recording(), mirrorGuiding())

    suspend fun quit() {
        log("Hard quit")
        step("flag") { steps.setQuitUntilOpen() }
        step("trip") { steps.cancelTrip() }
        step("recording") {
            if (steps.recordingActive()) {
                steps.stopRecording()
                steps.awaitRecordingStopped()
            }
        }
        step("bike") { steps.stopBike() }
        step("navigation service") { steps.stopNavService() }
        step("notifications") { steps.cancelNotifications() }
        step("close") { steps.finishActivity() }
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("!! quit step '$name' failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    companion object {
        fun create(context: Context, trip: TripController, connection: ConnectionRepository, activity: () -> Activity?): QuitController {
            val app = context.applicationContext
            return QuitController(
                tripLive = { trip.phase.value.isLive },
                recording = { RideSession.state.value.active },
                mirrorGuiding = { NotificationNavProvider.state.value.sessionState.isActiveNav() },
                steps = AndroidQuitSteps(app, trip, connection, activity),
            )
        }
    }
}

private class AndroidQuitSteps(
    private val app: Context,
    private val trip: TripController,
    private val connection: ConnectionRepository,
    private val activity: () -> Activity?,
) : QuitSteps {
    override fun setQuitUntilOpen() {
        AppSettings(app).quitUntilOpen = true
    }

    override suspend fun cancelTrip() = withContext(Dispatchers.Main) { trip.cancel() }

    override fun recordingActive() = RideSession.state.value.active

    override fun stopRecording() = RideRecordingService.stop(app)

    override suspend fun awaitRecordingStopped() {
        withTimeoutOrNull(RECORDING_WAIT_MS) { RideSession.state.first { !it.active } }
            ?: AppLogger.log("Quit", "recording still active after $RECORDING_WAIT_MS ms")
    }

    override suspend fun stopBike() {
        if (BccuConnectionService.isRunning) {
            BccuConnectionService.clearGuidanceIfRunning()
            delay(DASH_CLEAR_MS) // the clear is a few BLE writes: let them leave before the link goes
        }
        connection.quit() // also when it is still starting (not "running" yet): its own start check then stops it
        withTimeoutOrNull(BIKE_WAIT_MS) { while (BccuConnectionService.isRunning) delay(50) }
            ?: AppLogger.log("Quit", "bike service still running after $BIKE_WAIT_MS ms")
    }

    // The SDK keeps its service in the foreground even with no trip, and Navigator.cleanup() does not end it (device spike, RF4.0).
    override suspend fun stopNavService() {
        app.stopService(Intent().setClassName(app.packageName, NAV_SERVICE))
    }

    // Not "Ride saved": it is how the rider finds the ride this quit just saved.
    override fun cancelNotifications() {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.id != RideSavedNotice.NOTIFICATION_ID }.forEach { nm.cancel(it.tag, it.id) }
    }

    override suspend fun finishActivity() = withContext(Dispatchers.Main) { activity()?.finishAndRemoveTask() ?: Unit }

    private companion object {
        const val NAV_SERVICE = "com.google.android.libraries.geo.navcore.service.base.NavigationService"
        const val RECORDING_WAIT_MS = 3_000L
        const val BIKE_WAIT_MS = 2_000L
        const val DASH_CLEAR_MS = 300L
    }
}
