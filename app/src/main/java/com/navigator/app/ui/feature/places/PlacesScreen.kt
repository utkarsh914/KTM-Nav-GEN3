package com.navigator.app.ui.feature.places

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.feature.navigate.StraightLine
import com.navigator.app.ui.shell.GoogleMapsLauncher
import com.navigator.app.ui.shell.LocalFeedback

/**
 * The Places tab: wires [PlacesContent] to the view model, the ⋮ sheet and the snackbars. [inApp] is whether the app's own
 * maps drive (else Google Maps does the navigating, mirror mode). [onRideSlot] and [onShowPlace] hand a place to the
 * Navigate tab; [onPick] opens the place picker for a slot.
 */
@Composable
fun PlacesScreen(
    inApp: Boolean,
    onRideSlot: (FavoriteSlot) -> Unit,
    onShowPlace: (SavedPlace, directions: Boolean) -> Unit,
    onPick: (FavoriteSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val feedback = LocalFeedback.current
    val vm: PlacesViewModel = viewModel(factory = PlacesViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    var target by rememberSaveable(stateSaver = PlaceTarget.Saver) { mutableStateOf<PlaceTarget?>(null) }
    val undo = resources.getString(R.string.common_undo)

    LaunchedEffect(vm) { vm.refreshOrigin() }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is PlacesEvent.Removed -> {
                    val only = event.items.singleOrNull()
                    val message = when (only?.slot) {
                        FavoriteSlot.HOME -> resources.getString(R.string.places_slot_removed, resources.getString(R.string.navigate_tile_home))
                        FavoriteSlot.WORK -> resources.getString(R.string.places_slot_removed, resources.getString(R.string.navigate_tile_work))
                        else -> resources.getQuantityString(R.plurals.places_removed, event.items.size, event.items.size)
                    }
                    feedback.snackbar(message, undo, onAction = { vm.undoRemove(event.items) })
                }
                is PlacesEvent.Assigned -> feedback.snackbar(
                    if (event.slot == FavoriteSlot.OTHER) resources.getString(R.string.places_saved_one)
                    else resources.getString(R.string.places_slot_set, resources.getString(slotName(event.slot))),
                    undo, onAction = { vm.undoAssign(event) },
                )
            }
        }
    }
    BackHandler(enabled = ui.selecting) { vm.stopSelecting() }

    fun openMaps(intent: Intent) {
        if (!GoogleMapsLauncher.open(context, intent)) feedback.snackbar(resources.getString(R.string.places_maps_missing))
    }

    PlacesContent(
        ui = ui, modifier = modifier,
        actions = PlacesActions(
            onOpenSlot = { slot, place -> if (inApp) onRideSlot(slot) else openMaps(PlaceActions.navigationIntent(place)) },
            onSetSlot = onPick,
            onSlotMore = { slot, place -> target = PlaceTarget.Slot(slot, place) },
            onOpenPlace = { place -> if (inApp) onShowPlace(place, false) else target = PlaceTarget.Saved(place) },
            onPlaceMore = { place -> target = PlaceTarget.Saved(place) },
            onAdd = { onPick(FavoriteSlot.OTHER) },
            onSelect = vm::startSelecting,
            onToggle = vm::toggle,
            onSelectAll = vm::selectAll,
            onStopSelecting = vm::stopSelecting,
            onDelete = vm::deleteSelected,
        ),
    )

    target?.let { t ->
        PlaceActionsSheet(
            t, inApp, onDismiss = { target = null }, distance = StraightLine.label(ui.origin, t.place),
            onAction = { action ->
                target = null
                when (action) {
                    PlaceAction.Change -> (t as? PlaceTarget.Slot)?.let { onPick(it.slot) }
                    PlaceAction.Directions -> onShowPlace(t.place, true)
                    PlaceAction.ShowOnMap -> onShowPlace(t.place, false)
                    PlaceAction.OpenInMaps -> openMaps(PlaceActions.showIntent(t.place))
                    PlaceAction.SetHome -> vm.assign(t.place, FavoriteSlot.HOME)
                    PlaceAction.SetWork -> vm.assign(t.place, FavoriteSlot.WORK)
                    PlaceAction.Remove -> when (t) {
                        is PlaceTarget.Slot -> vm.removeSlot(t.slot)
                        is PlaceTarget.Saved -> vm.removePlace(t.place)
                    }
                }
            },
        )
    }
}

fun slotName(slot: FavoriteSlot): Int = if (slot == FavoriteSlot.WORK) R.string.navigate_tile_work else R.string.navigate_tile_home

