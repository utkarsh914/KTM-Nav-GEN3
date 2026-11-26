package com.navigator.app.ui.feature.navigate.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navigator.app.nav.destination.RoutePreview
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ui.feature.navigate.StraightLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The Routes API behind a seam, so the view model is tested with a fake. */
fun interface RoutesApi {
    suspend fun compute(
        origin: Pair<Double, Double>,
        dest: Pair<Double, Double>,
        mode: TravelMode,
        intermediates: List<Pair<Double, Double>>,
    ): List<RoutePreview>
}

data class RoutePreviewState(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val routes: List<RoutePreview> = emptyList(),
    val selected: Int = 0,
    /** The stops [routes] were computed for: leg lines are only valid against these. */
    val forStops: List<SavedPlace> = emptyList(),
    val origin: Pair<Double, Double>? = null,
) {
    val selectedRoute: RoutePreview? get() = routes.getOrNull(selected)
}

/**
 * The route preview's data (PL2-PL4): fetches the alternatives (or the single multi-leg route when there
 * are stops) from a fresh position, and keeps which one is selected. What the stops are, and what Start
 * does, belong to the host.
 */
class RoutePreviewViewModel(
    private val api: RoutesApi,
    /** A fresh GPS fix, or null; waited for at most [FRESH_FIX_MS]. */
    private val freshLocation: suspend () -> Pair<Double, Double>?,
    private val lastKnown: () -> Pair<Double, Double>?,
    private val mode: () -> TravelMode,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    // No origin until one has been found for a load: the host takes it from here, and one read at construction can be hours old
    // by the time the screen is back (this view model outlives rotations).
    private val _state = MutableStateFlow(RoutePreviewState())
    val state: StateFlow<RoutePreviewState> = _state.asStateFlow()
    private var job: Job? = null
    private var loadedFor: Pair<SavedPlace, List<SavedPlace>>? = null

    /** A GPS fix this view model got itself, and when: the only origin it can date. */
    private var fresh: Pair<Pair<Double, Double>, Long>? = null

    /** The origin the routes on screen were computed from, and when. */
    private var computedFrom: Pair<Pair<Double, Double>, Long>? = null

    /** (Re)compute for [dest] through [stops]. A newer call replaces one still running. */
    fun load(dest: SavedPlace, stops: List<SavedPlace>) {
        // Coming back to the same plan (from search or Pick on map) keeps the routes and the rider's choice.
        // A failed or interrupted load is tried again.
        if (loadedFor == dest to stops && !_state.value.failed && job?.isCompleted != false && !routesStale()) return
        loadedFor = dest to stops
        job?.cancel()
        _state.update { it.copy(loading = true, failed = false, routes = emptyList(), selected = 0) }
        job = viewModelScope.launch {
            // A FRESH fix so route-token origins match the SDK's GPS at Start (a stale origin makes the SDK
            // snap to the nearest/fastest route).
            val gps = withTimeoutOrNull(FRESH_FIX_MS) { freshLocation() }
            if (gps != null) fresh = gps to clock()
            // Without a fix in time: the system's newest known position, unless a fix of ours is only seconds old.
            val origin = gps ?: fresh?.takeIf { clock() - it.second < RECENT_FIX_MS }?.first ?: lastKnown() ?: fresh?.first
            if (origin == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _state.update { it.copy(origin = origin) }
            // The API client reports failure as no routes; anything unexpected is a failure too, never a crash.
            val routes = try {
                api.compute(origin, dest.lat to dest.lng, mode(), stops.map { it.lat to it.lng })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            computedFrom = origin to clock()
            _state.update {
                it.copy(
                    loading = false, failed = routes.isEmpty(), routes = routes, forStops = stops, origin = origin,
                    selected = RouteSelection.clamp(it.selected, routes.size),
                )
            }
        }
    }

    /** Routes are good for a few minutes and a short way: a rider who waited, or moved, gets new ones. */
    private fun routesStale(): Boolean {
        val (from, at) = computedFrom ?: return false
        if (clock() - at > ROUTES_TTL_MS) return true
        val here = lastKnown() ?: return false
        return StraightLine.meters(from, here.first, here.second) > ROUTES_MOVED_M
    }

    fun select(index: Int) {
        _state.update { it.copy(selected = RouteSelection.clamp(index, it.routes.size)) }
    }

    /** Leave the preview: forget the routes, keep the last known position. */
    fun clear() {
        job?.cancel()
        loadedFor = null
        computedFrom = null
        _state.update { RoutePreviewState(origin = it.origin) }
    }

    companion object {
        const val FRESH_FIX_MS = 4_000L

        /** A fix of ours this young still beats the system's last known one when the next fix is slow. */
        const val RECENT_FIX_MS = 60_000L
        const val ROUTES_TTL_MS = 5 * 60_000L
        const val ROUTES_MOVED_M = 500
    }
}
