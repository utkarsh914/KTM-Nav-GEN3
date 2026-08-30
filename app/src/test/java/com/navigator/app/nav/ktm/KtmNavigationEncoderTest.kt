package com.navigator.app.nav.ktm

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class KtmNavigationEncoderTest {

    private val utc = ZoneId.of("UTC")

    private fun encoder(minIntervalMs: Long = 0L) =
        KtmNavigationEncoder(zone = utc, minIntervalMs = minIntervalMs)

    private fun enroute(
        t: Long,
        maneuver: NormalizedManeuver = NormalizedManeuver.LEFT,
        dist: Int? = 100,
        road: String? = "Main St",
        remSec: Int? = 600,
        remDist: Int? = 5000,
        units: DistanceUnits = DistanceUnits.METRIC,
    ) = NormalizedNavigationState(
        sessionState = NavSessionState.ENROUTE,
        maneuver = maneuver,
        distanceToManeuverMeters = dist,
        roadName = road,
        remainingTimeSeconds = remSec,
        remainingDistanceMeters = remDist,
        units = units,
        producedAtMs = t,
    )

    @Test fun firstUpdate_emitsNavStateThenGuidance() {
        val enc = encoder()
        val w = enc.encode(enroute(0))
        assertEquals(
            listOf(
                DashWrite.SetNavState(guidanceOn = true),
                DashWrite.TurnIcon(TurnIcon.QUITE_LEFT),
                DashWrite.TurnRoad("Main St"),
                DashWrite.TurnDistance("100 m"),
                DashWrite.Eta("00:10"),
                DashWrite.RemainingDistance("5.0 km"),
            ),
            w,
        )
    }

    @Test fun duplicateUpdate_producesNoWrites() {
        val enc = encoder()
        enc.encode(enroute(0))
        val w = enc.encode(enroute(1000)) // identical values, ETA within deadband
        assertTrue("duplicate should produce no writes, got $w", w.isEmpty())
    }

    @Test fun onlyDistanceChanges_emitsOnlyDistance() {
        val enc = encoder()
        enc.encode(enroute(0))
        val w = enc.encode(enroute(2000, dist = 80))
        assertEquals(listOf(DashWrite.TurnDistance("80 m")), w)
    }

    @Test fun unknownManeuver_holdsLastIcon() {
        val enc = encoder()
        enc.encode(enroute(0, maneuver = NormalizedManeuver.LEFT))
        val w = enc.encode(enroute(2000, maneuver = NormalizedManeuver.UNKNOWN, dist = 50))
        assertEquals(listOf(DashWrite.TurnDistance("50 m")), w) // no icon write
    }

    @Test fun rerouting_blanksStaleTurn() {
        val enc = encoder()
        enc.encode(enroute(0))
        val w = enc.encode(
            NormalizedNavigationState(NavSessionState.REROUTING, producedAtMs = 2000),
        )
        assertEquals(
            listOf(DashWrite.TurnIcon(TurnIcon.UNDEFINED), DashWrite.TurnDistance("")),
            w,
        )
    }

    @Test fun enrouteAfterRerouting_recovers() {
        val enc = encoder()
        enc.encode(enroute(0))
        enc.encode(NormalizedNavigationState(NavSessionState.REROUTING, producedAtMs = 2000))
        val w = enc.encode(enroute(3000, maneuver = NormalizedManeuver.RIGHT, dist = 200))
        assertEquals(
            listOf(DashWrite.TurnIcon(TurnIcon.QUITE_RIGHT), DashWrite.TurnDistance("200 m")),
            w,
        )
    }

    @Test fun arrived_emitsEndThenDebouncedClear() {
        val enc = encoder()
        enc.encode(enroute(0))
        val w = enc.encode(NormalizedNavigationState(NavSessionState.ARRIVED, producedAtMs = 5000))
        assertEquals(
            listOf(DashWrite.TurnIcon(TurnIcon.END), DashWrite.ClearGuidance(immediate = false)),
            w,
        )
    }

    @Test fun arrived_isIdempotent() {
        val enc = encoder()
        enc.encode(enroute(0))
        enc.encode(NormalizedNavigationState(NavSessionState.ARRIVED, producedAtMs = 5000))
        val w = enc.encode(NormalizedNavigationState(NavSessionState.ARRIVED, producedAtMs = 6000))
        assertTrue(w.isEmpty())
    }

    @Test fun stopped_emitsImmediateClear() {
        val enc = encoder()
        enc.encode(enroute(0))
        val w = enc.encode(NormalizedNavigationState(NavSessionState.STOPPED, producedAtMs = 5000))
        assertEquals(listOf(DashWrite.ClearGuidance(immediate = true)), w)
    }

    @Test fun idleWhenAlreadyIdle_producesNothing() {
        val enc = encoder()
        val w = enc.encode(NormalizedNavigationState(NavSessionState.IDLE, producedAtMs = 0))
        assertTrue(w.isEmpty())
    }

    @Test fun navState_emittedOnlyOnce() {
        val enc = encoder()
        enc.encode(enroute(0))
        val w = enc.encode(enroute(2000, dist = 80))
        assertTrue("SetNavState must not repeat", w.none { it is DashWrite.SetNavState })
    }

    @Test fun throttle_dropsLabelOnlyChurnWithinWindow() {
        val enc = encoder(minIntervalMs = 1000)
        enc.encode(enroute(0))
        val throttled = enc.encode(enroute(500, dist = 80))
        assertTrue("within window, label change should be dropped: $throttled", throttled.isEmpty())
        val passed = enc.encode(enroute(1500, dist = 60))
        assertEquals(listOf(DashWrite.TurnDistance("60 m")), passed)
    }

    @Test fun throttle_neverBlocksIconChange() {
        val enc = encoder(minIntervalMs = 1000)
        enc.encode(enroute(0, maneuver = NormalizedManeuver.LEFT))
        val w = enc.encode(enroute(500, maneuver = NormalizedManeuver.RIGHT))
        assertEquals(listOf(DashWrite.TurnIcon(TurnIcon.QUITE_RIGHT)), w)
    }

    @Test fun imperialUnits_convertLabels() {
        val enc = encoder()
        val w = enc.encode(
            enroute(0, road = "Hwy", dist = 1609, remDist = 32187, units = DistanceUnits.IMPERIAL),
        )
        assertTrue(w.contains(DashWrite.TurnDistance("1.0 mi")))
        assertTrue(w.contains(DashWrite.RemainingDistance("20 mi")))
    }

    @Test fun passthroughOverrides_usedVerbatim() {
        // The notification provider supplies pre-formatted strings + a resolved
        // icon; the encoder should send them as-is (no formatting / mapping),
        // while still deduping and running the state machine.
        val enc = encoder()
        val s = NormalizedNavigationState(
            sessionState = NavSessionState.ENROUTE,
            resolvedIcon = TurnIcon.UTURN_RIGHT,
            preformattedDistance = "110 m",
            preformattedEta = "12:45",
            preformattedRemaining = "9.7 km",
            roadName = "1st Cross Rd",
            producedAtMs = 0,
        )
        assertEquals(
            listOf(
                DashWrite.SetNavState(guidanceOn = true),
                DashWrite.TurnIcon(TurnIcon.UTURN_RIGHT),
                DashWrite.TurnRoad("1st Cross Rd"),
                DashWrite.TurnDistance("110 m"),
                DashWrite.Eta("12:45"),
                DashWrite.RemainingDistance("9.7 km"),
            ),
            enc.encode(s),
        )
        // Same values again -> fully deduped.
        assertTrue(enc.encode(s.copy(producedAtMs = 2000)).isEmpty())
    }

    @Test fun passthroughOverrides_nullIconHoldsLast() {
        val enc = encoder()
        enc.encode(
            NormalizedNavigationState(
                sessionState = NavSessionState.ENROUTE,
                resolvedIcon = TurnIcon.QUITE_LEFT,
                preformattedDistance = "200 m",
                producedAtMs = 0,
            ),
        )
        // Icon unknown this tick (heuristic returned null) -> hold last icon.
        val w = enc.encode(
            NormalizedNavigationState(
                sessionState = NavSessionState.ENROUTE,
                resolvedIcon = null,
                preformattedDistance = "150 m",
                producedAtMs = 2000,
            ),
        )
        assertEquals(listOf(DashWrite.TurnDistance("150 m")), w)
    }

    @Test fun reset_reEmitsEverything() {
        val enc = encoder()
        enc.encode(enroute(0))
        enc.reset()
        val w = enc.encode(enroute(1000))
        assertTrue("first write after reset must be SetNavState", w.firstOrNull() is DashWrite.SetNavState)
        assertTrue(w.contains(DashWrite.TurnIcon(TurnIcon.QUITE_LEFT)))
        assertTrue(w.contains(DashWrite.TurnDistance("100 m")))
    }
}
