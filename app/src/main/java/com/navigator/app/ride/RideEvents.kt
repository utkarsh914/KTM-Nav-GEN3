package com.navigator.app.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The outcome of finalising a ride recording, published the moment the recorder
 * stops. Lets the "Trip finished" screen show the just-completed ride (its route
 * + stats) without polling the store, and tell apart "saved" from "too short to
 * keep".
 */
sealed interface RideFinish {
    /** The ride cleared the keep cutoffs and is now in history. */
    data class Saved(val ride: RecordedRide) : RideFinish

    /** The ride was recorded but discarded (below the distance/duration cutoffs). */
    data object Discarded : RideFinish
}

/**
 * Tiny process-wide event bus for ride-recording outcomes. [RideRecorder]
 * publishes here on finalize; the map home's finished screen observes it.
 *
 * The trip screen clears [lastFinish] to null when a new trip starts, so the
 * next non-null emission is unambiguously this trip's result.
 */
object RideEvents {
    private val _lastFinish = MutableStateFlow<RideFinish?>(null)
    val lastFinish: StateFlow<RideFinish?> = _lastFinish

    fun publish(result: RideFinish) {
        _lastFinish.value = result
    }

    fun clear() {
        _lastFinish.value = null
    }
}
