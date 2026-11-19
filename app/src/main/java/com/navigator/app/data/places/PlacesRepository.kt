package com.navigator.app.data.places

import android.content.Context
import android.content.SharedPreferences
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
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

    fun saveFavorite(place: SavedPlace, slot: FavoriteSlot) {
        store.saveFavorite(place, slot)
        refresh()
    }
}
