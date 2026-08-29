package com.navigator.app.nav.model

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
) {
    companion object {
        /** Convenience: a fully idle snapshot. */
        val IDLE = NormalizedNavigationState(sessionState = NavSessionState.IDLE)
    }
}

/** A routing target for provider-owned navigation (Nav SDK now, Routes API later). */
data class NavDestination(val lat: Double, val lng: Double, val label: String? = null)

/** Travel mode requested for routing. */
enum class TravelMode { TWO_WHEELER, DRIVING }
