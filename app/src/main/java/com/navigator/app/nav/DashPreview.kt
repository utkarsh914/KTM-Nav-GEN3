package com.navigator.app.nav

import com.navigator.app.ble.BccuProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Read-only mirror of what the app asks the dash to show (guidance and banners), so the status
 * screen can show it and guidance can be verified with no bike connected. Fed by [DashPreviewTee]
 * and [banner]; recording is always on (cheap), the debug chip is [overlayEnabled].
 */
object DashPreview {
    data class State(
        val guidanceOn: Boolean? = null,
        val gpsIconOn: Boolean? = null,
        val icon: BccuProtocol.TurnIcon? = null,
        val distance: String? = null,
        val road: String? = null,
        val eta: String? = null,
        val remaining: String? = null,
        val offlineBanner: Boolean = false,
        /** The last message shown on the dash banner (notification, greeting, speed readout, test). */
        val lastBanner: String? = null,
        val lastEvent: String = "none",
        val updates: Int = 0,
        val updatedAtMs: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Whether the on-screen chip is shown (Settings > Diagnostics, debug builds). Recording continues either way. */
    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled

    fun setOverlayEnabled(enabled: Boolean) { _overlayEnabled.value = enabled }

    /** A message went to the dash banner. */
    fun banner(text: String) {
        runCatching { _state.update { it.copy(lastBanner = text) } }
    }

    /** The banner was cleared on the dash. */
    internal fun clearBanner() {
        runCatching { _state.update { it.copy(lastBanner = null) } }
    }

    internal fun reset() { _state.value = State() }

    /** Never throws: the preview must not be able to disturb real dash output. */
    internal fun update(event: String, block: (State) -> State) {
        runCatching { updateUnsafe(event, block) }
    }

    private fun updateUnsafe(event: String, block: (State) -> State) {
        _state.update {
            block(it).copy(
                lastEvent = event,
                updates = it.updates + 1,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
    }
}

/** Forwards every call to [delegate] unchanged, then records it in [DashPreview]. */
class DashPreviewTee(private val delegate: DashOutput) : DashOutput {
    override fun setNavState(guidanceOn: Boolean, gpsIconOn: Boolean) {
        delegate.setNavState(guidanceOn, gpsIconOn)
        DashPreview.update("navState on=$guidanceOn gps=$gpsIconOn") {
            it.copy(guidanceOn = guidanceOn, gpsIconOn = gpsIconOn)
        }
    }

    override fun turnIcon(icon: BccuProtocol.TurnIcon) {
        delegate.turnIcon(icon)
        DashPreview.update("icon ${icon.name}") { it.copy(icon = icon) }
    }

    override fun guidance(distance: String?, road: String?, eta: String?, remaining: String?) {
        delegate.guidance(distance, road, eta, remaining)
        DashPreview.update("guidance") {
            it.copy(
                distance = distance ?: it.distance,
                road = road ?: it.road,
                eta = eta ?: it.eta,
                remaining = remaining ?: it.remaining,
            )
        }
    }

    override fun clearNow() {
        delegate.clearNow()
        DashPreview.update("clear now") { cleared(it) }
    }

    override fun clearDebounced() {
        delegate.clearDebounced()
        DashPreview.update("clear (debounced)") { cleared(it) }
    }

    override fun offlineBanner(show: Boolean) {
        delegate.offlineBanner(show)
        DashPreview.update("offline banner $show") { it.copy(offlineBanner = show) }
    }

    private fun cleared(s: DashPreview.State) =
        s.copy(icon = null, distance = null, road = null, eta = null, remaining = null)
}
