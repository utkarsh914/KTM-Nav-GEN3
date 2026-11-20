package com.navigator.app.ui.feature.rides.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.MergeRefusal
import com.navigator.app.ride.MergeResult
import com.navigator.app.ride.MergeToken
import com.navigator.app.ride.RecordedRide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MergeUi {
    data object Loading : MergeUi

    /** A ride in the list no longer exists (deleted meanwhile). */
    data object Gone : MergeUi

    data class Ready(val plan: MergePlan, val merging: Boolean = false) : MergeUi
}

sealed interface MergeEvent {
    data class Merged(val ride: RecordedRide, val token: MergeToken) : MergeEvent

    /** The merge was refused or failed after the preview; [reason] says why. */
    data class Failed(val reason: MergeRefusal) : MergeEvent
}

class MergeViewModel(
    private val ids: List<String>,
    private val repo: RidesRepository,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _ui = MutableStateFlow<MergeUi>(MergeUi.Loading)
    val ui: StateFlow<MergeUi> = _ui.asStateFlow()
    private val _events = Channel<MergeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val rides = ids.mapNotNull { repo.get(it) }
            if (rides.size != ids.size) { _ui.value = MergeUi.Gone; return@launch }
            val points = rides.associate { it.id to repo.points(it.id) }
            // Joining and summarising a long track is CPU work: off the main thread.
            val plan = withContext(compute) { MergePlanner.plan(rides, isActive = { false }) { points[it].orEmpty() } }
            _ui.value = MergeUi.Ready(plan)
        }
    }

    fun merge() {
        val ready = _ui.value as? MergeUi.Ready ?: return
        if (ready.plan.block != null || ready.merging) return
        _ui.value = ready.copy(merging = true)
        viewModelScope.launch {
            when (val r = repo.merge(ids)) {
                is MergeResult.Merged -> _events.send(MergeEvent.Merged(r.ride, r.token))
                is MergeResult.Refused -> {
                    _ui.value = ready.copy(merging = false)
                    _events.send(MergeEvent.Failed(r.reason))
                    // The rides changed since the preview: show them as they are now (or that one is gone).
                    if (r.reason == MergeRefusal.Failed || r.reason == MergeRefusal.MissingTrack) load()
                }
            }
        }
    }

    companion object {
        fun factory(ids: List<String>, container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MergeViewModel(ids, container.rides) as T
        }
    }
}
