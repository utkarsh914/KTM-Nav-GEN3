package com.navigator.app.ui.feature.navigate

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.map.MapController
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.screens.lastKnownLocation
import com.navigator.app.ui.shell.LocalFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Whether the phone has a speech recogniser to hand the voice button to. */
internal fun voiceAvailable(context: android.content.Context): Boolean =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null

/**
 * Stateful host of the browse screen on the Navigate tab. The old map screen still owns the map and
 * the later stages and calls this for BROWSE; what the rider picks goes back through [onSearch] and [onRide].
 */
@Composable
fun NavigateRoute(
    map: MapController,
    mapState: MapState,
    mapBearing: Float,
    location: LocationState,
    padMap: Boolean,
    onSearch: (query: String?) -> Unit,
    onRide: (SavedPlace) -> Unit,
    onResumeTrip: () -> Unit,
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
    val hasVoice = remember { voiceAvailable(context) }

    // Fresh distances whenever the rider comes back to the screen.
    LifecycleResumeEffect(location) {
        vm.updateOrigin(lastKnownLocation(context))
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { container.places.refresh() }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?.let { onSearch(it) }
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
    val pickHint = stringResource(R.string.navigate_pick_hint)

    val actions = NavigateBrowseActions(
        sheet = WhereToActions(
            onSearch = { onSearch(null) },
            onVoice = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
                runCatching { speech.launch(intent) }
            },
            onTile = { action ->
                when (action) {
                    is TileAction.Ride -> onRide(action.place)
                    // Choosing a place for the slot arrives with the shared search (B7b); Places has it today.
                    is TileAction.SetSlot -> onOpenPlaces()
                    TileAction.Pick -> feedback.snackbar(pickHint)
                    TileAction.None -> Unit
                }
            },
            onPlace = onRide,
            onResumeTrip = onResumeTrip,
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

    NavigateBrowse(
        state = state, location = location, mapState = mapState, mapBearing = mapBearing,
        voiceAvailable = hasVoice, actions = actions, recordControls = recordControls,
    )

    if (showBike) {
        val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
        BikeSheet(
            state = state, dashTime = time,
            onBeeps = vm::setBeeps, onMessages = vm::setMessages, onSendTest = vm::sendTest,
            onRePair = { showBike = false; vm.rePair(); onOpenPairing() },
            onDismiss = { showBike = false },
        )
    }
}

private tailrec fun Context.findHostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
