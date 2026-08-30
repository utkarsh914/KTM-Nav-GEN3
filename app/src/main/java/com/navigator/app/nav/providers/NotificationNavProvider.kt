package com.navigator.app.nav.providers

import com.navigator.app.ble.BccuProtocol
import com.navigator.app.nav.NavigationProvider
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Passive provider that mirrors another nav app's (e.g. Google Maps) foreground
 * notification. The [com.navigator.app.notifications.AppNotificationListener]
 * pushes already-extracted guidance here; the values flow through the encoder
 * unchanged (via the passthrough override fields) but now gain dedup / throttle /
 * state-machine / clear handling that the old direct-to-BLE path lacked.
 *
 * Process singleton because the notification listener is created by the system
 * and needs a stable target (mirrors [com.navigator.app.notifications.NotificationRepository]).
 */
object NotificationNavProvider : NavigationProvider {

    /** Matches the dash's historical guidance-clear grace (Maps removes+reposts mid-route). */
    private const val SESSION_END_DEBOUNCE_MS = 4_000L

    override val id: String = "notification"

    private val _state = MutableStateFlow(NormalizedNavigationState.IDLE)
    override val state: StateFlow<NormalizedNavigationState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var endJob: Job? = null

    override fun attach() { /* passive - the listener drives it */ }

    override fun detach() {
        endJob?.cancel()
        endJob = null
        _state.value = NormalizedNavigationState.IDLE
    }

    /** A fresh guidance update parsed from a nav-app notification. */
    fun pushGuidance(
        distanceText: String?,
        roadText: String?,
        etaText: String?,
        remainingText: String?,
        icon: BccuProtocol.TurnIcon?,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        endJob?.cancel()
        endJob = null
        _state.value = NormalizedNavigationState(
            sessionState = NavSessionState.ENROUTE,
            resolvedIcon = icon,
            preformattedDistance = distanceText?.ifBlank { null },
            preformattedEta = etaText?.ifBlank { null },
            preformattedRemaining = remainingText?.ifBlank { null },
            roadName = roadText?.ifBlank { null },
            producedAtMs = nowMs,
        )
    }

    /**
     * The nav notification was removed. Google Maps routinely removes+reposts
     * mid-route, so treat this as "maybe ended": wait [SESSION_END_DEBOUNCE_MS],
     * and only if no new guidance arrives emit STOPPED (a real end). Any
     * [pushGuidance] before then cancels it. Keeps the encoder's state machine
     * authoritative without churning on transient removes.
     */
    fun pushRemoved() {
        if (_state.value.sessionState != NavSessionState.ENROUTE) return
        endJob?.cancel()
        endJob = scope.launch {
            delay(SESSION_END_DEBOUNCE_MS)
            _state.value = NormalizedNavigationState(
                sessionState = NavSessionState.STOPPED,
                producedAtMs = System.currentTimeMillis(),
            )
        }
    }
}
