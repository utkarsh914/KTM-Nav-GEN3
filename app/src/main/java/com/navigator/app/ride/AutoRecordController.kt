package com.navigator.app.ride

import com.navigator.app.nav.providers.GoogleNavSdkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the policy remembers about the recording it started for a trip. */
data class AutoRecordState(val recording: Boolean = false, val rideKey: Long = -1L)

/** What to do after a trip change: [stopService], then (when [restartGap]) wait before considering a start. */
data class AutoRecordStep(val state: AutoRecordState, val stopService: Boolean, val restartGap: Boolean)

/**
 * When the in-app engine records a ride for the trip. Pure, so the sequence is testable with a
 * fake clock. Keyed on the ride key, not the destination: A to B replacements and same-destination
 * restarts are separate rides, while a stop added mid-trip keeps the same ride.
 */
object AutoRecordPolicy {
    /** A trip appeared, changed or ended ([rideKey] null when none). */
    fun onTrip(state: AutoRecordState, rideKey: Long?, manualSessionRunning: Boolean): AutoRecordStep {
        if (state.recording && (rideKey == null || rideKey != state.rideKey)) {
            // A rider-started recording that took over meanwhile is theirs to stop.
            return AutoRecordStep(AutoRecordState(), stopService = !manualSessionRunning, restartGap = rideKey != null)
        }
        return AutoRecordStep(state, stopService = false, restartGap = false)
    }

    /** Whether to start recording now for the trip with [rideKey]. */
    fun shouldStart(
        state: AutoRecordState,
        rideKey: Long?,
        anyRecordingRunning: Boolean,
        recordingEnabled: Boolean,
        locationGranted: Boolean,
    ): Boolean = rideKey != null && !state.recording && !anyRecordingRunning && recordingEnabled && locationGranted
}

/**
 * Starts and stops ride recording from the in-app trip, for the life of the process. Runs off the
 * navigation intent (a destination was set), not the guidance state, so the GPS track still
 * records when the first route can't be computed offline.
 */
class AutoRecordController(
    private val scope: CoroutineScope,
    private val trips: Flow<GoogleNavSdkProvider.ActiveTrip?>,
    private val session: () -> RideSessionState,
    private val recordingEnabled: () -> Boolean,
    private val locationGranted: () -> Boolean,
    private val startRecording: (GoogleNavSdkProvider.ActiveTrip) -> Boolean,
    private val stopRecording: () -> Unit,
    private val clearFinish: () -> Unit = RideEvents::clear,
    private val restartGapMs: Long = RIDE_RESTART_GAP_MS,
) {
    fun start() {
        scope.launch {
            var state = AutoRecordState()
            trips.collect { trip ->
                val key = trip?.rideKey
                val step = AutoRecordPolicy.onTrip(state, key, session().manual)
                state = step.state
                if (step.stopService) stopRecording()
                if (step.restartGap) {
                    // Let the service finalise the old ride before the next one starts, then drop
                    // its result so the new trip's finish screen can't pick it up.
                    delay(restartGapMs)
                    clearFinish()
                }
                if (trip != null && AutoRecordPolicy.shouldStart(state, key, session().active, recordingEnabled(), locationGranted())) {
                    if (startRecording(trip)) state = AutoRecordState(recording = true, rideKey = trip.rideKey)
                }
            }
        }
    }

    companion object {
        const val RIDE_RESTART_GAP_MS = 600L
    }
}
