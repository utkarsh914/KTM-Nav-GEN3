package com.navigator.app.nav.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemainingStopsTest {
    // ~1.1 km apart along a meridian
    private val a = Stop(12.00, 77.0, "A")
    private val b = Stop(12.01, 77.0, "B")
    private val c = Stop(12.02, 77.0, "C")
    private val dest = 12.03 to 77.0

    private fun r(lat: Double, lng: Double = 77.0) = RemainingStops.resolve(listOf(a, b, c), dest.first, dest.second, lat, lng)

    @Test fun headingToTheFirstStopKeepsAll() = assertEquals(listOf(a, b, c), r(12.0))
    @Test fun passedStopsAreDropped() = assertEquals(listOf(b, c), r(12.01))
    @Test fun headingToTheLastStop() = assertEquals(listOf(c), r(12.02))
    @Test fun headingToTheDestinationLeavesNone() = assertEquals(emptyList<Stop>(), r(12.03))
    @Test fun snappedWaypointStillMatches() = assertEquals(listOf(b, c), r(12.0102))
    @Test fun noMatchMeansUnknown() = assertNull(r(12.005))
    @Test fun noStopsAlwaysEmpty() =
        assertEquals(emptyList<Stop>(), RemainingStops.resolve(emptyList(), 1.0, 1.0, 1.0, 1.0))
    @Test fun twoCloseWaypointsPickTheNearest() {
        val x = Stop(12.0000, 77.0); val y = Stop(12.0010, 77.0)
        assertEquals(listOf(y), RemainingStops.resolve(listOf(x, y), 13.0, 77.0, 12.0009, 77.0))
    }

    @Test fun aStopAtTheDestinationErrsTowardVisitingItAgain() {
        // Loop trip: the first stop is where the trip ends. Never drop a stop that may be unvisited.
        val loop = Stop(12.03, 77.0)
        assertEquals(listOf(loop), RemainingStops.resolve(listOf(loop), 12.03, 77.0, 12.03, 77.0))
    }
}
