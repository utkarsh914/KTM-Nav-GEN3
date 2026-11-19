package com.navigator.app.trip

import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.Stop
import com.navigator.app.nav.model.RemainingStops
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.ride.RideEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is known about the trip once it has ended, until the rider dismisses the summary. */
data class FinishedTrip(val destination: SavedPlace, val durationSec: Int)

/**
 * The in-app trip as the app sees it. Lives with the app, not a screen, so leaving the map for
 * Settings (or Rides) does not lose the trip, its added stops or the arrival detection.
 */
data class TripState(
    /** The destination last handed to guidance: what Retry starts again and add-stop re-plans. */
    val dest: NavDestination? = null,
    /** Where the trip began (lat, lng), for "Ride back" when no track was recorded. */
    val origin: Pair<Double, Double>? = null,
    /** The route being navigated (lat, lng), for "search along route"; null once a stop re-plans it. */
    val route: List<Pair<Double, Double>>? = null,
    /** Every stop of the trip in visiting order, passed ones included. */
    val allStops: List<Stop> = emptyList(),
    val startMs: Long = 0L,
    /** The rider stepped away from the guidance view; the trip keeps running. */
    val minimised: Boolean = false,
    /** Set the moment the trip ends, so continued movement can't re-run arrival handling. */
    val arrivalLatched: Boolean = false,
    val finished: FinishedTrip? = null,
)

sealed interface TripEvent {
    /** The trip ended (arrival, stall fallback or the rider ending it). */
    data class Finished(val trip: FinishedTrip) : TripEvent
    data class StopAdded(val label: String) : TripEvent
    data class StopAddRejected(val problem: StopAddProblem) : TripEvent

    /** The re-plan with the new stop failed; the rider's earlier trip was restored. */
    data object StopAddFailed : TripEvent
}

/**
 * The single owner of the active in-app trip: start, retry, add a stop (with rollback), end, the
 * arrival detectors (SDK arrival, proximity, stall) and "Ride back". Decisions are in
 * [TripPolicies]; this holds the state and talks to [guidance]. Call from the main thread.
 */
class TripController(
    scope: CoroutineScope,
    private val guidance: GuidanceSource,
    private val isOnline: StateFlow<Boolean>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TripEvent> = _events.asSharedFlow()

    // Bookkeeping nobody observes.
    /** When the rider came within the dwell radius of the destination, or null while outside it. */
    private var nearSince: Long? = null
    private var lastStartAtMs = 0L
    private var lastStopAddMs = 0L
    private var beforeStop: NavDestination? = null
    private var allStopsBefore: List<Stop>? = null

    /** A trip we started that has not ended. */
    private val owning: Boolean get() = _state.value.let { it.dest != null && !it.arrivalLatched }

    init {
        guidance.arrivals.onEach { if (owning) end() }.launchIn(scope)

        var lastSession: NavSessionState? = null
        guidance.state.onEach { nav ->
            val session = nav.sessionState
            // The re-plan worked once guidance comes (back) to ENROUTE: nothing to undo. Only on the
            // transition: the old route's ENROUTE frames keep arriving until the re-plan starts.
            if (session == NavSessionState.ENROUTE && lastSession != NavSessionState.ENROUTE) beforeStop = null
            lastSession = session
            if (owning && session == NavSessionState.ARRIVED) {
                end()
                return@onEach
            }
            nearSince = TripPolicies.nearSince(owning, session.isActiveNav(), nav.remainingDistanceMeters, nearSince, clock())
            if (TripPolicies.dwellDone(owning, session.isActiveNav(), nearSince, clock())) end()
        }.launchIn(scope)

        // The dwell is also checked on a timer so it fires even when no fresh frames arrive (parked, GPS quiet).
        scope.launch {
            _state.map { it.dest != null && !it.arrivalLatched }.distinctUntilChanged().collectLatest { watching ->
                while (watching) {
                    delay(DWELL_TICK_MS)
                    val nav = guidance.state.value
                    if (TripPolicies.dwellDone(owning, nav.sessionState.isActiveNav(), nearSince, clock())) {
                        end()
                        break
                    }
                }
            }
        }

        guidance.startFailure.onEach { failure ->
            if (failure != null) rollBackStop()
        }.launchIn(scope)
    }

    /** Begin guidance to [dest]. False when ignored as a double tap. */
    fun start(dest: NavDestination, origin: Pair<Double, Double>?, route: List<Pair<Double, Double>>?): Boolean {
        val now = clock()
        if (now - lastStartAtMs < START_DEBOUNCE_MS) return false
        lastStartAtMs = now
        guidance.clearStartFailure()
        beforeStop = null
        allStopsBefore = null
        lastStopAddMs = 0L
        _state.value = TripState(
            dest = dest, origin = origin, route = route, allStops = dest.stops, startMs = now,
        )
        guidance.start(dest)
        nearSince = null
        // A prior ride's result must not be taken for this trip's.
        RideEvents.clear()
        return true
    }

    /** Start the last destination again (the failure card's Retry). */
    fun retry() {
        val dest = _state.value.dest ?: return
        _state.update { it.copy(arrivalLatched = false, minimised = false, finished = null) }
        nearSince = null
        RideEvents.clear()
        guidance.start(dest)
    }

    /** Let the next Start through even if it comes right after the last (leaving the failure card). */
    fun resetStartDebounce() { lastStartAtMs = 0L }

    fun setMinimised(minimised: Boolean) = _state.update { it.copy(minimised = minimised) }

    /** Whether the stop search may open now; the reason is announced as [TripEvent.StopAddRejected]. */
    fun canSearchForStop(): Boolean = check(forSearch = true) is StopAddCheck.Ok

    /** Re-plan the running trip from here via [place], then on to the destination. */
    fun addStop(place: SavedPlace): Boolean {
        val trip = _state.value
        val dest = trip.dest ?: return false
        val ahead = (check(forSearch = false) as? StopAddCheck.Ok)?.ahead ?: return false
        val plan = TripPolicies.planAddStop(dest, trip.allStops, ahead, Stop(place.lat, place.lng, place.label))
        allStopsBefore = trip.allStops
        lastStopAddMs = clock()
        beforeStop = plan.before // lets a failed re-plan fall back to the trip the rider had
        // The old polyline no longer matches the trip.
        _state.update { it.copy(dest = plan.replan, allStops = plan.allStops, route = null, arrivalLatched = false) }
        nearSince = null
        guidance.start(plan.replan)
        _events.tryEmit(TripEvent.StopAdded(place.label))
        return true
    }

    /** End the trip: freeze its duration, stop guidance and publish [TripEvent.Finished]. */
    fun end() {
        val trip = _state.value
        val dest = trip.dest ?: return
        if (trip.arrivalLatched) return // already ended (the latch outlives the summary)
        val finished = FinishedTrip(
            SavedPlace(dest.lat, dest.lng, dest.label ?: "Destination"),
            // Frozen now: computing it while drawing kept growing it.
            if (trip.startMs > 0L) ((clock() - trip.startMs) / 1000).toInt() else 0,
        )
        beforeStop = null
        _state.update { it.copy(arrivalLatched = true, minimised = false, finished = finished) }
        guidance.stop()
        _events.tryEmit(TripEvent.Finished(finished))
    }

    /**
     * The trip stopped without arriving (the rider closed the failure card, went back to the
     * route preview, or switched engine): guidance stops and the trip is no longer ours to end.
     */
    fun cancel() {
        guidance.stop()
        _state.update { if (it.dest == null) it else it.copy(arrivalLatched = true, minimised = false) }
    }

    /** The rider dismissed the summary (or left it for another trip). */
    fun acknowledgeFinish() = _state.update { if (it.finished == null) it else it.copy(finished = null) }

    fun rideBackPlan(firstPoint: Pair<Double, Double>?): RideBackPlan? =
        TripPolicies.rideBack(firstPoint, _state.value.origin, _state.value.allStops)

    private fun check(forSearch: Boolean): StopAddCheck {
        val trip = _state.value
        val nav = guidance.state.value
        val result = TripPolicies.checkAddStop(
            hasTrip = trip.dest != null,
            sessionActive = nav.sessionState.isActiveNav(),
            arrivalLatched = trip.arrivalLatched,
            online = isOnline.value,
            lastAddMs = lastStopAddMs,
            nowMs = clock(),
            forSearch = forSearch,
            ahead = { remainingStops(trip.dest!!) },
        )
        if (result is StopAddCheck.Rejected) _events.tryEmit(TripEvent.StopAddRejected(result.problem))
        return result
    }

    /** The stops of [d] still ahead, from the SDK's current target; null when it can't be told. */
    private fun remainingStops(d: NavDestination): List<Stop>? {
        if (d.stops.isEmpty()) return emptyList()
        val target = guidance.currentTarget() ?: return null
        return RemainingStops.resolve(d.stops, d.lat, d.lng, target.first, target.second)
    }

    /** A failed mid-trip re-plan must not strand the rider: go back to the trip they had. */
    private fun rollBackStop() {
        val before = beforeStop ?: return
        beforeStop = null
        lastStopAddMs = 0L
        guidance.clearStartFailure()
        val restored = before.copy(continuesRide = true, routeToken = null)
        _state.update { it.copy(dest = before, allStops = allStopsBefore ?: it.allStops) }
        allStopsBefore = null
        nearSince = null
        guidance.start(restored)
        _events.tryEmit(TripEvent.StopAddFailed)
    }

    private companion object {
        const val DWELL_TICK_MS = 5_000L
    }
}
