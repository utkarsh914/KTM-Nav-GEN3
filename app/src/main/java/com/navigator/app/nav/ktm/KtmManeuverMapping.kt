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

            // Joining the highway itself. Google's MERGE_LEFT is "current road
            // joins another on the left": the rider moves LEFT off a ramp on the
            // RIGHT (and vice versa). An unqualified merge's ramp is on the
            // driving side (same rule as the notification path).
            NormalizedManeuver.MERGE_LEFT -> enterHighwayIcon(rampOnLeft = false)
            NormalizedManeuver.MERGE_RIGHT -> enterHighwayIcon(rampOnLeft = true)
            NormalizedManeuver.MERGE_UNSPECIFIED ->
                enterHighwayIcon(rampOnLeft = rampOnLeftFor(drivingSide))

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
            NormalizedManeuver.ROUNDABOUT_SLIGHT_LEFT -> roundaboutIcon(-45, clockwise)
            NormalizedManeuver.ROUNDABOUT_LEFT -> roundaboutIcon(-90, clockwise)
            NormalizedManeuver.ROUNDABOUT_SHARP_LEFT -> roundaboutIcon(-135, clockwise)
            NormalizedManeuver.ROUNDABOUT_SLIGHT_RIGHT -> roundaboutIcon(45, clockwise)
            NormalizedManeuver.ROUNDABOUT_RIGHT -> roundaboutIcon(90, clockwise)
            NormalizedManeuver.ROUNDABOUT_SHARP_RIGHT -> roundaboutIcon(135, clockwise)
            NormalizedManeuver.ROUNDABOUT_STRAIGHT -> roundaboutIcon(0, clockwise)
            NormalizedManeuver.ROUNDABOUT_EXIT -> roundaboutIcon(0, clockwise)
            // A roundabout U-turn shows the plain U-turn arrow (product decision),
            // not a roundabout-section glyph.
            NormalizedManeuver.ROUNDABOUT_UTURN -> roundaboutIcon(180, clockwise)
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
     * [rampOnLeft] is the side of the carriageway the joining ramp is on - NOT
     * the direction the rider moves (a ramp on the left merges rightwards).
     *
     * Verified from dash photos: code 14 (ENTER_HIGHWAY_RIGHT_LANE) is drawn as
     * a ramp on the RIGHT whose arrow bends LEFT into the carriageway; code 15
     * (ENTER_HIGHWAY_LEFT_LANE) is its mirror - ramp on the LEFT, arrow bending
     * RIGHT (the normal left-hand-traffic on-ramp).
     *
     * NOT yet confirmed on a ride: that Google's MERGE_LEFT ("joins another on
     * the left") means the rider moves left, i.e. a ramp on the right. That is
     * our reading of the SDK doc; the per-step log in GoogleNavSdkProvider lets
     * a real MERGE_LEFT/RIGHT confirm it.
     */
    fun enterHighwayIcon(rampOnLeft: Boolean): TurnIcon =
        if (rampOnLeft) TurnIcon.ENTER_HIGHWAY_LEFT_LANE else TurnIcon.ENTER_HIGHWAY_RIGHT_LANE

    /**
     * The dash icon for a nav state, shared by the dash encoder and the phone
     * guidance header so the two can never disagree.
     *
     * Safeguard: when the SDK reports a MERGE_* step but its own instruction
     * explicitly says to turn ("Turn left onto the ramp to NE 7"), the turn wins -
     * the enter-highway glyph's arrow can lean the opposite way to the turn.
     */
    fun resolveIcon(
        maneuver: NormalizedManeuver,
        rotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
        instruction: String? = null,
    ): TurnIcon {
        val isMerge = maneuver == NormalizedManeuver.MERGE_LEFT ||
            maneuver == NormalizedManeuver.MERGE_RIGHT ||
            maneuver == NormalizedManeuver.MERGE_UNSPECIFIED
        if (isMerge && !instruction.isNullOrBlank()) {
            explicitTurnFromInstruction(instruction)?.let { return it }
        }
        return toTurnIcon(maneuver, rotation, drivingSide)
    }

    /**
     * An explicit, graded turn named in an instruction ("turn left", "sharp
     * right", "slight left"), or null. Whole words only, English only (the SDK
     * text follows the device language; other languages simply don't override).
     * The turn phrase must hold the FIRST left/right word in the text: a later
     * one belongs to a road name or a following step ("Keep right to merge,
     * then turn left" is not a left turn). "U-turn left" is not a plain turn.
     */
    fun explicitTurnFromInstruction(text: String): TurnIcon? {
        val t = text.lowercase()
        val firstSide = FIRST_SIDE_RE.find(t) ?: return null
        val m = EXPLICIT_TURN_RE.find(t) ?: return null
        val sideGroup = m.groups[2] ?: return null
        if (sideGroup.range.first != firstSide.range.first) return null
        val left = sideGroup.value == "left"
        return when (m.groupValues[1]) {
            "sharp", "sharply", "hard" -> if (left) TurnIcon.HEAVY_LEFT else TurnIcon.HEAVY_RIGHT
            "slight", "slightly", "soft", "bear" -> if (left) TurnIcon.LIGHT_LEFT else TurnIcon.LIGHT_RIGHT
            else -> if (left) TurnIcon.QUITE_LEFT else TurnIcon.QUITE_RIGHT
        }
    }

    private val FIRST_SIDE_RE = Regex("""\b(left|right)\b""")
    private val EXPLICIT_TURN_RE =
        Regex("""(?<![-\w])(turn|sharp|sharply|hard|slight|slightly|soft|bear)\s+(?:turn\s+)?(left|right)\b""")

    /**
     * The one roundabout rule shared by every path (SDK, notification text,
     * TFLite classifier): a roundabout exit at [turnAngleDeg] (right = +, left
     * = -, straight = 0) becomes the angle-based section glyph, and a U-turn
     * (|angle| >= 180) becomes the plain U-turn arrow (product decision) in the
     * direction of circulation.
     */
    fun roundaboutIcon(turnAngleDeg: Int, clockwise: Boolean): TurnIcon =
        if (kotlin.math.abs(turnAngleDeg) >= 180) {
            if (clockwise) TurnIcon.UTURN_RIGHT else TurnIcon.UTURN_LEFT
        } else {
            roundaboutSection(turnAngleDeg, clockwise)
        }

    /**
     * Best-effort exit ANGLE from an ordinal exit count ("take the 2nd exit"),
     * for when the nav app gives only the ordinal. Assumes a regular 4-arm
     * roundabout: clockwise circulation (left-hand traffic) meets the left arm
     * first, so 1st = left, 2nd = straight, 3rd = right, 4th+ = U-turn;
     * counter-clockwise is the mirror. An approximation - the icon classifier
     * (which sees the real exit angle) is preferred whenever it is confident.
     */
    fun ordinalExitAngle(exit: Int, clockwise: Boolean): Int = when {
        exit <= 1 -> if (clockwise) -90 else 90
        exit == 2 -> 0
        exit == 3 -> if (clockwise) 90 else -90
        else -> 180
    }

    /** True for any roundabout section glyph (either circulation bank). */
    fun isRoundaboutSection(icon: TurnIcon): Boolean = icon.binary in 26..57

    /**
     * Signed turn angle of a roundabout section glyph (inverse of
     * [roundaboutSection]): RH N -> (8 - N) * 22.5, LH N -> (N - 8) * 22.5.
     * Null for non-roundabout icons.
     */
    fun roundaboutSectionAngle(icon: TurnIcon): Double? {
        val b = icon.binary
        return when (b) {
            in 26..41 -> (8 - (b - 25)) * 22.5
            in 42..57 -> ((b - 41) - 8) * 22.5
            else -> null
        }
    }

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
