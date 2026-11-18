package com.navigator.app.notifications

import com.navigator.app.ble.BccuProtocol.TurnIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverTextTest {
    @Test fun highwayKeepAndMiddleAreNotMislabelledAsTurns() {
        assertEquals("Keep left onto", ManeuverText.forIcon(TurnIcon.HIGHWAY_KEEP_LEFT))
        assertEquals("Keep right onto", ManeuverText.forIcon(TurnIcon.HIGHWAY_KEEP_RIGHT))
        assertEquals("Continue straight onto", ManeuverText.forIcon(TurnIcon.KEEP_MIDDLE))
        assertEquals("Keep left onto", ManeuverText.forIcon(TurnIcon.KEEP_LEFT))
    }

    @Test fun familiesStayDistinct() {
        assertEquals("Make a U-turn onto", ManeuverText.forIcon(TurnIcon.UTURN_LEFT))
        assertEquals("Merge onto", ManeuverText.forIcon(TurnIcon.ENTER_HIGHWAY_LEFT_LANE))
        assertEquals("Take the exit onto", ManeuverText.forIcon(TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE))
        assertEquals("At the roundabout, exit onto", ManeuverText.forIcon(TurnIcon.RAB_SECT_3_RH))
        assertEquals("Turn left onto", ManeuverText.forIcon(TurnIcon.QUITE_LEFT))
        assertEquals("Turn right onto", ManeuverText.forIcon(TurnIcon.HEAVY_RIGHT))
        assertEquals("Continue onto", ManeuverText.forIcon(TurnIcon.GO_STRAIGHT))
        assertEquals(null, ManeuverText.forIcon(null))
    }
}
