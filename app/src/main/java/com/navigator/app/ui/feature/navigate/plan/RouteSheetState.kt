package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.RoutePreview
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import java.time.ZoneId

/** One route in the list (PL2): its time, distance, which it is and how much traffic it carries. */
data class RouteOptionUi(
    val time: String,
    val distance: String,
    val badge: RouteBadge,
    val alternative: Int,
    val delayTone: DelayTone,
    val delayMinutes: Int,
)

data class RouteSheetState(
    val destination: String,
    val stops: List<String>,
    val options: List<RouteOptionUi>,
    val selected: Int,
    val loading: Boolean,
    val failed: Boolean,
    /** Per-leg lines ("arrive 14:32 · 12 min · 8.4 km"), one for each of origin→stop…→destination; empty when unknown. */
    val legLines: List<String>,
    /** "1 h 05 min · 42 km · arrive 6:36 pm" for the selected route; shown in the header when stops replace the alternatives. */
    val summary: String?,
) {
    val canAddStop: Boolean get() = stops.size < StopList.MAX
    val stopsLeft: Int get() = (StopList.MAX - stops.size).coerceAtLeast(0)
}

object RouteSheetMapper {
    fun options(routes: List<RoutePreview>, stopCount: Int): List<RouteOptionUi> = routes.mapIndexed { i, r ->
        RouteOptionUi(
            time = StopLegs.duration(r.durationSeconds),
            distance = DistanceFormatter.format(r.distanceMeters, DistanceUnits.METRIC),
            badge = RouteSelection.badge(i, stopCount),
            alternative = RouteSelection.alternativeNumber(i),
            delayTone = RouteSelection.delayTone(r.delaySeconds),
            delayMinutes = RouteSelection.delayMinutes(r.delaySeconds),
        )
    }

    /** The time label on each route of the map. Only alternatives need telling apart: a single route's time is in the sheet. */
    fun callouts(routes: List<RoutePreview>, stopCount: Int): List<String> =
        if (stopCount > 0 || routes.size < 2) emptyList() else routes.map { StopLegs.duration(it.durationSeconds) }

    /** Stops that share a name get their address (or a number) added, so a reorder can be seen. */
    fun distinctLabels(stops: List<SavedPlace>): List<String> {
        val count = stops.groupingBy { it.label }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return stops.map { s ->
            if ((count[s.label] ?: 0) < 2) s.label else {
                val n = (seen[s.label] ?: 0) + 1
                seen[s.label] = n
                val extra = s.address?.takeIf { it.isNotBlank() && it != s.label }
                if (extra != null) "${s.label} · $extra" else "${s.label} ($n)"
            }
        }
    }

    /** "20 min · 6.8 km · arrive 6:36 pm · +2 min traffic" (PL3); the traffic part only when there is some. */
    fun summary(o: RouteOptionUi, durationSeconds: Int, nowMs: Long): String = buildList {
        add(o.time)
        add(o.distance)
        add("arrive " + ClockFormat.local(nowMs + durationSeconds * 1000L, ZoneId.systemDefault()))
        if (o.delayTone != DelayTone.OnTime) add("+${o.delayMinutes} min traffic")
    }.joinToString(" \u00b7 ")

    fun state(
        destination: SavedPlace,
        stops: List<SavedPlace>,
        preview: RoutePreviewState,
        nowMs: Long,
    ): RouteSheetState {
        val options = options(preview.routes, stops.size)
        val selected = preview.selectedRoute
        // Leg lines only fit the stops the routes were computed for: right after a reorder, add or remove the
        // old legs would otherwise show against the new order until the new routes land.
        val legLines = if (preview.forStops != stops || selected == null) emptyList()
        else StopLegs.lines(selected.legs, stops.size + 1, nowMs)
        return RouteSheetState(
            destination = destination.label,
            stops = distinctLabels(stops),
            options = options,
            selected = preview.selected,
            loading = preview.loading,
            failed = preview.failed,
            legLines = legLines,
            summary = selected?.let { r -> options.getOrNull(preview.selected)?.let { o -> summary(o, r.durationSeconds, nowMs) } },
        )
    }
}
