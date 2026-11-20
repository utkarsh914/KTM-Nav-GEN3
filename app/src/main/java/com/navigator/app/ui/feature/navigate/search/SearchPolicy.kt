package com.navigator.app.ui.feature.navigate.search

import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PlacesJson
import com.navigator.app.nav.destination.SavedPlace

/** Why the search is being opened: what the picked place will be used for. */
sealed interface SearchMode {
    /** The trip's destination. */
    data object Destination : SearchMode

    /** An extra stop in the route preview. */
    data object Stop : SearchMode

    /** A stop for the trip that is running (categories search along the route that is still ahead). */
    data object TripStop : SearchMode

    /** Choosing the place for an unset Home or Work. */
    data class Pick(val slot: FavoriteSlot) : SearchMode
}

/** Which list the search shows. */
enum class SearchView { Browse, Results, Category }

/** The search's pure rules: when to search, which list to show, the offline fallback, the bold match. */
object SearchPolicy {
    const val MIN_CHARS = 3
    const val DEBOUNCE_MS = 300L

    fun searchable(query: String): Boolean = query.trim().length >= MIN_CHARS

    fun view(query: String, category: PlaceCategory?): SearchView = when {
        searchable(query) -> SearchView.Results
        query.isBlank() && category != null -> SearchView.Category
        else -> SearchView.Browse
    }

    /**
     * Saved and recent places that match [query], saved first and each place once. This is what keeps
     * the search useful offline or when the service fails.
     */
    fun localMatches(query: String, favorites: List<FavoritePlace>, recents: List<SavedPlace>): List<SavedPlace> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        return (favorites.map { it.place } + recents).filter { p ->
            (p.label.contains(q, ignoreCase = true) || p.address?.contains(q, ignoreCase = true) == true) &&
                seen.add(PlacesJson.coordKey(p.lat, p.lng))
        }
    }

    /**
     * The saved places the empty search lists. Destination search shows Home and Work as tiles on the
     * browse screen, so only the other saved places; adding a stop has no tiles, so Home and Work come first.
     */
    fun browseFavorites(favorites: List<FavoritePlace>, mode: SearchMode): List<FavoritePlace> {
        val withSlots = mode is SearchMode.Stop || mode is SearchMode.TripStop
        return favorites.filter { it.slot == FavoriteSlot.OTHER || withSlots }
            .sortedBy { if (it.slot == FavoriteSlot.HOME) 0 else if (it.slot == FavoriteSlot.WORK) 1 else 2 }
    }

    /** Where [query] first occurs in [text] (any case), to draw it in bold; null when it does not. */
    fun matchRange(text: String, query: String): IntRange? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val i = text.indexOf(q, ignoreCase = true)
        return if (i < 0) null else i until i + q.length
    }
}
