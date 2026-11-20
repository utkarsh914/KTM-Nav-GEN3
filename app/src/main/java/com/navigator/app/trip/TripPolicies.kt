package com.navigator.app.trip

import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.Stop

/** Maximum intermediate stops (Google Maps allows up to a handful; we cap at 4). */
internal const val MAX_STOPS = 4

/** After adding a stop mid-trip, the next add waits this long so the SDK reports the new route's target. */
internal const val STOP_ADD_SETTLE_MS = 8_000L

/** A second Start within this long of the first is a double tap: it would supersede the first mid-compute. */
internal const val START_DEBOUNCE_MS = 1_500L

/**
 * Arrival detection beyond Google's own arrival: staying within [DWELL_RADIUS_M] of the destination
 * for [DWELL_MS] ends the trip. Nothing else ends a trip on its own, so a red light or a jam near
 * the destination never does.
 */
internal object ArrivalPolicy {
    const val DWELL_RADIUS_M = 20
    const val DWELL_MS = 120_000L

    /** When the rider came within the radius ([prevNearSince] while they stay), or null once outside. */
    fun nearSince(prevNearSince: Long?, remainingM: Int, nowMs: Long): Long? =
        if (remainingM <= DWELL_RADIUS_M) prevNearSince ?: nowMs else null

    fun dwellReached(nearSince: Long?, nowMs: Long): Boolean = nearSince != null && nowMs - nearSince >= DWELL_MS
}

/** Why a stop can't be added to the trip right now. */
enum class StopAddProblem { NOT_NAVIGATING, ENDED, OFFLINE, SETTLING, UNKNOWN_NEXT, MAX_STOPS }

sealed interface StopAddCheck {
    /** Allowed; [ahead] are the stops of the trip still to visit. */
    data class Ok(val ahead: List<Stop>) : StopAddCheck
    data class Rejected(val problem: StopAddProblem) : StopAddCheck
}

/** The trip as re-planned with one more stop. */
data class AddStopPlan(val before: NavDestination, val replan: NavDestination, val allStops: List<Stop>)

/** Where to ride back to, and the stops of the trip in the order they were visited (the flow reverses them). */
data class RideBackPlan(val origin: Pair<Double, Double>, val tripStops: List<Stop>)

/** The trip's pure decisions: what the controller does, with no clock, SDK or screen in sight. */
internal object TripPolicies {

    /**
     * Whether a stop may be added to the running trip. [forSearch] is the earlier check made when
     * opening the stop search (needs a connection, says "start navigating first"); the later one
     * made on the pick says the trip "has already ended". [ahead] is read lazily, last, because
     * asking the SDK for the current target is not free.
     */
    fun checkAddStop(
        hasTrip: Boolean,
        sessionActive: Boolean,
        arrivalLatched: Boolean,
        online: Boolean,
        lastAddMs: Long,
        nowMs: Long,
        forSearch: Boolean,
        /** Removing a stop is fine at the stop limit. */
        removing: Boolean = false,
        ahead: () -> List<Stop>?,
    ): StopAddCheck {
        if (!hasTrip) return StopAddCheck.Rejected(StopAddProblem.NOT_NAVIGATING)
        if (forSearch) {
            if (!sessionActive) return StopAddCheck.Rejected(StopAddProblem.NOT_NAVIGATING)
            if (!online) return StopAddCheck.Rejected(StopAddProblem.OFFLINE)
        } else if (arrivalLatched || !sessionActive) {
            return StopAddCheck.Rejected(StopAddProblem.ENDED)
        }
        if (nowMs - lastAddMs < STOP_ADD_SETTLE_MS) return StopAddCheck.Rejected(StopAddProblem.SETTLING)
        val remaining = ahead() ?: return StopAddCheck.Rejected(StopAddProblem.UNKNOWN_NEXT)
        if (!removing && remaining.size >= MAX_STOPS) return StopAddCheck.Rejected(StopAddProblem.MAX_STOPS)
        return StopAddCheck.Ok(remaining)
    }

    /**
     * Re-plan from here via [stop], then on through the stops still [ahead], to the destination.
     * Stops already passed are dropped from the guidance but stay in [allStops] (for "Ride back").
     * The old route token is for the old waypoints, so it is dropped; `continuesRide` keeps the
     * recording going.
     */
    fun planAddStop(dest: NavDestination, allStops: List<Stop>, ahead: List<Stop>, stop: Stop): AddStopPlan {
        val before = dest.copy(stops = ahead)
        return AddStopPlan(
            before = before,
            replan = before.copy(routeToken = null, stops = listOf(stop) + ahead, continuesRide = true),
            allStops = allStops.take((allStops.size - ahead.size).coerceAtLeast(0)) + stop + ahead,
        )
    }

    /**
     * The trip without its next stop: re-plan from here through the stops still [ahead] after it. Null when no
     * stop is ahead. The route token is for the old waypoints, so it is dropped.
     */
    fun planRemoveNext(dest: NavDestination, allStops: List<Stop>, ahead: List<Stop>): AddStopPlan? {
        if (ahead.isEmpty()) return null
        val rest = ahead.drop(1)
        val before = dest.copy(stops = ahead)
        val passed = allStops.take((allStops.size - ahead.size).coerceAtLeast(0))
        return AddStopPlan(before, before.copy(routeToken = null, stops = rest, continuesRide = true), passed + rest)
    }

    /** "Ride back": start from where the ride began (its first point, else the trip's origin). */
    fun rideBack(firstPoint: Pair<Double, Double>?, tripOrigin: Pair<Double, Double>?, allStops: List<Stop>): RideBackPlan? {
        val origin = firstPoint ?: tripOrigin ?: return null
        return RideBackPlan(origin, allStops)
    }

    /**
     * Since when the rider has been within the dwell radius of the destination. Reset (null) while the
     * trip is not ours or not active; unchanged when a frame carries no distance.
     */
    fun nearSince(owning: Boolean, sessionActive: Boolean, remainingM: Int?, prev: Long?, nowMs: Long): Long? = when {
        !owning || !sessionActive -> null
        remainingM == null -> prev
        else -> ArrivalPolicy.nearSince(prev, remainingM, nowMs)
    }

    /** Whether the dwell is long enough to end the trip. */
    fun dwellDone(owning: Boolean, sessionActive: Boolean, nearSince: Long?, nowMs: Long): Boolean =
        owning && sessionActive && ArrivalPolicy.dwellReached(nearSince, nowMs)
}
