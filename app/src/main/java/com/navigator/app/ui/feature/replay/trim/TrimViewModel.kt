package com.navigator.app.ui.feature.replay.trim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface TrimUi {
    data object Loading : TrimUi
    data object Gone : TrimUi
    data class Ready(val ride: RecordedRide, val points: List<RidePoint>, val applying: Boolean = false) : TrimUi
}

sealed interface TrimEvent {
    /** Trimmed; [merged] when the ride is a merge result (its Undo offer ends). */
    data class Trimmed(val merged: Boolean) : TrimEvent
    data object Failed : TrimEvent
}

/** Loads a ride's track and cuts it. The cut is the whole edit: nothing is held on the page. */
class TrimViewModel(private val rideId: String, private val repo: RidesRepository) : ViewModel() {
    private val _ui = MutableStateFlow<TrimUi>(TrimUi.Loading)
    val ui: StateFlow<TrimUi> = _ui.asStateFlow()
    private val _events = Channel<TrimEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val ride = repo.get(rideId)
            val points = if (ride != null) repo.points(rideId) else emptyList()
            _ui.value = if (ride == null || points.size < 2) TrimUi.Gone else TrimUi.Ready(ride, points)
        }
    }

    fun apply(from: Int, to: Int) {
        val ready = _ui.value as? TrimUi.Ready ?: return
        if (ready.applying) return
        _ui.value = ready.copy(applying = true)
        viewModelScope.launch {
            val trimmed = repo.trim(rideId, from, to)
            if (trimmed != null) _events.send(TrimEvent.Trimmed(ready.ride.source == RideSource.MERGED))
            else {
                _ui.value = ready.copy(applying = false)
                _events.send(TrimEvent.Failed)
            }
        }
    }

    companion object {
        fun factory(rideId: String, container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TrimViewModel(rideId, container.rides) as T
        }
    }
}
