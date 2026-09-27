package com.navigator.app.nav

import com.navigator.app.ble.BccuProtocol
import com.navigator.app.nav.ktm.DashWrite
import com.navigator.app.nav.ktm.KtmNavigationEncoder
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.net.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /** Show/clear a one-shot "Offline" alert banner on the dash (network dropped
     *  mid-trip). Persistent offline state is also reflected via the GPS/nav
     *  status bit in [setNavState]; this is the human-readable heads-up. */
    fun offlineBanner(show: Boolean)
}

/**
 * Wires the active [NavigationProvider] to the KTM dash: collects the provider's
 * normalized state, runs each snapshot through the [KtmNavigationEncoder], and
 * applies the resulting [DashWrite]s via [DashOutput]. Owned by
 * `BccuConnectionService`.
 */
class NavigationCoordinator(
    private val scope: CoroutineScope,
    /** The default source (notification mirroring). Reverted to when a routing session ends. */
    private val fallbackProvider: NavigationProvider,
    private val output: DashOutput,
    private val encoder: KtmNavigationEncoder = KtmNavigationEncoder(),
    /** Internet reachability; merged into each snapshot as [NormalizedNavigationState.offline]. */
    private val isOnline: StateFlow<Boolean> = ConnectivityMonitor.isOnline,
    /** Wall clock (same base as [NormalizedNavigationState.producedAtMs]). */
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    /** Full guidance re-send period while navigating (0 = off). Recovers from a
     *  lost write or the dash dropping its display state, which the encoder's
     *  dedup would otherwise never repair until the value changed. */
    private val refreshIntervalMs: Long = 15_000L,
) {
    private var provider: NavigationProvider = fallbackProvider

    /** The provider currently driving the dash. */
    val activeProvider: NavigationProvider get() = provider
    private var job: Job? = null
    /** Last offline value we surfaced as a dash banner, so we only alert on change. */
    private var lastOfflineBanner = false
    /** Latest snapshot seen (offline merged), for the throttle's trailing flush. */
    private var latestState: NormalizedNavigationState? = null
    private var flushJob: Job? = null
    /** When the current provider was selected, and whether it has been live
     *  (ENROUTE/REROUTING) since - see [maybeRevertToFallback]. */
    private var providerSelectedAtMs = 0L
    private var sawActiveSession = false
    private var refreshJob: Job? = null

    /** True while the dash is showing live guidance from the active provider. */
    val isGuidanceActive: Boolean get() = encoder.isGuidanceOnDash

    fun start() {
        providerSelectedAtMs = wallClockMs()
        sawActiveSession = false
        provider.attach()
        collect()
        if (refreshIntervalMs > 0) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                while (true) {
                    delay(refreshIntervalMs)
                    if (encoder.isGuidanceOnDash) resyncNow()
                }
            }
        }
    }

    /**
     * Stop driving the dash (service teardown). A routing provider is NOT
     * detached here: detaching it stops the rider's in-app trip, and the BLE
     * service going away (bike off / disconnect) must not cancel navigation.
     */
    fun stop() {
        job?.cancel()
        job = null
        flushJob?.cancel(); flushJob = null
        refreshJob?.cancel(); refreshJob = null
        if (lastOfflineBanner) { lastOfflineBanner = false; output.offlineBanner(false) }
        if (provider === fallbackProvider) provider.detach()
    }

    /**
     * Switch the active navigation source (e.g. notification <-> Google Nav SDK).
     * Detaches the old provider, blanks whatever the old one left on the dash,
     * resets the encoder so the new provider's first snapshot is sent in full,
     * and starts collecting the new one.
     */
    fun setProvider(newProvider: NavigationProvider) {
        if (newProvider === provider) return
        job?.cancel()
        flushJob?.cancel(); flushJob = null
        latestState = null
        provider.detach()
        // Don't leave the old source's turn on the dash until the new source's
        // first update (e.g. mirrored Maps guidance when in-app nav starts).
        if (encoder.isGuidanceOnDash) output.clearNow()
        encoder.reset()
        provider = newProvider
        providerSelectedAtMs = wallClockMs()
        sawActiveSession = false
        provider.attach()
        collect()
    }

    private fun collect() {
        val activeProvider = provider
        job = combine(activeProvider.state, isOnline) { state, online ->
            state.copy(offline = !online)
        }
            .onEach { state ->
                encodeAndApply(state)
                maybeOfflineBanner(activeProvider, state)
                maybeRevertToFallback(state)
            }
            .launchIn(scope)
    }

    /**
     * Fire a one-shot dash "Offline" banner when data drops during an active
     * routing session (and nothing when it returns). Only for the routing
     * provider — the notification-mirror fallback isn't ours to annotate.
     */
    private fun maybeOfflineBanner(activeProvider: NavigationProvider, state: NormalizedNavigationState) {
        val eligible = activeProvider !== fallbackProvider && state.sessionState.isActiveNav()
        val offline = eligible && state.offline
        if (offline != lastOfflineBanner) {
            lastOfflineBanner = offline
            output.offlineBanner(offline)
        }
    }

    /**
     * When a routing provider (Google Nav SDK) finishes a trip - ARRIVED or
     * STOPPED - hand the dash back to the notification-mirroring fallback so
     * mirroring keeps working afterwards. Posted to [scope] so we don't cancel
     * the collector from inside its own emission.
     *
     * Only a GENUINE end counts: the provider's StateFlow replays its last value
     * on collection, so right after switching to it for a new trip we'd first see
     * the PREVIOUS trip's STOPPED/ARRIVED - reverting on that killed the new trip
     * (or left the dash blank). A terminal state reverts only if this session
     * was seen live, or it was produced after the provider was selected (e.g.
     * the new route failed to compute).
     */
    private fun maybeRevertToFallback(state: NormalizedNavigationState) {
        if (provider === fallbackProvider) return
        if (state.sessionState.isActiveNav()) {
            sawActiveSession = true
            return
        }
        val terminal = state.sessionState == NavSessionState.ARRIVED || state.sessionState == NavSessionState.STOPPED
        if (terminal && (sawActiveSession || state.producedAtMs >= providerSelectedAtMs)) {
            val from = provider
            scope.launch { if (provider === from) setProvider(fallbackProvider) }
        }
    }

    /**
     * After a BLE re-auth: forget everything the encoder thinks it has sent and
     * re-apply the current snapshot, so a reconnect never leaves the dash stale.
     */
    fun onReAuth() {
        scope.launch { reAuthNow() }
    }

    /** [onReAuth] for callers already on the coordinator's (main) thread. */
    fun reAuthNow() = resyncNow()

    /**
     * A dash write failed (or timed out): the encoder believes it was delivered,
     * so re-send the whole current guidance. No-op when no guidance is live.
     */
    fun resync() {
        scope.launch { if (encoder.isGuidanceOnDash) resyncNow() }
    }

    private fun resyncNow() {
        encoder.reset()
        encodeAndApply(provider.state.value.copy(offline = !isOnline.value))
    }

    private fun encodeAndApply(state: NormalizedNavigationState) {
        latestState = state
        apply(encoder.encode(state))
        scheduleTrailingFlush()
    }

    /**
     * The encoder throttles label-only churn. If it held a change back, re-encode
     * the latest snapshot once the window has passed so the final value of a
     * burst always reaches the dash (a new snapshot arriving first supersedes it).
     */
    private fun scheduleTrailingFlush() {
        if (!encoder.hasThrottledChanges) return
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(encoder.minIntervalMs)
            latestState?.let { apply(encoder.encode(it)) }
            // A second hold-back (rare) chains another flush.
            flushJob = null
            scheduleTrailingFlush()
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
