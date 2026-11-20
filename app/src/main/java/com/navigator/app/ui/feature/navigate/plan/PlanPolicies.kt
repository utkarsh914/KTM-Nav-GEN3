package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.trip.MAX_STOPS

/** The editable stops between the rider and the destination (PL3), as pure list operations. */
object StopList {
    const val MAX = MAX_STOPS

    fun remaining(stops: List<SavedPlace>): Int = (MAX - stops.size).coerceAtLeast(0)

    fun canAdd(stops: List<SavedPlace>): Boolean = stops.size < MAX

    /** The new stop goes in just before the destination, i.e. last in the list. Unchanged when full. */
    fun add(stops: List<SavedPlace>, place: SavedPlace): List<SavedPlace> =
        if (canAdd(stops)) stops + place else stops

    fun removeAt(stops: List<SavedPlace>, index: Int): List<SavedPlace> =
        if (index in stops.indices) stops.filterIndexed { i, _ -> i != index } else stops

    /** Put [place] back where it was (Undo); clamped when the list has changed since. */
    fun restore(stops: List<SavedPlace>, index: Int, place: SavedPlace): List<SavedPlace> =
        if (!canAdd(stops)) stops else stops.toMutableList().apply { add(index.coerceIn(0, size), place) }

    /** Move one stop by [delta] rows; unchanged when that leaves the list. */
    fun move(stops: List<SavedPlace>, index: Int, delta: Int): List<SavedPlace> {
        val to = index + delta
        if (index !in stops.indices || to !in stops.indices || delta == 0) return stops
        return stops.toMutableList().apply { add(to, removeAt(index)) }
    }

    /** The number the next stop will get (1-based). */
    fun nextNumber(stops: List<SavedPlace>): Int = stops.size + 1
}

enum class RouteBadge { Fastest, Alternative, Only }

enum class DelayTone { OnTime, Minor, Heavy }

/** Which route is which, and how its delay reads (PL2). The routes come fastest first. */
object RouteSelection {
    /** With stops there is a single multi-leg route; otherwise the first is the fastest. */
    fun badge(index: Int, stopCount: Int): RouteBadge = when {
        stopCount > 0 -> RouteBadge.Only
        index == 0 -> RouteBadge.Fastest
        else -> RouteBadge.Alternative
    }

    /** 1-based number for "Alternative n". */
    fun alternativeNumber(index: Int): Int = index.coerceAtLeast(1)

    fun delayTone(delaySeconds: Int): DelayTone = when (delaySeconds / 60) {
        0 -> DelayTone.OnTime
        in 1..14 -> DelayTone.Minor
        else -> DelayTone.Heavy
    }

    fun delayMinutes(delaySeconds: Int): Int = delaySeconds / 60

    /** A selection that is still valid after the list of routes changed. */
    fun clamp(selected: Int, routeCount: Int): Int = if (routeCount <= 0) 0 else selected.coerceIn(0, routeCount - 1)

    /** Where on each route its time label sits, so labels of nearly identical routes do not overlap. */
    fun calloutFraction(index: Int, selected: Int): Double = when {
        index == selected -> 0.5
        index % 2 == 0 -> 0.68
        else -> 0.32
    }
}

/** What Directions / Start do while another trip may be running (DL3). */
sealed interface ReplaceDecision {
    /** Nothing is running: just carry on. */
    data object Proceed : ReplaceDecision

    /** A trip is running: ask Replace / Keep / Add as a stop. */
    data object Ask : ReplaceDecision
}

object ReplacePolicy {
    fun decide(tripLive: Boolean): ReplaceDecision = if (tripLive) ReplaceDecision.Ask else ReplaceDecision.Proceed
}

/** The Add-a-stop sheet of PL6: where the new stop goes and what it is called. */
object NewStop {
    /** "New stop" until the place is named (a tapped place label, or the street under the pin). */
    fun title(name: String?, fallback: String): String = name?.takeIf { it.isNotBlank() } ?: fallback
}

object PlaceSaving {
    /** Whether [place] is in the plain saved list (Home and Work are separate slots and do not count). */
    fun isSaved(favorites: List<com.navigator.app.nav.destination.FavoritePlace>, place: SavedPlace): Boolean {
        val key = com.navigator.app.nav.destination.PlacesJson.coordKey(place.lat, place.lng)
        return favorites.any {
            it.slot == com.navigator.app.nav.destination.FavoriteSlot.OTHER &&
                com.navigator.app.nav.destination.PlacesJson.coordKey(it.place.lat, it.place.lng) == key
        }
    }
}
