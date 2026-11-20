package com.navigator.app.ui.feature.navigate.riding

import androidx.annotation.StringRes
import com.navigator.app.R
import com.navigator.app.nav.providers.StartFailure
import com.navigator.app.trip.MAX_STOPS

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
}
