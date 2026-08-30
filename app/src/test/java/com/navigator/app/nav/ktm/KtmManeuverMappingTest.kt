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
        drivingSide: DrivingSide = DrivingSide.UNKNOWN,
    ) = KtmManeuverMapping.toTurnIcon(m, rotation, drivingSide)

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

    @Test fun roundaboutExitByAngle_rhBlock() {
        // RH (clockwise): section chosen by exit turn-angle, N = 8 - angle/22.5.
        // Hardware-verified: N1 sharpest right ... N8 straight ... N16 U-turn.
        val cw = RoundaboutRotation.CLOCKWISE
        assertEquals(TurnIcon.RAB_SECT_2_RH, map(ROUNDABOUT_SHARP_RIGHT, cw))  // +135 -> N2
        assertEquals(TurnIcon.RAB_SECT_4_RH, map(ROUNDABOUT_RIGHT, cw))        // +90  -> N4
        assertEquals(TurnIcon.RAB_SECT_6_RH, map(ROUNDABOUT_SLIGHT_RIGHT, cw)) // +45  -> N6
        assertEquals(TurnIcon.RAB_SECT_8_RH, map(ROUNDABOUT_STRAIGHT, cw))     //  0   -> N8
        assertEquals(TurnIcon.RAB_SECT_8_RH, map(ROUNDABOUT_EXIT, cw))         //  0   -> N8
        assertEquals(TurnIcon.RAB_SECT_10_RH, map(ROUNDABOUT_SLIGHT_LEFT, cw)) // -45  -> N10
        assertEquals(TurnIcon.RAB_SECT_12_RH, map(ROUNDABOUT_LEFT, cw))        // -90  -> N12
        assertEquals(TurnIcon.RAB_SECT_14_RH, map(ROUNDABOUT_SHARP_LEFT, cw))  // -135 -> N14
    }

    @Test fun roundaboutExitByAngle_lhBlockMirrored() {
        // LH (counter-clockwise): mirrored, N = 8 + angle/22.5.
        val ccw = RoundaboutRotation.COUNTERCLOCKWISE
        assertEquals(TurnIcon.RAB_SECT_14_LH, map(ROUNDABOUT_SHARP_RIGHT, ccw))
        assertEquals(TurnIcon.RAB_SECT_12_LH, map(ROUNDABOUT_RIGHT, ccw))
        assertEquals(TurnIcon.RAB_SECT_10_LH, map(ROUNDABOUT_SLIGHT_RIGHT, ccw))
        assertEquals(TurnIcon.RAB_SECT_8_LH, map(ROUNDABOUT_STRAIGHT, ccw))
        assertEquals(TurnIcon.RAB_SECT_6_LH, map(ROUNDABOUT_SLIGHT_LEFT, ccw))
        assertEquals(TurnIcon.RAB_SECT_4_LH, map(ROUNDABOUT_LEFT, ccw))
        assertEquals(TurnIcon.RAB_SECT_2_LH, map(ROUNDABOUT_SHARP_LEFT, ccw))
    }

    @Test fun roundaboutRotationFromDrivingSideWhenUnknown() {
        // Left-hand traffic -> clockwise -> RH; right-hand traffic -> CCW -> LH.
        assertEquals(TurnIcon.RAB_SECT_4_RH, map(ROUNDABOUT_RIGHT, drivingSide = DrivingSide.LEFT))
        assertEquals(TurnIcon.RAB_SECT_12_LH, map(ROUNDABOUT_RIGHT, drivingSide = DrivingSide.RIGHT))
    }

    @Test fun roundaboutDefaultsToClockwiseRhWhenAllUnknown() {
        assertEquals(TurnIcon.RAB_SECT_4_RH, map(ROUNDABOUT_RIGHT))
    }

    @Test fun roundaboutUTurnIsPlainArrow() {
        // Product decision: a roundabout U-turn shows the plain U-turn arrow.
        assertEquals(TurnIcon.UTURN_RIGHT, map(ROUNDABOUT_UTURN, RoundaboutRotation.CLOCKWISE))
        assertEquals(TurnIcon.UTURN_LEFT, map(ROUNDABOUT_UTURN, RoundaboutRotation.COUNTERCLOCKWISE))
    }

    @Test fun roundaboutGenericHasNoArrow() {
        assertEquals(TurnIcon.UNDEFINED, map(ROUNDABOUT_GENERIC))
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
