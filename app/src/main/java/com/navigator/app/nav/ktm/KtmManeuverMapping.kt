package com.navigator.app.nav.ktm

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.DrivingSide
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.RoundaboutRotation
import kotlin.math.roundToInt

/**
 * The single, lossy reduction from the rich [NormalizedManeuver] vocabulary to
 * the 58-entry KTM [TurnIcon] enum. Pure and table-driven.
 *
 * The `when` is deliberately exhaustive with no `else`: adding a maneuver to
 * [NormalizedManeuver] will fail to compile here until it is mapped - a
 * tripwire so new SDK maneuvers can never silently fall through to a wrong arrow.
 *
 * Roundabout RH/LH selection and the section->angle law are **hardware-verified**
 * (see [roundaboutSection] and docs/ENGINEERING_NOTES.md): RH = clockwise circulation.
 */
object KtmManeuverMapping {

    fun toTurnIcon(
        maneuver: NormalizedManeuver,
        rotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ): TurnIcon {
        val clockwise = resolveClockwise(rotation, drivingSide)

        return when (maneuver) {
            NormalizedManeuver.DEPART -> TurnIcon.START

            NormalizedManeuver.NAME_CHANGE,
            NormalizedManeuver.STRAIGHT,
            NormalizedManeuver.ON_RAMP_UNSPECIFIED,
            NormalizedManeuver.OFF_RAMP_UNSPECIFIED -> TurnIcon.GO_STRAIGHT

            // ON_RAMP_<dir> is the turn you make FROM the surface road ONTO the
            // ramp ("Sharp right onto the ramp to NH 48") - the rider is not on
            // the highway yet; joining it is a separate, later MERGE_* step. So
            // the dash must show the actual turn, graded by severity, NOT the
            // enter-highway glyph (whose arrow leans the opposite way and pointed
            // riders left before a sharp right). This also matches the
            // notification-text and TFLite paths, which already map by turn shape.
            NormalizedManeuver.SLIGHT_LEFT,
            NormalizedManeuver.ON_RAMP_SLIGHT_LEFT -> TurnIcon.LIGHT_LEFT
            NormalizedManeuver.LEFT,
            NormalizedManeuver.ON_RAMP_LEFT -> TurnIcon.QUITE_LEFT
            NormalizedManeuver.SHARP_LEFT,
            NormalizedManeuver.ON_RAMP_SHARP_LEFT,
            // A sharp exit is a real hairpin-ish turn; the gentle LEAVE_HIGHWAY
            // split glyph would understate it.
            NormalizedManeuver.OFF_RAMP_SHARP_LEFT -> TurnIcon.HEAVY_LEFT
            NormalizedManeuver.SLIGHT_RIGHT,
            NormalizedManeuver.ON_RAMP_SLIGHT_RIGHT -> TurnIcon.LIGHT_RIGHT
            NormalizedManeuver.RIGHT,
            NormalizedManeuver.ON_RAMP_RIGHT -> TurnIcon.QUITE_RIGHT
            NormalizedManeuver.SHARP_RIGHT,
            NormalizedManeuver.ON_RAMP_SHARP_RIGHT,
            NormalizedManeuver.OFF_RAMP_SHARP_RIGHT -> TurnIcon.HEAVY_RIGHT

            // Ramp-keeps collapse to the plain KEEP_* arrow, NOT the dash's
            // HIGHWAY_KEEP_LEFT/RIGHT (18/19) codes. Those two codes are
            // intentionally never emitted: a "keep left/right to stay on the
            // ramp" reads the same as a normal lane-keep on the dash, and using
            // the distinct highway-keep glyphs gave no rider benefit in testing.
            // Left here as a deliberate gap, not an oversight.
            NormalizedManeuver.KEEP_LEFT,
            NormalizedManeuver.FORK_LEFT,
            NormalizedManeuver.ON_RAMP_KEEP_LEFT,
            NormalizedManeuver.OFF_RAMP_KEEP_LEFT -> TurnIcon.KEEP_LEFT

            NormalizedManeuver.KEEP_RIGHT,
            NormalizedManeuver.FORK_RIGHT,
            NormalizedManeuver.ON_RAMP_KEEP_RIGHT,
            NormalizedManeuver.OFF_RAMP_KEEP_RIGHT -> TurnIcon.KEEP_RIGHT

            // Joining the highway itself. An unqualified merge takes the
            // driving-side lane (same rule as the notification path).
            NormalizedManeuver.MERGE_LEFT -> enterHighwayIcon(mergeLeft = true)
            NormalizedManeuver.MERGE_RIGHT -> enterHighwayIcon(mergeLeft = false)
            NormalizedManeuver.MERGE_UNSPECIFIED ->
                enterHighwayIcon(mergeLeft = rampOnLeftFor(drivingSide))

            NormalizedManeuver.OFF_RAMP_LEFT,
            NormalizedManeuver.OFF_RAMP_SLIGHT_LEFT -> TurnIcon.LEAVE_HIGHWAY_LEFT_LANE

            NormalizedManeuver.OFF_RAMP_RIGHT,
            NormalizedManeuver.OFF_RAMP_SLIGHT_RIGHT -> TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE

            NormalizedManeuver.UTURN_LEFT -> TurnIcon.UTURN_LEFT
            NormalizedManeuver.UTURN_RIGHT -> TurnIcon.UTURN_RIGHT

            // Roundabout exits: the section glyph is chosen by the exit's TURN
            // ANGLE (right = +, left = -, straight = 0), not Google's ordinal exit
            // count - the dash renders a fixed glyph per code and can't show an
            // ordinal. Buckets come at 45deg resolution from the SDK maneuver.
            NormalizedManeuver.ROUNDABOUT_SLIGHT_LEFT -> roundaboutSection(-45, clockwise)
            NormalizedManeuver.ROUNDABOUT_LEFT -> roundaboutSection(-90, clockwise)
            NormalizedManeuver.ROUNDABOUT_SHARP_LEFT -> roundaboutSection(-135, clockwise)
            NormalizedManeuver.ROUNDABOUT_SLIGHT_RIGHT -> roundaboutSection(45, clockwise)
            NormalizedManeuver.ROUNDABOUT_RIGHT -> roundaboutSection(90, clockwise)
            NormalizedManeuver.ROUNDABOUT_SHARP_RIGHT -> roundaboutSection(135, clockwise)
            NormalizedManeuver.ROUNDABOUT_STRAIGHT -> roundaboutSection(0, clockwise)
            NormalizedManeuver.ROUNDABOUT_EXIT -> roundaboutSection(0, clockwise)
            // A roundabout U-turn shows the plain U-turn arrow (product decision),
            // not a roundabout-section glyph.
            NormalizedManeuver.ROUNDABOUT_UTURN ->
                if (clockwise) TurnIcon.UTURN_RIGHT else TurnIcon.UTURN_LEFT
            // Rotation known but exit direction unknown: no arrow (the dash has no
            // generic roundabout glyph, and we won't invent a direction).
            NormalizedManeuver.ROUNDABOUT_GENERIC -> TurnIcon.UNDEFINED

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
            RoundaboutRotation.UNKNOWN -> clockwiseForDrivingSide(drivingSide)
        }

    /**
     * The single source of truth for the "which way round a roundabout" and
     * "which lane an unqualified merge/exit uses" decision, shared by the SDK
     * path (here) and the notification-mirroring path
     * ([com.navigator.app.notifications.TurnIconHeuristic]) so they can never
     * drift. Left-hand traffic (India, UK, JP, AU…) circulates **clockwise**
     * (RH glyph bank) and its ramps/exits are on the **left**; right-hand
     * traffic is the mirror. Unknown defaults to left-hand/clockwise (the plan
     * default; overridable via the driving-side setting).
     */
    fun clockwiseForDrivingSide(drivingSide: DrivingSide): Boolean = when (drivingSide) {
        DrivingSide.LEFT -> true
        DrivingSide.RIGHT -> false
        DrivingSide.UNKNOWN -> true
    }

    /**
     * The side an unqualified ramp/exit takes for a given driving side: left-hand
     * traffic exits/merges on the **left**, right-hand traffic on the **right**.
     * Returns true for the left lane. Unknown defaults to left (LHT).
     */
    fun rampOnLeftFor(drivingSide: DrivingSide): Boolean = when (drivingSide) {
        DrivingSide.LEFT -> true
        DrivingSide.RIGHT -> false
        DrivingSide.UNKNOWN -> true
    }

    /**
     * The single place that picks the enter-highway (merge) dash code, shared by
     * the SDK path, the notification-text parser and the TFLite classifier so a
     * direction fix only ever needs to happen here.
     *
     * [mergeLeft] is the nav app's merge side (Google's MERGE_LEFT / "merge
     * left").
     *
     * UNVERIFIED ON HARDWARE: a dash photo (2026-09) shows code 14
     * (ENTER_HIGHWAY_RIGHT_LANE) drawn as a ramp on the RIGHT whose arrow bends
     * LEFT into the carriageway - i.e. it visually reads as a "merge left". If a
     * Symbol Test confirms 15 is its mirror, swap the two branches below (and the
     * dash_highway_left/right preview drawables).
     */
    fun enterHighwayIcon(mergeLeft: Boolean): TurnIcon =
        if (mergeLeft) TurnIcon.ENTER_HIGHWAY_LEFT_LANE else TurnIcon.ENTER_HIGHWAY_RIGHT_LANE

    /**
     * Section glyph for a roundabout exit, chosen by the exit's TURN ANGLE
     * ([turnAngleDeg]: right = +, left = -, straight = 0, U-turn = ±180) - NOT by
     * Google's ordinal exit count (the dash renders a fixed glyph per code and has
     * no route geometry, so `RAB_SECT_1..16` can only be 16 fixed exit-angle
     * glyphs, ~22.5° apart).
     *
     * Hardware-verified on the RH block (binary 26..41): the exit arrow rotates
     * counter-clockwise as N increases - N=1 sharpest right, N=4 = 90° right,
     * N=8 = straight through, N=12 = left, N=16 = U-turn - i.e.
     *   turnAngle = (8 - N) * 22.5°   ⟹   N = 8 - turnAngle / 22.5
     * The LH block (42..57) is mirrored (N = 8 + turnAngle / 22.5). RH vs LH is the
     * clockwise choice from [resolveClockwise] (RH = clockwise). N clamped 1..16.
     */
    private fun roundaboutSection(turnAngleDeg: Int, clockwise: Boolean): TurnIcon {
        val steps = (turnAngleDeg / 22.5).roundToInt() // 22.5° per section
        val n = (if (clockwise) 8 - steps else 8 + steps).coerceIn(1, 16)
        val base = if (clockwise) 26 else 42
        return TurnIcon.fromBinary(base + (n - 1))
    }
}
