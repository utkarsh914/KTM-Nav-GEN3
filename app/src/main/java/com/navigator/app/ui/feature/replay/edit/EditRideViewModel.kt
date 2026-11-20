package com.navigator.app.ui.feature.replay.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSource
import com.navigator.app.ride.RideTrim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EditUi {
    data object Loading : EditUi
    data object Gone : EditUi
    data class Ready(val name: String, val notes: String, val saved: Boolean) : EditUi
}

sealed interface EditEvent {
    data class Deleted(val ids: List<String>) : EditEvent
    data object Gone : EditEvent

}

/**
 * Edits a ride's name, notes and star. Nothing is held back: every change is saved after a short pause, and leaving
 * saves what is pending, so a tab tap (which closes the page) never loses typing. [saveScope] outlives the page.
 */
class EditRideViewModel(
    private val rideId: String,
    private val repo: RidesRepository,
    private val saveScope: CoroutineScope,
    private val debounceMs: Long = SAVE_DELAY_MS,
    /** Called the moment a merged ride is first changed, before anything is saved: the page may be left at once. */
    private val onMergedRideChanged: () -> Unit = {},
) : ViewModel() {
    private val _ui = MutableStateFlow<EditUi>(EditUi.Loading)
    val ui: StateFlow<EditUi> = _ui.asStateFlow()
    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pending: Job? = null
    private var dirty = false
    private var merged = false
    private var announcedMerged = false
    private var deleting = false

    init {
        viewModelScope.launch {
            val ride = repo.get(rideId)
            if (ride == null) { _ui.value = EditUi.Gone; return@launch }
            merged = ride.source == RideSource.MERGED
            _ui.value = EditUi.Ready(ride.destinationLabel.orEmpty(), ride.notes.orEmpty(), ride.saved)
            repo.rides.collect { all -> onRidesChanged(all.firstOrNull { it.id == rideId }) }
        }
    }

    private suspend fun onRidesChanged(latest: RecordedRide?) {
        if (latest == null) {
            if (!deleting) _events.send(EditEvent.Gone)
            return
        }
        // The text is the rider's own while they type; only the star can change under them (from the replay).
        _ui.update { if (it is EditUi.Ready && it.saved != latest.saved) it.copy(saved = latest.saved) else it }
    }

    fun onName(value: String) = edit { it.copy(name = value.take(RideTrim.MAX_NAME)) }
    fun onNotes(value: String) = edit { it.copy(notes = value.take(RideTrim.MAX_NOTES)) }

    private fun edit(block: (EditUi.Ready) -> EditUi.Ready) {
        val before = _ui.value as? EditUi.Ready ?: return
        val after = block(before)
        if (after == before) return
        mergedChanged()
        _ui.value = after
        dirty = true
        pending?.cancel()
        pending = saveScope.launch { delay(debounceMs); save() }
    }

    private suspend fun save() {
        val now = _ui.value as? EditUi.Ready ?: return
        dirty = false
        repo.updateMeta(rideId, now.name, now.notes)
    }

    private fun mergedChanged() {
        if (merged && !announcedMerged) { announcedMerged = true; onMergedRideChanged() }
    }

    /** Save what is pending now: the page is being left. */
    fun flush() {
        pending?.cancel()
        if (dirty) saveScope.launch { save() }
    }

    fun toggleStar() {
        val now = _ui.value as? EditUi.Ready ?: return
        mergedChanged()
        _ui.value = now.copy(saved = !now.saved)
        saveScope.launch { repo.setSaved(rideId, !now.saved) }
    }

    fun delete() {
        deleting = true
        viewModelScope.launch {
            val gone = repo.softDelete(listOf(rideId))
            if (gone.isEmpty()) deleting = false else {
                // Only now is typed text moot: a refused delete must not lose it.
                pending?.cancel()
                dirty = false
                _events.send(EditEvent.Deleted(gone))
            }
        }
    }

    override fun onCleared() = flush()

    companion object {
        const val SAVE_DELAY_MS = 500L

        fun factory(rideId: String, container: AppContainer, onMergedRideChanged: () -> Unit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditRideViewModel(rideId, container.rides, container.appScope, onMergedRideChanged = onMergedRideChanged) as T
        }
    }
}
