package com.navigator.app.ui.feature.navigate.search

import android.content.Context
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.nav.destination.CategoryResult
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PlaceLocation
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaceSearchViewModelTest {
    private class FakeApi : PlacesApi {
        val autocompleteCalls = mutableListOf<String>()
        var suggestions = listOf(PlaceSuggestion("p1", "Toit Brewpub", "Indiranagar", 1200))
        var failed = false
        var details: PlaceLocation? = PlaceLocation(12.97, 77.64)
        var detailsGate: CompletableDeferred<Unit>? = null
        val categoryCalls = mutableListOf<Pair<PlaceCategory, List<Pair<Double, Double>>?>>()
        var categoryItems = listOf(CategoryResult("Indian Oil", "NH 48", 13.0, 77.5, 800))
        var categoryFailed = false

        override suspend fun autocomplete(input: String, sessionToken: String, origin: Pair<Double, Double>?): PlacesClient.AutocompleteResult {
            autocompleteCalls += input
            return PlacesClient.AutocompleteResult(if (failed) emptyList() else suggestions, failed)
        }

        override suspend fun details(placeId: String, sessionToken: String): PlaceLocation? {
            detailsGate?.await()
            return details
        }

        override suspend fun category(category: PlaceCategory, origin: Pair<Double, Double>?, route: List<Pair<Double, Double>>?): PlacesClient.CategorySearch {
            categoryCalls += category to route
            return PlacesClient.CategorySearch(if (categoryFailed) emptyList() else categoryItems, categoryFailed)
        }
    }

    private lateinit var ctx: Context
    private lateinit var places: PlacesRepository
    private val api = FakeApi()
    private val online = MutableStateFlow(true)

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences(PlacesStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        places = PlacesRepository(ctx)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(query: String = "", category: PlaceCategory? = null): PlaceSearchViewModel =
        PlaceSearchViewModel(places, api, online, { 12.9 to 77.6 }, query, category).also {
            backgroundScope.launch { it.state.collect { } }
            runCurrent()
        }

    @Test fun typingSearchesOnlyAfterTheDebounceAndOnlyTheLastText() = runTest {
        val vm = vm()
        vm.setQuery("toi"); advanceTimeBy(100)
        vm.setQuery("toit"); advanceTimeBy(SearchPolicy.DEBOUNCE_MS - 1)
        assertTrue(api.autocompleteCalls.isEmpty())
        assertTrue(vm.state.value.loading)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("toit"), api.autocompleteCalls)
        assertEquals(1, vm.state.value.suggestions.size)
        assertFalse(vm.state.value.loading)
        assertEquals(SearchView.Results, vm.state.value.view)
    }

    @Test fun aSlowOlderAnswerNeverOverwritesTheNewerQuery() = runTest {
        val vm = vm()
        vm.setQuery("toi"); advanceTimeBy(400) // the first lookup is now in flight (the fake answers at once, so use two distinct ones)
        api.suggestions = listOf(PlaceSuggestion("p2", "Toit Express", null, null))
        vm.setQuery("toit"); advanceTimeBy(400); runCurrent()
        assertEquals(listOf("toi", "toit"), api.autocompleteCalls)
        assertEquals("Toit Express", vm.state.value.suggestions.single().primary)
        assertEquals("toit", vm.state.value.query)
    }

    @Test fun theFirstFrameKeepsTheTypedTextAndCategory() = runTest {
        val typed = PlaceSearchViewModel(places, api, online, { null }, initialQuery = "cubbon")
        assertEquals("cubbon", typed.state.value.query)
        val chip = PlaceSearchViewModel(places, api, online, { null }, initialCategory = PlaceCategory.FUEL)
        assertEquals(PlaceCategory.FUEL, chip.state.value.category)
    }

    @Test fun belowThreeCharactersNothingIsSearchedAndTheResultsClear() = runTest {
        val vm = vm()
        vm.setQuery("toit"); advanceTimeBy(400); runCurrent()
        vm.setQuery("to"); advanceTimeBy(400); runCurrent()
        assertEquals(listOf("toit"), api.autocompleteCalls)
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertEquals(SearchView.Browse, vm.state.value.view)
    }

    @Test fun aFailedLookupFallsBackToTheRidersOwnPlaces() = runTest {
        places.addRecent(SavedPlace(12.1, 77.1, "Toit Brewpub", "100 Feet Rd"))
        runCurrent()
        api.failed = true
        val vm = vm()
        vm.setQuery("toit"); advanceTimeBy(400); runCurrent()
        val s = vm.state.value
        assertTrue(s.offline)
        assertTrue(s.suggestions.isEmpty())
        assertEquals(listOf("Toit Brewpub"), s.localResults.map { it.label })
    }

    @Test fun beingOfflineShowsLocalMatchesBeforeAnyAnswer() = runTest {
        places.addRecent(SavedPlace(12.1, 77.1, "Cubbon Park"))
        runCurrent()
        online.value = false
        val vm = vm()
        vm.setQuery("cubb"); advanceTimeBy(400); runCurrent()
        assertTrue(vm.state.value.offline)
        assertEquals(listOf("Cubbon Park"), vm.state.value.localResults.map { it.label })
        online.value = true
        runCurrent()
        assertFalse(vm.state.value.offline)
    }

    @Test fun pickingASuggestionLoadsItsPlaceOnce() = runTest {
        val vm = vm()
        val events = mutableListOf<PlaceSearchEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        api.detailsGate = CompletableDeferred()
        vm.setQuery("toit"); advanceTimeBy(400); runCurrent()
        val s = vm.state.value.suggestions.first()
        vm.pickSuggestion(s); runCurrent()
        assertTrue(vm.state.value.resolving)
        vm.pickSuggestion(s) // a second tap while it loads does nothing
        api.detailsGate!!.complete(Unit); runCurrent()
        assertEquals(listOf<PlaceSearchEvent>(PlaceSearchEvent.Picked(SavedPlace(12.97, 77.64, "Toit Brewpub", "Indiranagar"))), events)
        assertFalse(vm.state.value.resolving)
    }

    @Test fun aPlaceThatWillNotLoadIsReportedNotPicked() = runTest {
        val vm = vm()
        val events = mutableListOf<PlaceSearchEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        api.details = null
        vm.setQuery("toit"); advanceTimeBy(400); runCurrent()
        vm.pickSuggestion(vm.state.value.suggestions.first()); runCurrent()
        assertEquals(listOf<PlaceSearchEvent>(PlaceSearchEvent.LoadFailed), events)
    }

    @Test fun leavingCancelsALookupInFlight() = runTest {
        val vm = vm()
        val events = mutableListOf<PlaceSearchEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        api.detailsGate = CompletableDeferred()
        vm.setQuery("toit"); advanceTimeBy(400); runCurrent()
        vm.pickSuggestion(vm.state.value.suggestions.first()); runCurrent()
        vm.cancelPending(); api.detailsGate!!.complete(Unit); runCurrent()
        assertTrue("no jump after the rider backed out", events.isEmpty())
        assertFalse(vm.state.value.resolving)
    }

    @Test fun categoriesSearchAroundTheRiderOrAlongTheRouteAhead() = runTest {
        val vm = vm()
        vm.selectCategory(PlaceCategory.FUEL); runCurrent()
        assertEquals(PlaceCategory.FUEL to null, api.categoryCalls.last())
        assertFalse(vm.state.value.alongRoute)
        assertEquals(SearchView.Category, vm.state.value.view)
        assertEquals(1, vm.state.value.categoryResults.size)

        val ahead = listOf(12.9 to 77.6, 13.0 to 77.7)
        vm.routeProvider = { ahead }
        vm.selectCategory(PlaceCategory.COFFEE); runCurrent()
        assertEquals(PlaceCategory.COFFEE to ahead, api.categoryCalls.last())
        assertTrue(vm.state.value.alongRoute)
    }

    @Test fun theSameChipAgainClearsItAndTypingReplacesIt() = runTest {
        val vm = vm()
        vm.selectCategory(PlaceCategory.FUEL); runCurrent()
        vm.selectCategory(PlaceCategory.FUEL); runCurrent()
        assertNull(vm.state.value.category)
        vm.selectCategory(PlaceCategory.FOOD); runCurrent()
        vm.setQuery("t"); runCurrent()
        assertNull(vm.state.value.category)
    }

    @Test fun aFailedCategorySearchIsNotAnEmptyOne() = runTest {
        api.categoryFailed = true
        val vm = vm()
        vm.selectCategory(PlaceCategory.ATM); runCurrent()
        assertTrue(vm.state.value.categoryFailed)
        assertTrue(vm.state.value.categoryResults.isEmpty())
    }

    @Test fun startingWithACategoryOrAQuery() = runTest {
        assertEquals(PlaceCategory.PARKING, vm(category = PlaceCategory.PARKING).state.value.category)
        runCurrent()
        val typed = vm(query = "cubbon")
        advanceTimeBy(400); runCurrent()
        assertEquals("cubbon", typed.state.value.query)
        assertTrue(api.autocompleteCalls.contains("cubbon"))
    }

    @Test fun removingARecentRemovesItAndOffersUndo() = runTest {
        val park = SavedPlace(12.2, 77.2, "Cubbon Park")
        places.addRecent(park); runCurrent()
        val vm = vm()
        val events = mutableListOf<PlaceSearchEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.removeRecent(park); runCurrent()
        assertTrue(vm.state.value.recents.isEmpty())
        assertEquals(listOf<PlaceSearchEvent>(PlaceSearchEvent.RecentRemoved(park)), events)
        vm.undoRemoveRecent(park); runCurrent()
        assertEquals(listOf(park), vm.state.value.recents)
    }

    @Test fun savedPlacesFeedTheBrowseList() = runTest {
        places.saveFavorite(SavedPlace(12.0, 77.0, "Flat"), FavoriteSlot.HOME)
        val vm = vm(); runCurrent()
        assertNotNull(vm.state.value.favorites.firstOrNull { it.slot == FavoriteSlot.HOME })
    }
}
