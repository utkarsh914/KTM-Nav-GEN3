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
 *   holding the last valid turn (never emitting UNKNOWN as an arrow) and blanking
 *   a stale turn while rerouting.
 *
 * Pure logic, no I/O - the coordinator applies the returned writes. Deterministic
 * given its inputs ([NormalizedNavigationState.producedAtMs] drives time), so the
 * whole thing is unit-testable.
 */
class KtmNavigationEncoder(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val minIntervalMs: Long = 1_000L,
    private val etaDeadbandMs: Long = 60_000L,
) {
    private var lastSession: NavSessionState = NavSessionState.IDLE
    private var guidanceOnDash = false
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
    private var lastEmitMs: Long = Long.MIN_VALUE

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
                val throttled = lastEmitMs != Long.MIN_VALUE &&
                    (state.producedAtMs - lastEmitMs) < minIntervalMs

                // Turn icon (significant - always allowed). Prefer a
                // provider-resolved icon (notification path); else map the
                // maneuver; else hold the last valid one (never emit
                // UNKNOWN/UNDEFINED as an arrow).
                val icon: TurnIcon? = state.resolvedIcon
                    ?: if (state.maneuver != NormalizedManeuver.UNKNOWN) {
                        KtmManeuverMapping.toTurnIcon(
                            maneuver = state.maneuver,
                            rotation = state.roundaboutRotation,
                            drivingSide = state.drivingSide,
                        )
                    } else {
                        null
                    }
                if (icon != null && icon != TurnIcon.UNDEFINED && icon != lastIcon) {
                    writes += DashWrite.TurnIcon(icon)
                    lastIcon = icon
                }

                // Road name (significant - always allowed).
                val road = state.roadName?.trim()?.takeIf { it.isNotEmpty() }
                if (road != null && road != lastRoad) {
                    writes += DashWrite.TurnRoad(road)
                    lastRoad = road
                }

                // Throttle-eligible labels: distance / ETA / remaining. A
                // provider-preformatted string bypasses the numeric formatter.
                if (!throttled) {
                    val distance = state.preformattedDistance
                        ?: state.distanceToManeuverMeters?.let { DistanceFormatter.format(it, state.units) }
                    if (distance != null && distance != lastDistance) {
                        writes += DashWrite.TurnDistance(distance)
                        lastDistance = distance
                    }

                    val eta: String? = when {
                        state.preformattedEta != null -> state.preformattedEta
                        state.remainingTimeSeconds != null -> {
                            val candidate = EtaFormatter.etaEpochMs(state.remainingTimeSeconds, state.producedAtMs)
                            if (EtaFormatter.shouldUpdate(lastEtaEpochMs, candidate, etaDeadbandMs)) {
                                lastEtaEpochMs = candidate
                                EtaFormatter.format(candidate, zone)
                            } else {
                                null
                            }
                        }
                        else -> null
                    }
                    if (eta != null && eta != lastEtaText) {
                        writes += DashWrite.Eta(eta)
                        lastEtaText = eta
                    }

                    val remaining = state.preformattedRemaining
                        ?: state.remainingDistanceMeters?.let { DistanceFormatter.format(it, state.units) }
                    if (remaining != null && remaining != lastRemaining) {
                        writes += DashWrite.RemainingDistance(remaining)
                        lastRemaining = remaining
                    }
                }

                lastSession = NavSessionState.ENROUTE
                if (writes.isNotEmpty()) lastEmitMs = state.producedAtMs
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
        lastEmitMs = Long.MIN_VALUE
    }
}
