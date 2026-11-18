package com.navigator.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory nav-state shared between AppNotificationListener (the writer) and
 * the UI / dash-writer (the readers). A plain singleton object is enough here -
 * no persistence needed, this is transient "what's currently pending" state.
 */
object NotificationRepository {

    /** Text of the most recent notification from whichever app is currently "the nav app". */
    private val _currentNavText = MutableStateFlow<String?>(null)
    val currentNavText: StateFlow<String?> = _currentNavText

    /** Package name of the app auto-detected (or manually overridden) as the nav app. */
    private val _currentNavPackage = MutableStateFlow<String?>(null)
    val currentNavPackage: StateFlow<String?> = _currentNavPackage

    /** Structured turn-by-turn guidance (the same fields mirrored to the dash), for the Direction screen. */
    data class NavGuidance(
        val distance: String?,
        val road: String?,
        val eta: String?,
        val remaining: String?,
        val maneuver: String?,
        /** The classified maneuver icon (same one sent to the dash), for the in-app arrow. */
        val turnIcon: com.navigator.app.ble.BccuProtocol.TurnIcon? = null,
    )

    private val _navGuidance = MutableStateFlow<NavGuidance?>(null)
    val navGuidance: StateFlow<NavGuidance?> = _navGuidance

    /** When guidance last arrived from the nav app (epoch ms), 0 when none; drives the freshness line. */
    private val _lastUpdateMs = MutableStateFlow(0L)
    val lastUpdateMs: StateFlow<Long> = _lastUpdateMs

    /** Whether the notification listener is currently bound by the system. */
    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun updateNavGuidance(guidance: NavGuidance) {
        _navGuidance.value = guidance
        _lastUpdateMs.value = System.currentTimeMillis()
    }

    fun updateNavText(packageName: String, text: String) {
        _currentNavPackage.value = packageName
        _currentNavText.value = text
        _lastUpdateMs.value = System.currentTimeMillis()
    }

    /** Reset navigation state when guidance ends (nav app notification removed). */
    fun clearNav() {
        _currentNavText.value = null
        _currentNavPackage.value = null
        _navGuidance.value = null
        _lastUpdateMs.value = 0L
    }
}
