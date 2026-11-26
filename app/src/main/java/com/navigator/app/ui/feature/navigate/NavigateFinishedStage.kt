package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ride.GpxShare
import com.navigator.app.ride.RideStore
import com.navigator.app.settings.AppSettings
import com.navigator.app.trip.TripState
import com.navigator.app.ui.feature.navigate.finished.FinishState
import com.navigator.app.ui.feature.navigate.finished.FinishUi
import com.navigator.app.ui.feature.navigate.finished.TripFinishedActions
import com.navigator.app.ui.feature.navigate.finished.TripFinishedContent
import com.navigator.app.ui.feature.navigate.finished.TripFinishedModel
import com.navigator.app.ui.feature.navigate.finished.TripFinishedViewModel
import com.navigator.app.ui.feature.navigate.plan.LocalFillPanel
import com.navigator.app.ui.feature.navigate.plan.PlanPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The trip-finished stage of [NavigateHost]: the summary panel with the ride's stats and the follow-ups (save the
 * place, ride back, share the track, replay). The ride itself is resolved by the TripFinishedViewModel; what to do
 * next belongs to the host, so it comes in as callbacks.
 */
@Composable
internal fun TripFinishedStage(
    selectedNow: SavedPlace?,
    finishState: FinishState,
    tripState: TripState,
    appSettings: AppSettings,
    recordingTurnedOn: Boolean,
    store: PlacesStore,
    rideStore: RideStore,
    /** The host's scope: Save place and Share finish even if Done is tapped right after. */
    scope: CoroutineScope,
    onFinishInsets: (left: Int, bottom: Int) -> Unit,
    onDone: () -> Unit,
    onRideBack: () -> Unit,
    onRecordingTurnedOn: () -> Unit,
    onOpenReplay: (String) -> Unit,
    onOpenRecordingSettings: () -> Unit,
) {
    val context = LocalContext.current
    // The summary leaves with what it showed: the place and the trip's length are cleared the moment the stage changes.
    val selected = rememberLast(selectedNow)
    val finishedDuration = rememberLast(tripState.finished?.durationSec)
    var placeSaved by remember(selected) { mutableStateOf(selected?.let { store.isSaved(it.lat, it.lng) } ?: false) }
    val model = TripFinishedModel(
        destination = selected?.label,
        ui = finishState.ui,
        fallbackDurationSec = finishedDuration ?: 0,
        avgMode = appSettings.avgSpeedMode,
        placeSaved = placeSaved,
        canSavePlace = selected != null,
        canRideBack = finishState.points?.firstOrNull() != null || tripState.origin != null,
        minDistanceM = appSettings.rideMinDistanceMeters,
        minDurationS = appSettings.rideMinDurationSeconds,
        recordingNowOn = recordingTurnedOn,
    )
    val ride = (finishState.ui as? FinishUi.Recorded)?.ride
    PlanPanel(onInsets = onFinishInsets) {
        TripFinishedContent(
            model = model,
            landscape = LocalFillPanel.current,
            actions = TripFinishedActions(
                onDone = onDone,
                onReplay = { ride?.let { onOpenReplay(it.id) } },
                onSavePlace = {
                    selected?.let {
                        // "Saved" only once the write is done.
                        scope.launch {
                            withContext(Dispatchers.IO) { store.saveFavorite(it, FavoriteSlot.OTHER) }
                            placeSaved = true
                        }
                    }
                },
                onRideBack = onRideBack,
                onShare = {
                    if (ride != null) scope.launch {
                        val intent = withContext(Dispatchers.IO) { GpxShare.buildIntent(context, ride, rideStore) }
                        if (intent != null) runCatching { context.startActivity(intent) }
                    }
                },
                onTurnOnRecording = { appSettings.rideRecordingEnabled = true; onRecordingTurnedOn() },
                onChangeLimits = onOpenRecordingSettings,
            ),
        )
    }
}
