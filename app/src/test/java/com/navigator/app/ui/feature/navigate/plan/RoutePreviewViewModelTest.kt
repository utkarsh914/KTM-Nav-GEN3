package com.navigator.app.ui.feature.navigate.plan

import com.google.android.gms.maps.model.LatLng
import com.navigator.app.nav.destination.RouteLeg
import com.navigator.app.nav.destination.RoutePreview
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.TravelMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutePreviewViewModelTest {
    private val dest = SavedPlace(13.0, 77.5, "Toit")
    private val stop = SavedPlace(12.9, 77.6, "Fuel")

    private fun route(minutes: Int, delay: Int = 0, legs: List<RouteLeg> = emptyList(), token: String? = "t$minutes") =
        RoutePreview(minutes * 60, minutes * 1000, listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), token, delay, TravelMode.TWO_WHEELER, legs)

    private class Api(var routes: List<RoutePreview>) : RoutesApi {
        val calls = mutableListOf<Triple<Pair<Double, Double>, Pair<Double, Double>, List<Pair<Double, Double>>>>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun compute(origin: Pair<Double, Double>, dest: Pair<Double, Double>, mode: TravelMode, intermediates: List<Pair<Double, Double>>): List<RoutePreview> {
            calls += Triple(origin, dest, intermediates)
            gate?.await()
            return routes
        }
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(api: RoutesApi, fresh: suspend () -> Pair<Double, Double>? = { 12.0 to 77.0 }, last: () -> Pair<Double, Double>? = { 11.0 to 76.0 }) =
        RoutePreviewViewModel(api, fresh, last, { TravelMode.TWO_WHEELER })

    @Test fun loadsRoutesFromAFreshFixAndSelectsTheFirst() = runTest {
        val api = Api(listOf(route(30), route(35)))
        val vm = vm(api)
        vm.load(dest, emptyList())
        assertTrue(vm.state.value.loading)
        runCurrent()
        val s = vm.state.value
        assertFalse(s.loading); assertFalse(s.failed)
        assertEquals(2, s.routes.size)
        assertEquals(0, s.selected)
        assertEquals(12.0 to 77.0, api.calls.single().first)
        assertEquals(12.0 to 77.0, s.origin)
    }

    @Test fun stopsAreSentAsIntermediatesAndRememberedForTheLegLines() = runTest {
        val api = Api(listOf(route(40)))
        val vm = vm(api)
        vm.load(dest, listOf(stop)); runCurrent()
        assertEquals(listOf(stop.lat to stop.lng), api.calls.single().third)
        assertEquals(listOf(stop), vm.state.value.forStops)
    }

    @Test fun noRoutesIsAFailureButNotACrash() = runTest {
        val vm = vm(Api(emptyList()))
        vm.load(dest, emptyList()); runCurrent()
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.loading)
    }

    @Test fun aSlowFixFallsBackToTheLastKnownPosition() = runTest {
        val api = Api(listOf(route(30)))
        val vm = vm(api, fresh = { delay(60_000); 1.0 to 1.0 })
        vm.load(dest, emptyList())
        advanceTimeBy(RoutePreviewViewModel.FRESH_FIX_MS + 1); runCurrent()
        assertEquals(11.0 to 76.0, api.calls.single().first)
    }

    @Test fun noPositionAtAllFailsTheLoad() = runTest {
        val api = Api(listOf(route(30)))
        val vm = vm(api, fresh = { null }, last = { null })
        vm.load(dest, emptyList()); runCurrent()
        assertTrue(vm.state.value.failed)
        assertTrue("never asked the API without a start", api.calls.isEmpty())
    }

    @Test fun aNewLoadReplacesOneStillRunning() = runTest {
        val api = Api(listOf(route(30)))
        api.gate = CompletableDeferred()
        val vm = vm(api)
        vm.load(dest, emptyList()); runCurrent()
        api.routes = listOf(route(99))
        vm.load(dest, listOf(stop)); runCurrent()
        api.gate!!.complete(Unit); runCurrent()
        assertEquals("only the newer answer is shown", 99 * 60, vm.state.value.routes.single().durationSeconds)
        assertEquals(listOf(stop), vm.state.value.forStops)
    }

    @Test fun selectingStaysInsideTheRoutesAndANewPlanStartsFromTheFirst() = runTest {
        val api = Api(listOf(route(30), route(35), route(40)))
        val vm = vm(api)
        vm.load(dest, emptyList()); runCurrent()
        vm.select(2); assertEquals(2, vm.state.value.selected)
        vm.select(9); assertEquals(2, vm.state.value.selected)
        api.routes = listOf(route(30))
        vm.load(dest, listOf(stop)); runCurrent()
        assertEquals("a reload starts from the first", 0, vm.state.value.selected)
    }

    @Test fun returningToTheSamePlanKeepsTheRoutesAndTheChoice() = runTest {
        val api = Api(listOf(route(30), route(35)))
        val vm = vm(api)
        vm.load(dest, emptyList()); runCurrent()
        vm.select(1)
        vm.load(dest, emptyList()); runCurrent()
        assertEquals("no second request", 1, api.calls.size)
        assertEquals(1, vm.state.value.selected)
        vm.load(dest, listOf(stop)); runCurrent()
        assertEquals("other stops are a new plan", 2, api.calls.size)
    }

    @Test fun aFailedLoadIsTriedAgainOnReturn() = runTest {
        val api = Api(emptyList())
        val vm = vm(api)
        vm.load(dest, emptyList()); runCurrent()
        assertTrue(vm.state.value.failed)
        api.routes = listOf(route(30))
        vm.load(dest, emptyList()); runCurrent()
        assertEquals(2, api.calls.size)
        assertFalse(vm.state.value.failed)
    }

    @Test fun anApiThatThrowsIsAFailureNotACrash() = runTest {
        val vm = vm(RoutesApi { _, _, _, _ -> error("boom") })
        vm.load(dest, emptyList()); runCurrent()
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.loading)
    }

    @Test fun clearingForgetsTheRoutesAndCancelsTheLoad() = runTest {
        val api = Api(listOf(route(30)))
        api.gate = CompletableDeferred()
        val vm = vm(api)
        vm.load(dest, emptyList()); runCurrent()
        vm.clear()
        api.gate!!.complete(Unit); runCurrent()
        val s = vm.state.value
        assertTrue(s.routes.isEmpty()); assertFalse(s.loading); assertFalse(s.failed)
        assertNotNull(s.origin)
    }

    @Test fun legLinesOnlyShowForTheStopsTheRoutesWereComputedFor() = runTest {
        val legs = listOf(RouteLeg(600, 5000, emptyList()), RouteLeg(900, 7000, emptyList()))
        val api = Api(listOf(route(25, legs = legs)))
        val vm = vm(api)
        vm.load(dest, listOf(stop)); runCurrent()
        val fresh = RouteSheetMapper.state(dest, listOf(stop), vm.state.value, nowMs = 0L)
        assertEquals(2, fresh.legLines.size)
        val reordered = RouteSheetMapper.state(dest, listOf(stop, SavedPlace(1.0, 1.0, "B")), vm.state.value, nowMs = 0L)
        assertTrue("stale legs are hidden until the new routes land", reordered.legLines.isEmpty())
        assertNull(RouteSheetMapper.state(dest, emptyList(), RoutePreviewState(), 0L).summary)
        assertTrue(fresh.summary!!.contains("25 min"))
    }
}
