package com.navigator.app.nav.ktm

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import java.time.ZoneId

/**
 * Turns a stream of [NormalizedNavigationState] into the minimal set of
 * [DashWrite]s needed to keep the KTM center guidance view correct. This is the
 * reliability core: it holds the last-sent snapshot and enforces
 *
 * - **dedup** - a write is emitted only when its formatted value changed,
 * - **distance rounding** - via [DistanceFormatter] buckets (kills GPS jitter),
 * - **ETA deadband** - via [EtaFormatter] (no minute-boundary flapping),
 * - **throttle** - label-only churn is capped to ~1 write-set/[minIntervalMs]
 *   (state/icon/road changes always pass; the existing GATT queue coalesces the rest),
 * - **state machine** - IDLE/ENROUTE/REROUTING/ARRIVED/STOPPED handling, including
 *   holding the last turn on flaky ticks, blanking it on an unrecognised new step
 *   (never emitting UNKNOWN as an arrow), and blanking
 *   a stale turn while rerouting.
 *
 * Pure logic, no I/O - the coordinator applies the returned writes. Deterministic
 * given its inputs ([NormalizedNavigationState.producedAtMs] drives time), so the
 * whole thing is unit-testable.
 */
class KtmNavigationEncoder(
    /** Fixed zone for tests; null = the device's CURRENT zone at format time
     *  (was captured once at service start, so a mid-ride zone change was missed). */
    private val zone: ZoneId? = null,
    val minIntervalMs: Long = 1_000L,
    private val etaDeadbandMs: Long = 60_000L,
    /** Monotonic clock (ms) for the throttle. Deliberately NOT the snapshot's
     *  wall-clock [NormalizedNavigationState.producedAtMs]: a backwards wall-clock
     *  step (NTP / manual change) would otherwise freeze distance/ETA updates. */
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastSession: NavSessionState = NavSessionState.IDLE
    private var guidanceOnDash = false

    /** True while the encoder believes the dash is showing guidance. */
    val isGuidanceOnDash: Boolean get() = guidanceOnDash
    /** Last GPS/nav status bit sent in NAVIGATION_STATE. Flipped off while offline
     *  so the dash shows a degraded-navigation indicator (reflection of
     *  [NormalizedNavigationState.offline]). */
    private var gpsIconOnDash = true

    private var lastIcon: TurnIcon? = null
    private var lastDistance: String? = null
    private var lastRoad: String? = null
    private var lastEtaText: String? = null
    private var lastEtaEpochMs: Long? = null
    private var lastRemaining: String? = null
    private var lastEmitMs: Long? = null

    /**
     * True when the last [encode] held back a changed label because of the
     * throttle. The coordinator then re-encodes the latest snapshot once the
     * window has passed, so the final value of a burst is never left unsent
     * (the notification path may not produce another update for a long time).
     */
    var hasThrottledChanges: Boolean = false
        private set

    /**
     * Forget all sent state so the next [encode] re-emits everything from
     * scratch. Used by the coordinator after a BLE re-auth so a reconnect never
     * leaves the dash stale.
     */
    fun reset() {
        lastSession = NavSessionState.IDLE
        resetGuidance()
    }

    fun encode(state: NormalizedNavigationState): List<DashWrite> {
        val writes = mutableListOf<DashWrite>()
        hasThrottledChanges = false
        when (state.sessionState) {
            NavSessionState.IDLE, NavSessionState.STOPPED -> {
                if (guidanceOnDash) {
                    writes += DashWrite.ClearGuidance(immediate = state.sessionState == NavSessionState.STOPPED)
                    resetGuidance()
                }
                lastSession = state.sessionState
            }

            NavSessionState.ARRIVED -> {
                if (lastSession != NavSessionState.ARRIVED) {
                    ensureNavState(writes, gpsIconOn = !state.offline)
                    if (lastIcon != TurnIcon.END) {
                        writes += DashWrite.TurnIcon(TurnIcon.END)
                    }
                    // Show END briefly, then let the debounced clear blank it.
                    writes += DashWrite.ClearGuidance(immediate = false)
                    resetGuidance()
                    lastSession = NavSessionState.ARRIVED
                }
            }

            NavSessionState.REROUTING -> {
                ensureNavState(writes, gpsIconOn = !state.offline)
                // Never leave a stale turn on the dash while recalculating.
                if (lastIcon != TurnIcon.UNDEFINED) {
                    writes += DashWrite.TurnIcon(TurnIcon.UNDEFINED)
                    lastIcon = TurnIcon.UNDEFINED
                }
                if (lastDistance != "") {
                    writes += DashWrite.TurnDistance("")
                    lastDistance = ""
                }
                lastSession = NavSessionState.REROUTING
            }

            NavSessionState.ENROUTE -> {
                ensureNavState(writes, gpsIconOn = !state.offline)
                val now = clockMs()
                val withinWindow = lastEmitMs?.let { now - it < minIntervalMs } ?: false

                val road = state.roadName?.trim()?.takeIf { it.isNotEmpty() }
                val roadChanged = road != null && road != lastRoad

                // Turn icon (significant - always allowed). Prefer a
                // provider-resolved icon (notification path); else map the
                // maneuver. Never emit UNKNOWN as an arrow.
                val icon: TurnIcon? = state.resolvedIcon
                    ?: if (state.maneuver != NormalizedManeuver.UNKNOWN) {
                        KtmManeuverMapping.resolveIcon(
                            maneuver = state.maneuver,
                            rotation = state.roundaboutRotation,
                            drivingSide = state.drivingSide,
                            instruction = state.fullInstruction,
                        )
                    } else {
                        null
                    }
                val resolved = icon?.takeIf { it != TurnIcon.UNDEFINED }
                var iconChanged = false
                if (resolved != null) {
                    if (resolved != lastIcon) {
                        writes += DashWrite.TurnIcon(resolved)
                        lastIcon = resolved
                        iconChanged = true
                    }
                } else if (roadChanged && lastIcon != null && lastIcon != TurnIcon.UNDEFINED) {
                    // Unrecognised maneuver on a NEW step (the road moved on): blank
                    // the arrow rather than keep showing the previous step's turn
                    // next to the new road/distance - a stale arrow could point the
                    // wrong way. Same step (road unchanged): hold the last arrow,
                    // it's just a flaky tick.
                    writes += DashWrite.TurnIcon(TurnIcon.UNDEFINED)
                    lastIcon = TurnIcon.UNDEFINED
                    iconChanged = true
                }

                // Road name (significant - always allowed).
                if (roadChanged) {
                    writes += DashWrite.TurnRoad(road!!)
                    lastRoad = road
                }

                // Distance belongs to the step: when the icon/road changed it must
                // go out with them (never a new arrow next to the old step's
                // "0 m"), so the throttle only applies to label-only churn.
                val throttled = withinWindow && !iconChanged && !roadChanged

                val distance = state.preformattedDistance
                    ?: state.distanceToManeuverMeters?.let { DistanceFormatter.format(it, state.units) }
                if (distance != null && distance != lastDistance) {
                    if (throttled) {
                        hasThrottledChanges = true
                    } else {
                        writes += DashWrite.TurnDistance(distance)
                        lastDistance = distance
                    }
                }

                // ETA / remaining: throttle-eligible (they never change per step).
                val labelsThrottled = withinWindow
                val eta: String? = when {
                    state.preformattedEta != null -> state.preformattedEta
                    state.remainingTimeSeconds != null -> {
                        val candidate = EtaFormatter.etaEpochMs(state.remainingTimeSeconds, state.producedAtMs)
                        if (EtaFormatter.shouldUpdate(lastEtaEpochMs, candidate, etaDeadbandMs)) {
                            if (labelsThrottled) {
                                hasThrottledChanges = true
                                null
                            } else {
                                lastEtaEpochMs = candidate
                                EtaFormatter.format(candidate, zone ?: ZoneId.systemDefault())
                            }
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                if (eta != null && eta != lastEtaText) {
                    if (labelsThrottled) {
                        hasThrottledChanges = true
                    } else {
                        writes += DashWrite.Eta(eta)
                        lastEtaText = eta
                    }
                }

                val remaining = state.preformattedRemaining
                    ?: state.remainingDistanceMeters?.let { DistanceFormatter.format(it, state.units) }
                if (remaining != null && remaining != lastRemaining) {
                    if (labelsThrottled) {
                        hasThrottledChanges = true
                    } else {
                        writes += DashWrite.RemainingDistance(remaining)
                        lastRemaining = remaining
                    }
                }

                lastSession = NavSessionState.ENROUTE
                if (writes.isNotEmpty()) lastEmitMs = now
            }
        }
        return writes
    }

    private fun ensureNavState(writes: MutableList<DashWrite>, gpsIconOn: Boolean) {
        if (!guidanceOnDash) {
            writes += DashWrite.SetNavState(guidanceOn = true, gpsIconOn = gpsIconOn)
            guidanceOnDash = true
            gpsIconOnDash = gpsIconOn
        } else if (gpsIconOnDash != gpsIconOn) {
            // Guidance already on; only the online/offline status changed.
            writes += DashWrite.SetNavState(guidanceOn = true, gpsIconOn = gpsIconOn)
            gpsIconOnDash = gpsIconOn
        }
    }

    private fun resetGuidance() {
        guidanceOnDash = false
        gpsIconOnDash = true
        lastIcon = null
        lastDistance = null
        lastRoad = null
        lastEtaText = null
        lastEtaEpochMs = null
        lastRemaining = null
        lastEmitMs = null
        hasThrottledChanges = false
    }
}
