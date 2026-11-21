package com.navigator.app.ui.feature.navigate.riding

import androidx.annotation.StringRes
import com.navigator.app.R
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.Stop
import com.navigator.app.nav.providers.StartFailure
import com.navigator.app.trip.MAX_STOPS
import com.navigator.app.ui.feature.navigate.StraightLine

/** The riders' words for a start failure, and what else the sheet may offer. */
data class StartFailureUi(@StringRes val message: Int, val canRetry: Boolean, val offerMirror: Boolean)

object StartFailureMapper {
    fun of(failure: StartFailure): StartFailureUi = when (failure) {
        StartFailure.ROUTE -> StartFailureUi(R.string.start_failed_route, canRetry = true, offerMirror = false)
        StartFailure.NAVIGATOR_UNAVAILABLE -> StartFailureUi(R.string.start_failed_not_ready, canRetry = true, offerMirror = false)
        // Trying again can't change what the phone supports: the way out is mirror mode.
        StartFailure.UNAVAILABLE -> StartFailureUi(R.string.start_failed_unavailable, canRetry = false, offerMirror = true)
    }
}

/** What the Add-a-stop sheet (SE4) says about room, and whether a place can be added now. */
object TripStopRules {
    const val MAX = MAX_STOPS

    /** Stops that can still be added; [ahead] is how many are left to visit, null when the SDK can't tell. */
    fun left(ahead: Int?, planned: Int): Int = (MAX - (ahead ?: planned)).coerceAtLeast(0)

    fun canAdd(left: Int, online: Boolean): Boolean = left > 0 && online

    /** The next stop's number among the trip's stops (what its map pin says): those already passed, plus one. */
    fun nextNumber(planned: Int, ahead: Int): Int = (planned - ahead + 1).coerceAtLeast(1)

    /** How far the next stop is from the rider, straight line and metric; null without a fix or when basically there. */
    fun distanceTo(origin: Pair<Double, Double>?, stop: Stop): String? =
        origin?.let { StraightLine.meters(it, stop.lat, stop.lng) }?.takeIf { it > 0 }
            ?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) }
}

/** NL4: the landscape trip controls flip back to guidance on their own once nothing has been touched for this long. */
object ControlsIdle {
    const val FLIP_BACK_MS = 10_000L
    const val CHECK_EVERY_MS = 500L

    /** [idleMs] is the time since the panel was last touched, counted in the same ticks the screen waits in. */
    fun shouldFlipBack(idleMs: Long): Boolean = idleMs >= FLIP_BACK_MS
}
