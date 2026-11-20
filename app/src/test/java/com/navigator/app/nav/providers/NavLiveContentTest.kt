package com.navigator.app.nav.providers

import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLiveContentTest {

    private fun enroute(
        instr: String? = "Turn left onto MG Road", road: String? = "MG Road",
        toTurn: Int? = 220, remaining: Int? = 5200, secs: Int? = 600,
        state: NavSessionState = NavSessionState.ENROUTE,
    ) = NormalizedNavigationState(
        sessionState = state, fullInstruction = instr, roadName = road,
        distanceToManeuverMeters = toTurn, remainingDistanceMeters = remaining, remainingTimeSeconds = secs,
        units = DistanceUnits.METRIC,
    )

    @Test fun progressIsWholeRouteFraction() {
        assertEquals(3000 to 5000, NavLiveContentBuilder.progress(5000, 2000))
        assertEquals(0 to 5000, NavLiveContentBuilder.progress(5000, 5000))
        assertEquals(5000 to 5000, NavLiveContentBuilder.progress(5000, 0))
        assertEquals(0 to 5000, NavLiveContentBuilder.progress(5000, 9000)) // clamped after a longer reroute
        assertNull(NavLiveContentBuilder.progress(0, 100))
        assertNull(NavLiveContentBuilder.progress(5000, null))
    }

    @Test fun routeLengthTracksTheLargestRemainingAndResetsWhenIdle() {
        var total = 0
        total = NavLiveContentBuilder.trackTotal(total, enroute(remaining = 5000))
        total = NavLiveContentBuilder.trackTotal(total, enroute(remaining = 4000))
        assertEquals(5000, total)
        total = NavLiveContentBuilder.trackTotal(total, enroute(remaining = 7000, state = NavSessionState.REROUTING))
        assertEquals(7000, total)
        total = NavLiveContentBuilder.trackTotal(total, NormalizedNavigationState(NavSessionState.STOPPED))
        assertEquals(0, total)
    }

    @Test fun refreshOnlyWhileGuidanceIsLive() {
        assertTrue(NavLiveContentBuilder.shouldRefresh(enroute()))
        assertTrue(NavLiveContentBuilder.shouldRefresh(enroute(state = NavSessionState.REROUTING)))
        for (st in listOf(NavSessionState.IDLE, NavSessionState.STOPPED, NavSessionState.ARRIVED)) {
            assertEquals(false, NavLiveContentBuilder.shouldRefresh(enroute(state = st)))
        }
    }
}
