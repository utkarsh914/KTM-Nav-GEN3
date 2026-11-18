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

    /** Fake monotonic clock driving the throttle. */
    private var now = 0L

    private fun encoder(minIntervalMs: Long = 0L) =
        KtmNavigationEncoder(zone = utc, minIntervalMs = minIntervalMs, clockMs = { now })

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

    @Test fun mergeWithTurnInstruction_emitsTheTurnIcon() {
        val enc = encoder()
        val state = enroute(0, maneuver = NormalizedManeuver.MERGE_UNSPECIFIED)
            .copy(fullInstruction = "Turn left onto the ramp to NE 7")
        val icons = enc.encode(state).filterIsInstance<DashWrite.TurnIcon>()
        assertEquals(listOf(DashWrite.TurnIcon(TurnIcon.QUITE_LEFT)), icons)
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

    @Test fun unknownManeuverOnNewStep_blanksArrow() {
        val enc = encoder()
        enc.encode(enroute(0, maneuver = NormalizedManeuver.LEFT))
        val w = enc.encode(enroute(2000, maneuver = NormalizedManeuver.UNKNOWN, road = "2nd Cross", dist = 300))
        assertEquals(
            listOf(DashWrite.TurnIcon(TurnIcon.UNDEFINED), DashWrite.TurnRoad("2nd Cross"), DashWrite.TurnDistance("300 m")),
            w,
        )
        // Recognised again -> arrow comes back.
        val back = enc.encode(enroute(3000, maneuver = NormalizedManeuver.RIGHT, road = "2nd Cross", dist = 300))
        assertEquals(listOf(DashWrite.TurnIcon(TurnIcon.QUITE_RIGHT)), back)
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
        now = 0; enc.encode(enroute(0))
        now = 500
        val throttled = enc.encode(enroute(500, dist = 80))
        assertTrue("within window, label change should be dropped: $throttled", throttled.isEmpty())
        assertTrue("held-back change must be flagged for a trailing flush", enc.hasThrottledChanges)
        now = 1500
        val passed = enc.encode(enroute(1500, dist = 60))
        assertEquals(listOf(DashWrite.TurnDistance("60 m")), passed)
        assertTrue(!enc.hasThrottledChanges)
    }

    @Test fun throttle_trailingFlushSendsLastValue() {
        val enc = encoder(minIntervalMs = 1000)
        now = 0; enc.encode(enroute(0))
        now = 400; val s = enroute(400, dist = 20)
        assertTrue(enc.encode(s).isEmpty())
        now = 1400 // coordinator re-encodes the same latest snapshot after the window
        assertEquals(listOf(DashWrite.TurnDistance("20 m")), enc.encode(s))
    }

    @Test fun throttle_neverSplitsNewTurnFromItsDistance() {
        val enc = encoder(minIntervalMs = 1000)
        now = 0; enc.encode(enroute(0, maneuver = NormalizedManeuver.LEFT, dist = 0))
        now = 500
        val w = enc.encode(enroute(500, maneuver = NormalizedManeuver.RIGHT, dist = 400))
        assertEquals(listOf(DashWrite.TurnIcon(TurnIcon.QUITE_RIGHT), DashWrite.TurnDistance("400 m")), w)
    }

    @Test fun throttle_usesMonotonicClockNotWallClock() {
        val enc = encoder(minIntervalMs = 1000)
        now = 0; enc.encode(enroute(1_000_000))
        // Wall clock jumps backwards by an hour; monotonic clock moves forward.
        now = 2000
        val w = enc.encode(enroute(1_000_000 - 3_600_000, dist = 50))
        assertTrue(w.contains(DashWrite.TurnDistance("50 m")))
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

    // ---- Offline reflection (gpsIconOn) ----------------------------------

    @Test fun offlineFirstUpdate_setsGpsIconOff() {
        val enc = encoder()
        val w = enc.encode(enroute(0).copy(offline = true))
        assertEquals(DashWrite.SetNavState(guidanceOn = true, gpsIconOn = false), w.first())
    }

    @Test fun goingOffline_emitsNavStateWithGpsIconOff() {
        val enc = encoder()
        enc.encode(enroute(0)) // online first
        val w = enc.encode(enroute(1000).copy(offline = true))
        assertTrue(
            "expected a SetNavState with gpsIconOn=false, got $w",
            w.contains(DashWrite.SetNavState(guidanceOn = true, gpsIconOn = false)),
        )
    }

    @Test fun comingBackOnline_restoresGpsIcon() {
        val enc = encoder()
        enc.encode(enroute(0))
        enc.encode(enroute(1000).copy(offline = true))
        val w = enc.encode(enroute(2000).copy(offline = false))
        assertTrue(
            "expected a SetNavState with gpsIconOn=true, got $w",
            w.contains(DashWrite.SetNavState(guidanceOn = true, gpsIconOn = true)),
        )
    }

    @Test fun offlineUnchanged_producesNoNavStateChurn() {
        val enc = encoder()
        enc.encode(enroute(0).copy(offline = true))
        // Same values, still offline, ETA within deadband -> no writes at all.
        val w = enc.encode(enroute(1000).copy(offline = true))
        assertTrue("steady offline should not re-emit SetNavState, got $w", w.isEmpty())
    }
}
