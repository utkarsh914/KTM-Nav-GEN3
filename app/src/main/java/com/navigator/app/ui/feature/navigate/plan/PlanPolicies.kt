package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesJson
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.trip.MAX_STOPS
import com.navigator.app.ui.map.MapPoint
import kotlin.math.abs

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

    /**
     * Where the time label of route [index] goes: its preferred spot, or the nearest alternative along the line that
     * is not close to a pin ([avoid]: where the ride starts, the stops, the destination). Null when every spot is taken.
     */
    fun calloutPoint(points: List<MapPoint>, index: Int, selected: Int, avoid: List<MapPoint>): MapPoint? {
        if (points.isEmpty()) return null
        val span = maxOf(
            points.maxOf { it.lat } - points.minOf { it.lat },
            points.maxOf { it.lng } - points.minOf { it.lng },
        )
        val clear = span * CALLOUT_CLEARANCE
        val preferred = calloutFraction(index, selected)
        val tries = listOf(preferred, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8, 0.5)
        return tries.map { points[(points.lastIndex * it).toInt()] }.firstOrNull { p ->
            avoid.none { abs(it.lat - p.lat) < clear && abs(it.lng - p.lng) < clear }
        }
    }

    private const val CALLOUT_CLEARANCE = 0.12
}

/** What Directions / Start do while another trip may be running (DL3). */
sealed interface ReplaceDecision {
    /** Nothing is running: just carry on. */
    data object Proceed : ReplaceDecision

    /** A trip is running: ask Replace / Keep / Add as a stop. */
    data object Ask : ReplaceDecision
}

/** When the plan's camera moves: once per change of what is framed (a card that resizes must not re-centre a panned map). */
object PlanFraming {
    /**
     * [waitingForRoutes]: the preview's routes are still loading, so there is nothing to frame yet. A failed load is not
     * waiting: the camera goes to the destination (the controller does that for a scene without routes).
     */
    fun reframe(pickingStop: Boolean, changed: Boolean, waitingForRoutes: Boolean): Boolean = !pickingStop && changed && !waitingForRoutes
}

object ReplacePolicy {
    /**
     * A live trip is only replaced by the rider's say-so, asked once: the place card's Replace carries into the preview
     * ([agreed]), where Start does the replacing. Until then the trip keeps running, so backing out loses nothing.
     */
    fun decide(tripLive: Boolean, agreed: Boolean = false): ReplaceDecision =
        if (tripLive && !agreed) ReplaceDecision.Ask else ReplaceDecision.Proceed
}

/** The Add-a-stop sheet of PL6: where the new stop goes and what it is called. */
object NewStop {
    /** "New stop" until the place is named (a tapped place label, or the street under the pin). */
    fun title(name: String?, fallback: String): String = name?.takeIf { it.isNotBlank() } ?: fallback
}

object PlaceSaving {
    /** Whether [place] is in the plain saved list (Home and Work are separate slots and do not count). */
    fun isSaved(favorites: List<FavoritePlace>, place: SavedPlace): Boolean {
        val key = PlacesJson.coordKey(place.lat, place.lng)
        return favorites.any {
            it.slot == FavoriteSlot.OTHER &&
                PlacesJson.coordKey(it.place.lat, it.place.lng) == key
        }
    }
}
