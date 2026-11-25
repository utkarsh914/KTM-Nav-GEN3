package com.navigator.app.ui.feature.navigate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.location.lastKnownLocation
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.feature.navigate.pick.AndroidReverseGeocoder
import com.navigator.app.ui.feature.navigate.pick.PickOnMapController
import com.navigator.app.ui.feature.navigate.pick.PickOnMapScreen
import com.navigator.app.ui.feature.navigate.search.PlaceSearchRoute
import com.navigator.app.ui.feature.navigate.search.SearchMode
import com.navigator.app.ui.findHostActivity
import com.navigator.app.ui.map.MapController
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.shell.ImmersiveEffect
import com.navigator.app.ui.shell.LocalFeedback

/** Whether the phone has a speech recogniser to hand the voice button to. */
internal fun voiceAvailable(context: Context): Boolean =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null

/**
 * Stateful host of the browse screen on the Navigate tab. [NavigateHost] owns the map and the other
 * stages and calls this for BROWSE; what the rider picks goes back through [onSearch] and [onRide].
 */
@Composable
fun NavigateRoute(
    map: MapController,
    mapState: MapState,
    mapBearing: () -> Float,
    location: LocationState,
    padMap: Boolean,
    onSearch: (query: String?, category: PlaceCategory?) -> Unit,
    onRide: (SavedPlace) -> Unit,
    /** A point picked on the map: on to the place card. */
    onChoose: (SavedPlace) -> Unit,
    onResumeTrip: () -> Unit,
    onAddStop: () -> Unit = {},
    onOpenPairing: () -> Unit,
    onOpenPlaces: () -> Unit,
    onRetryMap: () -> Unit,
    recordControls: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: NavigateViewModel = viewModel(factory = NavigateViewModel.factory(container))
    val state by vm.state.collectAsState()
    val feedback = LocalFeedback.current
    var showBike by remember { mutableStateOf(false) }
    var overlay by rememberSaveable(stateSaver = BrowseOverlay.Saver) { mutableStateOf<BrowseOverlay>(BrowseOverlay.None) }
    // Search and Pick on map take the whole screen: the tab bar steps aside.
    ImmersiveEffect(overlay != BrowseOverlay.None)
    val hasVoice = remember { voiceAvailable(context) }

    // Fresh distances whenever the rider comes back to the screen.
    LifecycleResumeEffect(location) {
        vm.updateOrigin(lastKnownLocation(context))
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { container.places.refresh() }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { onSearch(it, null) }
    }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // "Don't ask again": the system dialog can no longer appear, so take the rider to the app's settings.
        val activity = context.findHostActivity()
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
        }
    }
    val speechPrompt = stringResource(R.string.navigate_voice_prompt)
    val removed = stringResource(R.string.search_recent_removed)
    val undo = stringResource(R.string.search_undo)

    val actions = NavigateBrowseActions(
        sheet = WhereToActions(
            onSearch = { onSearch(null, null) },
            onVoice = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
                runCatching { speech.launch(intent) }
            },
            onTile = { action ->
                when (action) {
                    is TileAction.Ride -> onRide(action.place)
                    is TileAction.SetSlot -> overlay = BrowseOverlay.PickSlot(action.slot)
                    TileAction.Pick -> overlay = BrowseOverlay.PickOnMap
                    TileAction.None -> Unit
                }
            },
            onPlace = onRide,
            onResumeTrip = onResumeTrip,
            onAddStop = onAddStop,
            onCategory = { onSearch(null, it) },
            onRemoveRecent = { place ->
                container.places.removeRecent(place)
                feedback.snackbar(removed, undo) { container.places.addRecent(place) }
            },
        ),
        onBike = { if (state.bike.opensPairing) onOpenPairing() else showBike = true },
        onFixLocation = {
            if (location == LocationState.PermissionOff) allow.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            else runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        },
        onRetryMap = onRetryMap,
        onUseMirror = {
            container.trip.cancel() // the two engines never drive the dash together
            container.settings.update { googleNavEnabled = false }
        },
        onRecenter = { lastKnownLocation(context)?.let { map.recenter(MapPoint(it.first, it.second)) } },
        onResetNorth = map::resetNorth,
        onMapInsets = { left, bottom -> if (padMap) map.setPadding(left, 0, 0, bottom) },
    )

    when (val o = overlay) {
        BrowseOverlay.None -> NavigateBrowse(
            state = state, location = location, mapState = mapState, mapBearing = mapBearing,
            voiceAvailable = hasVoice, actions = actions, recordControls = recordControls,
        )
        BrowseOverlay.PickOnMap -> PickOnMapHost(
            map = map, onBack = { overlay = BrowseOverlay.None },
            onUse = { place -> overlay = BrowseOverlay.None; onChoose(place) },
        )
        is BrowseOverlay.PickSlot -> {
            val saved = stringResource(
                R.string.search_slot_saved,
                stringResource(if (o.slot == FavoriteSlot.HOME) R.string.navigate_tile_home else R.string.navigate_tile_work),
            )
            PlaceSearchRoute(
                mode = SearchMode.Pick(o.slot),
                onPicked = { place ->
                    container.places.saveFavorite(place, o.slot)
                    feedback.snackbar(saved)
                    overlay = BrowseOverlay.None
                },
                onBack = { overlay = BrowseOverlay.None },
            )
        }
    }
    BackHandler(enabled = overlay != BrowseOverlay.None) { overlay = BrowseOverlay.None }

    if (showBike) {
        BikeSheetHost(tripRunning = state.activity != null, onOpenPairing = onOpenPairing, onDismiss = { showBike = false })
    }
}

/** What covers the browse screen while the rider picks something. */
private sealed interface BrowseOverlay {
    data object None : BrowseOverlay
    data object PickOnMap : BrowseOverlay
    data class PickSlot(val slot: FavoriteSlot) : BrowseOverlay

    companion object {
        /** Survives rotation and process death: "none", "map", or the slot's name. */
        val Saver = Saver<BrowseOverlay, String>(
            save = { when (it) { None -> "none"; PickOnMap -> "map"; is PickSlot -> it.slot.name } },
            restore = { s ->
                when (s) {
                    "none" -> None
                    "map" -> PickOnMap
                    else -> runCatching { PickSlot(FavoriteSlot.valueOf(s)) }.getOrDefault(None)
                }
            },
        )
    }
}

@Composable
internal fun PickOnMapHost(map: MapController, onBack: () -> Unit, onUse: (SavedPlace) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(map) { PickOnMapController(scope, map, AndroidReverseGeocoder(context)) }
    val fallback = stringResource(R.string.pick_dropped_pin)
    val state by controller.state.collectAsState()
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    PickOnMapScreen(
        state = state, onBack = onBack, onUse = { controller.use(fallback)?.let(onUse) },
        // The whole map is the canvas in portrait; in landscape the panel's side is not, so the pin moves to the open part.
        onFootprint = { map.setPadding(it, 0, 0, 0); controller.refresh() },
    )
}
