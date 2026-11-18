package com.navigator.app.ride

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live state of the ride being recorded, for the UI (record chip) and the notification. */
data class RideSessionState(
    val active: Boolean = false,
    val paused: Boolean = false,
    /** True when the rider started it with the Record button (not an auto-recorded trip). */
    val manual: Boolean = false,
    /** Recorded (unpaused) time accumulated before the current running segment. */
    val elapsedBeforeMs: Long = 0L,
    /** Wall-clock start of the current running segment; meaningless while paused. */
    val segmentStartMs: Long = 0L,
    val distanceMeters: Int = 0,
) {
    fun elapsedMs(nowMs: Long): Long =
        if (!active) 0L else elapsedBeforeMs + if (paused) 0L else (nowMs - segmentStartMs).coerceAtLeast(0L)
}

/**
 * Process-wide mirror of the recorder's state. The recorder (in the foreground service)
 * writes it; screens and the notification read it. Also lets the nav-trip observer see that a
 * manual recording is already running so a trip doesn't start a second one.
 */
object RideSession {
    private val _state = MutableStateFlow(RideSessionState())
    val state: StateFlow<RideSessionState> = _state

    fun started(manual: Boolean, nowMs: Long) {
        _state.value = RideSessionState(active = true, manual = manual, segmentStartMs = nowMs)
    }

    fun paused(nowMs: Long) {
        val s = _state.value
        if (!s.active || s.paused) return
        _state.value = s.copy(paused = true, elapsedBeforeMs = s.elapsedMs(nowMs))
    }

    fun resumed(nowMs: Long) {
        val s = _state.value
        if (!s.active || !s.paused) return
        _state.value = s.copy(paused = false, segmentStartMs = nowMs)
    }

    fun distance(meters: Int) {
        val s = _state.value
        if (s.active && s.distanceMeters != meters) _state.value = s.copy(distanceMeters = meters)
    }

    fun ended() {
        _state.value = RideSessionState()
    }
}
