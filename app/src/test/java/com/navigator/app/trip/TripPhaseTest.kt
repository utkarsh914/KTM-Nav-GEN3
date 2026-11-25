package com.navigator.app.trip

import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.providers.StartFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPhaseTest {
    private val dest = NavDestination(13.0, 77.5, "Toit")
    private val running = TripState(dest = dest)

    private fun phase(trip: TripState, session: NavSessionState, failure: StartFailure? = null) =
        TripPhasePolicy.of(trip, session, failure)

    @Test fun tableOfPhases() {
        assertEquals(TripPhase.NONE, phase(TripState(), NavSessionState.ENROUTE))
        assertEquals(TripPhase.STARTING, phase(running, NavSessionState.IDLE))
        assertEquals(TripPhase.GUIDING, phase(running, NavSessionState.ENROUTE))
        assertEquals("rerouting is still guiding", TripPhase.GUIDING, phase(running, NavSessionState.REROUTING))
        assertEquals(TripPhase.UPDATING, phase(running.copy(guided = true), NavSessionState.IDLE))
        assertEquals(TripPhase.UPDATING, phase(running.copy(guided = true), NavSessionState.STOPPED))
        assertEquals(TripPhase.FAILED, phase(running, NavSessionState.IDLE, StartFailure.ROUTE))
        assertEquals(TripPhase.FAILED, phase(running.copy(guided = true), NavSessionState.IDLE, StartFailure.ROUTE))
        assertEquals(TripPhase.NONE, phase(running.copy(arrivalLatched = true), NavSessionState.ENROUTE))
        val finished = running.copy(arrivalLatched = true, finished = FinishedTrip(com.navigator.app.nav.destination.SavedPlace(1.0, 2.0, "x"), 5))
        assertEquals(TripPhase.FINISHED, phase(finished, NavSessionState.STOPPED))
    }

    @Test fun onlyStartingGuidingAndUpdatingAreLive() {
        assertEquals(setOf(TripPhase.STARTING, TripPhase.GUIDING, TripPhase.UPDATING), TripPhase.entries.filter { it.isLive }.toSet())
        assertFalse(TripPhase.FAILED.isLive)
        assertTrue(TripPhase.GUIDING.isLive)
    }
}
