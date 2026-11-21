package com.navigator.app.ui.feature.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ride.ChartSeries
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideChart
import com.navigator.app.ride.RideMerge
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the replay screen shows. The marker's position is separate ([ReplayViewModel.position]): it changes every frame. */
sealed interface ReplayUi {
    data object Loading : ReplayUi

    /** No ride with that id, or its track has nothing to play. */
    data class NoTrack(val ride: RecordedRide?) : ReplayUi

    data class Ready(
        val ride: RecordedRide,
        val points: List<RidePoint>,
        val chart: ChartSeries?,
        /** Where the stops between parts fall along the track (0..1), for the seek bar and the chart. */
        val seams: List<Float>,
        /** How many parts a merged ride has (1 for any other ride): only while the track still has that many segments. */
        val parts: Int,
        val playing: Boolean = false,
        val speed: Int = PlaybackEngine.DEFAULT_SPEED,
        val following: Boolean = false,
        /** The map line's simplification in metres; 0 draws every fix. In-session, not saved. */
        val smoothingMeters: Int = DEFAULT_SMOOTHING_M,
        val showElevation: Boolean = true,
        /** Which of [seams] are joins between merged rides; the others are pauses. */
        val mergeSeams: List<Float> = emptyList(),
    ) : ReplayUi {
        val hasElevation: Boolean get() = chart?.alt != null
    }

    companion object {
        const val DEFAULT_SMOOTHING_M = 6
        val SMOOTHING_STEPS = listOf(0, 3, 6, 12, 20, 30)
    }
}

sealed interface ReplayEvent {
    /** The rider deleted the ride from here: the screen leaves and offers Undo. */
    data class Deleted(val ids: List<String>) : ReplayEvent

    /** The ride is gone (deleted elsewhere, a merge undone): nothing left to show. */
    data object Gone : ReplayEvent

    /** The rider changed a merged ride, so the offer to undo its merge is over. */
    data object MergedRideChanged : ReplayEvent
}

class ReplayViewModel(
    private val rideId: String,
    private val repo: RidesRepository,
    /** The user's speed bands and average mode come from settings; the VM only carries them to the screen. */
    val avgMode: AvgSpeedMode,
    val bands: RideMetrics.SpeedBands,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    /** Monotonic milliseconds: playback is paced by it, so a slow frame doesn't slow the replay down. */
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : ViewModel() {
    private val _ui = MutableStateFlow<ReplayUi>(ReplayUi.Loading)
    val ui: StateFlow<ReplayUi> = _ui.asStateFlow()
    private val _position = MutableStateFlow(0)

    /** The fix the marker is at. */
    val position: StateFlow<Int> = _position.asStateFlow()
    private val _events = Channel<ReplayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var playJob: Job? = null
    private var deleting = false

    init {
        viewModelScope.launch {
            load()
            repo.rides.collect { all -> onRidesChanged(all.firstOrNull { it.id == rideId }) }
        }
    }

    private suspend fun load() {
        val ride = repo.get(rideId)
        val points = if (ride != null) repo.points(rideId) else emptyList()
        _position.value = 0
        _ui.value = if (ride == null || points.size < 2) ReplayUi.NoTrack(ride) else {
            // Resampling a long track is CPU work: off the main thread.
            val built = withContext(compute) {
                Built(
                    RideChart.build(points), PlaybackEngine.seams(points),
                    PlaybackEngine.mergeSeams(points, ride.source == RideSource.MERGED, ride.parts), partsOf(ride, points),
                )
            }
            val previous = _ui.value as? ReplayUi.Ready
            ReplayUi.Ready(
                ride = ride, points = points, chart = built.chart, seams = built.seams, mergeSeams = built.mergeSeams, parts = built.parts,
                speed = previous?.speed ?: PlaybackEngine.DEFAULT_SPEED, following = previous?.following ?: false,
                smoothingMeters = previous?.smoothingMeters ?: ReplayUi.DEFAULT_SMOOTHING_M,
                showElevation = previous?.showElevation ?: true,
            )
        }
    }

    private class Built(val chart: ChartSeries?, val seams: List<Float>, val mergeSeams: List<Float>, val parts: Int)

    private suspend fun onRidesChanged(latest: RecordedRide?) {
        val shown = when (val ui = _ui.value) {
            ReplayUi.Loading -> return
            is ReplayUi.NoTrack -> ui.ride
            is ReplayUi.Ready -> ui.ride
        }
        when {
            latest == null -> if (!deleting && shown != null) { stopPlaying(); _ui.value = ReplayUi.Loading; _events.send(ReplayEvent.Gone) }
            shown == null || latest.pointCount != shown.pointCount -> { stopPlaying(); load() }
            latest != shown -> _ui.update { if (it is ReplayUi.Ready) it.copy(ride = latest) else ReplayUi.NoTrack(latest) }
        }
    }

    private fun partsOf(ride: RecordedRide, points: List<RidePoint>): Int =
        if (ride.source == RideSource.MERGED) RideMerge.partsIn(points, ride.parts) else 1

    private inline fun ready(block: (ReplayUi.Ready) -> Unit) {
        (_ui.value as? ReplayUi.Ready)?.let(block)
    }

    private fun change(block: (ReplayUi.Ready) -> ReplayUi.Ready) {
        _ui.update { if (it is ReplayUi.Ready) block(it) else it }
    }

    // ---- Playback ------------------------------------------------------------

    fun togglePlay() = ready { r ->
        if (r.playing) stopPlaying() else {
            if (_position.value >= r.points.lastIndex) _position.value = 0
            change { it.copy(playing = true) }
            playJob = viewModelScope.launch { play(r.points) }
        }
    }

    private suspend fun play(points: List<RidePoint>) {
        var carry = 0L
        var last = nowMs()
        while (true) {
            delay(FRAME_MS)
            val speed = (_ui.value as? ReplayUi.Ready)?.speed ?: break
            val now = nowMs()
            // Capped: a stall (doze, a long GC) must not make the marker jump across the track.
            val elapsed = (now - last).coerceIn(0L, MAX_FRAME_MS)
            last = now
            val step = PlaybackEngine.advance(points, _position.value, carry, elapsed * speed)
            carry = step.carryMs
            _position.value = step.index
            if (step.index >= points.lastIndex) break
        }
        change { it.copy(playing = false) }
    }

    private fun stopPlaying() {
        playJob?.cancel()
        playJob = null
        change { it.copy(playing = false) }
    }

    fun restart() {
        stopPlaying()
        _position.value = 0
    }

    /** Jump to [fraction] of the track. A tap keeps playing if it was; a scrub holds playback ([beginScrub]). */
    fun seek(fraction: Float) = ready { r -> _position.value = PlaybackEngine.indexAt(fraction, r.points.size) }

    /** The rider put a finger on the seek bar or chart: hold playback while they scrub. */
    fun beginScrub() = stopPlaying()

    /** The replay is covered or the app left: nothing should keep walking the marker. */
    fun pause() = stopPlaying()

    /** A tap on the map: the fix nearest to it, if the tap was close to the track. */
    fun seekToMap(lat: Double, lng: Double, maxM: Double = PlaybackEngine.SEEK_TAP_MAX_M) = ready { r ->
        PlaybackEngine.nearest(r.points, lat, lng, maxM)?.let { stopPlaying(); _position.value = it }
    }

    fun stepSpeed(direction: Int) = change { it.copy(speed = PlaybackEngine.stepSpeed(it.speed, direction)) }
    fun toggleFollow() = change { it.copy(following = !it.following) }
    fun stopFollowing() = change { if (it.following) it.copy(following = false) else it }
    fun toggleElevation() = change { it.copy(showElevation = !it.showElevation) }

    fun stepSmoothing(direction: Int) = change { r ->
        val steps = ReplayUi.SMOOTHING_STEPS
        val i = steps.indexOf(r.smoothingMeters).let { if (it < 0) steps.indexOf(ReplayUi.DEFAULT_SMOOTHING_M) else it }
        r.copy(smoothingMeters = steps[(i + direction).coerceIn(0, steps.lastIndex)])
    }

    // ---- The ride ------------------------------------------------------------

    fun toggleStar() = ready { r ->
        // Before the write: the offer must end even if the rider leaves this page at once.
        if (r.ride.source == RideSource.MERGED) _events.trySend(ReplayEvent.MergedRideChanged)
        viewModelScope.launch { repo.setSaved(r.ride.id, !r.ride.saved) }
    }

    fun delete() = ready { r ->
        stopPlaying()
        deleting = true
        viewModelScope.launch {
            val gone = repo.softDelete(listOf(r.ride.id))
            if (gone.isEmpty()) deleting = false else _events.send(ReplayEvent.Deleted(gone))
        }
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    companion object {
        /** One playback tick; each consumes the real time since the last one (at most [MAX_FRAME_MS]) times the speed. */
        const val FRAME_MS = 16L

        /** The most real time one tick may count. */
        const val MAX_FRAME_MS = 100L

        fun factory(rideId: String, container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val s = container.settings.legacy
                return ReplayViewModel(
                    rideId, container.rides, s.avgSpeedMode,
                    RideMetrics.SpeedBands(s.speedBandBaseKmh, s.speedBandStepKmh, s.speedBandCount),
                ) as T
            }
        }
    }
}
