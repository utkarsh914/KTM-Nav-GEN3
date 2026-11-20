package com.navigator.app.ui.feature.rides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.rides.ImportResult
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.RecordedRide
import kotlinx.coroutines.channels.Channel
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the list shows. */
sealed interface RidesBody {
    /** No ride at all: how rides get here. */
    data object Empty : RidesBody

    /** Rides exist but none matches the search. */
    data object NoMatch : RidesBody

    data class Items(val items: List<RideListItem>) : RidesBody
}

data class RidesUi(
    val body: RidesBody,
    val query: String,
    val searchOpen: Boolean,
    val sort: RideSort,
    val selecting: Boolean,
    /** Only rides still on screen; a selection never outlives its rides. */
    val selected: Set<String>,
    val starredSelected: Int,
    val allSelected: Boolean,
    /** Starring the selection would star (true) or unstar (false) them. */
    val starWould: Boolean,
)

sealed interface RidesEvent {
    /** Rides were deleted: offer Undo, and purge them when the offer ends. */
    data class Deleted(val ids: List<String>) : RidesEvent

    data class Imported(val result: ImportResult) : RidesEvent
}

private data class Local(
    val query: String = "",
    val searchOpen: Boolean = false,
    val sort: RideSort = RideSort(),
    val selecting: Boolean = false,
    val selected: Set<String> = emptySet(),
)

class RidesViewModel(private val repo: RidesRepository) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val _events = Channel<RidesEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rides: List<RecordedRide> = emptyList()

    /** Null until the first reading, so "No rides yet" can't flash. */
    val ui: StateFlow<RidesUi?> = combine(repo.rides, local) { all, l ->
        rides = all
        val items = RidesListBuilder.build(all, l.query, l.sort)
        val body = when {
            all.isEmpty() -> RidesBody.Empty
            items.isEmpty() -> RidesBody.NoMatch
            else -> RidesBody.Items(items)
        }
        // A list that emptied out leaves selection mode for good, not until a ride appears again.
        val ids = RideSelection.visible(l.selected, items)
        // The list emptied out, or every selected ride is gone (a merge, not a search that hides them): selection ends.
        val survivors = l.selected.any { id -> all.any { it.id == id } }
        if (l.selecting && (body !is RidesBody.Items || (l.selected.isNotEmpty() && !survivors))) {
            local.update { it.copy(selecting = false, selected = emptySet()) }
        }
        val chosen = all.filter { it.id in ids }
        val visibleCount = items.count { it is RideListItem.Ride }
        RidesUi(
            body = body, query = l.query, searchOpen = l.searchOpen, sort = l.sort,
            selecting = l.selecting && body is RidesBody.Items, selected = ids,
            starredSelected = chosen.count { it.saved },
            allSelected = visibleCount > 0 && ids.size == visibleCount,
            starWould = RideSelection.starTarget(chosen),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openSearch() = local.update { it.copy(searchOpen = true) }
    fun closeSearch() = local.update { it.copy(searchOpen = false, query = "") }
    fun setQuery(q: String) = local.update { it.copy(query = q) }
    fun setSort(sort: RideSort) = local.update { it.copy(sort = sort) }

    fun startSelecting(id: String? = null) =
        local.update { it.copy(selecting = true, selected = if (id != null) it.selected + id else it.selected) }

    fun toggle(id: String) = local.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun selectAll(all: Boolean) {
        val items = RidesListBuilder.build(rides, local.value.query, local.value.sort)
        local.update { it.copy(selected = if (all) items.filterIsInstance<RideListItem.Ride>().map { r -> r.ride.id }.toSet() else emptySet()) }
    }

    fun stopSelecting() = local.update { it.copy(selecting = false, selected = emptySet()) }

    fun toggleStar(id: String) {
        val ride = rides.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { repo.setSaved(id, !ride.saved) }
    }

    /** Star the selection, or unstar it when everything in it is starred. */
    fun starSelected() {
        val ids = ui.value?.selected.orEmpty()
        val chosen = rides.filter { it.id in ids }
        val star = RideSelection.starTarget(chosen)
        viewModelScope.launch { chosen.filter { it.saved != star }.forEach { repo.setSaved(it.id, star) } }
    }

    fun deleteSelected() {
        val ids = ui.value?.selected.orEmpty().toList()
        if (ids.isEmpty()) return
        stopSelecting()
        viewModelScope.launch {
            val gone = repo.softDelete(ids)
            if (gone.isNotEmpty()) _events.send(RidesEvent.Deleted(gone))
        }
    }

    fun undoDelete(ids: List<String>) {
        viewModelScope.launch { repo.restore(ids) }
    }

    /** The Undo offer is over (taken, replaced or timed out): nothing more to restore. */
    fun purge(ids: List<String>) {
        viewModelScope.launch { repo.purge(ids) }
    }

    fun importGpx(open: () -> InputStream?, name: String?) {
        viewModelScope.launch { _events.send(RidesEvent.Imported(repo.importGpx(open, name))) }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RidesViewModel(container.rides) as T
        }
    }
}
