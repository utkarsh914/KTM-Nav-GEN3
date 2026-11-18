package com.navigator.app.ui.screens

import com.navigator.app.nav.destination.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavFlowTest {

    private fun place(n: Int) = SavedPlace(10.0 + n, 20.0 + n, "P$n", "addr$n")

    // ---- Picking ----------------------------------------------------------

    @Test fun pickFromSearchGoesToConfirmAndBackReturnsToSearch() {
        val s = NavFlowState().openSearch(SearchTarget.DESTINATION).applyPicked(place(1))
        assertEquals(NavStage.CONFIRM, s.stage)
        assertEquals(place(1), s.selected)
        val back = s.back(tripLive = false)
        assertEquals(NavStage.SEARCH, back.state.stage)
        assertNull(back.state.selected)
        assertEquals(BackEffect.NONE, back.effect)
    }

    @Test fun pinDropLinkOrSavedPlaceBackGoesToTheMap() {
        val s = NavFlowState().choose(place(1))
        assertFalse(s.confirmFromSearch)
        assertEquals(BackEffect.BACK_TO_BROWSE, s.back(false).effect)
    }

    @Test fun newDestinationDropsStopsOfThePreviousOne() {
        val s = NavFlowState(stops = listOf(place(5)), stage = NavStage.SEARCH).applyPicked(place(1))
        assertTrue(s.stops.isEmpty())
        assertTrue(NavFlowState(stops = listOf(place(5))).choose(place(2)).stops.isEmpty())
    }

    // ---- Stops ------------------------------------------------------------

    @Test fun stopPickAppendsAndReturnsToPreview() {
        var s = NavFlowState(stage = NavStage.PREVIEW, selected = place(1)).openSearch(SearchTarget.STOP)
        assertEquals(NavStage.SEARCH, s.stage)
        s = s.applyPicked(place(2))
        assertEquals(NavStage.PREVIEW, s.stage)
        assertEquals(listOf(place(2)), s.stops)
        assertEquals(SearchTarget.DESTINATION, s.searchTarget)
        assertEquals(place(1), s.selected)
    }

    @Test fun stopsAreCapped() {
        var s = NavFlowState(stage = NavStage.PREVIEW, selected = place(1))
        repeat(MAX_STOPS + 3) { i -> s = s.openSearch(SearchTarget.STOP).applyPicked(place(10 + i)) }
        assertEquals(MAX_STOPS, s.stops.size)
    }

    // ---- Back -------------------------------------------------------------

    @Test fun backWalksPreviewConfirmSearchBrowse() {
        val preview = NavFlowState(stage = NavStage.PREVIEW, selected = place(1), confirmFromSearch = true)
        val confirm = preview.back(false).state
        assertEquals(NavStage.CONFIRM, confirm.stage)
        val search = confirm.back(false).state
        assertEquals(NavStage.SEARCH, search.stage)
        assertEquals(BackEffect.BACK_TO_BROWSE, search.back(false).effect)
    }

    @Test fun backFromStopSearchReturnsToPreviewNotBrowse() {
        val s = NavFlowState(stage = NavStage.SEARCH, selected = place(1), searchTarget = SearchTarget.STOP)
        val out = s.back(false)
        assertEquals(NavStage.PREVIEW, out.state.stage)
        assertEquals(SearchTarget.DESTINATION, out.state.searchTarget)
    }

    @Test fun backOnMapResumesALiveTripElseAsksToExit() {
        assertEquals(BackEffect.RESUME_NAV, NavFlowState().back(tripLive = true).effect)
        assertEquals(BackEffect.CONFIRM_EXIT, NavFlowState().back(tripLive = false).effect)
    }

    @Test fun backWhileNavigatingStepsAwayAndFinishedGoesToMap() {
        assertEquals(BackEffect.MOVE_AWAY_FROM_NAV, NavFlowState(stage = NavStage.NAVIGATING).back(true).effect)
        assertEquals(BackEffect.BACK_TO_BROWSE, NavFlowState(stage = NavStage.TRIP_FINISHED).back(false).effect)
    }

    @Test fun backToBrowseClearsPlanningState() {
        val s = NavFlowState(
            stage = NavStage.PREVIEW, selected = place(1), stops = listOf(place(2)),
            searchTarget = SearchTarget.STOP, query = "cafe",
        ).backToBrowse()
        assertEquals(NavFlowState(), s)
    }

    @Test fun finishTripKeepsDestinationForTheSummaryCard() {
        val s = NavFlowState(stage = NavStage.NAVIGATING, selected = place(1), stops = listOf(place(2)), query = "x").finishTrip()
        assertEquals(NavStage.TRIP_FINISHED, s.stage)
        assertEquals(place(1), s.selected)
        assertTrue(s.stops.isEmpty())
        assertEquals("", s.query)
    }

    @Test fun rideBackPlansTheReverseTripWithStopsReversed() {
        val s = NavFlowState(stage = NavStage.TRIP_FINISHED, selected = place(9))
            .rideBack(place(0), listOf(place(1), place(2), place(3)))
        assertEquals(NavStage.PREVIEW, s.stage)
        assertEquals(place(0), s.selected)
        assertEquals(listOf(place(3), place(2), place(1)), s.stops)
        assertFalse(s.confirmFromSearch)
        assertEquals(SearchTarget.DESTINATION, s.searchTarget)
        // Back from the reverse preview goes to CONFIRM like any other preview.
        assertEquals(NavStage.CONFIRM, s.back(false).state.stage)
    }

    @Test fun rideBackNeverExceedsTheStopCap() {
        val many = (1..MAX_STOPS + 2).map { place(it) }
        assertEquals(MAX_STOPS, NavFlowState().rideBack(place(0), many).stops.size)
    }

    // ---- Session rules (C2/C3/U1/U2) ---------------------------------------

    @Test fun autoResumeOnlyWhenTripLiveAndNotDeliberatelyAway() {
        assertTrue(NavFlowRules.shouldAutoResume(true, NavStage.BROWSE, false))
        assertTrue(NavFlowRules.shouldAutoResume(true, NavStage.PREVIEW, false))
        assertFalse(NavFlowRules.shouldAutoResume(true, NavStage.BROWSE, leftNavManually = true))
        assertFalse(NavFlowRules.shouldAutoResume(true, NavStage.TRIP_FINISHED, false))
        assertFalse(NavFlowRules.shouldAutoResume(true, NavStage.NAVIGATING, false))
        assertFalse(NavFlowRules.shouldAutoResume(false, NavStage.BROWSE, false))
    }

    @Test fun owningTripWhileNavigatingOrSteppedAway() {
        assertTrue(NavFlowRules.owningTrip(NavStage.NAVIGATING, false))
        assertTrue(NavFlowRules.owningTrip(NavStage.BROWSE, true))
        assertFalse(NavFlowRules.owningTrip(NavStage.BROWSE, false))
        assertFalse(NavFlowRules.owningTrip(NavStage.PREVIEW, true))
    }

    // ---- Arrival ------------------------------------------------------------

    @Test fun arrivalProximity() {
        assertTrue(ArrivalPolicy.reached(40))
        assertFalse(ArrivalPolicy.reached(41))
    }

    @Test fun progressOnlyResetsOnMeaningfulDrop() {
        var p = ArrivalPolicy.Progress.start(0)
        p = ArrivalPolicy.track(p, 1000, 1_000)
        assertEquals(1_000L, p.lastProgressMs)
        p = ArrivalPolicy.track(p, 990, 2_000) // < 15 m drop: not progress
        assertEquals(1_000L, p.lastProgressMs)
        p = ArrivalPolicy.track(p, 900, 3_000)
        assertEquals(3_000L, p.lastProgressMs)
        assertEquals(900.0, p.bestRemainingM, 0.0)
    }

    @Test fun stallFallbackNeedsOuterRadiusAndLongStall() {
        val p = ArrivalPolicy.Progress(bestRemainingM = 100.0, lastProgressMs = 0)
        assertFalse(ArrivalPolicy.stalledNearDestination(p, 100, ArrivalPolicy.STALL_MS - 1))
        assertTrue(ArrivalPolicy.stalledNearDestination(p, 100, ArrivalPolicy.STALL_MS))
        assertFalse(ArrivalPolicy.stalledNearDestination(p, 151, ArrivalPolicy.STALL_MS * 2))
    }

    // ---- Persistence --------------------------------------------------------

    @Test fun codecRoundTrip() {
        val s = NavFlowState(
            stage = NavStage.PREVIEW, selected = place(1), stops = listOf(place(2), place(3)),
            searchTarget = SearchTarget.STOP, confirmFromSearch = false, query = "pet rol",
        )
        assertEquals(s, NavFlowCodec.decode(NavFlowCodec.encode(s)))
    }

    @Test fun codecToleratesGarbage() {
        assertEquals(NavFlowState(), NavFlowCodec.decode(null))
        assertEquals(NavFlowState(), NavFlowCodec.decode(""))
        assertEquals(NavFlowState(), NavFlowCodec.decode("{not json"))
        assertEquals(NavStage.BROWSE, NavFlowCodec.decode("""{"stage":"BOGUS"}""").stage)
    }

    @Test fun restoreKeepsPlanningStagesOnly() {
        val confirm = NavFlowState(stage = NavStage.CONFIRM, selected = place(1))
        assertEquals(confirm, confirm.restored())
        val preview = NavFlowState(stage = NavStage.PREVIEW, selected = place(1), stops = listOf(place(2)))
        assertEquals(preview, preview.restored())
        // A live trip can't have survived the process.
        assertEquals(NavFlowState(), NavFlowState(stage = NavStage.NAVIGATING, selected = place(1)).restored())
        assertEquals(NavFlowState(), NavFlowState(stage = NavStage.TRIP_FINISHED, selected = place(1)).restored())
        // Stages that need a destination fall back to the map without one.
        assertEquals(NavFlowState(), NavFlowState(stage = NavStage.CONFIRM).restored())
        assertEquals(NavFlowState(), NavFlowState(stage = NavStage.PREVIEW).restored())
        assertEquals(
            NavFlowState(),
            NavFlowState(stage = NavStage.SEARCH, searchTarget = SearchTarget.STOP).restored(),
        )
        val search = NavFlowState(stage = NavStage.SEARCH, query = "abc")
        assertEquals(search, search.restored())
    }

    // ---- ViewModel lifecycle ------------------------------------------------

    private fun vm() = NavFlowViewModel(androidx.lifecycle.SavedStateHandle())

    @Test fun lastScreenLeavingResetsPlanningAndTripBookkeeping() {
        val vm = vm()
        vm.attach()
        vm.update { it.choose(place(1)) }
        vm.arrivalLatched = true
        vm.tripStartMs = 5L
        vm.leftNavManually = true
        vm.detach(configChange = false)
        assertEquals(NavFlowState(), vm.state)
        assertFalse(vm.arrivalLatched)
        assertEquals(0L, vm.tripStartMs)
        assertFalse(vm.leftNavManually)
    }

    @Test fun configurationChangeKeepsState() {
        val vm = vm()
        vm.attach()
        vm.update { it.choose(place(1)) }
        vm.detach(configChange = true)
        vm.attach()
        assertEquals(place(1), vm.state.selected)
    }

    @Test fun quickReentryDoesNotWipeTheLiveScreen() {
        val vm = vm()
        vm.attach()                       // old screen
        vm.attach()                       // new screen composed first
        vm.update { it.choose(place(1)) }
        vm.detach(configChange = false)   // old screen disposed after its exit transition
        assertEquals(place(1), vm.state.selected)
        vm.detach(configChange = false)   // the live one really leaves
        assertEquals(NavFlowState(), vm.state)
    }

    @Test fun stateIsPersistedToTheHandleAndRestored() {
        val handle = androidx.lifecycle.SavedStateHandle()
        val a = NavFlowViewModel(handle)
        a.update { it.choose(place(1)).copy(stage = NavStage.PREVIEW) }
        val b = NavFlowViewModel(handle) // "new process" with the saved handle
        assertEquals(NavStage.PREVIEW, b.state.stage)
        assertEquals(place(1), b.state.selected)
    }
}
