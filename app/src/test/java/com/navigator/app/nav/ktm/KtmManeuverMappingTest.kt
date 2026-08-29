package com.navigator.app.nav.ktm

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.DrivingSide
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedManeuver.*
import com.navigator.app.nav.model.RoundaboutRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class KtmManeuverMappingTest {

    private fun map(
        m: NormalizedManeuver,
        rotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
        exit: Int? = null,
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ) = KtmManeuverMapping.toTurnIcon(m, rotation, exit, drivingSide)

    @Test fun basicTurns() {
        assertEquals(TurnIcon.START, map(DEPART))
        assertEquals(TurnIcon.GO_STRAIGHT, map(STRAIGHT))
        assertEquals(TurnIcon.GO_STRAIGHT, map(NAME_CHANGE))
        assertEquals(TurnIcon.LIGHT_LEFT, map(SLIGHT_LEFT))
        assertEquals(TurnIcon.QUITE_LEFT, map(LEFT))
        assertEquals(TurnIcon.HEAVY_LEFT, map(SHARP_LEFT))
        assertEquals(TurnIcon.LIGHT_RIGHT, map(SLIGHT_RIGHT))
        assertEquals(TurnIcon.QUITE_RIGHT, map(RIGHT))
        assertEquals(TurnIcon.HEAVY_RIGHT, map(SHARP_RIGHT))
    }

    @Test fun keepsForksRampsMerges() {
        assertEquals(TurnIcon.KEEP_LEFT, map(KEEP_LEFT))
        assertEquals(TurnIcon.KEEP_RIGHT, map(FORK_RIGHT))
        assertEquals(TurnIcon.ENTER_HIGHWAY_LEFT_LANE, map(MERGE_LEFT))
        assertEquals(TurnIcon.ENTER_HIGHWAY_RIGHT_LANE, map(ON_RAMP_RIGHT))
        assertEquals(TurnIcon.ENTER_HIGHWAY_LEFT_LANE, map(ON_RAMP_SHARP_LEFT))
        assertEquals(TurnIcon.LEAVE_HIGHWAY_LEFT_LANE, map(OFF_RAMP_LEFT))
        assertEquals(TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE, map(OFF_RAMP_RIGHT))
        assertEquals(TurnIcon.GO_STRAIGHT, map(MERGE_UNSPECIFIED))
        assertEquals(TurnIcon.GO_STRAIGHT, map(OFF_RAMP_UNSPECIFIED))
    }

    @Test fun uTurnsDestinationsFerries() {
        assertEquals(TurnIcon.UTURN_LEFT, map(UTURN_LEFT))
        assertEquals(TurnIcon.UTURN_RIGHT, map(UTURN_RIGHT))
        assertEquals(TurnIcon.END, map(DESTINATION))
        assertEquals(TurnIcon.END, map(DESTINATION_LEFT))
        assertEquals(TurnIcon.END, map(DESTINATION_RIGHT))
        assertEquals(TurnIcon.FERRY, map(FERRY_BOAT))
        assertEquals(TurnIcon.FERRY, map(FERRY_TRAIN))
    }

    @Test fun unknownNeverGuessesAnArrow() {
        assertEquals(TurnIcon.UNDEFINED, map(UNKNOWN))
    }

    @Test fun roundaboutWithExit_rotationSelectsRhLh() {
        assertEquals(TurnIcon.RAB_SECT_2_RH, map(ROUNDABOUT_RIGHT, RoundaboutRotation.CLOCKWISE, exit = 2))
        assertEquals(TurnIcon.RAB_SECT_2_LH, map(ROUNDABOUT_RIGHT, RoundaboutRotation.COUNTERCLOCKWISE, exit = 2))
        assertEquals(TurnIcon.RAB_SECT_1_RH, map(ROUNDABOUT_LEFT, RoundaboutRotation.CLOCKWISE, exit = 1))
    }

    @Test fun roundaboutExitClampedToSixteen() {
        assertEquals(TurnIcon.RAB_SECT_16_RH, map(ROUNDABOUT_RIGHT, RoundaboutRotation.CLOCKWISE, exit = 20))
    }

    @Test fun roundaboutRotationFromDrivingSideWhenUnknown() {
        // Left-hand traffic -> clockwise -> RH; right-hand traffic -> CCW -> LH.
        assertEquals(TurnIcon.RAB_SECT_3_RH, map(ROUNDABOUT_RIGHT, exit = 3, drivingSide = DrivingSide.LEFT))
        assertEquals(TurnIcon.RAB_SECT_3_LH, map(ROUNDABOUT_RIGHT, exit = 3, drivingSide = DrivingSide.RIGHT))
    }

    @Test fun roundaboutDefaultsToClockwiseRhWhenAllUnknown() {
        assertEquals(TurnIcon.RAB_SECT_2_RH, map(ROUNDABOUT_RIGHT, exit = 2))
    }

    @Test fun roundaboutWithoutExit_fallsBackToShape() {
        assertEquals(TurnIcon.QUITE_RIGHT, map(ROUNDABOUT_RIGHT))
        assertEquals(TurnIcon.HEAVY_LEFT, map(ROUNDABOUT_SHARP_LEFT))
        assertEquals(TurnIcon.LIGHT_RIGHT, map(ROUNDABOUT_SLIGHT_RIGHT))
        assertEquals(TurnIcon.GO_STRAIGHT, map(ROUNDABOUT_STRAIGHT))
        assertEquals(TurnIcon.UTURN_RIGHT, map(ROUNDABOUT_UTURN, RoundaboutRotation.CLOCKWISE))
        assertEquals(TurnIcon.UTURN_LEFT, map(ROUNDABOUT_UTURN, RoundaboutRotation.COUNTERCLOCKWISE))
        assertEquals(TurnIcon.UNDEFINED, map(ROUNDABOUT_GENERIC))
    }

    @Test fun roundaboutStraightWithExitStillUsesSection() {
        // A known exit is the richest representation regardless of shape.
        assertEquals(TurnIcon.RAB_SECT_3_RH, map(ROUNDABOUT_STRAIGHT, RoundaboutRotation.CLOCKWISE, exit = 3))
    }

    @Test fun everyManeuverMapsWithoutThrowing() {
        // Exhaustiveness backstop: no maneuver should be unmapped.
        for (mn in NormalizedManeuver.entries) {
            val icon = map(mn)
            // Only UNKNOWN / bare ROUNDABOUT_GENERIC may resolve to UNDEFINED.
            if (mn != UNKNOWN && mn != ROUNDABOUT_GENERIC) {
                assertEquals("$mn should not be UNDEFINED", false, icon == TurnIcon.UNDEFINED)
            }
        }
    }
}
