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

    @Test fun roundaboutBankFollowsDrivingSide() {
        // Left-hand traffic circulates clockwise -> RH glyph bank.
        assertEquals(TurnIcon.RAB_SECT_3_RH, parse("At the roundabout, take the 3rd exit", DrivingSide.LEFT))
        assertEquals(TurnIcon.RAB_SECT_3_LH, parse("At the roundabout, take the 3rd exit", DrivingSide.RIGHT))
        // No ordinal -> default section, still on the correct bank.
        assertEquals(TurnIcon.RAB_SECT_2_RH, parse("Enter the roundabout", DrivingSide.LEFT))
        assertEquals(TurnIcon.RAB_SECT_2_LH, parse("Enter the roundabout", DrivingSide.RIGHT))
    }

    // --- Non-highway maneuvers unaffected by driving side ---

    @Test fun ordinaryTurnsUnaffected() {
        assertEquals(TurnIcon.HEAVY_LEFT, parse("Sharp left", DrivingSide.RIGHT))
        assertEquals(TurnIcon.QUITE_RIGHT, parse("Turn right", DrivingSide.LEFT))
        assertEquals(TurnIcon.UTURN_LEFT, parse("Make a U-turn", DrivingSide.LEFT))
        assertEquals(TurnIcon.GO_STRAIGHT, parse("Continue straight", DrivingSide.LEFT))
    }
}
