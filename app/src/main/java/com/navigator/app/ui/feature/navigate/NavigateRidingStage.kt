package com.navigator.app.ui.feature.navigate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.app.AppContainer
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.StartFailure
import com.navigator.app.trip.TripController
import com.navigator.app.trip.TripState
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.feature.navigate.BikeSheetMapper
import com.navigator.app.ui.feature.navigate.riding.EndTripButton
import com.navigator.app.ui.feature.navigate.riding.ManeuverModel
import com.navigator.app.ui.feature.navigate.riding.MapButtonEvent
import com.navigator.app.ui.feature.navigate.riding.MapButtonState
import com.navigator.app.ui.feature.navigate.riding.RidingActions
import com.navigator.app.ui.feature.navigate.riding.RidingChips
import com.navigator.app.ui.feature.navigate.riding.RidingLandscape
import com.navigator.app.ui.feature.navigate.riding.RidingScreen
import com.navigator.app.ui.feature.navigate.riding.StartFailedActions
import com.navigator.app.ui.feature.navigate.riding.StartFailedSheet
import com.navigator.app.ui.feature.navigate.riding.TripBarModel
import com.navigator.app.ui.feature.navigate.riding.TripControlsBody
import com.navigator.app.ui.feature.navigate.riding.TripControlsUi
import com.navigator.app.ui.feature.navigate.riding.TripSheetActions
import com.navigator.app.ui.feature.navigate.riding.TripStopSheetHost
import com.navigator.app.ui.map.MapController
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.shell.Feedback
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.RidingTheme
import com.navigator.app.ui.theme.accentFor

/**
 * The riding stage of [NavigateHost]: the riding screen (portrait) or panel (landscape), the start-failed card, and
 * the sheets opened from the trip bar (bike, add a stop). What only this stage owns is in [RidingOverlayState].
 */
@Composable
internal fun NavigatingStage(
    riding: RidingOverlayState,
    ridingDark: Boolean,
    settingsSnap: SettingsSnapshot,
    startFailure: StartFailure?,
    tripState: TripState,
    navState: NormalizedNavigationState,
    selected: SavedPlace?,
    navSpeedKmh: Float?,
    initialRemainingM: Int?,
    mapBearing: Float,
    isOnline: Boolean,
    rerouteOfflinePaused: Boolean,
    bikeStatus: BikeStatus,
    trip: TripController,
    container: AppContainer,
    map: MapController,
    feedback: Feedback,
    onRidingInsets: (MapInsets) -> Unit,
    onApplyMapButton: (MapButtonEvent) -> Unit,
    onRetryStart: () -> Unit,
    onBackToBrowse: () -> Unit,
    onBackToPreview: () -> Unit,
    onMoveAway: () -> Unit,
    onOpenTripStopSearch: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    var showTripSheet by riding::showTripSheet
    var showTripStops by riding::showTripStops
    var showBike by riding::showBike
    var showEndConfirm by riding::showEndConfirm
    var mapButton by riding::mapButton
    RidingTheme(night = ridingDark, accent = accentFor(settingsSnap.accentColorId)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = LandscapePanelSpec.isLandscape(maxWidth, maxHeight)
            val narrow = maxWidth.value < KtmLayout.panelNarrowBelow.value
            // Landscape has no sheet to take Back: it flips the controls panel back to guidance.
            BackHandler(enabled = landscape && showTripSheet) { showTripSheet = false }
            startFailure?.let { failure ->
                StartFailedSheet(
                    failure = failure,
                    canRetry = tripState.dest != null,
                    actions = StartFailedActions(
                        onRetry = {
                            GoogleNavSdkProvider.clearStartFailure()
                            onRetryStart()
                        },
                        onBack = {
                            GoogleNavSdkProvider.clearStartFailure()
                            trip.cancel()
                            trip.resetStartDebounce()
                            onBackToPreview()
                        },
                        onClose = {
                            GoogleNavSdkProvider.clearStartFailure()
                            trip.cancel()
                            onBackToBrowse()
                        },
                        onMirror = {
                            GoogleNavSdkProvider.clearStartFailure()
                            trip.cancel()
                            container.settings.update { googleNavEnabled = false }
                            onBackToBrowse()
                        },
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            val destinationName = tripState.dest?.label ?: selected?.label
            val limit = if (settingsSnap.overspeedEnabled) settingsSnap.overspeedLimitKmh else null
            val bar = TripBarModel.from(navState, navSpeedKmh, limit, initialRemainingM, System.currentTimeMillis(), destinationName)
            // The failure card owns the screen: no riding chrome (or trip sheet) behind it.
            val ridingActions = RidingActions(
                onOpenSheet = { showTripSheet = true },
                onMapButton = { onApplyMapButton(MapButtonEvent.Tap) },
                onNorthUp = { map.resetNorth() },
                onDismissSheet = { showTripSheet = false },
            )
            val dashText = BikeSheetMapper.status(bikeStatus)
            val nextStopTarget = if (showTripSheet) remember { trip.stopsAhead()?.firstOrNull() } else null
            val nextStop = nextStopTarget?.let { it.label?.takeIf(String::isNotBlank) ?: stringResource(R.string.trip_stop_unnamed) }
            val dashStatus = stringResource(dashText.title, dashText.arg ?: 0)
            val canAddStop = tripState.dest != null && navState.sessionState.isActiveNav()
            val tripActions = TripSheetActions(
                onAddStop = { showTripSheet = false; showTripStops = true },
                onOverview = { showTripSheet = false; mapButton = MapButtonState(overview = true); map.showRouteOverview() },
                onMinimise = { showTripSheet = false; onMoveAway() },
                onBeeps = { on -> container.settings.update { turnBeepEnabled = on } },
                onMessages = { on -> container.settings.update { mirrorEnabled = on } },
                onDash = { showTripSheet = false; showBike = true },
                onRemoveNextStop = { showTripSheet = false; trip.removeNextStop(nextStopTarget) },
                onEnd = { showTripSheet = false; showEndConfirm = true },
                onDismiss = { showTripSheet = false },
            )
            if (startFailure == null) {
                val maneuver = remember(navState) { ManeuverModel.from(navState) }
                if (landscape) RidingLandscape(
                    maneuver = maneuver,
                    bar = bar,
                    chip = RidingChips.pick(feedback.chip, offline = !isOnline, reroutePaused = rerouteOfflinePaused),
                    recenter = mapButton.recenter,
                    bearing = mapBearing,
                    narrow = narrow,
                    controls = if (showTripSheet) TripControlsUi(settingsSnap.turnBeepEnabled, settingsSnap.mirrorEnabled, dashStatus, canAddStop, nextStop) else null,
                    actions = ridingActions,
                    controlActions = tripActions,
                    onInsets = { onRidingInsets(it) },
                ) else RidingScreen(
                    maneuver = maneuver,
                    bar = bar,
                    chip = RidingChips.pick(feedback.chip, offline = !isOnline, reroutePaused = rerouteOfflinePaused),
                    recenter = mapButton.recenter,
                    bearing = mapBearing,
                    actions = ridingActions,
                    onInsets = { top, bottom -> onRidingInsets(MapInsets(top = top, bottom = bottom)) },
                    sheetOpen = showTripSheet,
                    sheet = {
                        TripControlsBody(settingsSnap.turnBeepEnabled, settingsSnap.mirrorEnabled, dashStatus, canAddStop, nextStop, tripActions)
                        Spacer(Modifier.height(KtmSpacing.xl))
                        EndTripButton(tripActions.onEnd)
                    },
                )
            }
            if (showBike && startFailure == null) {
                BikeSheetHost(tripRunning = true, onOpenPairing = onOpenConnect, onDismiss = { showBike = false })
            }
            if (showTripStops && startFailure == null) {
                TripStopSheetHost(
                    trip = trip,
                    onSearchByName = { showTripStops = false; onOpenTripStopSearch() },
                    onDismiss = { showTripStops = false },
                )
            }
        }
    }
}
