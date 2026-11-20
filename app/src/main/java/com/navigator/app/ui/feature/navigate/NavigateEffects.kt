package com.navigator.app.ui.feature.navigate

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import com.navigator.app.R
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.trip.StopAddProblem
import com.navigator.app.trip.TripController
import com.navigator.app.trip.TripEvent
import com.navigator.app.ui.feature.navigate.link.SharedLinkController
import com.navigator.app.ui.feature.navigate.link.SharedLinkState
import com.navigator.app.ui.shell.ChipTone
import com.navigator.app.ui.shell.Feedback

/**
 * Says why the trip refused or undid something: a chip over the map while riding, a snackbar at rest. The stage is
 * read when an event arrives, not when this composed.
 */
@Composable
internal fun TripEventFeedback(trip: TripController, stage: NavStage, feedback: Feedback) {
    val resources = LocalResources.current
    val currentStage by rememberUpdatedState(stage)
    LaunchedEffect(trip) {
        trip.events.collect { e ->
            // While riding the message is a chip over the map; at rest it is a snackbar.
            fun say(res: Int, tone: ChipTone, vararg args: Any) {
                val text = resources.getString(res, *args)
                if (currentStage == NavStage.NAVIGATING) feedback.chip(text, tone) else feedback.snackbar(text)
            }
            when (e) {
                is TripEvent.StopAdded -> say(R.string.riding_chip_stop_added, ChipTone.Ok, e.label)
                is TripEvent.StopAddRejected -> when (e.problem) {
                    StopAddProblem.NOT_NAVIGATING -> say(R.string.riding_chip_not_navigating, ChipTone.Warn)
                    StopAddProblem.ENDED -> say(R.string.riding_chip_ended, ChipTone.Warn)
                    StopAddProblem.OFFLINE -> say(R.string.riding_chip_needs_connection, ChipTone.Warn)
                    StopAddProblem.SETTLING -> say(R.string.riding_chip_settling, ChipTone.Warn)
                    StopAddProblem.UNKNOWN_NEXT -> say(R.string.riding_chip_unknown_next, ChipTone.Warn)
                    StopAddProblem.MAX_STOPS -> say(R.string.riding_chip_max_stops, ChipTone.Warn)
                }
                TripEvent.StopAddFailed -> say(R.string.riding_chip_stop_failed, ChipTone.Warn)
                is TripEvent.StopRemoved -> say(R.string.riding_chip_stop_removed, ChipTone.Ok, e.label.ifBlank { resources.getString(R.string.trip_stop_unnamed) })
                TripEvent.StopRemoveFailed -> say(R.string.riding_chip_stop_remove_failed, ChipTone.Warn)
                is TripEvent.Finished -> {}
                TripEvent.RouteSubstituted -> say(R.string.route_using_google, ChipTone.Warn)
            }
        }
    }
}

/**
 * The shared-Google-Maps-link effects: open a link handed to the app, drop a failure that arrives mid-trip (it would
 * leave a stale card for the next visit to Browse), let Back dismiss the card, and offer a resolved place as CONFIRM
 * unless a trip is running (guidance is never swapped for a place card). [stage] is read live when a link resolves.
 */
@Composable
internal fun SharedLinkEffects(
    controller: SharedLinkController,
    state: SharedLinkState,
    sharedLink: String?,
    onConsumed: () -> Unit,
    stage: NavStage,
    feedback: Feedback,
    onResolved: (SavedPlace) -> Unit,
) {
    val resources = LocalResources.current
    val currentStage by rememberUpdatedState(stage)
    LaunchedEffect(sharedLink) {
        val link = sharedLink ?: return@LaunchedEffect
        onConsumed()
        controller.open(link)
    }
    val linkDuringTrip = resources.getString(R.string.navigate_link_during_trip)
    LaunchedEffect(state, stage) {
        if (state == SharedLinkState.Failed && stage == NavStage.NAVIGATING) {
            controller.dismiss()
            feedback.snackbar(linkDuringTrip)
        }
    }
    BackHandler(enabled = state != SharedLinkState.Idle && stage != NavStage.NAVIGATING) { controller.dismiss() }
    val resolved by rememberUpdatedState(onResolved)
    LaunchedEffect(controller) {
        controller.resolved.collect {
            if (currentStage == NavStage.NAVIGATING) feedback.snackbar(linkDuringTrip) else resolved(it)
        }
    }
}
