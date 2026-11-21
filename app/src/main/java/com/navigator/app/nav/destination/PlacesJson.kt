package com.navigator.app.nav.destination

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (de)serialisation for [PlacesStore], JVM-testable. Parsing is tolerant: one
 * malformed entry is skipped instead of discarding the whole list.
 */
internal object PlacesJson {

    /** Rounded-coordinate identity key. Locale.ROOT: a comma-decimal locale must not change keys. */
    fun coordKey(lat: Double, lng: Double): String = String.format(Locale.ROOT, "%.4f,%.4f", lat, lng)

    /** Result of parsing a stored list. [corrupt] is true when the JSON as a whole was unreadable. */
    class Parsed<T>(val items: List<T>, val corrupt: Boolean)

    fun parsePlaces(raw: String?): Parsed<SavedPlace> = parse(raw) { placeFrom(it) }

    fun parseFavorites(raw: String?): Parsed<FavoritePlace> = parse(raw) { o ->
        placeFrom(o)?.let { place ->
            FavoritePlace(
                place = place,
                slot = runCatching { FavoriteSlot.valueOf(o.optString("slot", "OTHER")) }
                    .getOrDefault(FavoriteSlot.OTHER),
            )
        }
    }

    private fun <T> parse(raw: String?, entry: (JSONObject) -> T?): Parsed<T> {
        if (raw.isNullOrEmpty()) return Parsed(emptyList(), corrupt = false)
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return Parsed(emptyList(), corrupt = true)
        }
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            runCatching { arr.getJSONObject(i) }.getOrNull()?.let { o ->
                runCatching { entry(o) }.getOrNull()?.let { out += it }
            }
        }
        return Parsed(out, corrupt = false)
    }

    private fun placeFrom(o: JSONObject): SavedPlace? {
        if (!o.has("lat") || !o.has("lng")) return null
        val lat = o.getDouble("lat")
        val lng = o.getDouble("lng")
        if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return SavedPlace(
            lat = lat,
            lng = lng,
            label = o.optString("label"),
            address = o.optString("address").ifBlank { null },
        )
    }

    fun placeJson(p: SavedPlace): JSONObject = JSONObject().apply {
        put("lat", p.lat)
        put("lng", p.lng)
        put("label", p.label)
        p.address?.let { put("address", it) }
    }

    fun writePlaces(places: List<SavedPlace>): String {
        val arr = JSONArray()
        places.forEach { arr.put(placeJson(it)) }
        return arr.toString()
    }

    fun writeFavorites(favs: List<FavoritePlace>): String {
        val arr = JSONArray()
        favs.forEach { fav -> arr.put(placeJson(fav.place).put("slot", fav.slot.name)) }
        return arr.toString()
    }
}
