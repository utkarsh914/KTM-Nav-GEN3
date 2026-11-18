package com.navigator.app.nav.destination

import android.content.Context

/** A place the rider has navigated to or saved. */
data class SavedPlace(
    val lat: Double,
    val lng: Double,
    val label: String,
    val address: String? = null,
)

/** Slot for a favorite; HOME/WORK are unique, OTHER is a plain saved place. */
enum class FavoriteSlot { HOME, WORK, OTHER }

data class FavoritePlace(
    val place: SavedPlace,
    val slot: FavoriteSlot = FavoriteSlot.OTHER,
)

/**
 * Local persistence for **recent** destinations and **favorites** (Home / Work /
 * saved), backed by a private JSON `SharedPreferences` file — see docs/architecture.md
 * §3. Deliberately lightweight: no Room/DataStore.
 *
 * Recents are de-duplicated by rounded coordinates, most-recent-first, and
 * capped at [MAX_RECENTS]. HOME/WORK favorites are unique (saving replaces the
 * existing one); OTHER favorites are keyed by coordinates.
 */
class PlacesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("places_store", Context.MODE_PRIVATE)

    // ---- Recents ----------------------------------------------------------

    fun recents(): List<SavedPlace> = readList(KEY_RECENTS, PlacesJson::parsePlaces)

    fun addRecent(place: SavedPlace) = synchronized(LOCK) {
        val key = coordKey(place.lat, place.lng)
        val updated = buildList {
            add(place)
            recents().forEach { if (coordKey(it.lat, it.lng) != key) add(it) }
        }.take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, PlacesJson.writePlaces(updated)).apply()
    }

    // ---- Favorites --------------------------------------------------------

    fun favorites(): List<FavoritePlace> = readList(KEY_FAVORITES, PlacesJson::parseFavorites)

    /** Tolerant read; an unreadable list is moved aside (not left to be overwritten by the next write). */
    private fun <T> readList(key: String, parse: (String?) -> PlacesJson.Parsed<T>): List<T> = synchronized(LOCK) {
        val raw = prefs.getString(key, null)
        val parsed = parse(raw)
        if (parsed.corrupt && raw != null) {
            prefs.edit().putString(key + "_corrupt", raw).remove(key).apply()
        }
        parsed.items
    }

    /** True if this place is in the SAVED (OTHER) list (by rounded coords). */
    fun isSaved(lat: Double, lng: Double): Boolean {
        val key = coordKey(lat, lng)
        return favorites().any { it.slot == FavoriteSlot.OTHER && coordKey(it.place.lat, it.place.lng) == key }
    }

    /**
     * Save [place] as a favorite in [slot]. HOME/WORK replace any existing entry
     * in that slot; OTHER replaces any existing favorite at the same coordinates.
     */
    fun saveFavorite(place: SavedPlace, slot: FavoriteSlot) = synchronized(LOCK) {
        val key = coordKey(place.lat, place.lng)
        val kept = favorites().filterNot { fav ->
            when (slot) {
                FavoriteSlot.HOME, FavoriteSlot.WORK -> fav.slot == slot
                FavoriteSlot.OTHER -> coordKey(fav.place.lat, fav.place.lng) == key
            }
        }
        writeFavorites(kept + FavoritePlace(place, slot))
    }

    fun removeFavorite(lat: Double, lng: Double) = synchronized(LOCK) {
        val key = coordKey(lat, lng)
        // Only the plain saved list: Home/Work live in their own slots (removeFavoriteSlot) and
        // must not vanish because a saved place happens to share their coordinates.
        writeFavorites(favorites().filterNot {
            it.slot == FavoriteSlot.OTHER && coordKey(it.place.lat, it.place.lng) == key
        })
    }

    /** Bulk-remove favorites whose rounded coordinates match any of [coordKeys]
     *  (see [favoriteKey]). Rewrites the list once. */
    fun removeFavorites(coordKeys: Set<String>) = synchronized(LOCK) {
        if (coordKeys.isEmpty()) return@synchronized
        writeFavorites(favorites().filterNot {
            it.slot == FavoriteSlot.OTHER && coordKey(it.place.lat, it.place.lng) in coordKeys
        })
    }

    /** Stable selection/identity key for a favorite place (rounded coords). */
    fun favoriteKey(place: SavedPlace): String = coordKey(place.lat, place.lng)

    /** Remove the favorite occupying [slot] (used for HOME/WORK). */
    fun removeFavoriteSlot(slot: FavoriteSlot) = synchronized(LOCK) {
        writeFavorites(favorites().filterNot { it.slot == slot })
    }

    // ---- helpers ----------------------------------------------------------

    private fun writeFavorites(favs: List<FavoritePlace>) {
        prefs.edit().putString(KEY_FAVORITES, PlacesJson.writeFavorites(favs)).apply()
        // Keep the launcher's "Ride home / work" shortcuts in step with Home / Work.
        PlaceShortcuts.sync(appContext, favs)
    }

    /** The raw stored lists, for a backup file. */
    fun exportRaw(): Pair<String, String> = synchronized(LOCK) {
        (prefs.getString(KEY_RECENTS, null) ?: "[]") to (prefs.getString(KEY_FAVORITES, null) ?: "[]")
    }

    /**
     * Merge places from a backup: what is already here wins (see [PlacesMerge]). Returns how many
     * places were added.
     */
    fun mergeFromBackup(recentsRaw: String?, favoritesRaw: String?): Int = synchronized(LOCK) {
        val haveFavs = favorites()
        val favs = PlacesMerge.favorites(haveFavs, PlacesJson.parseFavorites(favoritesRaw).items)
        if (favs.size != haveFavs.size) writeFavorites(favs)
        val haveRecents = recents()
        val rec = PlacesMerge.recents(haveRecents, PlacesJson.parsePlaces(recentsRaw).items, MAX_RECENTS)
        if (rec != haveRecents) prefs.edit().putString(KEY_RECENTS, PlacesJson.writePlaces(rec)).apply()
        (favs.size - haveFavs.size) + (rec.size - haveRecents.size).coerceAtLeast(0)
    }

    /** Re-publish the launcher shortcuts from what is stored (app start). */
    fun syncShortcuts() = PlaceShortcuts.sync(appContext, favorites())

    private fun coordKey(lat: Double, lng: Double): String = PlacesJson.coordKey(lat, lng)

    private companion object {
        const val KEY_RECENTS = "recents"
        const val KEY_FAVORITES = "favorites"
        const val MAX_RECENTS = 15
        val LOCK = Any()
    }
}
