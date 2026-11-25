package com.navigator.app.ui.feature.replay.trim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideSource
import com.navigator.app.ride.RideTrim
import com.navigator.app.ui.feature.replay.PlaybackEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the cut would leave. */
data class Kept(val distanceMeters: Int, val durationSeconds: Int)

sealed interface TrimUi {
    data object Loading : TrimUi
    data object Gone : TrimUi
    data class Ready(
        val ride: RecordedRide,
        val points: List<RidePoint>,
        val range: TrimRange,
        /** What the cut leaves: of [range] once [settled], else of the range before it (the page keeps its height while dragging). */
        val kept: Kept? = null,
        val settled: Boolean = true,
        val applying: Boolean = false,
        /** The store refused: shown on the page, under the button's reason. */
        val failed: Boolean = false,
        /** The cut is stored: the page leaves (as state, so a close that arrives while the app is away is not lost). */
        val closed: Boolean = false,
    ) : TrimUi {
        val canApply: Boolean get() = range.changed && settled && kept != null && !applying
    }
}

sealed interface TrimEvent {
    /** Trimmed; [merged] when the ride is a merge result (its Undo offer ends). */
    data class Trimmed(val merged: Boolean) : TrimEvent
}

/** Picks the part to keep and cuts it. Nothing is held outside the page: leaving discards the choice, applying saves it. */
class TrimViewModel(
    private val rideId: String,
    private val repo: RidesRepository,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _ui = MutableStateFlow<TrimUi>(TrimUi.Loading)
    val ui: StateFlow<TrimUi> = _ui.asStateFlow()
    private val _events = Channel<TrimEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var keptJob: Job? = null

    /** The ride was there and then went (not just never found): the page leaves by itself. */
    private val _vanished = MutableStateFlow(false)
    val vanished: StateFlow<Boolean> = _vanished.asStateFlow()

    init {
        viewModelScope.launch {
            val ride = repo.get(rideId)
            val points = if (ride != null) repo.points(rideId) else emptyList()
            if (ride == null || points.size < 2) { _ui.value = TrimUi.Gone; return@launch }
            val range = TrimRange.whole(points.size)
            val kept = withContext(compute) { keptOf(points, range) }
            _ui.value = TrimUi.Ready(ride, points, range, kept = kept)
            // A ride that goes away under the page (a merge undone, deleted elsewhere) must not stay editable.
            repo.rides.collect { all ->
                if (all.none { it.id == rideId } && ready()?.applying != true) { keptJob?.cancel(); _ui.value = TrimUi.Loading; _vanished.value = true }
            }
        }
    }

    private fun ready() = _ui.value as? TrimUi.Ready

    private fun keptOf(points: List<RidePoint>, range: TrimRange): Kept? =
        RideTrim.trim(points, range.from, range.to)?.let { RideMetrics.summarize(it) }?.let { Kept(it.distanceMeters, it.durationSeconds) }

    private fun setRange(block: (TrimRange) -> TrimRange) {
        val r = ready() ?: return
        if (r.applying) return // the write is under way for the range on show
        val next = block(r.range)
        if (next == r.range) return
        _ui.value = r.copy(range = next, settled = false, failed = false)
        keptJob?.cancel()
        keptJob = viewModelScope.launch {
            val kept = withContext(compute) { keptOf(r.points, next) }
            ready()?.let { if (it.range == next) _ui.value = it.copy(kept = kept, settled = true) }
        }
    }

    fun moveStart(fraction: Float) = ready()?.let { r -> setRange { it.moveStart(PlaybackEngine.indexAt(fraction, r.points.size)) } } ?: Unit
    fun moveEnd(fraction: Float) = ready()?.let { r -> setRange { it.moveEnd(PlaybackEngine.indexAt(fraction, r.points.size)) } } ?: Unit
    fun moveNearest(fraction: Float) = ready()?.let { r -> setRange { it.moveNearest(PlaybackEngine.indexAt(fraction, r.points.size)) } } ?: Unit

    /** Move a handle by [steps] fixes (the accessibility actions). */
    fun nudgeStart(steps: Int) = setRange { it.moveStart(it.from + steps) }
    fun nudgeEnd(steps: Int) = setRange { it.moveEnd(it.to + steps) }

    /** A tap on the map: the nearest handle goes to the fix nearest the tap, if the tap was close to the route. */
    fun tapRoute(lat: Double, lng: Double, maxM: Double = PlaybackEngine.SEEK_TAP_MAX_M) {
        val r = ready() ?: return
        PlaybackEngine.nearest(r.points, lat, lng, maxM)?.let { index -> setRange { it.moveNearest(index) } }
    }

    fun apply() {
        val r = ready() ?: return
        if (!r.canApply) return
        _ui.value = r.copy(applying = true, failed = false)
        viewModelScope.launch {
            val trimmed = repo.trim(rideId, r.range.from, r.range.to, expectedCount = r.points.size)
            if (trimmed != null) {
                _events.send(TrimEvent.Trimmed(r.ride.source == RideSource.MERGED))
                ready()?.let { _ui.value = it.copy(applying = false, closed = true) }
            } else if (repo.get(rideId) == null) { _ui.value = TrimUi.Loading; _vanished.value = true }
            else ready()?.let { _ui.value = it.copy(applying = false, failed = true) }
        }
    }

    companion object {
        fun factory(rideId: String, container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TrimViewModel(rideId, container.rides) as T
        }
    }
}
