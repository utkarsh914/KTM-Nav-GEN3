package com.navigator.app.ui.feature.navigate

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.MapControlButton
import com.navigator.app.ui.feature.navigate.plan.PickStopOverlay
import com.navigator.app.ui.feature.navigate.plan.PlaceSaving
import com.navigator.app.ui.feature.navigate.plan.PlaceSheet
import com.navigator.app.ui.feature.navigate.plan.PlaceSheetActions
import com.navigator.app.ui.feature.navigate.plan.PlaceSheetState
import com.navigator.app.ui.feature.navigate.plan.PlanPanel
import com.navigator.app.ui.feature.navigate.plan.ReplaceDecision
import com.navigator.app.ui.feature.navigate.plan.ReplacePolicy
import com.navigator.app.ui.feature.navigate.plan.RoutePreviewState
import com.navigator.app.ui.feature.navigate.plan.RouteSheet
import com.navigator.app.ui.feature.navigate.plan.RouteSheetActions
import com.navigator.app.ui.feature.navigate.plan.RouteSheetMapper
import com.navigator.app.ui.feature.navigate.plan.StopCandidateSheet
import com.navigator.app.ui.feature.navigate.plan.StopList
import com.navigator.app.ui.map.MapController
import com.navigator.app.ui.shell.Feedback
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.edgePadding
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What the CONFIRM and PREVIEW stages show and do. The values are read when the stage composes; the stops, stage and
 * place are read live through [NavFlowViewModel] where a later tap or an Undo needs the latest (see [PreviewStage]).
 * Everything that changes the flow comes back to [NavigateHost] as a callback.
 */
internal class PlanStage(
    val selected: SavedPlace?,
    val savedPlaces: List<FavoritePlace>,
    val origin: Pair<Double, Double>?,
    val routePreview: RoutePreviewState,
    val pickingStop: Boolean,
    val stopCandidate: SavedPlace?,
    /** A trip still runs while another is planned: one tap back to it. */
    val showResumePill: Boolean,
    /** The map is up (so its compass can show). */
    val mapReadyForControls: Boolean,
    val mapBearing: Float,
    val map: MapController,
    val feedback: Feedback,
    val places: PlacesRepository,
    val scope: CoroutineScope,
    val tripRunning: () -> Boolean,
    val onResume: () -> Unit,
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onInsets: (left: Int, bottom: Int) -> Unit,
    val onAskReplace: (ReplaceRequest) -> Unit,
    val onGoToPreview: () -> Unit,
    val onAddAsStop: (SavedPlace) -> Unit,
    val onPickingStop: (Boolean) -> Unit,
    val onStopCandidate: (SavedPlace?) -> Unit,
    val onSelectRoute: (Int) -> Unit,
    val onAddStop: () -> Unit,
    val onStart: (SavedPlace) -> Unit,
    val onRetryRoutes: () -> Unit,
)

/** CONFIRM: the place card, with a way back to a running trip and the compass. */
@Composable
internal fun BoxScope.ConfirmStage(plan: PlanStage) {
    val resources = LocalResources.current
    // A trip is still running while the rider plans another: one tap back to it.
    if (plan.showResumePill) {
        ResumeNavPill(
            onClick = plan.onResume,
            modifier = Modifier.align(Alignment.TopCenter).edgePadding().padding(top = KtmLayout.mapControlTop),
        )
    }
    val sel = plan.selected
    if (sel != null) {
        // PL1: Back is a map control above the sheet, the way to the search or the map it came from.
        // The landscape panel covers that corner and carries its own Back.
        // Judged by the same box as the panel (split-screen is portrait by configuration but wide by space).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth <= maxHeight) {
                MapControlButton(
                    KtmIcons.ArrowLeft, stringResource(R.string.common_back), plan.onBack,
                    Modifier.align(Alignment.TopStart).edgePadding().padding(top = KtmLayout.mapControlTop),
                )
            }
        }
        val removedMessage = resources.getString(R.string.place_removed)
        val undoLabel = resources.getString(R.string.common_undo)
        val saved = PlaceSaving.isSaved(plan.savedPlaces, sel)
        PlanPanel(onInsets = plan.onInsets) {
            PlaceSheet(
                state = PlaceSheetState(
                    title = sel.label, address = sel.address,
                    distance = StraightLine.label(plan.origin, sel),
                    saved = saved, tripRunning = plan.tripRunning(),
                ),
                actions = PlaceSheetActions(
                    onBack = plan.onBack,
                    onClose = plan.onClose,
                    onToggleSave = {
                        plan.scope.launch(Dispatchers.IO) {
                            if (saved) plan.places.removeFavorite(sel) else plan.places.saveFavorite(sel, FavoriteSlot.OTHER)
                        }
                        if (saved) {
                            plan.feedback.snackbar(removedMessage, undoLabel) {
                                plan.scope.launch(Dispatchers.IO) { plan.places.saveFavorite(sel, FavoriteSlot.OTHER) }
                            }
                        }
                    },
                    onDirections = {
                        if (ReplacePolicy.decide(plan.tripRunning()) == ReplaceDecision.Ask) {
                            plan.onAskReplace(ReplaceRequest(sel, cancelFirst = true) { plan.onGoToPreview() })
                        } else plan.onGoToPreview()
                    },
                    onAddAsStop = { plan.onAddAsStop(sel) },
                ),
            )
        }
    }
    // Top-right control: the compass, only when rotated (✕ is in the sheet).
    if (plan.mapReadyForControls && abs(plan.mapBearing) > 0.5f) {
        Column(
            modifier = Modifier.align(Alignment.TopEnd).edgePadding().padding(end = KtmSpacing.lg, top = KtmSpacing.lg),
            horizontalAlignment = Alignment.End,
        ) {
            MapControlButton(
                KtmIcons.CompassNorth, stringResource(R.string.navigate_reset_north), { plan.map.resetNorth() },
                rotationDegrees = -plan.mapBearing, tinted = false,
            )
        }
    }
}

/** PREVIEW: the route sheet, the proposed-stop sheet or Pick on map for a stop. [flow] is read live for the stops. */
@Composable
internal fun BoxScope.PreviewStage(plan: PlanStage, flow: NavFlowViewModel) {
    val resources = LocalResources.current
    // A trip is still running while the rider plans another: one tap back to it.
    if (plan.showResumePill) {
        ResumeNavPill(
            onClick = plan.onResume,
            modifier = Modifier.align(Alignment.TopCenter).edgePadding().padding(top = KtmLayout.mapControlTop),
        )
    }
    val dest = plan.selected
    if (dest != null) {
        val stopRemoved = { label: String -> resources.getString(R.string.route_stop_removed, label) }
        val undoLabel = resources.getString(R.string.common_undo)
        when {
            plan.pickingStop -> PickStopOverlay(
                map = plan.map, number = StopList.nextNumber(flow.stops), destination = dest.label,
                canAdd = StopList.canAdd(flow.stops),
                onBack = { plan.onPickingStop(false) },
                onUse = { plan.onPickingStop(false); flow.stops = StopList.add(flow.stops, it) },
            )
            plan.stopCandidate != null -> PlanPanel(onInsets = plan.onInsets) {
                plan.stopCandidate.let { candidate ->
                    StopCandidateSheet(
                        name = candidate.label, number = StopList.nextNumber(flow.stops), destination = dest.label,
                        canAdd = StopList.canAdd(flow.stops),
                        onAdd = { flow.stops = StopList.add(flow.stops, candidate); plan.onStopCandidate(null) },
                        onCancel = { plan.onStopCandidate(null) },
                    )
                }
            }
            else -> PlanPanel(onInsets = plan.onInsets) {
                RouteSheet(
                    state = RouteSheetMapper.state(dest, flow.stops, plan.routePreview, System.currentTimeMillis()),
                    actions = RouteSheetActions(
                        onBack = plan.onBack,
                        onSelect = plan.onSelectRoute,
                        onAddStop = plan.onAddStop,
                        onRemoveStop = { index ->
                            flow.stops.getOrNull(index)?.let { removed ->
                                flow.stops = StopList.removeAt(flow.stops, index)
                                plan.feedback.snackbar(stopRemoved(removed.label), undoLabel) {
                                    // Only while still planning: after Start or ✕ the stop belongs to a plan that is gone.
                                    if (flow.stage == NavStage.PREVIEW && flow.selected == dest) flow.stops = StopList.restore(flow.stops, index, removed)
                                }
                            }
                        },
                        onMoveStop = { index, delta -> flow.stops = StopList.move(flow.stops, index, delta) },
                        onRetry = plan.onRetryRoutes,
                        onStart = {
                            // A trip is already running (rider stepped away to the map): starting another replaces it, so ask first.
                            if (ReplacePolicy.decide(plan.tripRunning()) == ReplaceDecision.Ask) {
                                plan.onAskReplace(ReplaceRequest(dest, cancelFirst = false) { plan.onStart(dest) })
                            } else plan.onStart(dest)
                        },
                    ),
                )
            }
        }
    }
    // Top-right control: the compass, only when rotated (✕ is in the sheet).
    if (plan.mapReadyForControls && abs(plan.mapBearing) > 0.5f && !plan.pickingStop) {
        Column(
            modifier = Modifier.align(Alignment.TopEnd).edgePadding().padding(end = KtmSpacing.lg, top = KtmSpacing.lg),
            horizontalAlignment = Alignment.End,
        ) {
            MapControlButton(
                KtmIcons.CompassNorth, stringResource(R.string.navigate_reset_north), { plan.map.resetNorth() },
                rotationDegrees = -plan.mapBearing, tinted = false,
            )
        }
    }
}
