package com.navigator.app.trip

import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.Stop
import com.navigator.app.nav.providers.StartFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGuidance : GuidanceSource {
    override val state = MutableStateFlow(NormalizedNavigationState(NavSessionState.IDLE))
    override val startFailure = MutableStateFlow<StartFailure?>(null)
    val arrival = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val arrivals: Flow<Unit> = arrival
    var target: Pair<Double, Double>? = null
    val started = mutableListOf<NavDestination>()
    var stops = 0

    override fun currentTarget() = target
    override fun clearStartFailure() { startFailure.value = null }
    override fun start(dest: NavDestination) { started += dest }
    override fun stop() { stops++ }

    fun enroute(remaining: Int? = 5_000) {
        state.value = NormalizedNavigationState(NavSessionState.ENROUTE, remainingDistanceMeters = remaining)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerTest {
    private val dest = NavDestination(13.0, 77.5, "Toit")

    private class Env(scope: CoroutineScope) {
        val guidance = FakeGuidance()
        val online = MutableStateFlow(true)
        var now = 1_000_000L
        val controller = TripController(scope, guidance, online, clock = { now })
    }

    @Test fun startHandsTheDestinationToGuidanceAndRecordsTheTrip() = runTest {
        val e = Env(backgroundScope)
        assertTrue(e.controller.start(dest, 1.0 to 2.0, listOf(1.0 to 1.0)))
        val s = e.controller.state.value
        assertEquals(dest, s.dest)
        assertEquals(1.0 to 2.0, s.origin)
        assertFalse(s.arrivalLatched)
        assertEquals(listOf(dest), e.guidance.started)
    }

    @Test fun aDoubleTapStartsOnlyOnce() = runTest {
        val e = Env(backgroundScope)
        assertTrue(e.controller.start(dest, null, null))
        e.now += START_DEBOUNCE_MS - 1
        assertFalse(e.controller.start(dest, null, null))
        e.now += 2
        assertTrue(e.controller.start(dest, null, null))
        assertEquals(2, e.guidance.started.size)
        e.controller.resetStartDebounce()
        assertTrue(e.controller.start(dest, null, null))
    }

    @Test fun aMinimisedTripStaysMinimisedUntilResumed() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.controller.setMinimised(true)
        e.guidance.enroute(); runCurrent()
        assertTrue("nothing a screen does clears it", e.controller.state.value.minimised)
        e.controller.setMinimised(false)
        assertFalse(e.controller.state.value.minimised)
    }

    @Test fun anArrivalFromTheSdkEndsTheTripOnce() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        e.controller.start(dest, null, null)
        runCurrent()
        e.guidance.arrival.tryEmit(Unit)
        runCurrent()
        e.guidance.arrival.tryEmit(Unit)
        e.guidance.state.value = NormalizedNavigationState(NavSessionState.ARRIVED)
        runCurrent()
        assertEquals(1, e.guidance.stops)
        assertEquals(1, events.filterIsInstance<TripEvent.Finished>().size)
        val finished = e.controller.state.value.finished!!
        assertEquals(SavedPlace(13.0, 77.5, "Toit"), finished.destination)
        assertTrue(e.controller.state.value.arrivalLatched)
    }

    private val inside = ArrivalPolicy.DWELL_RADIUS_M

    @Test fun twoMinutesWithinTwentyMetresEndsTheTrip() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        runCurrent()
        e.guidance.enroute(inside); runCurrent()
        assertNull(e.controller.state.value.finished)
        e.now += ArrivalPolicy.DWELL_MS - 1_000
        advanceTimeBy(ArrivalPolicy.DWELL_MS - 1_000); runCurrent()
        assertNull("not yet", e.controller.state.value.finished)
        e.now += 1_000
        advanceTimeBy(6_000); runCurrent()
        assertNotNull(e.controller.state.value.finished)
    }

    @Test fun beingCloseButOutsideTwentyMetresNeverEndsIt() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.guidance.enroute(inside + 20); runCurrent() // 40 m: used to end the trip at once
        e.now += ArrivalPolicy.DWELL_MS * 5
        advanceTimeBy(ArrivalPolicy.DWELL_MS * 5); runCurrent()
        assertNull(e.controller.state.value.finished)
    }

    @Test fun leavingTheCircleRestartsTheClock() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.guidance.enroute(10); runCurrent()
        e.now += 90_000
        advanceTimeBy(90_000); runCurrent()
        e.guidance.enroute(60); runCurrent() // rode off
        e.now += 5_000
        e.guidance.enroute(10); runCurrent() // and back: the clock starts again
        e.now += 60_000
        advanceTimeBy(60_000); runCurrent()
        assertNull("only 60 s since coming back", e.controller.state.value.finished)
        e.now += 61_000
        advanceTimeBy(61_000); runCurrent()
        assertNotNull(e.controller.state.value.finished)
    }

    @Test fun durationIsFrozenAtTheMomentOfFinishing() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.now += 125_000
        e.controller.end()
        e.now += 600_000
        assertEquals(125, e.controller.state.value.finished!!.durationSec)
    }

    @Test fun nothingHappensWithoutATrip() = runTest {
        val e = Env(backgroundScope)
        e.guidance.enroute(10); runCurrent()
        e.guidance.arrival.tryEmit(Unit); runCurrent()
        assertNull(e.controller.state.value.finished)
        assertEquals(0, e.guidance.stops)
    }

    @Test fun finishedStaysUntilAcknowledgedAndCannotFinishTwice() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.controller.end(); e.controller.end()
        assertEquals(1, e.guidance.stops)
        e.controller.acknowledgeFinish()
        assertNull(e.controller.state.value.finished)
        assertTrue("still latched: movement past the destination can't re-trigger", e.controller.state.value.arrivalLatched)
    }

    // ---- add stop ----

    private fun Env.liveTripWithStops(stops: List<Stop> = emptyList()) {
        controller.start(dest.copy(stops = stops), 1.0 to 2.0, null)
        guidance.enroute()
        guidance.target = dest.lat to dest.lng // heading to the final destination
    }

    @Test fun addingAStopReplansAndKeepsTheRide() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        e.liveTripWithStops(); runCurrent()
        assertTrue(e.controller.addStop(SavedPlace(12.5, 77.1, "Fuel")))
        runCurrent()
        val replan = e.guidance.started.last()
        assertEquals(listOf(Stop(12.5, 77.1, "Fuel")), replan.stops)
        assertTrue(replan.continuesRide)
        assertNull(replan.routeToken)
        assertEquals(listOf(Stop(12.5, 77.1, "Fuel")), e.controller.state.value.allStops)
        assertNull("route polyline no longer matches", e.controller.state.value.route)
        assertTrue(events.contains(TripEvent.StopAdded("Fuel")))
    }

    @Test fun aSecondStopImmediatelyAfterIsRefusedThenAllowed() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        e.liveTripWithStops(); runCurrent()
        e.controller.addStop(SavedPlace(12.5, 77.1, "A"))
        assertFalse(e.controller.addStop(SavedPlace(12.6, 77.1, "B")))
        runCurrent()
        assertTrue(events.contains(TripEvent.StopAddRejected(StopAddProblem.SETTLING)))
        e.now += STOP_ADD_SETTLE_MS
        e.guidance.target = 12.5 to 77.1 // now heading to the new stop
        assertTrue(e.controller.addStop(SavedPlace(12.6, 77.1, "B")))
    }

    @Test fun aFailedReplanRestoresTheTripTheRiderHad() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        e.liveTripWithStops(); runCurrent()
        e.controller.addStop(SavedPlace(12.5, 77.1, "Fuel"))
        runCurrent()
        e.guidance.startFailure.value = StartFailure.ROUTE
        runCurrent()
        assertEquals(dest, e.controller.state.value.dest)
        assertTrue(e.controller.state.value.allStops.isEmpty())
        assertTrue(events.contains(TripEvent.StopAddFailed))
        val restored = e.guidance.started.last()
        assertTrue(restored.continuesRide)
        assertNull(e.guidance.startFailure.value)
    }

    @Test fun theOldRoutesFramesAfterAddStopDoNotCancelTheRollback() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        e.liveTripWithStops(); runCurrent()
        e.controller.addStop(SavedPlace(12.5, 77.1, "Fuel"))
        // The old route keeps reporting ENROUTE (new distances) until the re-plan takes over.
        e.guidance.enroute(4_900); runCurrent()
        e.guidance.enroute(4_800); runCurrent()
        e.guidance.startFailure.value = StartFailure.ROUTE; runCurrent()
        assertTrue(events.contains(TripEvent.StopAddFailed))
        assertEquals(dest, e.controller.state.value.dest)
    }

    @Test fun addingAStopWithNoTripSaysNothing() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        runCurrent()
        assertFalse(e.controller.addStop(SavedPlace(12.5, 77.1, "Fuel")))
        runCurrent()
        assertTrue(events.isEmpty())
    }

    @Test fun anEndedTripCannotEndAgainEvenAfterTheSummaryIsDismissed() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        runCurrent()
        e.controller.start(dest, null, null)
        e.controller.end(); e.controller.acknowledgeFinish(); e.controller.end()
        runCurrent()
        assertEquals(1, e.guidance.stops)
        assertEquals(1, events.filterIsInstance<TripEvent.Finished>().size)
    }

    @Test fun aCancelledTripIsNoLongerOursToEnd() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.controller.setMinimised(true)
        e.controller.cancel()
        assertEquals(1, e.guidance.stops)
        assertTrue(e.controller.state.value.arrivalLatched)
        assertFalse(e.controller.state.value.minimised)
        e.guidance.enroute(10); runCurrent()
        assertNull("close to the old destination, but it was cancelled", e.controller.state.value.finished)
    }

    @Test fun endStillStopsGuidanceThatCameUpAfterTheTripWasCancelled() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.controller.cancel()
        assertEquals(1, e.guidance.stops)
        e.guidance.enroute(); runCurrent() // the pending start completed after the cancel
        e.controller.end()
        assertEquals("End must be able to stop it", 2, e.guidance.stops)
        assertNull("but nothing is published for a cancelled trip", e.controller.state.value.finished)
    }

    @Test fun aFailedReplanAfterACancelDoesNotRestartTheTrip() = runTest {
        val e = Env(backgroundScope)
        e.liveTripWithStops(); runCurrent()
        e.controller.addStop(SavedPlace(12.5, 77.1, "Fuel"))
        val startsBefore = e.guidance.started.size
        e.controller.cancel()
        e.guidance.startFailure.value = StartFailure.ROUTE; runCurrent()
        assertEquals("no restart of a cancelled trip", startsBefore, e.guidance.started.size)
    }

    @Test fun theDwellNeverEndsATripThatWasCancelledOrNeverStarted() = runTest {
        val e = Env(backgroundScope)
        e.guidance.enroute(5); runCurrent()
        e.now += ArrivalPolicy.DWELL_MS * 2
        advanceTimeBy(ArrivalPolicy.DWELL_MS * 2); runCurrent()
        assertNull("no trip", e.controller.state.value.finished)
        e.controller.start(dest, null, null)
        e.guidance.enroute(5); runCurrent()
        e.controller.cancel()
        e.now += ArrivalPolicy.DWELL_MS * 2
        advanceTimeBy(ArrivalPolicy.DWELL_MS * 2); runCurrent()
        assertNull("cancelled", e.controller.state.value.finished)
    }

    @Test fun aReplanThatWorksCannotBeRolledBackLater() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        e.liveTripWithStops(); runCurrent()
        e.controller.addStop(SavedPlace(12.5, 77.1, "Fuel"))
        e.guidance.state.value = NormalizedNavigationState(NavSessionState.REROUTING); runCurrent()
        e.guidance.enroute(); runCurrent() // ENROUTE again: it worked
        val startsBefore = e.guidance.started.size
        e.guidance.startFailure.value = StartFailure.ROUTE
        runCurrent()
        assertEquals(startsBefore, e.guidance.started.size)
        assertFalse(events.contains(TripEvent.StopAddFailed))
    }

    @Test fun searchingForAStopNeedsAConnectionAndAnActiveTrip() = runTest {
        val e = Env(backgroundScope)
        val events = mutableListOf<TripEvent>()
        backgroundScope.launch { e.controller.events.toList(events) }
        assertFalse(e.controller.canSearchForStop())
        e.liveTripWithStops(); runCurrent()
        e.online.value = false
        assertFalse(e.controller.canSearchForStop())
        e.online.value = true
        assertTrue(e.controller.canSearchForStop())
        runCurrent()
        assertTrue(events.contains(TripEvent.StopAddRejected(StopAddProblem.OFFLINE)))
    }

    @Test fun retryStartsTheLastDestinationAgain() = runTest {
        val e = Env(backgroundScope)
        e.controller.start(dest, null, null)
        e.controller.end()
        e.controller.retry()
        assertEquals(2, e.guidance.started.size)
        assertFalse(e.controller.state.value.arrivalLatched)
        assertNull(e.controller.state.value.finished)
    }

    @Test fun rideBackUsesTheOriginAndTheStopsInOrder() = runTest {
        val e = Env(backgroundScope)
        val stops = listOf(Stop(1.0, 1.0, "A"), Stop(2.0, 2.0, "B"))
        e.controller.start(dest.copy(stops = stops), 9.0 to 8.0, null)
        val plan = e.controller.rideBackPlan(null)!!
        assertEquals(9.0 to 8.0, plan.origin)
        assertEquals(stops, plan.tripStops)
        assertEquals(3.0 to 4.0, e.controller.rideBackPlan(3.0 to 4.0)!!.origin)
    }
}
