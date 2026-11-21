package com.navigator.app.trip

import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPoliciesTest {
    private fun stop(i: Int) = Stop(12.0 + i / 100.0, 77.0, "S$i")
    private val dest = NavDestination(13.0, 77.5, "Toit", routeToken = "tok", stops = listOf(stop(1), stop(2)))

    private fun check(
        hasTrip: Boolean = true, active: Boolean = true, latched: Boolean = false, online: Boolean = true,
        lastAdd: Long = 0L, now: Long = 100_000L, forSearch: Boolean = false, ahead: List<Stop>? = emptyList(),
    ) = TripPolicies.checkAddStop(hasTrip, active, latched, online, lastAdd, now, forSearch) { ahead }

    private fun problem(c: StopAddCheck) = (c as StopAddCheck.Rejected).problem

    // ---- add-stop validation (same order as the screen used) ----

    @Test fun anIdleTripCannotTakeAStop() {
        assertEquals(StopAddProblem.NOT_NAVIGATING, problem(check(hasTrip = false)))
        assertEquals(StopAddProblem.NOT_NAVIGATING, problem(check(active = false, forSearch = true)))
        assertEquals("picked after the trip ended", StopAddProblem.ENDED, problem(check(active = false)))
        assertEquals(StopAddProblem.ENDED, problem(check(latched = true)))
    }

    @Test fun everyStopChangeNeedsAConnection() {
        assertEquals(StopAddProblem.OFFLINE, problem(check(online = false, forSearch = true)))
        assertEquals("pick, remove and add-as-stop re-plan too", StopAddProblem.OFFLINE, problem(check(online = false, forSearch = false)))
        assertEquals("an ended trip is still reported as ended", StopAddProblem.ENDED, problem(check(online = false, latched = true)))
    }

    @Test fun aSecondStopWaitsForTheRouteToSettle() {
        assertEquals(StopAddProblem.SETTLING, problem(check(lastAdd = 100_000L - STOP_ADD_SETTLE_MS + 1)))
        assertTrue(check(lastAdd = 100_000L - STOP_ADD_SETTLE_MS) is StopAddCheck.Ok)
    }

    @Test fun refusesWhenTheNextStopIsUnknownOrTheTripIsFull() {
        assertEquals(StopAddProblem.UNKNOWN_NEXT, problem(check(ahead = null)))
        assertEquals(StopAddProblem.MAX_STOPS, problem(check(ahead = (1..MAX_STOPS).map(::stop))))
        val ok = check(ahead = (1 until MAX_STOPS).map(::stop)) as StopAddCheck.Ok
        assertEquals(MAX_STOPS - 1, ok.ahead.size)
    }

    @Test fun theSdkIsOnlyAskedWhenEverythingElseAllows() {
        var asked = 0
        TripPolicies.checkAddStop(true, false, false, true, 0, 100_000, false) { asked++; emptyList() }
        TripPolicies.checkAddStop(true, true, false, true, 99_999, 100_000, false) { asked++; emptyList() }
        assertEquals(0, asked)
    }

    // ---- re-plan ----

    @Test fun addingAStopReplansViaItThenThroughTheStopsAhead() {
        val ahead = listOf(stop(2))
        val plan = TripPolicies.planAddStop(dest, dest.stops, ahead, stop(9))
        assertEquals("the trip the rider had, without passed stops", dest.copy(stops = ahead), plan.before)
        assertEquals(listOf(stop(9), stop(2)), plan.replan.stops)
        assertNull("old token is for the old waypoints", plan.replan.routeToken)
        assertTrue("same ride keeps recording", plan.replan.continuesRide)
        assertEquals("passed stops stay for Ride back", listOf(stop(1), stop(9), stop(2)), plan.allStops)
    }

    @Test fun addingToATripWithNoStopsAheadAppends() {
        val plan = TripPolicies.planAddStop(dest.copy(stops = emptyList()), emptyList(), emptyList(), stop(5))
        assertEquals(listOf(stop(5)), plan.replan.stops)
        assertEquals(listOf(stop(5)), plan.allStops)
    }

    // ---- ride back ----

    @Test fun rideBackStartsFromTheFirstRecordedPointElseTheTripOrigin() {
        val stops = listOf(stop(1), stop(2))
        assertEquals(1.0 to 2.0, TripPolicies.rideBack(1.0 to 2.0, 5.0 to 6.0, stops)!!.origin)
        assertEquals(5.0 to 6.0, TripPolicies.rideBack(null, 5.0 to 6.0, stops)!!.origin)
        assertEquals(stops, TripPolicies.rideBack(null, 5.0 to 6.0, stops)!!.tripStops)
        assertNull(TripPolicies.rideBack(null, null, stops))
    }

    // ---- arrival: 2 minutes within 20 m ----

    private val inside = ArrivalPolicy.DWELL_RADIUS_M
    private val dwell = ArrivalPolicy.DWELL_MS

    @Test fun theClockStartsOnEnteringTheRadiusAndKeepsItWhileInside() {
        assertNull(ArrivalPolicy.nearSince(null, inside + 1, 5_000))
        assertEquals(5_000L, ArrivalPolicy.nearSince(null, inside, 5_000))
        assertEquals("still inside: the original time stands", 5_000L, ArrivalPolicy.nearSince(5_000, 3, 9_000))
        assertNull("left the circle: the clock restarts", ArrivalPolicy.nearSince(5_000, inside + 1, 9_000))
    }

    @Test fun twoMinutesInsideEndsItOneMillisecondLessDoesNot() {
        assertFalse(ArrivalPolicy.dwellReached(1_000, 1_000 + dwell - 1))
        assertTrue(ArrivalPolicy.dwellReached(1_000, 1_000 + dwell))
        assertFalse(ArrivalPolicy.dwellReached(null, 10 * dwell))
    }

    @Test fun onlyAnOwnedActiveTripTracksTheDwell() {
        assertNull(TripPolicies.nearSince(false, true, 5, 1_000, 2_000))
        assertNull(TripPolicies.nearSince(true, false, 5, 1_000, 2_000))
        assertEquals("a frame without a distance changes nothing", 1_000L, TripPolicies.nearSince(true, true, null, 1_000, 2_000))
        assertEquals(2_000L, TripPolicies.nearSince(true, true, 5, null, 2_000))
        assertFalse(TripPolicies.dwellDone(false, true, 0, dwell))
        assertFalse(TripPolicies.dwellDone(true, false, 0, dwell))
        assertTrue(TripPolicies.dwellDone(true, true, 0, dwell))
    }
}
