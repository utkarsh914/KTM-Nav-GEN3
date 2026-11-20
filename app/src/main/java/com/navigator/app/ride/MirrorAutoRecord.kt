package com.navigator.app.ride

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Where mirror mode stands for recording: not the engine, between trips, or Google Maps is guiding. */
enum class MirrorPhase { Off, Idle, Guiding }

object MirrorRecordPolicy {
    /** A quick restart or reroute inside this gap is the same ride. */
    const val GRACE_MS = 120_000L

    fun phase(mirrorEngine: Boolean, guiding: Boolean): MirrorPhase = when {
        !mirrorEngine -> MirrorPhase.Off
        guiding -> MirrorPhase.Guiding
        else -> MirrorPhase.Idle
    }

    /**
     * Pausing the dash clears what Google Maps sent, which would look like the trip ending: while paused, a trip that was
     * guiding stays guiding ([last] is the previous result).
     */
    fun guiding(raw: Boolean, paused: Boolean, last: Boolean): Boolean = if (paused) last || raw else raw

    fun shouldStart(
        phase: MirrorPhase,
        owned: Boolean,
        anyRecordingRunning: Boolean,
        recordingEnabled: Boolean,
        locationGranted: Boolean,
    ): Boolean = phase == MirrorPhase.Guiding && !owned && !anyRecordingRunning && recordingEnabled && locationGranted
}

/**
 * Records the rides of mirror mode (Q11): starts when Google Maps starts guiding, stops [graceMs] after its guidance
 * goes away (a newer phase cancels the wait, so a restart or reroute keeps one ride), and at once when the engine is
 * switched to in-app. Only a recording it started itself is ever stopped: a rider's own recording is left alone, and a
 * rider who stopped this one is not restarted until the next trip. Pausing the dash does not pause the recording.
 */
class MirrorAutoRecordController(
    private val scope: CoroutineScope,
    private val phase: Flow<MirrorPhase>,
    private val session: () -> RideSessionState,
    private val recordingEnabled: () -> Boolean,
    private val locationGranted: () -> Boolean,
    private val startRecording: () -> Boolean,
    private val stopRecording: () -> Unit,
    private val graceMs: Long = MirrorRecordPolicy.GRACE_MS,
) {
    private var owned = false

    fun start() {
        scope.launch {
            phase.collectLatest { p ->
                when (p) {
                    MirrorPhase.Guiding -> {
                        // A ride the rider (or the keep limits) already ended is not ours any more: this is a new trip.
                        if (owned && !session().active) owned = false
                        if (MirrorRecordPolicy.shouldStart(p, owned, session().active, recordingEnabled(), locationGranted())) {
                            owned = startRecording()
                        }
                    }
                    MirrorPhase.Idle -> if (owned) {
                        delay(graceMs)
                        stopIfOwned()
                    }
                    MirrorPhase.Off -> stopIfOwned()
                }
            }
        }
    }

    private fun stopIfOwned() {
        if (!owned) return
        owned = false
        if (!session().manual) stopRecording()
    }
}
