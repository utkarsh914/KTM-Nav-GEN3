package com.navigator.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.navigator.app.trip.MAX_STOPS
import com.navigator.app.nav.destination.PlacesJson
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NavDestination
import org.json.JSONObject

internal enum class NavStage { BROWSE, SEARCH, CONFIRM, PREVIEW, NAVIGATING, TRIP_FINISHED }

/** Where a place picked in the search panel should be applied. */
internal enum class SearchTarget {
    DESTINATION, STOP,
    /** Adding a stop to the trip that is being navigated right now (search opened from guidance). */
    TRIP_STOP,
}

/** What the screen must do besides applying the new [NavFlowState] after a Back press. */
internal enum class BackEffect {
    NONE,
    /** Run the full reset (cancel lookups, clear preview, ...). */
    BACK_TO_BROWSE,
    MOVE_AWAY_FROM_NAV,
    RESUME_NAV,
    /** Nothing left to step back to: the system handles Back. */
    LEAVE,
}

internal data class BackOutcome(val state: NavFlowState, val effect: BackEffect = BackEffect.NONE)

/**
 * The map home's trip-planning flow as plain immutable data plus pure transitions, so it is
 * JVM-unit-testable and can be persisted (see [NavFlowCodec]) across activity recreation
 * and process death. Side effects (network, SDK calls, store writes) stay in the screen.
 */
internal data class NavFlowState(
    val stage: NavStage = NavStage.BROWSE,
    val selected: SavedPlace? = null,
    val stops: List<SavedPlace> = emptyList(),
    val searchTarget: SearchTarget = SearchTarget.DESTINATION,
    /** True when the CONFIRM card was reached from the search list (Back returns there). */
    val confirmFromSearch: Boolean = true,
    val query: String = "",
) {
    fun openSearch(target: SearchTarget): NavFlowState =
        copy(query = "", searchTarget = target, stage = NavStage.SEARCH)

    /** Apply a picked place either as the destination or as an appended stop. */
    fun applyPicked(p: SavedPlace): NavFlowState =
        if (searchTarget == SearchTarget.TRIP_STOP) {
            // Back to guidance; the screen re-plans the live trip with the new stop.
            copy(searchTarget = SearchTarget.DESTINATION, stage = NavStage.NAVIGATING)
        } else if (searchTarget == SearchTarget.STOP) {
            copy(
                stops = if (stops.size < MAX_STOPS) stops + p else stops,
                searchTarget = SearchTarget.DESTINATION,
                stage = NavStage.PREVIEW,
            )
        } else {
            // Stops belong to the previous destination.
            copy(selected = p, stops = emptyList(), confirmFromSearch = true, stage = NavStage.CONFIRM)
        }

    /** Pick from somewhere other than the search list (pin drop, link, saved place). */
    fun choose(p: SavedPlace): NavFlowState =
        copy(selected = p, stops = emptyList(), confirmFromSearch = false, stage = NavStage.CONFIRM)

    fun backToBrowse(): NavFlowState =
        copy(
            stage = NavStage.BROWSE, selected = null, query = "",
            stops = emptyList(), searchTarget = SearchTarget.DESTINATION,
        )

    /** End of a trip: keep [selected] (the finished card names it), clear transient planning state. */
    fun finishTrip(): NavFlowState =
        copy(stage = NavStage.TRIP_FINISHED, query = "", stops = emptyList(), searchTarget = SearchTarget.DESTINATION)

    /** One-tap route to a saved place (Home / Work chip, launcher shortcut): straight to the preview. */
    fun quickRoute(place: SavedPlace): NavFlowState = rideBack(place, emptyList())

    /** "Ride back": plan the reverse trip from the finished one (origin becomes the destination, stops reversed). */
    fun rideBack(origin: SavedPlace, tripStops: List<SavedPlace>): NavFlowState =
        NavFlowState(
            stage = NavStage.PREVIEW, selected = origin,
            stops = tripStops.asReversed().take(MAX_STOPS), confirmFromSearch = false,
        )

    fun back(tripLive: Boolean): BackOutcome = when (stage) {
        NavStage.PREVIEW -> BackOutcome(copy(stage = NavStage.CONFIRM))
        // Back goes where the rider came from: the search list if they picked from it,
        // the map if they dropped a pin / opened a link / tapped a saved place.
        NavStage.CONFIRM ->
            if (confirmFromSearch) BackOutcome(copy(stage = NavStage.SEARCH, selected = null))
            else BackOutcome(this, BackEffect.BACK_TO_BROWSE)
        // Back while navigating steps away to the map WITHOUT ending the trip.
        NavStage.NAVIGATING -> BackOutcome(this, BackEffect.MOVE_AWAY_FROM_NAV)
        NavStage.TRIP_FINISHED -> BackOutcome(this, BackEffect.BACK_TO_BROWSE)
        NavStage.SEARCH -> when (searchTarget) {
            SearchTarget.STOP ->
                BackOutcome(copy(searchTarget = SearchTarget.DESTINATION, stage = NavStage.PREVIEW))
            // Cancelled adding a stop: straight back to the live guidance.
            SearchTarget.TRIP_STOP ->
                BackOutcome(copy(searchTarget = SearchTarget.DESTINATION, stage = NavStage.NAVIGATING), BackEffect.RESUME_NAV)
            SearchTarget.DESTINATION -> BackOutcome(this, BackEffect.BACK_TO_BROWSE)
        }
        // At the map: a trip still running in the background is resumed, otherwise Back is the system's.
        NavStage.BROWSE ->
            BackOutcome(this, if (tripLive) BackEffect.RESUME_NAV else BackEffect.LEAVE)
    }

    /**
     * State to adopt after the process was killed and restored. Only the planning stages
     * survive: NAVIGATING / TRIP_FINISHED depended on a live in-process trip, and a stage
     * that needs a destination falls back to the map when it is missing.
     */
    fun restored(): NavFlowState {
        val fresh = NavFlowState()
        return when (stage) {
            NavStage.BROWSE, NavStage.NAVIGATING, NavStage.TRIP_FINISHED -> fresh
            NavStage.SEARCH -> when {
                searchTarget == SearchTarget.TRIP_STOP -> fresh // the live trip died with the process
                searchTarget == SearchTarget.STOP && selected == null -> fresh
                else -> this
            }
            NavStage.CONFIRM, NavStage.PREVIEW -> if (selected == null) fresh else this
        }
    }
}

/** Pure session/stage decisions shared by the screen's effects. */
internal object NavFlowRules {
    /** We "own" a trip on this screen while showing guidance, or while stepped away on the map with it still running. */
    fun owningTrip(stage: NavStage, leftNavManually: Boolean): Boolean =
        stage == NavStage.NAVIGATING ||
            (leftNavManually && (stage == NavStage.BROWSE || stage == NavStage.SEARCH))

    /**
     * Resume the NAVIGATING view whenever a trip is live but we're not showing it (activity
     * recreated, notification tapped) - but never from the completion screen (late ENROUTE
     * frames after arrival) nor when the rider deliberately stepped away.
     */
    fun shouldAutoResume(sessionActive: Boolean, stage: NavStage, leftNavManually: Boolean): Boolean =
        sessionActive && stage != NavStage.NAVIGATING && stage != NavStage.TRIP_FINISHED && !leftNavManually
}

/** (De)serialisation of [NavFlowState] for SavedStateHandle; tolerant of corrupt input. */
internal object NavFlowCodec {
    fun encode(s: NavFlowState): String = JSONObject().apply {
        put("stage", s.stage.name)
        put("selected", PlacesJson.writePlaces(listOfNotNull(s.selected)))
        put("stops", PlacesJson.writePlaces(s.stops))
        put("target", s.searchTarget.name)
        put("fromSearch", s.confirmFromSearch)
        put("query", s.query)
    }.toString()

    fun decode(raw: String?): NavFlowState {
        if (raw.isNullOrBlank()) return NavFlowState()
        return runCatching {
            val o = JSONObject(raw)
            NavFlowState(
                stage = runCatching { NavStage.valueOf(o.optString("stage")) }.getOrDefault(NavStage.BROWSE),
                selected = PlacesJson.parsePlaces(o.optString("selected")).items.firstOrNull(),
                stops = PlacesJson.parsePlaces(o.optString("stops")).items.take(MAX_STOPS),
                searchTarget = runCatching { SearchTarget.valueOf(o.optString("target")) }
                    .getOrDefault(SearchTarget.DESTINATION),
                confirmFromSearch = o.optBoolean("fromSearch", true),
                query = o.optString("query"),
            )
        }.getOrDefault(NavFlowState())
    }
}

/**
 * Holds the map home's planning state across activity recreation (in memory) and process death
 * (through [SavedStateHandle]). The running trip is not here: see TripController.
 */
internal class NavFlowViewModel(private val handle: SavedStateHandle) : ViewModel() {

    var state by mutableStateOf(NavFlowCodec.decode(handle.get<String>(KEY)).restored())
        private set

    fun update(transform: (NavFlowState) -> NavFlowState) {
        state = transform(state)
        handle[KEY] = NavFlowCodec.encode(state)
    }

    var stage: NavStage
        get() = state.stage
        set(v) = update { it.copy(stage = v) }
    var selected: SavedPlace?
        get() = state.selected
        set(v) = update { it.copy(selected = v) }
    var stops: List<SavedPlace>
        get() = state.stops
        set(v) = update { it.copy(stops = v) }
    var searchTarget: SearchTarget
        get() = state.searchTarget
        set(v) = update { it.copy(searchTarget = v) }
    var confirmFromSearch: Boolean
        get() = state.confirmFromSearch
        set(v) = update { it.copy(confirmFromSearch = v) }
    var query: String
        get() = state.query
        set(v) = update { it.copy(query = v) }

    private var attached = 0

    /** A screen instance entered composition. Pair with [detach]. */
    fun attach() { attached++ }

    /**
     * A screen instance left composition. When the LAST one leaves for good (not a
     * configuration change) the flow is reset like the old per-composition `remember` did.
     * Counting matters because AnimatedContent disposes the old screen only after the new
     * one composed: a quick leave-and-return must not wipe the live screen's state.
     */
    fun detach(configChange: Boolean) {
        attached = (attached - 1).coerceAtLeast(0)
        if (attached == 0 && !configChange) resetForScreenExit()
    }

    /** Back to a fresh planning state (the trip itself lives in the TripController). */
    fun resetForScreenExit() {
        update { NavFlowState() }
    }

    private companion object {
        const val KEY = "nav_flow"
    }
}
