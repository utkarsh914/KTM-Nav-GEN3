package com.navigator.app.nav

import com.navigator.app.ble.BccuProtocol
import com.navigator.app.nav.ktm.DashWrite
import com.navigator.app.nav.ktm.KtmNavigationEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Sink for encoder output. Implemented by `BccuConnectionService` over its
 * existing send/clear methods, so the coordinator never touches BLE internals.
 */
interface DashOutput {
    fun setNavState(guidanceOn: Boolean, gpsIconOn: Boolean)
    fun turnIcon(icon: BccuProtocol.TurnIcon)
    /** Center-guidance labels; null fields are left unchanged. */
    fun guidance(distance: String?, road: String?, eta: String?, remaining: String?)
    fun clearNow()
    fun clearDebounced()
}

/**
 * Wires the active [NavigationProvider] to the KTM dash: collects the provider's
 * normalized state, runs each snapshot through the [KtmNavigationEncoder], and
 * applies the resulting [DashWrite]s via [DashOutput]. Owned by
 * `BccuConnectionService`.
 */
class NavigationCoordinator(
    private val scope: CoroutineScope,
    initialProvider: NavigationProvider,
    private val output: DashOutput,
    private val encoder: KtmNavigationEncoder = KtmNavigationEncoder(),
) {
    private var provider: NavigationProvider = initialProvider
    private var job: Job? = null

    fun start() {
        provider.attach()
        job = provider.state
            .onEach { apply(encoder.encode(it)) }
            .launchIn(scope)
    }

    fun stop() {
        job?.cancel()
        job = null
        provider.detach()
    }

    /**
     * Switch the active navigation source (e.g. notification <-> Google Nav SDK).
     * Detaches the old provider, resets the encoder so the new provider's first
     * snapshot is sent in full, and starts collecting the new one.
     */
    fun setProvider(newProvider: NavigationProvider) {
        if (newProvider === provider) return
        job?.cancel()
        provider.detach()
        encoder.reset()
        provider = newProvider
        provider.attach()
        job = provider.state
            .onEach { apply(encoder.encode(it)) }
            .launchIn(scope)
    }

    /**
     * After a BLE re-auth: forget everything the encoder thinks it has sent and
     * re-apply the current snapshot, so a reconnect never leaves the dash stale.
     */
    fun onReAuth() {
        scope.launch {
            encoder.reset()
            apply(encoder.encode(provider.state.value))
        }
    }

    private fun apply(writes: List<DashWrite>) {
        if (writes.isEmpty()) return
        var navState: DashWrite.SetNavState? = null
        var icon: BccuProtocol.TurnIcon? = null
        var distance: String? = null
        var road: String? = null
        var eta: String? = null
        var remaining: String? = null
        var clear: DashWrite.ClearGuidance? = null
        for (w in writes) {
            when (w) {
                is DashWrite.SetNavState -> navState = w
                is DashWrite.TurnIcon -> icon = w.icon
                is DashWrite.TurnDistance -> distance = w.text
                is DashWrite.TurnRoad -> road = w.text
                is DashWrite.Eta -> eta = w.text
                is DashWrite.RemainingDistance -> remaining = w.text
                is DashWrite.ClearGuidance -> clear = w
            }
        }
        navState?.let { output.setNavState(it.guidanceOn, it.gpsIconOn) }
        icon?.let { output.turnIcon(it) }
        if (distance != null || road != null || eta != null || remaining != null) {
            output.guidance(distance, road, eta, remaining)
        }
        clear?.let { if (it.immediate) output.clearNow() else output.clearDebounced() }
    }
}
