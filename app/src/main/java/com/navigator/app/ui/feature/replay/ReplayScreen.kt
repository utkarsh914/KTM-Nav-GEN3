package com.navigator.app.ui.feature.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.map.LocationMarkerStyle
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.map.MapSurface
import com.navigator.app.ui.map.RetainedNavigationView
import com.navigator.app.ui.map.rememberDeviceHeadingState
import com.navigator.app.ui.map.rememberDeviceLatLng
import com.navigator.app.ui.map.rememberMapController
import com.navigator.app.ui.shell.LocalFeedback
import com.navigator.app.ui.shell.MERGE_OFFER
import com.navigator.app.ui.shell.PopWhenResumed
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.launch

/**
 * The ride replay page: the map behind, [ReplayContent] over it. The ViewModel owns playback; this wires it to the
 * map and to the pages that open from here. [onDeleted] leaves and offers Undo (the shell owns the snackbar, since
 * this page is gone by then).
 */
@Composable
fun ReplayScreen(
    rideId: String,
    retainedNav: RetainedNavigationView,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTrim: () -> Unit,
    onDeleted: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val resources = LocalResources.current
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val vm: ReplayViewModel = viewModel(factory = ReplayViewModel.factory(rideId, container))
    val ui by vm.ui.collectAsState()
    val position = vm.position.collectAsState()
    val map = rememberMapController(retainedNav, LocationMarkerStyle.fromId(container.settings.legacy.locationMarkerStyle))
    val mapReady by map.ready.collectAsState()
    val bearing by map.bearing.collectAsState()
    var insets by remember { mutableStateOf(MapInsets()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    val accent = KtmTheme.accent.fill.toArgb()
    val ready = ui as? ReplayUi.Ready
    val following by rememberUpdatedState(ready?.following == true)

    fun point(index: Int, points: List<com.navigator.app.ride.RidePoint>) = points.getOrNull(index)?.let { MapPoint(it.lat, it.lng) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ReplayEvent.Deleted -> onDeleted(event.ids)
                ReplayEvent.Gone -> gone = true
                ReplayEvent.MergedRideChanged -> feedback.endOffer(MERGE_OFFER + rideId)
            }
        }
    }

    PopWhenResumed(gone, onBack)

    // Playback must not keep walking the marker under a covered page or a backgrounded app.
    DisposableEffect(vm) {
        val owner = lifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // Undo for a merge sits above the sheet, not over it.
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.getBottom(density)
    LaunchedEffect(insets, navBar) {
        feedback.snackbarLift = with(density) { (insets.bottom - navBar).coerceAtLeast(0).toDp() }
    }
    DisposableEffect(feedback) { onDispose { feedback.snackbarLift = 0.dp } }

    Box(modifier.fillMaxSize()) {
        MapSurface(retained = retainedNav, controller = map, navUiEnabled = false, guidanceInsets = MapInsets(), dark = KtmTheme.colors.isDark)

        // MO8: the card's thumbnail covers the map until the map has framed the track (or a few seconds have gone).
        var mapFramed by remember { mutableStateOf(false) }
        var coverGone by remember { mutableStateOf(false) }
        LaunchedEffect(mapFramed) { if (mapFramed) { kotlinx.coroutines.delay(COVER_HOLD_MS); coverGone = true } }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(COVER_MAX_MS); coverGone = true }
        val coverPoints by androidx.compose.runtime.produceState<List<com.navigator.app.data.rides.ThumbPoint>?>(ready?.ride?.let(container.rides::peekThumbnail), ready?.ride?.id) {
            val ride = ready?.ride ?: return@produceState
            value = runCatching { container.rides.thumbnail(ride) }.getOrNull()
        }
        MapCover(visible = !coverGone, points = coverPoints)

        // The map's listeners: a tap on the track seeks, panning ends following.
        LaunchedEffect(mapReady) {
            if (!mapReady) return@LaunchedEffect
            map.setMapClickListening(true)
            map.setGestureListening(true)
        }
        LaunchedEffect(map) { map.mapClicks.collect { vm.seekToMap(it.lat, it.lng, PlaybackEngine.tapRadiusM(map.cameraZoom(), it.lat)) } }
        LaunchedEffect(map) { map.gesturePans.collect { vm.stopFollowing() } }

        // The track is drawn again when it, or its smoothing, changes (this puts the marker back where playback is).
        LaunchedEffect(mapReady, ready?.points, ready?.smoothingMeters) {
            val r = ready ?: return@LaunchedEffect
            if (!mapReady) return@LaunchedEffect
            val at = point(vm.position.value, r.points) ?: return@LaunchedEffect
            map.showReplay(r.points, vm.bands, r.smoothingMeters.toDouble(), at, accent)
        }
        LaunchedEffect(mapReady, ready?.points, ready?.smoothingMeters) {
            val r = ready ?: return@LaunchedEffect
            if (!mapReady) return@LaunchedEffect
            vm.position.collect { index -> point(index, r.points)?.let { map.moveReplayMarker(it, following) } }
        }
        LaunchedEffect(ready?.following) {
            val r = ready ?: return@LaunchedEffect
            if (r.following) point(vm.position.value, r.points)?.let { map.moveReplayMarker(it, true) }
        }

        // The "you are here" marker: the track drawing clears the map, so it is put back after each one.
        val heading = rememberDeviceHeadingState()
        val device = rememberDeviceLatLng()
        LaunchedEffect(mapReady, device, ready?.points, ready?.smoothingMeters) {
            if (!mapReady) return@LaunchedEffect
            val ll = device ?: return@LaunchedEffect
            snapshotFlow { heading.value }.collect { map.showUserMarker(MapPoint(ll.latitude, ll.longitude), it) }
        }

        // The camera frames the whole track in what the sheet or panel leaves open, again for a new track (after a trim).
        var framed by remember { mutableStateOf<Any?>(null) }
        LaunchedEffect(mapReady, insets, ready?.points) {
            val r = ready ?: return@LaunchedEffect
            if (!mapReady || insets == MapInsets()) return@LaunchedEffect
            map.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            if (framed !== r.points) { framed = r.points; map.frameTrack(r.points) }
            mapFramed = true
        }
        DisposableEffect(map) { onDispose { map.setMapClickListening(false); map.setGestureListening(false) } }

        if (ui == ReplayUi.Loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = KtmTheme.accent.fill)

        ReplayContent(
            ui = ui, position = position, bearing = bearing, avgMode = vm.avgMode, onInsets = { insets = it },
            actions = ReplayActions(
                onBack = onBack,
                onShare = {
                    val r = ready?.ride ?: return@ReplayActions
                    scope.launch {
                        val intent = container.rides.gpxShare(context, r)
                        if (intent != null) runCatching { context.startActivity(intent) }
                        else feedback.snackbar(resources.getString(R.string.replay_share_failed))
                    }
                },
                onEdit = { vm.pause(); onEdit() }, onTrim = { vm.pause(); onTrim() },
                onDelete = { if (ready?.ride?.saved == true) confirmDelete = true else vm.delete() },
                onToggleStar = vm::toggleStar, onTogglePlay = vm::togglePlay, onRestart = vm::restart,
                onSeek = vm::seek, onScrubStart = vm::beginScrub, onToggleFollow = vm::toggleFollow,
                onStepSpeed = vm::stepSpeed, onToggleElevation = vm::toggleElevation, onStepSmoothing = vm::stepSmoothing,
                onRecenter = { ready?.let { r -> point(vm.position.value, r.points)?.let(map::recenter) } },
                onResetNorth = map::resetNorth,
            ),
        )
    }

    if (confirmDelete) {
        KtmDialog(
            title = stringResource(R.string.replay_delete_starred_title),
            message = stringResource(
                R.string.replay_delete_starred_message,
                ready?.ride?.destinationLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.replay_delete_unnamed),
            ),
            onDismiss = { confirmDelete = false },
            confirm = DialogAction(stringResource(R.string.rides_delete_confirm), { confirmDelete = false; vm.delete() }, ButtonStyle.Danger),
            dismiss = DialogAction(stringResource(R.string.replay_delete_keep), { confirmDelete = false }, ButtonStyle.Text),
        )
    }
}
