package com.navigator.app.notifications

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.DrivingSide
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the notification-mirroring text path's driving-side handling: an
 * unqualified merge/exit/roundabout must follow the rider's driving side
 * (left-hand traffic = India), not a fixed right-hand default.
 */
class TurnIconHeuristicTest {

    private fun parse(text: String, side: DrivingSide = DrivingSide.UNKNOWN) =
        TurnIconHeuristic.parseManeuverFromText(text.lowercase(), side)

    // --- Explicit side always wins, regardless of driving side ---

    @Test fun explicitSideWinsForMerge() {
        assertEquals(TurnIcon.ENTER_HIGHWAY_LEFT_LANE, parse("Merge left onto NH-48", DrivingSide.RIGHT))
        assertEquals(TurnIcon.ENTER_HIGHWAY_RIGHT_LANE, parse("Merge right onto I-5", DrivingSide.LEFT))
    }

    @Test fun explicitSideWinsForExit() {
        assertEquals(TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE, parse("Take exit right", DrivingSide.LEFT))
        assertEquals(TurnIcon.LEAVE_HIGHWAY_LEFT_LANE, parse("Exit left", DrivingSide.RIGHT))
    }

    // --- Unqualified merge follows driving side ---

    @Test fun unqualifiedMergeFollowsDrivingSide() {
        assertEquals(TurnIcon.ENTER_HIGHWAY_LEFT_LANE, parse("Merge onto NH-48", DrivingSide.LEFT))
        assertEquals(TurnIcon.ENTER_HIGHWAY_RIGHT_LANE, parse("Merge onto I-5", DrivingSide.RIGHT))
        // Unknown defaults to left-hand traffic (the plan default).
        assertEquals(TurnIcon.ENTER_HIGHWAY_LEFT_LANE, parse("Merge onto the highway", DrivingSide.UNKNOWN))
    }

    // --- Unqualified exit/ramp now resolves (previously fell through) ---

    @Test fun unqualifiedExitFollowsDrivingSide() {
        assertEquals(TurnIcon.LEAVE_HIGHWAY_LEFT_LANE, parse("Take the exit", DrivingSide.LEFT))
        assertEquals(TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE, parse("Take the exit", DrivingSide.RIGHT))
        assertEquals(TurnIcon.LEAVE_HIGHWAY_LEFT_LANE, parse("Take the off-ramp", DrivingSide.LEFT))
    }

    // --- Turning onto an on-ramp is a turn, not a highway enter/exit ---

    @Test fun turnOntoRampShowsTheTurn() {
        // Regression (NH 48): must agree with the SDK path's ON_RAMP_* mapping.
        assertEquals(TurnIcon.HEAVY_RIGHT, parse("Sharp right onto the ramp to NH 48", DrivingSide.LEFT))
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right onto the ramp to NH 48", DrivingSide.LEFT))
        assertEquals(TurnIcon.QUITE_LEFT, parse("Turn left onto the ramp to NH 48", DrivingSide.RIGHT))
        assertEquals(TurnIcon.LIGHT_LEFT, parse("Slight left onto the ramp to NH 48", DrivingSide.LEFT))
        // A real exit is still an exit.
        assertEquals(TurnIcon.LEAVE_HIGHWAY_LEFT_LANE, parse("Take the exit on the left", DrivingSide.LEFT))
    }

    // --- Roundabout bank follows driving side ---

    @Test fun roundaboutOrdinalBecomesApproxAngle_clockwise() {
        // Left-hand traffic circulates clockwise -> RH bank; 1st exit = left.
        // Sections are exit-ANGLE glyphs (N = 8 - angle/22.5), never the ordinal.
        val s = DrivingSide.LEFT
        assertEquals(TurnIcon.RAB_SECT_12_RH, parse("At the roundabout, take the 1st exit", s)) // -90
        assertEquals(TurnIcon.RAB_SECT_8_RH, parse("At the roundabout, take the 2nd exit", s))  //   0
        assertEquals(TurnIcon.RAB_SECT_4_RH, parse("At the roundabout, take the 3rd exit", s))  // +90
        assertEquals(TurnIcon.UTURN_RIGHT, parse("At the roundabout, take the 4th exit", s))
        assertEquals(TurnIcon.RAB_SECT_8_RH, parse("At the roundabout, take the second exit", s))
    }

    @Test fun roundaboutOrdinalBecomesApproxAngle_counterClockwise() {
        // Right-hand traffic: CCW -> LH bank (N = 8 + angle/22.5); 1st exit = right.
        val s = DrivingSide.RIGHT
        assertEquals(TurnIcon.RAB_SECT_12_LH, parse("At the roundabout, take the 1st exit", s)) // +90
        assertEquals(TurnIcon.RAB_SECT_8_LH, parse("At the roundabout, take the 2nd exit", s))
        assertEquals(TurnIcon.RAB_SECT_4_LH, parse("At the roundabout, take the 3rd exit", s))  // -90
        assertEquals(TurnIcon.UTURN_LEFT, parse("At the roundabout, take the 5th exit", s))
    }

    @Test fun roundaboutWithoutOrdinalHasNoArrow() {
        assertEquals(TurnIcon.UNDEFINED, parse("Enter the roundabout", DrivingSide.LEFT))
        assertEquals(TurnIcon.UNDEFINED, parse("Enter the roundabout", DrivingSide.RIGHT))
    }

    @Test fun rotaryAsPlaceNameIsNotARoundabout() {
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right onto Rotary Club Rd", DrivingSide.LEFT))
        // But the US maneuver word still counts.
        assertEquals(TurnIcon.RAB_SECT_8_RH, parse("At the rotary, take the 2nd exit", DrivingSide.LEFT))
        // And a real roundabout onto a Rotary-named road still counts.
        assertEquals(
            TurnIcon.RAB_SECT_12_RH,
            parse("At the roundabout, take the 1st exit onto Rotary Club Rd", DrivingSide.LEFT),
        )
    }

    // --- Non-highway maneuvers unaffected by driving side ---

    @Test fun ordinaryTurnsUnaffected() {
        assertEquals(TurnIcon.HEAVY_LEFT, parse("Sharp left", DrivingSide.RIGHT))
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right", DrivingSide.LEFT))
        assertEquals(TurnIcon.UTURN_RIGHT, parse("Make a U-turn", DrivingSide.LEFT))
        assertEquals(TurnIcon.GO_STRAIGHT, parse("Continue straight", DrivingSide.LEFT))
    }

    // --- Whole-word matching / phrase precedence ---

    @Test fun placeNamesDoNotTriggerManeuvers() {
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right towards Rampura", DrivingSide.LEFT))
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right onto Leftwich Rd", DrivingSide.LEFT))
        assertEquals(TurnIcon.QUITE_LEFT, parse("Turn left onto Hardware Rd", DrivingSide.LEFT))
        // First side word is the instruction; a later one is the road name.
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right onto Left Bank Rd", DrivingSide.LEFT))
    }

    @Test fun turnToStayOnIsATurnNotAKeep() {
        assertEquals(TurnIcon.QUITE_LEFT, parse("Turn left to stay on NH 48", DrivingSide.LEFT))
        assertEquals(TurnIcon.KEEP_RIGHT, parse("Keep right to stay on NH 48", DrivingSide.LEFT))
        assertEquals(TurnIcon.LIGHT_LEFT, parse("Slight left to stay on NH 48", DrivingSide.LEFT))
    }

    @Test fun unqualifiedUTurnFollowsDrivingSide() {
        assertEquals(TurnIcon.UTURN_RIGHT, parse("Make a U-turn", DrivingSide.LEFT))
        assertEquals(TurnIcon.UTURN_LEFT, parse("Make a U-turn", DrivingSide.RIGHT))
        assertEquals(TurnIcon.UTURN_LEFT, parse("Make a U-turn to the left", DrivingSide.LEFT))
    }
}
