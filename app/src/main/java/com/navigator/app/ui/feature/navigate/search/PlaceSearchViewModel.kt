package com.navigator.app.ui.feature.navigate.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.nav.destination.CategoryResult
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.SavedPlace
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class PlaceSearchState(
    val query: String = "",
    val view: SearchView = SearchView.Browse,
    val loading: Boolean = false,
    /** The lookup failed or there is no connection: [results] then holds matching saved and recent places. */
    val offline: Boolean = false,
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val localResults: List<SavedPlace> = emptyList(),
    val category: PlaceCategory? = null,
    val categoryResults: List<CategoryResult> = emptyList(),
    val categoryFailed: Boolean = false,
    /** Category results are along the route still ahead, not around the rider. */
    val alongRoute: Boolean = false,
    val resolving: Boolean = false,
    val favorites: List<FavoritePlace> = emptyList(),
    val recents: List<SavedPlace> = emptyList(),
    val origin: Pair<Double, Double>? = null,
)

sealed interface PlaceSearchEvent {
    data class Picked(val place: SavedPlace) : PlaceSearchEvent

    /** The place behind a suggestion could not be loaded. */
    data object LoadFailed : PlaceSearchEvent
    data class RecentRemoved(val place: SavedPlace) : PlaceSearchEvent
}

/**
 * One search session: typing with a debounce, categories (around the rider, or along the route still
 * ahead), the saved and recent lists, and the offline fallback. Picking is reported as an event; what
 * the place is for (destination, stop, Home) is the host's business.
 */
class PlaceSearchViewModel(
    private val places: PlacesRepository,
    private val api: PlacesApi,
    private val online: StateFlow<Boolean>,
    private val originProvider: () -> Pair<Double, Double>?,
    initialQuery: String = "",
    initialCategory: PlaceCategory? = null,
    /** The route still ahead of the rider, for "along your route"; known from the start so an opening category uses it. */
    var routeProvider: () -> List<Pair<Double, Double>>? = { null },
) : ViewModel() {

    /** One per search session; renewed once a place has been looked up, which closes the session. */
    private var sessionToken = UUID.randomUUID().toString()

    private data class Core(
        val query: String = "",
        val loading: Boolean = false,
        val failed: Boolean = false,
        val suggestions: List<PlaceSuggestion> = emptyList(),
        val category: PlaceCategory? = null,
        val categoryResults: List<CategoryResult> = emptyList(),
        val categoryFailed: Boolean = false,
        val alongRoute: Boolean = false,
        val resolving: Boolean = false,
        val origin: Pair<Double, Double>? = null,
    )

    private val core = MutableStateFlow(
        Core(query = initialQuery, category = initialCategory.takeIf { initialQuery.isBlank() }, origin = originProvider()),
    )
    private var searchJob: Job? = null
    private var categoryJob: Job? = null
    private var detailsJob: Job? = null

    private val _events = Channel<PlaceSearchEvent>(Channel.BUFFERED)
    val events: Flow<PlaceSearchEvent> = _events.receiveAsFlow()

    private fun build(c: Core, favorites: List<FavoritePlace>, recents: List<SavedPlace>, isOnline: Boolean): PlaceSearchState {
        val view = SearchPolicy.view(c.query, c.category)
        val offline = view == SearchView.Results && (!isOnline || c.failed)
        return PlaceSearchState(
            query = c.query,
            view = view,
            loading = c.loading,
            offline = offline,
            suggestions = if (offline) emptyList() else c.suggestions,
            localResults = if (offline) SearchPolicy.localMatches(c.query, favorites, recents) else emptyList(),
            category = c.category,
            categoryResults = c.categoryResults,
            categoryFailed = c.categoryFailed,
            alongRoute = c.alongRoute,
            resolving = c.resolving,
            favorites = favorites,
            recents = recents,
            origin = c.origin,
        )
    }

    val state: StateFlow<PlaceSearchState> = combine(core, places.favorites, places.recents, online, ::build)
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            // The real starting point, so the first frame is not an empty Browse (and the typed text is kept).
            build(core.value, places.favorites.value, places.recents.value, online.value),
        )

    init {
        if (initialQuery.isNotEmpty()) setQuery(initialQuery)
        if (initialCategory != null && initialQuery.isBlank()) loadCategory(initialCategory)
    }

    fun setQuery(query: String) {
        // Typing replaces a category browse.
        core.update { it.copy(query = query, category = if (query.isNotEmpty()) null else it.category) }
        if (query.isNotEmpty()) categoryJob?.cancel()
        searchJob?.cancel()
        if (!SearchPolicy.searchable(query)) {
            core.update { it.copy(suggestions = emptyList(), loading = false, failed = false) }
            return
        }
        // The previous answer stays listed while the new one loads, but a failure of the last one must not pin the offline banner.
        core.update { it.copy(loading = true, failed = false, origin = originProvider() ?: it.origin) }
        searchJob = viewModelScope.launch {
            delay(SearchPolicy.DEBOUNCE_MS)
            val result = api.autocomplete(query.trim(), sessionToken, core.value.origin)
            core.update { it.copy(suggestions = result.items, failed = result.failed, loading = false) }
        }
    }

    /** Ask again: the category being browsed, or else the text in the field. */
    fun retry() {
        val now = core.value
        now.category?.let { loadCategory(it); return }
        setQuery(now.query)
    }

    /** Pick a category chip, or null to clear it. Picking the active one again clears it. */
    fun selectCategory(category: PlaceCategory?) {
        categoryJob?.cancel()
        searchJob?.cancel()
        val next = if (category == core.value.category) null else category
        if (next == null) {
            core.update { it.copy(category = null, categoryResults = emptyList(), categoryFailed = false, loading = false) }
            return
        }
        loadCategory(next)
    }

    private fun loadCategory(next: PlaceCategory) {
        categoryJob?.cancel()
        val route = routeProvider()
        core.update {
            it.copy(
                category = next, categoryResults = emptyList(), categoryFailed = false, loading = true,
                alongRoute = route != null, origin = originProvider() ?: it.origin,
            )
        }
        categoryJob = viewModelScope.launch {
            val result = api.category(next, core.value.origin, route)
            core.update { it.copy(categoryResults = result.items, categoryFailed = result.failed, loading = false) }
        }
    }

    fun pickSuggestion(suggestion: PlaceSuggestion) {
        if (core.value.resolving) return
        core.update { it.copy(resolving = true) }
        detailsJob = viewModelScope.launch {
            val loc = api.details(suggestion.placeId, sessionToken)
            sessionToken = UUID.randomUUID().toString()
            core.update { it.copy(resolving = false) }
            if (loc != null) _events.send(PlaceSearchEvent.Picked(SavedPlace(loc.lat, loc.lng, suggestion.primary, suggestion.secondary)))
            else _events.send(PlaceSearchEvent.LoadFailed)
        }
    }

    fun pickPlace(place: SavedPlace) {
        viewModelScope.launch { _events.send(PlaceSearchEvent.Picked(place)) }
    }

    fun pickCategoryResult(result: CategoryResult) =
        pickPlace(SavedPlace(result.lat, result.lng, result.name, result.address))

    fun removeRecent(place: SavedPlace) {
        places.removeRecent(place)
        viewModelScope.launch { _events.send(PlaceSearchEvent.RecentRemoved(place)) }
    }

    fun undoRemoveRecent(place: SavedPlace) = places.addRecent(place)

    /** The rider left: a place lookup still in flight must not jump to the next screen later. */
    fun cancelPending() {
        detailsJob?.cancel()
        core.update { it.copy(resolving = false) }
    }
}
