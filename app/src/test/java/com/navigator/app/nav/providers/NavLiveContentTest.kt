package com.navigator.app.nav.providers

import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class NavLiveContentTest {

    private val utc = ZoneOffset.UTC
    private val now = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC

    private fun enroute(
        instr: String? = "Turn left onto MG Road", road: String? = "MG Road",
        toTurn: Int? = 220, remaining: Int? = 5200, secs: Int? = 600,
        state: NavSessionState = NavSessionState.ENROUTE,
    ) = NormalizedNavigationState(
        sessionState = state, fullInstruction = instr, roadName = road,
        distanceToManeuverMeters = toTurn, remainingDistanceMeters = remaining, remainingTimeSeconds = secs,
        units = DistanceUnits.METRIC,
    )

    @Test fun enrouteShowsInstructionDistanceRemainingAndEta() {
        val c = NavLiveContentBuilder.build(enroute(), now, utc)
        assertEquals("Turn left onto MG Road", c.title)
        assertEquals("220 m \u00b7 5.2 km \u00b7 ETA 22:23", c.text)
        assertEquals("220 m", c.chip)
        assertEquals(now + 600_000, c.etaMs)
    }

    @Test fun fallsBackToRoadNameThenContinue() {
        assertEquals("MG Road", NavLiveContentBuilder.build(enroute(instr = null), now, utc).title)
        assertEquals("Continue", NavLiveContentBuilder.build(enroute(instr = " ", road = null), now, utc).title)
    }

    @Test fun chipFallsBackToEtaClockAndIsShort() {
        val c = NavLiveContentBuilder.build(enroute(toTurn = null), now, utc)
        assertEquals("22:23", c.chip)
        val long = NavLiveContentBuilder.build(enroute(toTurn = 123_456), now, utc)
        assertTrue(long.chip!!.length <= NavLiveContentBuilder.CHIP_MAX)
    }

    @Test fun reroutingAndPreStartStates() {
        val re = NavLiveContentBuilder.build(enroute(state = NavSessionState.REROUTING), now, utc)
        assertEquals("Rerouting\u2026", re.title)
        assertEquals("Reroute", re.chip)
        assertEquals("5.2 km \u00b7 ETA 22:23", re.text)
        val idle = NavLiveContentBuilder.build(NormalizedNavigationState(NavSessionState.IDLE), now, utc)
        assertEquals("Navigating", idle.title)
        assertEquals("Calculating route\u2026", idle.text)
        assertNull(idle.chip)
        assertNull(idle.etaMs)
    }

    @Test fun missingNumbersDegradeGracefully() {
        val c = NavLiveContentBuilder.build(enroute(toTurn = null, remaining = null, secs = null), now, utc)
        assertEquals("Guidance active", c.text)
        assertNull(c.chip)
    }

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

    @Test fun arrivedAndNegativeEtaDoNotCrashOrShowGarbage() {
        val c = NavLiveContentBuilder.build(enroute(state = NavSessionState.ARRIVED, secs = -5), now, utc)
        assertEquals("Navigating", c.title)
        assertNull(c.etaMs)
        assertNull(NavLiveContentBuilder.build(enroute(secs = -5), now, utc).etaMs)
    }
}
