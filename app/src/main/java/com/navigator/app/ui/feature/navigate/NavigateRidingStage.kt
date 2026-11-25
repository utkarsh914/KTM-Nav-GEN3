package com.navigator.app.ui.feature.navigate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.location.lastKnownLocation
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.StartFailure
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.trip.TripController
import com.navigator.app.trip.TripPhase
import com.navigator.app.trip.TripState
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.components.isNarrowWindow
import com.navigator.app.ui.feature.navigate.BikeSheetMapper
import com.navigator.app.ui.feature.navigate.plan.PickStopOverlay
import com.navigator.app.ui.feature.navigate.riding.EndTripButton
import com.navigator.app.ui.feature.navigate.riding.ManeuverCard
import com.navigator.app.ui.feature.navigate.riding.ManeuverModel
import com.navigator.app.ui.feature.navigate.riding.MapButtonEvent
import com.navigator.app.ui.feature.navigate.riding.MapButtonState
import com.navigator.app.ui.feature.navigate.riding.RidingActions
import com.navigator.app.ui.feature.navigate.riding.RidingChips
import com.navigator.app.ui.feature.navigate.riding.RidingDisplay
import com.navigator.app.ui.feature.navigate.riding.RidingLandscape
import com.navigator.app.ui.feature.navigate.riding.RidingScreen
import com.navigator.app.ui.feature.navigate.riding.StartFailedActions
import com.navigator.app.ui.feature.navigate.riding.StartFailedSheet
import com.navigator.app.ui.feature.navigate.riding.TripBarModel
import com.navigator.app.ui.feature.navigate.riding.TripControlsBody
import com.navigator.app.ui.feature.navigate.riding.TripControlsUi
import com.navigator.app.ui.feature.navigate.riding.TripSheetActions
import com.navigator.app.ui.feature.navigate.riding.TripStopRules
import com.navigator.app.ui.feature.navigate.riding.TripStopSheetHost
import com.navigator.app.ui.map.MapController
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.shell.Feedback
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.RidingTheme
import com.navigator.app.ui.theme.accentFor

/** What the riding stage hands back to [NavigateHost]: map insets and button, and the ways out of the stage. */
internal class NavigatingStageActions(
    val onRidingInsets: (MapInsets) -> Unit,
    val onApplyMapButton: (MapButtonEvent) -> Unit,
    val onRetryStart: () -> Unit,
    val onBackToBrowse: () -> Unit,
    val onBackToPreview: () -> Unit,
    val onMoveAway: () -> Unit,
    /** Opens the full search for a stop with [String] typed in (what was said; empty for none). */
    val onOpenTripStopSearch: (String) -> Unit,
    val onOpenConnect: () -> Unit,
)

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
    tripPhase: TripPhase,
    selected: SavedPlace?,
    navSpeedKmh: Float?,
    initialRemainingM: Int?,
    mapBearing: () -> Float,
    isOnline: Boolean,
    rerouteOfflinePaused: Boolean,
    bikeStatus: BikeStatus,
    trip: TripController,
    settings: SettingsRepository,
    map: MapController,
    feedback: Feedback,
    actions: NavigatingStageActions,
) {
    RidingTheme(night = ridingDark, accent = accentFor(settingsSnap.accentColorId)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = LandscapePanelSpec.isLandscape(maxWidth, maxHeight)
            val narrow = isNarrowWindow()
            // Landscape has no sheet to take Back: it flips the controls panel back to guidance.
            BackHandler(enabled = landscape && riding.showTripSheet) { riding.showTripSheet = false }
            val backFromFailure = {
                GoogleNavSdkProvider.clearStartFailure()
                trip.cancel()
                trip.resetStartDebounce()
                actions.onBackToPreview()
            }
            // System Back is the sheet's Back: without it the host would minimise a trip that never started.
            BackHandler(enabled = startFailure != null, onBack = backFromFailure)
            startFailure?.let { failure ->
                StartFailedSheet(
                    failure = failure,
                    canRetry = tripState.dest != null,
                    actions = StartFailedActions(
                        onRetry = {
                            GoogleNavSdkProvider.clearStartFailure()
                            actions.onRetryStart()
                        },
                        onBack = backFromFailure,
                        onClose = {
                            GoogleNavSdkProvider.clearStartFailure()
                            trip.cancel()
                            actions.onBackToBrowse()
                        },
                        onMirror = {
                            GoogleNavSdkProvider.clearStartFailure()
                            trip.cancel()
                            settings.update { googleNavEnabled = false }
                            actions.onBackToBrowse()
                        },
                    ),
                    modifier = Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter),
                    landscape = landscape,
                )
            }
            // Between turns of a re-plan (and while the screen leaves) the last real guidance stays on show.
            riding.rememberActive(tripState.startMs, navState, RidingDisplay.isGuiding(navState))
            val shown = RidingDisplay.select(navState, riding.lastActive, tripPhase)
            val destinationName = tripState.dest?.label ?: selected?.label
            val limit = if (settingsSnap.overspeedEnabled) settingsSnap.overspeedLimitKmh else null
            val bar = TripBarModel.from(shown.nav, navSpeedKmh, limit, initialRemainingM, System.currentTimeMillis(), destinationName)
            // The failure card owns the screen: no riding chrome (or trip sheet) behind it.
            val ridingActions = RidingActions(
                onOpenSheet = { riding.showTripSheet = true },
                onMapButton = { actions.onApplyMapButton(MapButtonEvent.Tap) },
                onNorthUp = { map.resetNorth() },
                onDismissSheet = { riding.showTripSheet = false },
            )
            val dashText = BikeSheetMapper.status(bikeStatus)
            val nextStopTarget = if (riding.showTripSheet) remember { trip.stopsAhead()?.firstOrNull() } else null
            val stopsLeft = if (riding.showTripSheet) remember { trip.stopsAhead()?.size } else null
            val context = LocalContext.current
            // A snapshot when the sheet opens: the figure is read at a glance, not watched.
            val nextStopDistance = nextStopTarget?.let { stop -> remember(stop) { TripStopRules.distanceTo(lastKnownLocation(context), stop) } }
            val nextStopNumber = nextStopTarget?.let { TripStopRules.nextNumber(tripState.allStops.size, stopsLeft ?: 1) }
            val nextStop = nextStopTarget?.let { it.label?.takeIf(String::isNotBlank) ?: stringResource(R.string.trip_stop_unnamed) }
            val dashPill = BikePillMapper.map(bikeStatus)
            val dashState = stringResource(dashText.title, dashText.arg ?: 0)
            // NV7: one line, the bike's name then its state, with a dot in the pill's tone.
            val dashStatus = dashPill.labelArg?.let { stringResource(R.string.trip_dash_line, it, dashState) } ?: dashState
            val canAddStop = tripState.dest != null && (tripPhase == TripPhase.GUIDING || tripPhase == TripPhase.UPDATING)
            val tripActions = TripSheetActions(
                onAddStop = { riding.showTripSheet = false; riding.showTripStops = true },
                onOverview = { riding.showTripSheet = false; riding.mapButton = MapButtonState(overview = true); map.showRouteOverview() },
                onMinimise = { riding.showTripSheet = false; actions.onMoveAway() },
                onBeeps = { on -> settings.update { turnBeepEnabled = on } },
                onMessages = { on -> settings.update { mirrorEnabled = on } },
                onDash = { riding.showTripSheet = false; riding.showBike = true },
                onRemoveNextStop = { riding.showTripSheet = false; trip.removeNextStop(nextStopTarget) },
                onEnd = { riding.showTripSheet = false; riding.showEndConfirm = true },
                onDismiss = { riding.showTripSheet = false },
            )
            // Also read by the pick screen, which keeps the next turn in view.
            val maneuver = remember(shown) { ManeuverModel.from(shown.nav, shown.updating) }
            if (startFailure == null && !riding.pickingStop) {
                if (landscape) RidingLandscape(
                    maneuver = maneuver,
                    bar = bar,
                    chip = RidingChips.pick(feedback.chip, offline = !isOnline, reroutePaused = rerouteOfflinePaused),
                    recenter = riding.mapButton.recenter,
                    bearing = mapBearing,
                    narrow = narrow,
                    controls = if (riding.showTripSheet) TripControlsUi(settingsSnap.turnBeepEnabled, settingsSnap.mirrorEnabled, dashStatus, canAddStop, nextStop, nextStopNumber, nextStopDistance, dashPill.tone) else null,
                    actions = ridingActions,
                    controlActions = tripActions,
                    onInsets = { actions.onRidingInsets(it) },
                ) else RidingScreen(
                    maneuver = maneuver,
                    bar = bar,
                    chip = RidingChips.pick(feedback.chip, offline = !isOnline, reroutePaused = rerouteOfflinePaused),
                    recenter = riding.mapButton.recenter,
                    bearing = mapBearing,
                    actions = ridingActions,
                    onInsets = { top, bottom -> actions.onRidingInsets(MapInsets(top = top, bottom = bottom)) },
                    sheetOpen = riding.showTripSheet,
                    sheet = {
                        TripControlsBody(settingsSnap.turnBeepEnabled, settingsSnap.mirrorEnabled, dashStatus, canAddStop, nextStop, tripActions, dashPill.tone, nextStopNumber = nextStopNumber, nextStopDistance = nextStopDistance)
                        Spacer(Modifier.height(KtmSpacing.xl))
                        EndTripButton(tripActions.onEnd)
                    },
                )
            }
            if (riding.showBike && startFailure == null) {
                // Sheets with body text that scrolls follow the rider's font setting, in the riding palette.
                RidingSheetTheme(ridingDark, settingsSnap.accentColorId) {
                    BikeSheetHost(tripRunning = true, onOpenPairing = actions.onOpenConnect, onDismiss = { riding.showBike = false })
                }
            }
            if (riding.pickingStop && startFailure == null) {
                val online by ConnectivityMonitor.isOnline.collectAsState()
                val ahead = remember { trip.stopsAhead()?.size }
                val left = remember { TripStopRules.left(ahead, tripState.allStops.size) }
                // Back to the Add a stop sheet, and the map follows the rider again.
                val leave = {
                    riding.pickingStop = false
                    actions.onApplyMapButton(MapButtonEvent.Tap)
                }
                // The landscape panel's side is not map: the riding insets are that and nothing else while picking, so what
                // "Add stop here" reads is the point under the pin.
                var pickFootprint by remember { mutableIntStateOf(0) }
                LaunchedEffect(pickFootprint) { actions.onRidingInsets(MapInsets(left = pickFootprint)) }
                BackHandler { leave(); riding.showTripStops = true }
                PickStopOverlay(
                    map = map, number = TripStopRules.nextNumber(tripState.allStops.size, ahead ?: 0), destination = tripState.dest?.label.orEmpty(),
                    canAdd = TripStopRules.canAdd(left, online),
                    onBack = { leave(); riding.showTripStops = true },
                    onUse = { place -> leave(); trip.addStop(place) },
                    // The trip keeps guiding underneath: the next turn stays on screen while the rider picks.
                    guidance = { ManeuverCard(maneuver, Modifier.fillMaxWidth(), destination = bar.destination) },
                    onFootprint = { pickFootprint = it },
                )
            }
            if (riding.showTripStops && startFailure == null) {
                RidingSheetTheme(ridingDark, settingsSnap.accentColorId) {
                    TripStopSheetHost(
                        trip = trip,
                        onSearchByName = { riding.showTripStops = false; actions.onOpenTripStopSearch(it) },
                        onPickOnMap = {
                            // Free camera over the whole route (as Overview), then the pin: the trip keeps guiding underneath.
                            riding.showTripStops = false
                            riding.mapButton = MapButtonState(overview = true)
                            map.showRouteOverview()
                            riding.pickingStop = true
                        },
                        onDismiss = { riding.showTripStops = false },
                    )
                }
            }
        }
    }
}

/** The riding palette for a sheet with scrolling body text: the rider's own font scale instead of the riding clamp (a theme above is already providing the bars). */
@Composable
private fun RidingSheetTheme(night: Boolean, accentId: String?, content: @Composable () -> Unit) =
    RidingTheme(night = night, accent = accentFor(accentId), controlSystemBars = false, clampFont = false, content = content)
