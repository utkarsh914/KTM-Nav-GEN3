package com.navigator.app.nav.providers

import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the guidance notification says, as plain strings (JVM-testable). */
data class NavLiveContent(
    val title: String,
    val text: String,
    /** Status-bar chip text for a Live Update (kept very short), or null. */
    val chip: String?,
    val etaMs: Long?,
)

object NavLiveContentBuilder {

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    fun build(
        state: NormalizedNavigationState,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): NavLiveContent {
        val etaMs = state.remainingTimeSeconds?.takeIf { it >= 0 }?.let { nowMs + it * 1000L }
        val eta = etaMs?.let { "ETA " + CLOCK.format(Instant.ofEpochMilli(it).atZone(zone)) }
        val toTurn = state.distanceToManeuverMeters?.let { DistanceFormatter.format(it, state.units) }
        val remaining = state.remainingDistanceMeters?.let { DistanceFormatter.format(it, state.units) }

        val title = when {
            state.sessionState == NavSessionState.REROUTING -> "Rerouting\u2026"
            state.sessionState != NavSessionState.ENROUTE -> "Navigating"
            else -> (state.fullInstruction?.takeIf { it.isNotBlank() }
                ?: state.roadName?.takeIf { it.isNotBlank() }
                ?: "Continue")
        }
        val text = when (state.sessionState) {
            NavSessionState.ENROUTE -> listOfNotNull(toTurn, remaining, eta).joinToString(" \u00b7 ")
                .ifEmpty { "Guidance active" }
            NavSessionState.REROUTING -> listOfNotNull(remaining, eta).joinToString(" \u00b7 ")
                .ifEmpty { "Finding a new route" }
            else -> "Calculating route\u2026"
        }
        val chip = when (state.sessionState) {
            NavSessionState.ENROUTE -> toTurn ?: eta?.removePrefix("ETA ")
            NavSessionState.REROUTING -> "Reroute"
            else -> null
        }
        return NavLiveContent(title, text, chip?.take(CHIP_MAX), etaMs)
    }

    /** Only refresh the notification while guidance is live (see NavLiveUpdate). */
    fun shouldRefresh(state: NormalizedNavigationState): Boolean =
        state.sessionState == NavSessionState.ENROUTE || state.sessionState == NavSessionState.REROUTING

    /** Longest status-bar chip text the system will show comfortably. */
    const val CHIP_MAX = 7

    /** Whole-route progress as (progress, total) metres, or null when it can't be drawn. */
    fun progress(totalM: Int, remainingM: Int?): Pair<Int, Int>? {
        val remaining = remainingM ?: return null
        if (totalM <= 0) return null
        return (totalM - remaining).coerceIn(0, totalM) to totalM
    }

    /** Largest remaining distance seen this trip (the route length so far); reset when nothing is live. */
    fun trackTotal(currentTotalM: Int, state: NormalizedNavigationState): Int = when {
        state.sessionState != NavSessionState.ENROUTE && state.sessionState != NavSessionState.REROUTING -> 0
        else -> maxOf(currentTotalM, state.remainingDistanceMeters ?: 0)
    }
}
