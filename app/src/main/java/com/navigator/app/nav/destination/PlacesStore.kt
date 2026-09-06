package com.navigator.app.nav.destination

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    private val prefs =
        context.applicationContext.getSharedPreferences("places_store", Context.MODE_PRIVATE)

    // ---- Recents ----------------------------------------------------------

    fun recents(): List<SavedPlace> =
        readPlaces(prefs.getString(KEY_RECENTS, null))

    fun addRecent(place: SavedPlace) {
        val key = coordKey(place.lat, place.lng)
        val updated = buildList {
            add(place)
            recents().forEach { if (coordKey(it.lat, it.lng) != key) add(it) }
        }.take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, writePlaces(updated)).apply()
    }

    // ---- Favorites --------------------------------------------------------

    fun favorites(): List<FavoritePlace> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FavoritePlace(
                    place = SavedPlace(
                        lat = o.getDouble("lat"),
                        lng = o.getDouble("lng"),
                        label = o.optString("label"),
                        address = o.optString("address").ifBlank { null },
                    ),
                    slot = runCatching { FavoriteSlot.valueOf(o.optString("slot", "OTHER")) }
                        .getOrDefault(FavoriteSlot.OTHER),
                )
            }
        }.getOrDefault(emptyList())
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
    fun saveFavorite(place: SavedPlace, slot: FavoriteSlot) {
        val key = coordKey(place.lat, place.lng)
        val kept = favorites().filterNot { fav ->
            when (slot) {
                FavoriteSlot.HOME, FavoriteSlot.WORK -> fav.slot == slot
                FavoriteSlot.OTHER -> coordKey(fav.place.lat, fav.place.lng) == key
            }
        }
        writeFavorites(kept + FavoritePlace(place, slot))
    }

    fun removeFavorite(lat: Double, lng: Double) {
        val key = coordKey(lat, lng)
        writeFavorites(favorites().filterNot { coordKey(it.place.lat, it.place.lng) == key })
    }

    /** Bulk-remove favorites whose rounded coordinates match any of [coordKeys]
     *  (see [favoriteKey]). Rewrites the list once. */
    fun removeFavorites(coordKeys: Set<String>) {
        if (coordKeys.isEmpty()) return
        writeFavorites(favorites().filterNot { coordKey(it.place.lat, it.place.lng) in coordKeys })
    }

    /** Stable selection/identity key for a favorite place (rounded coords). */
    fun favoriteKey(place: SavedPlace): String = coordKey(place.lat, place.lng)

    /** Remove the favorite occupying [slot] (used for HOME/WORK). */
    fun removeFavoriteSlot(slot: FavoriteSlot) {
        writeFavorites(favorites().filterNot { it.slot == slot })
    }

    // ---- JSON helpers -----------------------------------------------------

    private fun readPlaces(raw: String?): List<SavedPlace> {
        raw ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedPlace(
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    label = o.optString("label"),
                    address = o.optString("address").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writePlaces(places: List<SavedPlace>): String {
        val arr = JSONArray()
        places.forEach { arr.put(placeJson(it)) }
        return arr.toString()
    }

    private fun writeFavorites(favs: List<FavoritePlace>) {
        val arr = JSONArray()
        favs.forEach { fav ->
            arr.put(placeJson(fav.place).put("slot", fav.slot.name))
        }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply()
    }

    private fun placeJson(p: SavedPlace): JSONObject = JSONObject().apply {
        put("lat", p.lat)
        put("lng", p.lng)
        put("label", p.label)
        p.address?.let { put("address", it) }
    }

    private fun coordKey(lat: Double, lng: Double): String = "%.4f,%.4f".format(lat, lng)

    private companion object {
        const val KEY_RECENTS = "recents"
        const val KEY_FAVORITES = "favorites"
        const val MAX_RECENTS = 15
    }
}
