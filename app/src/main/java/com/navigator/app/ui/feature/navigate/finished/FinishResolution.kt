package com.navigator.app.ui.feature.navigate.finished

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RidePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Why a finished trip has no recorded ride. */
enum class NoRecordingReason { RecordingOff, TooShort }

/** What the trip-finished screen can say about the ride (TF1-TF3). */
sealed interface FinishUi {
    /** The recorder is still finalising: the stats that depend on the saved track are skeletons. */
    data object Saving : FinishUi
    data class Recorded(val ride: RecordedRide) : FinishUi
    data class NoRecording(val reason: NoRecordingReason) : FinishUi
}

object FinishResolution {
    /** The finished screen never waits longer than this for the recorder. */
    const val TIMEOUT_MS = 6_000L

    /**
     * Saved is the ride; Discarded is "too short to keep". With recording off nothing will arrive, so that is
     * said at once instead of after the wait. Nothing yet after [timedOut] is read as too short (the
     * recorder had its time).
     */
    fun resolve(finish: RideFinish?, timedOut: Boolean, recordingEnabled: Boolean): FinishUi = when {
        finish is RideFinish.Saved -> FinishUi.Recorded(finish.ride)
        finish == RideFinish.Discarded -> FinishUi.NoRecording(NoRecordingReason.TooShort)
        !recordingEnabled -> FinishUi.NoRecording(NoRecordingReason.RecordingOff)
        timedOut -> FinishUi.NoRecording(NoRecordingReason.TooShort)
        else -> FinishUi.Saving
    }
}

data class FinishState(val ui: FinishUi = FinishUi.Saving, val points: List<RidePoint>? = null)

/**
 * Resolves the just-finished ride for the trip-finished screen: [begin] when it appears, [reset] when it is left.
 * [finish] is the recorder's outcome (cleared when a trip starts, so a non-null value is this trip's).
 */
class TripFinishedViewModel(
    private val finish: Flow<RideFinish?>,
    private val loadPoints: suspend (rideId: String) -> List<RidePoint>,
    private val recordingEnabled: () -> Boolean,
    private val timeoutMs: Long = FinishResolution.TIMEOUT_MS,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(FinishState())
    val state: StateFlow<FinishState> = _state.asStateFlow()
    private var job: Job? = null

    /** Idempotent while it is running (rotation re-enters the screen); [reset] starts it over. */
    fun begin() {
        if (job?.isActive == true) return
        _state.value = FinishState()
        // Read once: turning recording on from the summary changes the setting for the next trip, not the banner of this one.
        val enabled = recordingEnabled()
        job = (scope ?: viewModelScope).launch {
            val timedOut = MutableStateFlow(false)
            launch { delay(timeoutMs); timedOut.value = true }
            combine(finish, timedOut) { f, t -> FinishResolution.resolve(f, t, enabled) }
                .distinctUntilChanged()
                .collect { ui ->
                    // A track that can't be read leaves the stats without a map line, not a crash.
                    val points = (ui as? FinishUi.Recorded)?.let {
                        try { loadPoints(it.ride.id) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                    }
                    _state.value = FinishState(ui, points)
                }
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        _state.value = FinishState()
    }
}
