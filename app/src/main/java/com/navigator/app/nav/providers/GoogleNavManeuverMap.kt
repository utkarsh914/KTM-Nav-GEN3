package com.navigator.app.nav.providers

import com.google.android.libraries.mapsplatform.turnbyturn.model.DrivingSide as SdkDrivingSide
import com.google.android.libraries.mapsplatform.turnbyturn.model.Maneuver
import com.navigator.app.nav.model.DrivingSide
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.RoundaboutRotation

/** A normalized maneuver plus its (roundabout/U-turn) rotation. */
data class MappedManeuver(
    val maneuver: NormalizedManeuver,
    val rotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
)

/**
 * Maps the Google Navigation SDK `Maneuver` int constants to the app's
 * [NormalizedManeuver] + [RoundaboutRotation]. The KTM-specific reduction then
 * happens once more in `KtmManeuverMapping`.
 *
 * The SDK constants are Java `static final int`, so Kotlin inlines them here at
 * compile time - this object has no runtime dependency on the SDK classes and is
 * therefore unit-testable on the JVM by passing the documented int values.
 */
object GoogleNavManeuverMap {

    private val CW = RoundaboutRotation.CLOCKWISE
    private val CCW = RoundaboutRotation.COUNTERCLOCKWISE

    fun toNormalized(maneuver: Int): MappedManeuver = when (maneuver) {
        Maneuver.DEPART -> MappedManeuver(NormalizedManeuver.DEPART)
        Maneuver.DESTINATION -> MappedManeuver(NormalizedManeuver.DESTINATION)
        Maneuver.DESTINATION_LEFT -> MappedManeuver(NormalizedManeuver.DESTINATION_LEFT)
        Maneuver.DESTINATION_RIGHT -> MappedManeuver(NormalizedManeuver.DESTINATION_RIGHT)
        Maneuver.STRAIGHT -> MappedManeuver(NormalizedManeuver.STRAIGHT)
        Maneuver.NAME_CHANGE -> MappedManeuver(NormalizedManeuver.NAME_CHANGE)

        Maneuver.TURN_LEFT -> MappedManeuver(NormalizedManeuver.LEFT)
        Maneuver.TURN_RIGHT -> MappedManeuver(NormalizedManeuver.RIGHT)
        Maneuver.TURN_KEEP_LEFT -> MappedManeuver(NormalizedManeuver.KEEP_LEFT)
        Maneuver.TURN_KEEP_RIGHT -> MappedManeuver(NormalizedManeuver.KEEP_RIGHT)
        Maneuver.TURN_SLIGHT_LEFT -> MappedManeuver(NormalizedManeuver.SLIGHT_LEFT)
        Maneuver.TURN_SLIGHT_RIGHT -> MappedManeuver(NormalizedManeuver.SLIGHT_RIGHT)
        Maneuver.TURN_SHARP_LEFT -> MappedManeuver(NormalizedManeuver.SHARP_LEFT)
        Maneuver.TURN_SHARP_RIGHT -> MappedManeuver(NormalizedManeuver.SHARP_RIGHT)
        Maneuver.TURN_U_TURN_CLOCKWISE -> MappedManeuver(NormalizedManeuver.UTURN_RIGHT, CW)
        Maneuver.TURN_U_TURN_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.UTURN_LEFT, CCW)

        Maneuver.MERGE_UNSPECIFIED -> MappedManeuver(NormalizedManeuver.MERGE_UNSPECIFIED)
        Maneuver.MERGE_LEFT -> MappedManeuver(NormalizedManeuver.MERGE_LEFT)
        Maneuver.MERGE_RIGHT -> MappedManeuver(NormalizedManeuver.MERGE_RIGHT)
        Maneuver.FORK_LEFT -> MappedManeuver(NormalizedManeuver.FORK_LEFT)
        Maneuver.FORK_RIGHT -> MappedManeuver(NormalizedManeuver.FORK_RIGHT)

        Maneuver.ON_RAMP_UNSPECIFIED -> MappedManeuver(NormalizedManeuver.ON_RAMP_UNSPECIFIED)
        Maneuver.ON_RAMP_LEFT -> MappedManeuver(NormalizedManeuver.ON_RAMP_LEFT)
        Maneuver.ON_RAMP_RIGHT -> MappedManeuver(NormalizedManeuver.ON_RAMP_RIGHT)
        Maneuver.ON_RAMP_KEEP_LEFT -> MappedManeuver(NormalizedManeuver.ON_RAMP_KEEP_LEFT)
        Maneuver.ON_RAMP_KEEP_RIGHT -> MappedManeuver(NormalizedManeuver.ON_RAMP_KEEP_RIGHT)
        Maneuver.ON_RAMP_SLIGHT_LEFT -> MappedManeuver(NormalizedManeuver.ON_RAMP_SLIGHT_LEFT)
        Maneuver.ON_RAMP_SLIGHT_RIGHT -> MappedManeuver(NormalizedManeuver.ON_RAMP_SLIGHT_RIGHT)
        Maneuver.ON_RAMP_SHARP_LEFT -> MappedManeuver(NormalizedManeuver.ON_RAMP_SHARP_LEFT)
        Maneuver.ON_RAMP_SHARP_RIGHT -> MappedManeuver(NormalizedManeuver.ON_RAMP_SHARP_RIGHT)
        Maneuver.ON_RAMP_U_TURN_CLOCKWISE -> MappedManeuver(NormalizedManeuver.UTURN_RIGHT, CW)
        Maneuver.ON_RAMP_U_TURN_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.UTURN_LEFT, CCW)

        Maneuver.OFF_RAMP_UNSPECIFIED -> MappedManeuver(NormalizedManeuver.OFF_RAMP_UNSPECIFIED)
        Maneuver.OFF_RAMP_LEFT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_LEFT)
        Maneuver.OFF_RAMP_RIGHT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_RIGHT)
        Maneuver.OFF_RAMP_KEEP_LEFT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_KEEP_LEFT)
        Maneuver.OFF_RAMP_KEEP_RIGHT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_KEEP_RIGHT)
        Maneuver.OFF_RAMP_SLIGHT_LEFT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_SLIGHT_LEFT)
        Maneuver.OFF_RAMP_SLIGHT_RIGHT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_SLIGHT_RIGHT)
        Maneuver.OFF_RAMP_SHARP_LEFT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_SHARP_LEFT)
        Maneuver.OFF_RAMP_SHARP_RIGHT -> MappedManeuver(NormalizedManeuver.OFF_RAMP_SHARP_RIGHT)
        Maneuver.OFF_RAMP_U_TURN_CLOCKWISE -> MappedManeuver(NormalizedManeuver.UTURN_RIGHT, CW)
        Maneuver.OFF_RAMP_U_TURN_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.UTURN_LEFT, CCW)

        Maneuver.ROUNDABOUT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_GENERIC, CW)
        Maneuver.ROUNDABOUT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_GENERIC, CCW)
        Maneuver.ROUNDABOUT_STRAIGHT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_STRAIGHT, CW)
        Maneuver.ROUNDABOUT_STRAIGHT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_STRAIGHT, CCW)
        Maneuver.ROUNDABOUT_LEFT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_LEFT, CW)
        Maneuver.ROUNDABOUT_LEFT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_LEFT, CCW)
        Maneuver.ROUNDABOUT_RIGHT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_RIGHT, CW)
        Maneuver.ROUNDABOUT_RIGHT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_RIGHT, CCW)
        Maneuver.ROUNDABOUT_SLIGHT_LEFT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SLIGHT_LEFT, CW)
        Maneuver.ROUNDABOUT_SLIGHT_LEFT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SLIGHT_LEFT, CCW)
        Maneuver.ROUNDABOUT_SLIGHT_RIGHT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SLIGHT_RIGHT, CW)
        Maneuver.ROUNDABOUT_SLIGHT_RIGHT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SLIGHT_RIGHT, CCW)
        Maneuver.ROUNDABOUT_SHARP_LEFT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SHARP_LEFT, CW)
        Maneuver.ROUNDABOUT_SHARP_LEFT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SHARP_LEFT, CCW)
        Maneuver.ROUNDABOUT_SHARP_RIGHT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SHARP_RIGHT, CW)
        Maneuver.ROUNDABOUT_SHARP_RIGHT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_SHARP_RIGHT, CCW)
        Maneuver.ROUNDABOUT_U_TURN_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_UTURN, CW)
        Maneuver.ROUNDABOUT_U_TURN_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_UTURN, CCW)
        Maneuver.ROUNDABOUT_EXIT_CLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_EXIT, CW)
        Maneuver.ROUNDABOUT_EXIT_COUNTERCLOCKWISE -> MappedManeuver(NormalizedManeuver.ROUNDABOUT_EXIT, CCW)

        Maneuver.FERRY_BOAT -> MappedManeuver(NormalizedManeuver.FERRY_BOAT)
        Maneuver.FERRY_TRAIN -> MappedManeuver(NormalizedManeuver.FERRY_TRAIN)

        else -> MappedManeuver(NormalizedManeuver.UNKNOWN) // includes Maneuver.UNKNOWN (0)
    }

    fun drivingSide(side: Int): DrivingSide = when (side) {
        SdkDrivingSide.LEFT -> DrivingSide.LEFT
        SdkDrivingSide.RIGHT -> DrivingSide.RIGHT
        else -> DrivingSide.UNKNOWN // SdkDrivingSide.NONE
    }
}
