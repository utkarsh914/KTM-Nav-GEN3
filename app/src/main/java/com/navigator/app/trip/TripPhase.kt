package com.navigator.app.trip

import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.providers.StartFailure

/**
 * What the in-app trip is doing, as one answer for every consumer (riding screen, Quit, notification, speed alert).
 * Guidance's own session state is not enough: it reads IDLE at every (re-)start, including stop re-plans.
 */
enum class TripPhase { NONE, STARTING, GUIDING, UPDATING, FAILED, FINISHED }

/** A trip is on (guidance coming up, running, or re-planning). */
val TripPhase.isLive: Boolean get() = this == TripPhase.STARTING || this == TripPhase.GUIDING || this == TripPhase.UPDATING

internal object TripPhasePolicy {
    fun of(trip: TripState, session: NavSessionState, failure: StartFailure?): TripPhase = when {
        trip.dest == null -> TripPhase.NONE
        trip.finished != null -> TripPhase.FINISHED
        trip.arrivalLatched -> TripPhase.NONE // cancelled or ended, summary dismissed
        failure != null -> TripPhase.FAILED
        session == NavSessionState.ENROUTE || session == NavSessionState.REROUTING -> TripPhase.GUIDING
        trip.guided -> TripPhase.UPDATING // IDLE / STOPPED / ARRIVED between re-plans
        else -> TripPhase.STARTING
    }
}
