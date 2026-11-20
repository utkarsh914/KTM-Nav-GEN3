package com.navigator.app.ui.feature.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.location.lastKnownLocation
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PlacesUi(
    val home: SavedPlace?,
    val work: SavedPlace?,
    val saved: List<SavedPlace>,
    /** Where the rider is, for straight-line distances; null when unknown. */
    val origin: Pair<Double, Double>?,
    val selecting: Boolean,
    /** Keys (see [PlacesRepository.key]) of saved places still in the list. */
    val selected: Set<String>,
) {
    val allSelected: Boolean get() = saved.isNotEmpty() && selected.size == saved.size
}

sealed interface PlacesEvent {
    /** Removed (one slot, one place or a selection); the offer is Undo. */
    data class Removed(val items: List<FavoritePlace>) : PlacesEvent

    /** A place became Home, Work or a saved place; [previous] is what it replaced. */
    data class Assigned(val place: SavedPlace, val slot: FavoriteSlot, val previous: FavoritePlace?) : PlacesEvent
}

/** The Places tab: Home, Work and the saved list over [PlacesRepository], plus selection. Writes are quick, so they run inline. */
class PlacesViewModel(
    private val repo: PlacesRepository,
    private val originProvider: () -> Pair<Double, Double>? = { null },
) : ViewModel() {
    private data class Local(val origin: Pair<Double, Double>? = null, val selecting: Boolean = false, val selected: Set<String> = emptySet())

    private val local = MutableStateFlow(Local(origin = originProvider()))
    private val _events = Channel<PlacesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var saved: List<SavedPlace> = emptyList()

    private fun build(favorites: List<FavoritePlace>, l: Local): PlacesUi {
        // One entry per spot: the list is keyed by it.
        val list = favorites.filter { it.slot == FavoriteSlot.OTHER }.map { it.place }.distinctBy(PlacesRepository::key)
        saved = list
        val chosen = l.selected.intersect(list.map(PlacesRepository::key).toSet())
        // The list emptied out: selection ends for good.
        if (l.selecting && list.isEmpty()) local.update { it.copy(selecting = false, selected = emptySet()) }
        return PlacesUi(
            home = favorites.firstOrNull { it.slot == FavoriteSlot.HOME }?.place,
            work = favorites.firstOrNull { it.slot == FavoriteSlot.WORK }?.place,
            saved = list, origin = l.origin, selecting = l.selecting && list.isNotEmpty(), selected = chosen,
        )
    }

    val ui: StateFlow<PlacesUi> = combine(repo.favorites, local, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.favorites.value, local.value))

    fun refreshOrigin() = local.update { it.copy(origin = originProvider() ?: it.origin) }

    fun startSelecting(key: String? = null) =
        local.update { it.copy(selecting = true, selected = if (key != null) it.selected + key else it.selected) }

    fun toggle(key: String) = local.update { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) }

    fun selectAll(all: Boolean) =
        local.update { it.copy(selected = if (all) saved.map(PlacesRepository::key).toSet() else emptySet()) }

    fun stopSelecting() = local.update { it.copy(selecting = false, selected = emptySet()) }

    fun removeSlot(slot: FavoriteSlot) {
        repo.removeSlot(slot)?.let { _events.trySend(PlacesEvent.Removed(listOf(it))) }
    }

    fun removePlace(place: SavedPlace) = removeSaved(setOf(PlacesRepository.key(place)))

    fun deleteSelected() {
        val keys = ui.value.selected
        if (keys.isEmpty()) return
        stopSelecting()
        removeSaved(keys)
    }

    private fun removeSaved(keys: Set<String>) {
        val gone = repo.removeSaved(keys)
        if (gone.isNotEmpty()) _events.trySend(PlacesEvent.Removed(gone))
    }

    fun assign(place: SavedPlace, slot: FavoriteSlot) {
        val previous = repo.assign(place, slot)
        _events.trySend(PlacesEvent.Assigned(place, slot, previous))
    }

    /** Undo offers outlive the screen, so these only touch the app-wide repository. */
    fun undoRemove(items: List<FavoritePlace>) = repo.restore(items)

    fun undoAssign(event: PlacesEvent.Assigned) = repo.undoAssign(event.place, event.slot, event.previous)

    companion object {
        fun factory(container: AppContainer, context: android.content.Context): ViewModelProvider.Factory {
            // Not the activity: the view model outlives it.
            val app = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlacesViewModel(container.places) { lastKnownLocation(app) } as T
            }
        }
    }
}
