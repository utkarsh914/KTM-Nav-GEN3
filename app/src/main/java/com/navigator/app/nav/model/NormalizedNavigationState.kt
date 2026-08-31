package com.navigator.app.nav.model

import com.navigator.app.ble.BccuProtocol

/**
 * Provider-agnostic navigation state. Every navigation source (Google Nav SDK,
 * the notification-mirroring fallback, a future Routes API provider) is
 * normalised into this one type, which the [com.navigator.app.nav.ktm.KtmNavigationEncoder]
 * turns into KTM dash writes.
 *
 * Pure Kotlin, no Android dependencies, so the whole navigation core is
 * unit-testable on the JVM.
 */

/** High-level session lifecycle. */
enum class NavSessionState {
    /** No active route. */
    IDLE,

    /** Guided navigation is active and the user is on the route. */
    ENROUTE,

    /** Navigation is active but the engine is recalculating; no valid upcoming step. */
    REROUTING,

    /**
     * The destination has been reached. NOT a Google Nav SDK nav state
     * (`NavInfo.getNavState()` only yields ENROUTE/REROUTING/STOPPED) - it is
     * synthesised by the provider from an arrival callback.
     */
    ARRIVED,

    /** Navigation has ended (user cancelled / exited). */
    STOPPED,
}

/** True while guidance is live (used to keep the screen on / show over lock screen). */
fun NavSessionState.isActiveNav(): Boolean =
    this == NavSessionState.ENROUTE || this == NavSessionState.REROUTING

/**
 * Maneuver vocabulary. Mirrors the Google Navigation SDK `Maneuver` set closely
 * (roundabout shapes + rotation are kept separate from the exit number) so the
 * lossy reduction to KTM turn-icon codes happens exactly once, in
 * [com.navigator.app.nav.ktm.KtmManeuverMapping]. A future Routes API provider
 * maps onto the same set.
 */
enum class NormalizedManeuver {
    DEPART,
    NAME_CHANGE,
    STRAIGHT,

    SLIGHT_LEFT, LEFT, SHARP_LEFT,
    SLIGHT_RIGHT, RIGHT, SHARP_RIGHT,

    KEEP_LEFT, KEEP_RIGHT,
    FORK_LEFT, FORK_RIGHT,

    MERGE_LEFT, MERGE_RIGHT, MERGE_UNSPECIFIED,

    ON_RAMP_LEFT, ON_RAMP_RIGHT,
    ON_RAMP_SLIGHT_LEFT, ON_RAMP_SLIGHT_RIGHT,
    ON_RAMP_SHARP_LEFT, ON_RAMP_SHARP_RIGHT,
    ON_RAMP_KEEP_LEFT, ON_RAMP_KEEP_RIGHT,
    ON_RAMP_UNSPECIFIED,

    OFF_RAMP_LEFT, OFF_RAMP_RIGHT,
    OFF_RAMP_SLIGHT_LEFT, OFF_RAMP_SLIGHT_RIGHT,
    OFF_RAMP_SHARP_LEFT, OFF_RAMP_SHARP_RIGHT,
    OFF_RAMP_KEEP_LEFT, OFF_RAMP_KEEP_RIGHT,
    OFF_RAMP_UNSPECIFIED,

    UTURN_LEFT, UTURN_RIGHT,

    // Roundabouts carry a shape AND a rotation; the exit number is a separate field.
    ROUNDABOUT_LEFT, ROUNDABOUT_RIGHT,
    ROUNDABOUT_SLIGHT_LEFT, ROUNDABOUT_SLIGHT_RIGHT,
    ROUNDABOUT_SHARP_LEFT, ROUNDABOUT_SHARP_RIGHT,
    ROUNDABOUT_STRAIGHT,
    ROUNDABOUT_UTURN,
    ROUNDABOUT_EXIT,
    ROUNDABOUT_GENERIC,

    DESTINATION, DESTINATION_LEFT, DESTINATION_RIGHT,

    FERRY_BOAT, FERRY_TRAIN,

    UNKNOWN,
}

/** Direction of travel around a roundabout / for a U-turn. */
enum class RoundaboutRotation { CLOCKWISE, COUNTERCLOCKWISE, UNKNOWN }

/** Side of the road traffic drives on (from the SDK's driving-side detection). */
enum class DrivingSide { LEFT, RIGHT, UNKNOWN }

/** Distance unit system for formatting. */
enum class DistanceUnits { METRIC, IMPERIAL }

/** A single arrow direction a lane can lead to (from the SDK's lane guidance). */
enum class LaneShape {
    STRAIGHT,
    SLIGHT_LEFT, LEFT, SHARP_LEFT, UTURN_LEFT,
    SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, UTURN_RIGHT,
    UNKNOWN,
}

/**
 * One lane's guidance: the [directions] it allows, and whether it is
 * [recommended] for the current maneuver (i.e. leads to the upcoming turn).
 */
data class LaneInfo(
    val directions: List<LaneShape>,
    val recommended: Boolean,
)

/**
 * A single normalised navigation snapshot.
 *
 * @property remainingTimeSeconds seconds to the final destination (NOT an epoch;
 *   the SDK has no ETA epoch). [com.navigator.app.nav.ktm.EtaFormatter] derives
 *   the wall-clock arrival time from this + the current time.
 * @property roundaboutExit exit number for roundabout maneuvers; null when absent
 *   OR when the SDK returned its `-1` sentinel (the provider normalises `-1` to null).
 * @property producedAtMs monotonic-ish timestamp (ms) of when this snapshot was
 *   produced; used by the encoder for throttling and ETA derivation.
 */
data class NormalizedNavigationState(
    val sessionState: NavSessionState,
    val maneuver: NormalizedManeuver = NormalizedManeuver.UNKNOWN,
    val roundaboutRotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
    val roundaboutExit: Int? = null,
    val drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    val distanceToManeuverMeters: Int? = null,
    val roadName: String? = null,
    val remainingTimeSeconds: Int? = null,
    val remainingDistanceMeters: Int? = null,
    val nextManeuver: NormalizedManeuver? = null,
    val units: DistanceUnits = DistanceUnits.METRIC,
    val producedAtMs: Long = 0L,

    // --- Richer guidance detail (Nav SDK provider; used by the phone UI only) ---
    /** Full turn instruction text, HTML stripped (e.g. "Turn right onto Foo St"). */
    val fullInstruction: String? = null,
    /** Highway/ramp exit number for the current step (e.g. "12A"), when present. */
    val exitNumber: String? = null,
    /** Lane guidance for the current step; empty when the SDK reports none. */
    val lanes: List<LaneInfo> = emptyList(),

    // --- Passthrough overrides (fallback/notification provider) ---
    // When set, these bypass the encoder's own maneuver-mapping / formatting and
    // are sent to the dash verbatim. They let the string-based notification path
    // stay byte-for-byte identical to today while still gaining the encoder's
    // dedup / throttle / state-machine / clear handling. The Nav SDK provider
    // leaves them null and uses the structured numeric fields above.
    // DECISION TO REVISIT at the end of all phases: whether to keep these
    // overrides or fully normalise the notification path to numeric fields.
    val resolvedIcon: BccuProtocol.TurnIcon? = null,
    val preformattedDistance: String? = null,
    val preformattedEta: String? = null,
    val preformattedRemaining: String? = null,
) {
    companion object {
        /** Convenience: a fully idle snapshot. */
        val IDLE = NormalizedNavigationState(sessionState = NavSessionState.IDLE)
    }
}

/**
 * A routing target for provider-owned navigation.
 *
 * [routeToken] (optional) is a Routes API route token identifying a *specific*
 * route the user picked in the preview; when present the Nav SDK is asked to
 * guide that exact route via `Navigator.setDestinations(waypoints, routeToken)`
 * (falls back to default routing if unavailable).
 */
data class NavDestination(
    val lat: Double,
    val lng: Double,
    val label: String? = null,
    val routeToken: String? = null,
)

/** Travel mode requested for routing. */
enum class TravelMode { TWO_WHEELER, DRIVING }
