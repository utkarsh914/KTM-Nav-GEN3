package com.navigator.app.nav.providers

import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.RoundaboutRotation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SDK `Maneuver` constants are compile-time int literals, so we test the map
 * by their documented values (7.9.0) without loading the SDK class. The 0..65
 * completeness sweep is the tripwire: if a future SDK bump adds a constant in
 * that range that we forgot to map, it surfaces as UNKNOWN here.
 */
class GoogleNavManeuverMapTest {

    // Documented Maneuver int values (Nav SDK 7.9.0).
    private companion object {
        const val UNKNOWN = 0
        const val DEPART = 1
        const val DESTINATION = 2
        const val STRAIGHT = 5
        const val TURN_LEFT = 6
        const val TURN_RIGHT = 7
        const val TURN_SHARP_LEFT = 12
        const val TURN_U_TURN_CLOCKWISE = 14
        const val TURN_U_TURN_COUNTERCLOCKWISE = 15
        const val ROUNDABOUT_RIGHT_CLOCKWISE = 49
        const val ROUNDABOUT_EXIT_COUNTERCLOCKWISE = 62
        const val FERRY_BOAT = 63
        const val NAME_CHANGE = 65
        const val MAX_KNOWN = 65
    }

    @Test fun representativeMappings() {
        assertEquals(NormalizedManeuver.DEPART, GoogleNavManeuverMap.toNormalized(DEPART).maneuver)
        assertEquals(NormalizedManeuver.DESTINATION, GoogleNavManeuverMap.toNormalized(DESTINATION).maneuver)
        assertEquals(NormalizedManeuver.STRAIGHT, GoogleNavManeuverMap.toNormalized(STRAIGHT).maneuver)
        assertEquals(NormalizedManeuver.LEFT, GoogleNavManeuverMap.toNormalized(TURN_LEFT).maneuver)
        assertEquals(NormalizedManeuver.RIGHT, GoogleNavManeuverMap.toNormalized(TURN_RIGHT).maneuver)
        assertEquals(NormalizedManeuver.SHARP_LEFT, GoogleNavManeuverMap.toNormalized(TURN_SHARP_LEFT).maneuver)
        assertEquals(NormalizedManeuver.NAME_CHANGE, GoogleNavManeuverMap.toNormalized(NAME_CHANGE).maneuver)
        assertEquals(NormalizedManeuver.FERRY_BOAT, GoogleNavManeuverMap.toNormalized(FERRY_BOAT).maneuver)
    }

    @Test fun uTurnRotationBecomesLeftRight() {
        val cw = GoogleNavManeuverMap.toNormalized(TURN_U_TURN_CLOCKWISE)
        assertEquals(NormalizedManeuver.UTURN_RIGHT, cw.maneuver)
        assertEquals(RoundaboutRotation.CLOCKWISE, cw.rotation)
        val ccw = GoogleNavManeuverMap.toNormalized(TURN_U_TURN_COUNTERCLOCKWISE)
        assertEquals(NormalizedManeuver.UTURN_LEFT, ccw.maneuver)
        assertEquals(RoundaboutRotation.COUNTERCLOCKWISE, ccw.rotation)
    }

    @Test fun roundaboutsCarryShapeAndRotation() {
        val r = GoogleNavManeuverMap.toNormalized(ROUNDABOUT_RIGHT_CLOCKWISE)
        assertEquals(NormalizedManeuver.ROUNDABOUT_RIGHT, r.maneuver)
        assertEquals(RoundaboutRotation.CLOCKWISE, r.rotation)
        val exit = GoogleNavManeuverMap.toNormalized(ROUNDABOUT_EXIT_COUNTERCLOCKWISE)
        assertEquals(NormalizedManeuver.ROUNDABOUT_EXIT, exit.maneuver)
        assertEquals(RoundaboutRotation.COUNTERCLOCKWISE, exit.rotation)
    }

    @Test fun unknownMapsToUnknown() {
        assertEquals(NormalizedManeuver.UNKNOWN, GoogleNavManeuverMap.toNormalized(UNKNOWN).maneuver)
    }

    @Test fun everyKnownConstantIsMapped() {
        // 1..65 must all resolve to a real maneuver; only 0 (UNKNOWN) may be UNKNOWN.
        for (v in 1..MAX_KNOWN) {
            assertEquals(
                "SDK Maneuver value $v is unmapped (resolved to UNKNOWN)",
                false,
                GoogleNavManeuverMap.toNormalized(v).maneuver == NormalizedManeuver.UNKNOWN,
            )
        }
    }
}
