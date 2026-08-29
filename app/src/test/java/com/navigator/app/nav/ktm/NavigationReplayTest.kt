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

/**
 * Record -> replay -> inspect: feed a recorded sequence of normalised states
 * through the encoder and assert the exact ordered dash writes. This is the
 * end-to-end check of the reliability core.
 */
class NavigationReplayTest {

    private val utc = ZoneId.of("UTC")

    private fun enroute(
        t: Long,
        maneuver: NormalizedManeuver,
        dist: Int,
        road: String,
        remSec: Int,
        remDist: Int,
    ) = NormalizedNavigationState(
        sessionState = NavSessionState.ENROUTE,
        maneuver = maneuver,
        distanceToManeuverMeters = dist,
        roadName = road,
        remainingTimeSeconds = remSec,
        remainingDistanceMeters = remDist,
        units = DistanceUnits.METRIC,
        producedAtMs = t,
    )

    @Test fun shortRoute_departTurnArrive() {
        val enc = KtmNavigationEncoder(zone = utc, minIntervalMs = 1000)

        val sequence = listOf(
            enroute(0, NormalizedManeuver.DEPART, 500, "Start Rd", 600, 5000),
            enroute(2000, NormalizedManeuver.LEFT, 200, "Main St", 500, 4000),
            enroute(4000, NormalizedManeuver.LEFT, 50, "Main St", 480, 3900),
            NormalizedNavigationState(NavSessionState.ARRIVED, producedAtMs = 6000),
        )

        val writes = sequence.flatMap { enc.encode(it) }

        assertEquals(
            listOf(
                // Depart
                DashWrite.SetNavState(guidanceOn = true),
                DashWrite.TurnIcon(TurnIcon.START),
                DashWrite.TurnRoad("Start Rd"),
                DashWrite.TurnDistance("500 m"),
                DashWrite.Eta("00:10"),
                DashWrite.RemainingDistance("5.0 km"),
                // Approaching the left
                DashWrite.TurnIcon(TurnIcon.QUITE_LEFT),
                DashWrite.TurnRoad("Main St"),
                DashWrite.TurnDistance("200 m"),
                DashWrite.Eta("00:08"),
                DashWrite.RemainingDistance("4.0 km"),
                // Closer to the left (icon/road/eta unchanged)
                DashWrite.TurnDistance("50 m"),
                DashWrite.RemainingDistance("3.9 km"),
                // Arrival
                DashWrite.TurnIcon(TurnIcon.END),
                DashWrite.ClearGuidance(immediate = false),
            ),
            writes,
        )
    }

    @Test fun navStateEmittedOnce_andClearsAtEnd() {
        val enc = KtmNavigationEncoder(zone = utc, minIntervalMs = 1000)
        val writes = listOf(
            enroute(0, NormalizedManeuver.DEPART, 500, "Start Rd", 600, 5000),
            enroute(2000, NormalizedManeuver.LEFT, 200, "Main St", 500, 4000),
            NormalizedNavigationState(NavSessionState.STOPPED, producedAtMs = 3000),
        ).flatMap { enc.encode(it) }

        assertEquals(1, writes.count { it is DashWrite.SetNavState })
        assertTrue(writes.last() is DashWrite.ClearGuidance)
        assertEquals(DashWrite.ClearGuidance(immediate = true), writes.last())
    }
}
