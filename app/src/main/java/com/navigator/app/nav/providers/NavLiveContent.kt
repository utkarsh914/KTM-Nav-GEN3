package com.navigator.app.nav.providers

import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState

object NavLiveContentBuilder {

    /** Only refresh the notification while guidance is live (see NavLiveUpdate). */
    fun shouldRefresh(state: NormalizedNavigationState): Boolean =
        state.sessionState == NavSessionState.ENROUTE || state.sessionState == NavSessionState.REROUTING

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
