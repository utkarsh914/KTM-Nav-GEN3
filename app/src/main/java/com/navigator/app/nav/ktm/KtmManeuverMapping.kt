package com.navigator.app.nav.ktm

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.DrivingSide
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.RoundaboutRotation

/**
 * The single, lossy reduction from the rich [NormalizedManeuver] vocabulary to
 * the 58-entry KTM [TurnIcon] enum. Pure and table-driven.
 *
 * The `when` is deliberately exhaustive with no `else`: adding a maneuver to
 * [NormalizedManeuver] will fail to compile here until it is mapped - a
 * tripwire so new SDK maneuvers can never silently fall through to a wrong arrow.
 *
 * Roundabout RH/LH selection is **reverse-engineered** (default clockwise->RH)
 * and overridable via the existing Turn-icon Calibration screen; verify on
 * hardware (see the revamp plan §5.3).
 */
object KtmManeuverMapping {

    fun toTurnIcon(
        maneuver: NormalizedManeuver,
        rotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
        exit: Int? = null,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ): TurnIcon {
        val clockwise = resolveClockwise(rotation, drivingSide)

        // For any roundabout: prefer the exact section+exit glyph when we know
        // the exit; otherwise fall back to the closest graded shape.
        fun rab(shapeFallback: TurnIcon): TurnIcon =
            if (exit != null && exit >= 1) roundaboutIcon(exit, clockwise) else shapeFallback

        return when (maneuver) {
            NormalizedManeuver.DEPART -> TurnIcon.START

            NormalizedManeuver.NAME_CHANGE,
            NormalizedManeuver.STRAIGHT,
            NormalizedManeuver.MERGE_UNSPECIFIED,
            NormalizedManeuver.ON_RAMP_UNSPECIFIED,
            NormalizedManeuver.OFF_RAMP_UNSPECIFIED -> TurnIcon.GO_STRAIGHT

            NormalizedManeuver.SLIGHT_LEFT -> TurnIcon.LIGHT_LEFT
            NormalizedManeuver.LEFT -> TurnIcon.QUITE_LEFT
            NormalizedManeuver.SHARP_LEFT -> TurnIcon.HEAVY_LEFT
            NormalizedManeuver.SLIGHT_RIGHT -> TurnIcon.LIGHT_RIGHT
            NormalizedManeuver.RIGHT -> TurnIcon.QUITE_RIGHT
            NormalizedManeuver.SHARP_RIGHT -> TurnIcon.HEAVY_RIGHT

            NormalizedManeuver.KEEP_LEFT,
            NormalizedManeuver.FORK_LEFT,
            NormalizedManeuver.ON_RAMP_KEEP_LEFT,
            NormalizedManeuver.OFF_RAMP_KEEP_LEFT -> TurnIcon.KEEP_LEFT

            NormalizedManeuver.KEEP_RIGHT,
            NormalizedManeuver.FORK_RIGHT,
            NormalizedManeuver.ON_RAMP_KEEP_RIGHT,
            NormalizedManeuver.OFF_RAMP_KEEP_RIGHT -> TurnIcon.KEEP_RIGHT

            NormalizedManeuver.MERGE_LEFT,
            NormalizedManeuver.ON_RAMP_LEFT,
            NormalizedManeuver.ON_RAMP_SLIGHT_LEFT,
            NormalizedManeuver.ON_RAMP_SHARP_LEFT -> TurnIcon.ENTER_HIGHWAY_LEFT_LANE

            NormalizedManeuver.MERGE_RIGHT,
            NormalizedManeuver.ON_RAMP_RIGHT,
            NormalizedManeuver.ON_RAMP_SLIGHT_RIGHT,
            NormalizedManeuver.ON_RAMP_SHARP_RIGHT -> TurnIcon.ENTER_HIGHWAY_RIGHT_LANE

            NormalizedManeuver.OFF_RAMP_LEFT,
            NormalizedManeuver.OFF_RAMP_SLIGHT_LEFT,
            NormalizedManeuver.OFF_RAMP_SHARP_LEFT -> TurnIcon.LEAVE_HIGHWAY_LEFT_LANE

            NormalizedManeuver.OFF_RAMP_RIGHT,
            NormalizedManeuver.OFF_RAMP_SLIGHT_RIGHT,
            NormalizedManeuver.OFF_RAMP_SHARP_RIGHT -> TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE

            NormalizedManeuver.UTURN_LEFT -> TurnIcon.UTURN_LEFT
            NormalizedManeuver.UTURN_RIGHT -> TurnIcon.UTURN_RIGHT

            NormalizedManeuver.ROUNDABOUT_SLIGHT_LEFT -> rab(TurnIcon.LIGHT_LEFT)
            NormalizedManeuver.ROUNDABOUT_LEFT -> rab(TurnIcon.QUITE_LEFT)
            NormalizedManeuver.ROUNDABOUT_SHARP_LEFT -> rab(TurnIcon.HEAVY_LEFT)
            NormalizedManeuver.ROUNDABOUT_SLIGHT_RIGHT -> rab(TurnIcon.LIGHT_RIGHT)
            NormalizedManeuver.ROUNDABOUT_RIGHT -> rab(TurnIcon.QUITE_RIGHT)
            NormalizedManeuver.ROUNDABOUT_SHARP_RIGHT -> rab(TurnIcon.HEAVY_RIGHT)
            NormalizedManeuver.ROUNDABOUT_STRAIGHT -> rab(TurnIcon.GO_STRAIGHT)
            NormalizedManeuver.ROUNDABOUT_EXIT -> rab(TurnIcon.GO_STRAIGHT)
            NormalizedManeuver.ROUNDABOUT_UTURN ->
                rab(if (clockwise) TurnIcon.UTURN_RIGHT else TurnIcon.UTURN_LEFT)
            NormalizedManeuver.ROUNDABOUT_GENERIC -> rab(TurnIcon.UNDEFINED)

            NormalizedManeuver.DESTINATION,
            NormalizedManeuver.DESTINATION_LEFT,
            NormalizedManeuver.DESTINATION_RIGHT -> TurnIcon.END

            NormalizedManeuver.FERRY_BOAT,
            NormalizedManeuver.FERRY_TRAIN -> TurnIcon.FERRY

            // Never emit a guessed arrow for an unknown maneuver.
            NormalizedManeuver.UNKNOWN -> TurnIcon.UNDEFINED
        }
    }

    /**
     * Resolve travel direction around the roundabout.
     * Left-hand traffic (e.g. India) circulates clockwise. When neither the
     * rotation nor the driving side is known, default to clockwise (plan default
     * CW->RH; overridable via calibration).
     */
    private fun resolveClockwise(rotation: RoundaboutRotation, drivingSide: DrivingSide): Boolean =
        when (rotation) {
            RoundaboutRotation.CLOCKWISE -> true
            RoundaboutRotation.COUNTERCLOCKWISE -> false
            RoundaboutRotation.UNKNOWN -> when (drivingSide) {
                DrivingSide.LEFT -> true
                DrivingSide.RIGHT -> false
                DrivingSide.UNKNOWN -> true
            }
        }

    /**
     * Section glyph for a roundabout exit. RH block is binary 26..41
     * (RAB_SECT_1_RH..16_RH), LH block is 42..57. Exit clamped to 1..16.
     */
    private fun roundaboutIcon(exit: Int, clockwise: Boolean): TurnIcon {
        val n = exit.coerceIn(1, 16)
        val base = if (clockwise) 26 else 42
        return TurnIcon.fromBinary(base + (n - 1))
    }
}
