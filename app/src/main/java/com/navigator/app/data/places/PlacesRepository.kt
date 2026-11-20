package com.navigator.app.data.places

import android.content.Context
import android.content.SharedPreferences
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesJson
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable saved and recent places over [PlacesStore]. Anything that writes the store (the old
 * screens, backup restore, the launcher shortcuts sync) shows up here because the preferences file
 * is watched, so UI state comes from [favorites] and [recents] and never from a fresh store read.
 */
class PlacesRepository(context: Context, private val store: PlacesStore = PlacesStore(context)) {
    private val _favorites = MutableStateFlow(store.favorites())
    val favorites: StateFlow<List<FavoritePlace>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(store.recents())
    val recents: StateFlow<List<SavedPlace>> = _recents.asStateFlow()

    // Held strongly: SharedPreferences keeps its listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    init {
        context.applicationContext.getSharedPreferences(PlacesStore.PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun refresh() {
        _favorites.value = store.favorites()
        _recents.value = store.recents()
    }

    fun removeRecent(place: SavedPlace) {
        store.removeRecent(place.lat, place.lng)
        refresh()
    }

    fun addRecent(place: SavedPlace) {
        store.addRecent(place)
        refresh()
    }

    fun removeFavorite(place: SavedPlace) {
        store.removeFavorite(place.lat, place.lng)
        refresh()
    }

    fun saveFavorite(place: SavedPlace, slot: FavoriteSlot) {
        store.saveFavorite(place, slot)
        refresh()
    }

    /**
     * Save [place] in [slot] and return what it replaced (the previous Home or Work, or the saved place at the same
     * spot), so a snackbar can offer Undo with [undoAssign].
     */
    fun assign(place: SavedPlace, slot: FavoriteSlot): FavoritePlace? {
        val previous = occupant(place, slot)
        store.saveFavorite(place, slot)
        refresh()
        return previous
    }

    /** Undo an [assign]: the slot (or the spot) goes back to [previous], or to nothing; not if the slot was changed again since. */
    fun undoAssign(place: SavedPlace, slot: FavoriteSlot, previous: FavoritePlace?) {
        // A later change to the same Home or Work is the rider's newer wish: leave it be.
        if (slot != FavoriteSlot.OTHER && _favorites.value.firstOrNull { it.slot == slot }?.place?.let { key(it) != key(place) } != false) return
        if (slot == FavoriteSlot.OTHER) store.removeFavorite(place.lat, place.lng) else store.removeFavoriteSlot(slot)
        previous?.let { store.saveFavorite(it.place, it.slot) }
        refresh()
    }

    /** Clear Home or Work; returns what was there. */
    fun removeSlot(slot: FavoriteSlot): FavoritePlace? {
        val had = _favorites.value.firstOrNull { it.slot == slot }
        store.removeFavoriteSlot(slot)
        refresh()
        return had
    }

    /** Remove saved places by [keys] (see [key]); returns the ones that were removed. */
    fun removeSaved(keys: Set<String>): List<FavoritePlace> {
        val had = store.favorites().filter { it.slot == FavoriteSlot.OTHER && key(it.place) in keys }
        store.removeFavorites(keys)
        refresh()
        return had
    }

    /** Put removed places back (Undo). They go to the end of the list. */
    fun restore(items: List<FavoritePlace>) {
        items.forEach { store.saveFavorite(it.place, it.slot) }
        refresh()
    }

    private fun occupant(place: SavedPlace, slot: FavoriteSlot): FavoritePlace? = store.favorites().firstOrNull {
        if (slot == FavoriteSlot.OTHER) it.slot == FavoriteSlot.OTHER && key(it.place) == key(place) else it.slot == slot
    }

    companion object {
        /** What identifies a saved place (its rounded coordinates), for selections. */
        fun key(place: SavedPlace): String = PlacesJson.coordKey(place.lat, place.lng)
    }
}
