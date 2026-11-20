package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.RoutePreview
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits

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
    /** Per-leg lines ("12 min · 8.4 km · arrive 14:32"), one for each of origin→stop…→destination; empty when unknown. */
    val legLines: List<String>,
    /** "1 h 05 min · 42 km" for the selected route; shown in the header when stops replace the alternatives. */
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

    /** The time label on each route of the map. */
    fun callouts(routes: List<RoutePreview>): List<String> = routes.map { StopLegs.duration(it.durationSeconds) }

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
            stops = stops.map { it.label },
            options = options,
            selected = preview.selected,
            loading = preview.loading,
            failed = preview.failed,
            legLines = legLines,
            summary = selected?.let { options.getOrNull(preview.selected)?.let { o -> "${o.time} · ${o.distance}" } },
        )
    }
}
