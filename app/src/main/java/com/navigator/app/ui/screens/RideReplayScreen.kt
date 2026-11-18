package com.navigator.app.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import com.navigator.app.ui.components.CircleBackButton
import com.navigator.app.ui.components.SquareMapControl
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Longest gap we honour between fixes during playback, so a GPS dropout doesn't
 *  freeze the marker for minutes. */
private const val MAX_STEP_MS = 3000L

/** Playback tick; each frame consumes `PLAYBACK_FRAME_MS * speedMult` of recorded
 *  track time, so even 64x advances smoothly without a busy loop. */
private const val PLAYBACK_FRAME_MS = 16L

/** Supported playback multipliers, cycled by the speed chip. */
private val SPEED_STEPS = listOf(1, 2, 4, 8, 16, 32, 64)

/** Zoom used when the rider taps "locate" to zoom into the current ride point. */
private const val FOLLOW_ZOOM = 16.5f

/** The locate button hides once the current point is this close to the map centre
 *  (metres) AND the zoom is within [ZOOM_MATCH_EPS] of [FOLLOW_ZOOM]. */
private const val CENTER_MATCH_METERS = 20.0
private const val ZOOM_MATCH_EPS = 0.15f

/** Upper bound (metres) of the in-session "Track detail" simplification slider. */
private const val TRACK_DETAIL_MAX_M = 30

/** Visual track width of the popover sliders. */
private val SLIDER_WIDTH = 200.dp

/** Finger travel that spans the full slider range in press-drag mode. Smaller =
 *  more sensitive; ~100dp means a short swipe covers Off..max. */
private val SLIDER_TRAVEL = 100.dp

/** Hold this long (ms) without lifting to enter slider drag mode (hold-then-slide),
 *  even if the finger hasn't moved past the touch slop yet. */
private const val LONG_PRESS_MS = 200L

/** The control popovers on the replay controls row; at most one open at a time. */
private enum class ReplayPopover { SPEED, TRACK_DETAIL }

/**
 * Replays a recorded ride on the map: a speed-coloured track (green slow → red
 * fast) with start/end markers, a stats card, and animated playback (a marker
 * that walks the track using the recorded timestamps, with play/pause, a seek
 * bar you can tap/scrub, and 1x…64x speed). A locate button zooms to the current
 * point, a follow toggle keeps it centred, and a compass resets the map to north.
 * Reuses the app's retained [NavigationView].
 */
@Composable
fun RideReplayScreen(
    retainedNav: RetainedNavigationView,
    rideId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { RideStore(context) }
    // Speed-band colouring scheme (Advanced settings), read once per screen entry.
    val speedBands = remember {
        val s = com.navigator.app.settings.AppSettings(context)
        RideMetrics.SpeedBands(s.speedBandBaseKmh, s.speedBandStepKmh, s.speedBandCount)
    }
    // Extra map bottom padding on top of the measured card height (its outer
    // margin + a small gap), so the framed/centred track clears the card.
    val bottomMarginPx = with(LocalDensity.current) { 28.dp.roundToPx() }
    var ride by remember(rideId) { mutableStateOf(rideId?.let { store.get(it) }) }

    var points by remember(rideId) { mutableStateOf<List<RidePoint>?>(null) }
    LaunchedEffect(rideId) {
        points = withContext(Dispatchers.IO) { rideId?.let { store.points(it) } ?: emptyList() }
    }

    var gm by remember { mutableStateOf<GoogleMap?>(null) }
    val mapOwnerToken = remember { Any() }
    var marker by remember { mutableStateOf<Marker?>(null) }
    // Custom compass-driven "you are here" marker (SDK puck is small + GPS-only).
    val userMarker = remember {
        UserLocationMarker(
            context,
            LocationMarkerStyle.fromId(com.navigator.app.settings.AppSettings(context).locationMarkerStyle),
        )
    }
    // Heading as a State object read only inside effects (never in composition),
    // so a ~16 Hz sensor tick updates the marker without recomposing the screen.
    val deviceHeadingState = rememberDeviceHeadingState()
    val deviceLatLng = rememberDeviceLatLng()
    var currentIndex by remember(rideId) { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    // Default playback speed 4x (a full ride at 1x is too slow to skim).
    var speedMult by remember { mutableStateOf(4) }
    var following by remember { mutableStateOf(false) }
    // In-session track-simplification distance (Douglas-Peucker epsilon). Keyed on
    // rideId so it resets to the default on every screen entry - deliberately NOT
    // persisted (a per-view "Track detail" knob, not a saved preference). 0 = off.
    var simplifyEpsilonM by remember(rideId) { mutableStateOf(SIMPLIFY_EPSILON_M) }
    // Which control popover (if any) is open - hoisted so Speed and Track-detail
    // are mutually exclusive (opening one closes the other). null = none.
    var openPopover by remember { mutableStateOf<ReplayPopover?>(null) }
    // Kept as the State object (not read via `by` in composition): the camera
    // move-listener writes it per-frame during a rotate gesture, and only the
    // isolated ResetNorthControl child reads it - so bearing churn recomposes a
    // single ~48dp button, not the whole replay overlay.
    val mapBearingState = remember { mutableStateOf(0f) }
    // Live camera target/zoom, so we can hide the locate button when the current
    // point is already centred at the follow zoom (see [locateButtonVisible]).
    var camTarget by remember { mutableStateOf<LatLng?>(null) }
    var camZoom by remember { mutableStateOf(0f) }
    // Height (px) occupied by the bottom control card, so the map centres above it.
    var cardHeightPx by remember { mutableStateOf(0) }
    var framed by remember(rideId) { mutableStateOf(false) }

    // Reposition the marker to a track index and, while following, pan the camera
    // to keep it centred (moveCamera is cheap and doesn't count as a user gesture).
    fun place(index: Int) {
        val pts = points ?: return
        val p = pts.getOrNull(index) ?: return
        val ll = LatLng(p.lat, p.lng)
        marker?.position = ll
        if (following) runCatching { gm?.moveCamera(CameraUpdateFactory.newLatLng(ll)) }
    }

    // The locate/zoom button is shown unless the current point is already centred
    // at the follow zoom (so it disappears once you've zoomed in / while following).
    // Wrapped in derivedStateOf so it only recomputes when its inputs actually
    // change (camTarget/camZoom settle at camera-idle, currentIndex at playback),
    // and only the locate button - not the whole overlay - recomposes.
    val locateButtonVisible by remember {
        derivedStateOf {
            val p = points?.getOrNull(currentIndex) ?: return@derivedStateOf false
            val t = camTarget ?: return@derivedStateOf true
            val centered = RideMetrics.haversineMeters(t.latitude, t.longitude, p.lat, p.lng) < CENTER_MATCH_METERS
            val zoomMatch = abs(camZoom - FOLLOW_ZOOM) < ZOOM_MATCH_EPS
            !(centered && zoomMatch)
        }
    }

    fun seekToFraction(fraction: Float) {
        val pts = points ?: return
        if (pts.size < 2) return
        currentIndex = (fraction.coerceIn(0f, 1f) * pts.lastIndex).toInt().coerceIn(0, pts.lastIndex)
        place(currentIndex)
    }

    // Tap-on-track: jump playback to the recorded point nearest the tapped
    // location (pauses playback, repositions the marker).
    fun jumpToNearest(tapped: LatLng) {
        val pts = points ?: return
        if (pts.isEmpty()) return
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in pts.indices) {
            val d = RideMetrics.haversineMeters(tapped.latitude, tapped.longitude, pts[i].lat, pts[i].lng)
            if (d < bestDist) { bestDist = d; best = i }
        }
        // A tap far from the track is just a map tap, not a seek.
        if (bestDist > SEEK_TAP_MAX_M) return
        playing = false
        currentIndex = best
        place(best)
    }

    Box(Modifier.fillMaxSize().background(Ktm.Screen)) {

        // ---- Map surface (reused retained NavigationView) --------------------
        val navView = remember(retainedNav) { retainedNav.getOrCreate() }
        AndroidView(
            factory = {
                (navView.parent as? ViewGroup)?.removeView(navView)
                navView
            },
            modifier = Modifier.fillMaxSize(),
        )
        LaunchedEffect(navView) {
            runCatching { navView.setNavigationUiEnabled(false) }
            navView.getMapAsync { map ->
                gm = map
                retainedNav.mapOwner = mapOwnerToken
                map.uiSettings.isMyLocationButtonEnabled = false
                map.uiSettings.isCompassEnabled = false
                // We draw our own compass-driven location marker (see below), so
                // leave the SDK's small GPS-course-only puck off to avoid two dots.
                // Tap anywhere on/near the track to seek playback to that point.
                map.setOnMapClickListener { latLng -> jumpToNearest(latLng) }
                mapBearingState.value = map.cameraPosition.bearing
                camTarget = map.cameraPosition.target
                camZoom = map.cameraPosition.zoom
                // During an active gesture (pinch-zoom/pan) only update the cheap
                // bearing state the compass needs. Writing target/zoom on every
                // frame recomputed [locateButtonVisible] and recomposed the whole
                // overlay per frame, which made zooming visibly laggy. Defer the
                // target/zoom sync to camera-idle so it settles once the gesture
                // finishes rather than thrashing state mid-pinch. Bearing writes
                // are isolated to ResetNorthControl (see below), so even per-frame
                // rotation churn recomposes only that button.
                map.setOnCameraMoveListener {
                    val b = map.cameraPosition.bearing
                    if (b != mapBearingState.value) mapBearingState.value = b
                }
                map.setOnCameraIdleListener {
                    mapBearingState.value = map.cameraPosition.bearing
                    camTarget = map.cameraPosition.target
                    camZoom = map.cameraPosition.zoom
                }
                // A user pan/zoom/rotate cancels auto-follow; our own programmatic
                // camera moves report a different reason and are ignored.
                map.setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        following = false
                    }
                }
            }
        }

        // Draw the track (polylines + start/end + moving marker) once ready, and
        // redraw when the simplification distance changes (Track detail control).
        LaunchedEffect(gm, points, simplifyEpsilonM) {
            val map = gm ?: return@LaunchedEffect
            val pts = points ?: return@LaunchedEffect
            drawRideTrack(map, pts, speedBands, simplifyEpsilonM = simplifyEpsilonM)
            // Keep the current playback position across a simplification redraw;
            // only clamp if it somehow fell out of range. (Ride/map change resets
            // currentIndex via its own remember(rideId).)
            val idx = currentIndex.coerceIn(0, pts.lastIndex.coerceAtLeast(0))
            marker = if (pts.isNotEmpty()) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(pts[idx].lat, pts[idx].lng))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
                )
            } else null
            // drawRideTrack() cleared the map, dropping our user marker too; drop
            // the stale handle so the heading effect re-adds it on its next tick.
            userMarker.remove()
            deviceLatLng?.let { userMarker.update(map, it, deviceHeadingState.value) }
        }

        // Compass-driven "you are here" marker: follows the device location and
        // rotates its beam with the phone's orientation (like Google Maps).
        // Heading is collected via snapshotFlow so beam rotation updates happen
        // off the composition path - a sensor tick mutates the marker directly
        // and never recomposes RideReplayScreen (this was a key zoom-lag source).
        LaunchedEffect(gm, deviceLatLng) {
            val map = gm ?: return@LaunchedEffect
            val ll = deviceLatLng ?: return@LaunchedEffect
            snapshotFlow { deviceHeadingState.value }.collect { heading ->
                userMarker.update(map, ll, heading)
            }
        }

        // Keep the map's bottom padding equal to the control card height so the
        // camera centres (and frames) the track above it, then frame once.
        LaunchedEffect(gm, points, cardHeightPx) {
            val map = gm ?: return@LaunchedEffect
            val pts = points ?: return@LaunchedEffect
            if (pts.isEmpty()) return@LaunchedEffect
            runCatching { map.setPadding(0, 0, 0, cardHeightPx) }
            if (!framed && cardHeightPx > 0) {
                frameRideTrack(map, pts)
                framed = true
            }
        }

        // Playback: advance the marker using a per-frame time budget so high
        // speeds stay smooth (carry unspent budget across frames for real-time 1x).
        LaunchedEffect(playing, speedMult, points, gm) {
            val pts = points ?: return@LaunchedEffect
            if (!playing || pts.size < 2) return@LaunchedEffect
            if (currentIndex >= pts.lastIndex) currentIndex = 0
            var carryMs = 0L
            while (playing && currentIndex < pts.lastIndex) {
                delay(PLAYBACK_FRAME_MS)
                carryMs += PLAYBACK_FRAME_MS * speedMult
                while (currentIndex < pts.lastIndex) {
                    val gap = (pts[currentIndex + 1].timeMs - pts[currentIndex].timeMs)
                        .coerceIn(0L, MAX_STEP_MS)
                    if (gap <= carryMs) {
                        carryMs -= gap
                        currentIndex++
                    } else break
                }
                place(currentIndex)
            }
            if (currentIndex >= pts.lastIndex) playing = false
        }

        DisposableEffect(Unit) {
            onDispose {
                // Leave the shared map clean for the next screen that reuses it - but
                // only if that screen hasn't already taken it over (the incoming screen
                // is composed before this one is disposed during the transition).
                runCatching { userMarker.remove() } // our own marker, whoever owns the map now
                if (retainedNav.mapOwner === mapOwnerToken) runCatching {
                    gm?.setOnCameraMoveListener(null)
                    gm?.setOnCameraIdleListener(null)
                    gm?.setOnCameraMoveStartedListener(null)
                    gm?.setOnMapClickListener(null)
                    userMarker.remove()
                    gm?.setPadding(0, 0, 0, 0)
                    gm?.clear()
                }
            }
        }

        // ---- Back button -----------------------------------------------------
        Box(
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding()
                .padding(start = 16.dp, top = 10.dp),
        ) {
            CircleBackButton(onClick = onBack)
        }

        // ---- Bottom overlay: loading / empty / stats + controls --------------
        when {
            points == null -> Box(
                Modifier.align(Alignment.Center),
            ) { CircularProgressIndicator(color = Ktm.Orange) }

            points!!.isEmpty() || ride == null -> Box(
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp),
            ) {
                Text(
                    "This recording has no track data.",
                    color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp,
                )
            }

            else -> {
                val pts = points!!
                val r = ride!!
                val curP = pts.getOrNull(currentIndex)
                val liveSpeed = curP?.speedKmh
                val timeText = curP?.let { formatClockTime(it.timeMs) } ?: "--:--:--"
                // Map buttons stack sits just above the info card; the card is
                // anchored to the bottom so button visibility never shifts it.
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .systemBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(end = 2.dp, bottom = 10.dp),
                    ) {
                        // Reset-to-north (only when rotated) — on top after the swap.
                        // Isolated child so per-frame bearing changes recompose
                        // only this button, not the whole overlay.
                        ResetNorthControl(
                            bearingState = mapBearingState,
                            onResetNorth = {
                                gm?.let { map ->
                                    val north = CameraPosition.Builder(map.cameraPosition)
                                        .bearing(0f).tilt(0f).build()
                                    runCatching { map.animateCamera(CameraUpdateFactory.newCameraPosition(north)) }
                                }
                            },
                        )
                        // Zoom-to-current — hidden once already centred at follow zoom.
                        if (locateButtonVisible) {
                            SquareMapControl(
                                icon = OpenDashIcons.LocateFixed,
                                contentDescription = "Zoom to current position",
                                onClick = {
                                    points?.getOrNull(currentIndex)?.let { p ->
                                        runCatching {
                                            gm?.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), FOLLOW_ZOOM),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }

                    ReplayControls(
                        title = r.destinationLabel ?: "Ride",
                        dateText = formatRideDate(r.startMs),
                        distanceText = DistanceFormatter.format(r.distanceMeters, DistanceUnits.METRIC),
                        durationText = formatDurationShort(r.durationSeconds),
                        avgSpeed = r.avgSpeedKmh.toInt(),
                        maxSpeed = r.maxSpeedKmh.toInt(),
                        liveSpeed = liveSpeed?.toInt(),
                        timeText = timeText,
                        progress = if (pts.size > 1) currentIndex.toFloat() / pts.lastIndex else 0f,
                        playing = playing,
                        speedMult = speedMult,
                        simplifyEpsilonM = simplifyEpsilonM,
                        openPopover = openPopover,
                        onOpenPopover = { openPopover = it },
                        saved = r.saved,
                        following = following,
                        onPlayPause = {
                            if (currentIndex >= pts.lastIndex) currentIndex = 0
                            playing = !playing
                        },
                        onReplay = {
                            playing = false
                            currentIndex = 0
                            place(0)
                        },
                        onSetSpeed = { speedMult = it },
                        onSetSimplify = { simplifyEpsilonM = it },
                        onToggleFollow = {
                            following = !following
                            if (following) place(currentIndex)
                        },
                        onToggleSave = {
                            val newSaved = !r.saved
                            store.setSaved(r.id, newSaved)
                            ride = r.copy(saved = newSaved)
                            android.widget.Toast.makeText(
                                context,
                                if (newSaved) "Ride saved" else "Ride unsaved",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onSeek = { fraction -> seekToFraction(fraction) },
                        onScrubStart = { fraction -> playing = false; seekToFraction(fraction) },
                        modifier = Modifier.fillMaxWidth()
                            .onGloballyPositioned { cardHeightPx = it.size.height + bottomMarginPx },
                    )
                }
            }
        }
    }
}

/**
 * A control-row button that opens a horizontal slider **popover directly above
 * itself**, with two ways to use it:
 *  - **Press-drag:** press the button and, without lifting, slide left/right - the
 *    value tracks your finger live; releasing commits and dismisses.
 *  - **Tap:** opens the popover and leaves it up so you can drag the slider
 *    normally; tapping outside dismisses.
 *
 * The button owns the whole gesture and drives the value by cumulative horizontal
 * drag over [SLIDER_TRAVEL] (so no cross-window coordinate math is needed); the
 * popover is a visual mirror during a press-drag and an interactive slider in
 * tap-open mode. [valueFraction] is the current value as 0..1; [labelFor] formats
 * a fraction into the value text (used for both the button face and popover, so
 * both update live). [onFraction] fires live; [onCommit] fires on release/dismiss
 * (used by Track detail to defer its map redraw).
 */
@Composable
private fun PopoverSliderButton(
    open: Boolean,
    valueFraction: Float,
    label: String,
    labelFor: (Float) -> String,
    onRequestOpen: () -> Unit,
    onRequestClose: () -> Unit,
    onFraction: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    // Latest fraction; drives the face + popover live. Synced to the source of
    // truth whenever we're not actively showing the popover.
    var fraction by remember { mutableStateOf(valueFraction) }
    if (!open) fraction = valueFraction

    // The long-lived pointerInput coroutine (key = Unit) captures values from
    // first composition; wrap the ones it reads so it always sees the latest.
    val openState = androidx.compose.runtime.rememberUpdatedState(open)
    val requestOpen = androidx.compose.runtime.rememberUpdatedState(onRequestOpen)
    val requestClose = androidx.compose.runtime.rememberUpdatedState(onRequestClose)
    val commit = androidx.compose.runtime.rememberUpdatedState(onCommit)
    val emitFraction = androidx.compose.runtime.rememberUpdatedState(onFraction)
    val labelForNow = androidx.compose.runtime.rememberUpdatedState(labelFor)

    // Full slider range over ~SLIDER_TRAVEL of finger movement (sensitivity).
    val travelPx = with(density) { SLIDER_TRAVEL.toPx() }
    val touchSlop = with(density) { 6.dp.toPx() }
    // Suppresses a button-tap re-opening right after the Popup's outside-dismiss
    // fired for that same tap (they're separate windows and both see the touch).
    val lastDismissNanos = remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 44.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(if (open) Ktm.Orange else Ktm.Screen)
            .border(1.dp, if (open) Ktm.Orange else Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.nanoTime()
                        var totalDx = 0f
                        var isDrag = false
                        // Discrete value label at drag start; a tick fires whenever
                        // it changes so the rider feels each step/metre.
                        var lastTickLabel = labelForNow.value(fraction)
                        // Enter drag mode either by moving past the slop OR by
                        // holding past the long-press timeout (hold-then-slide).
                        fun enterDrag() {
                            if (isDrag) return
                            isDrag = true
                            haptics.tap()
                            if (!openState.value) requestOpen.value()
                        }
                        // Track this pointer until it lifts.
                        while (true) {
                            // Wait for the next event, but wake up around the
                            // long-press deadline so a stationary hold still opens.
                            val event = withTimeoutOrNull(60) { awaitPointerEvent() }
                            if (event == null) {
                                // No movement; check for long-press hold.
                                val heldMs = (System.nanoTime() - downTime) / 1_000_000
                                if (!isDrag && heldMs >= LONG_PRESS_MS) enterDrag()
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            val dx = change.positionChange().x
                            totalDx += dx
                            if (!isDrag && kotlin.math.abs(totalDx) > touchSlop) enterDrag()
                            if (isDrag && dx != 0f) {
                                val next = (fraction + dx / travelPx).coerceIn(0f, 1f)
                                fraction = next
                                emitFraction.value(next)
                                // Tick on each discrete value change.
                                val lbl = labelForNow.value(next)
                                if (lbl != lastTickLabel) {
                                    lastTickLabel = lbl
                                    haptics.tap()
                                }
                                change.consume()
                            }
                        }
                        if (isDrag) {
                            // Press-drag / hold-slide released: commit + close.
                            commit.value(fraction)
                            requestClose.value()
                        } else {
                            // Bare tap: toggle (haptic on both open and close).
                            // Ignore an open-tap that just dismissed the popup.
                            val sinceDismiss = (System.nanoTime() - lastDismissNanos.value) / 1_000_000
                            if (openState.value) {
                                haptics.tap()
                                commit.value(fraction)
                                requestClose.value()
                            } else if (sinceDismiss > 250) {
                                haptics.tap()
                                requestOpen.value()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            labelFor(fraction),
            color = if (open) Ktm.OnAccent else Ktm.White,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            maxLines = 1,
        )

        if (open) {
            // Popover anchored centered above this button.
            val anchor = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize,
                    ): IntOffset {
                        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
                            .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
                        val y = anchorBounds.top - popupContentSize.height - 8
                        return IntOffset(x, y.coerceAtLeast(8))
                    }
                }
            }
            Popup(
                popupPositionProvider = anchor,
                onDismissRequest = {
                    lastDismissNanos.value = System.nanoTime()
                    haptics.tap()
                    onCommit(fraction)
                    onRequestClose()
                },
                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            ) {
                SliderPopoverContent(
                    label = label,
                    labelFor = labelFor,
                    fraction = fraction,
                    onFraction = { f -> fraction = f; onFraction(f) },
                )
            }
        }
    }
}

/** Visual + tap-interactive content of a [PopoverSliderButton]'s popover.
 *  [labelFor] formats a fraction to its discrete value text; a haptic tick fires
 *  whenever that text changes as the in-popover slider is dragged. */
@Composable
private fun SliderPopoverContent(
    label: String,
    labelFor: (Float) -> String,
    fraction: Float,
    onFraction: (Float) -> Unit,
) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    // Last discrete value tick'd, so dragging the slider buzzes per value change.
    val lastTick = remember { mutableStateOf(labelFor(fraction)) }
    Column(
        modifier = Modifier
            .width(SLIDER_WIDTH + 28.dp)
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, color = Ktm.TextPrimary, fontFamily = Barlow, fontSize = 13.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                labelFor(fraction), color = Ktm.Orange, fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
            )
        }
        Spacer(Modifier.size(4.dp))
        androidx.compose.material3.Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { f ->
                onFraction(f)
                val lbl = labelFor(f)
                if (lbl != lastTick.value) {
                    lastTick.value = lbl
                    haptics.tap()
                }
            },
            modifier = Modifier.width(SLIDER_WIDTH),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Ktm.Orange,
                activeTrackColor = Ktm.Orange,
                inactiveTrackColor = Ktm.Border,
            ),
        )
    }
}

/**
 * Reset-to-north button, shown only while the map is rotated. Reads [bearingState]
 * itself so the camera move-listener's per-frame bearing writes recompose only
 * this ~48dp control (not the whole replay overlay) - the core of the zoom/rotate
 * lag fix.
 */
@Composable
private fun ResetNorthControl(
    bearingState: State<Float>,
    onResetNorth: () -> Unit,
) {
    val bearing by bearingState
    if (abs(bearing) > 0.5f) {
        SquareMapControl(
            icon = OpenDashIcons.Compass,
            contentDescription = "Reset orientation to north",
            tint = Ktm.Danger,
            rotation = -bearing,
            onClick = onResetNorth,
        )
    }
}

@Composable
private fun ReplayControls(
    title: String,
    dateText: String,
    distanceText: String,
    durationText: String,
    avgSpeed: Int,
    maxSpeed: Int,
    liveSpeed: Int?,
    timeText: String,
    progress: Float,
    playing: Boolean,
    speedMult: Int,
    simplifyEpsilonM: Double,
    openPopover: ReplayPopover?,
    onOpenPopover: (ReplayPopover?) -> Unit,
    saved: Boolean,
    following: Boolean,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSetSpeed: (Int) -> Unit,
    onSetSimplify: (Double) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleSave: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubStart: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(dateText, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp)
            }
            // Save / pin toggle with a subtle pop animation.
            SaveStar(saved = saved, boxSize = 36.dp, iconSize = 24.dp, onToggle = onToggleSave)
        }

        Spacer(Modifier.size(10.dp))
        // Equal-weight stat strip (matches the ride-list card) so values never
        // wrap however long the numbers get. Regular spaces are swapped for a
        // thin space so the number and its unit sit closer (e.g. "70 km").
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatColumn(distanceText.tightSpaces(), "DISTANCE", modifier = Modifier.weight(1f))
            StatDivider()
            StatColumn(durationText.tightSpaces(), "TIME", modifier = Modifier.weight(1f))
            StatDivider()
            StatColumn("$avgSpeed kmph".tightSpaces(), "AVG", modifier = Modifier.weight(1f))
            StatDivider()
            StatColumn("$maxSpeed kmph".tightSpaces(), "MAX", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.size(10.dp))
        // Time-of-day of the current fix, inline above the seek bar (updates as
        // playback advances / the rider scrubs). Folded here to save a row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TIME OF DAY",
                color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 9.sp, letterSpacing = 1.sp,
            )
            Text(timeText, color = Ktm.White, fontFamily = JetBrainsMono, fontSize = 13.sp)
        }

        Spacer(Modifier.size(6.dp))
        // Seek bar: tap to seek, drag to scrub. The touch target is tall for
        // glove use; the visible bar stays thin.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        onSeek((off.x / size.width.toFloat()))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { off -> onScrubStart(off.x / size.width.toFloat()) },
                        onHorizontalDrag = { change, _ ->
                            onSeek(change.position.x / size.width.toFloat())
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Ktm.BorderSoft),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Ktm.Orange),
                )
            }
        }

        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundControl(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                desc = if (playing) "Pause" else "Play",
                onClick = onPlayPause,
            )
            Spacer(Modifier.size(8.dp))
            RoundControl(icon = Icons.Filled.Replay, desc = "Restart", onClick = onReplay)
            Spacer(Modifier.size(8.dp))
            // Follow toggle: keeps the camera centred on the moving marker.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Ktm.RadiusButton))
                    .background(if (following) Ktm.Orange else Ktm.Screen)
                    .border(
                        1.dp,
                        if (following) Ktm.Orange else Ktm.Border,
                        RoundedCornerShape(Ktm.RadiusButton),
                    )
                    .clickable { haptics.toggle(!following); onToggleFollow() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    if (following) "Stop following" else "Follow ride position",
                    tint = if (following) Ktm.OnAccent else Ktm.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            // Speed: press-drag or tap opens a slider popover above the button.
            // Value snaps to the discrete SPEED_STEPS; updates playback live.
            val speedLast = (SPEED_STEPS.size - 1).coerceAtLeast(1)
            fun fractionToSpeed(f: Float) = SPEED_STEPS[(f * speedLast).roundToInt().coerceIn(0, speedLast)]
            PopoverSliderButton(
                open = openPopover == ReplayPopover.SPEED,
                valueFraction = SPEED_STEPS.indexOf(speedMult).coerceAtLeast(0).toFloat() / speedLast,
                label = "Playback speed",
                labelFor = { f -> "${fractionToSpeed(f)}x" },
                onRequestOpen = { onOpenPopover(ReplayPopover.SPEED) },
                onRequestClose = { onOpenPopover(null) },
                onFraction = { f ->
                    val v = fractionToSpeed(f)
                    if (v != speedMult) onSetSpeed(v)
                },
                onCommit = { /* speed updates live; nothing deferred */ },
            )
            Spacer(Modifier.size(8.dp))
            // Track detail: same interaction; label updates live, but the map
            // redraw is deferred to release (onCommit) to stay smooth on big rides.
            fun fractionToMetres(f: Float) = (f * TRACK_DETAIL_MAX_M).roundToInt()
            PopoverSliderButton(
                open = openPopover == ReplayPopover.TRACK_DETAIL,
                valueFraction = (simplifyEpsilonM / TRACK_DETAIL_MAX_M).toFloat().coerceIn(0f, 1f),
                label = "Track detail",
                labelFor = { f -> fractionToMetres(f).let { if (it <= 0) "Off" else "$it m" } },
                onRequestOpen = { onOpenPopover(ReplayPopover.TRACK_DETAIL) },
                onRequestClose = { onOpenPopover(null) },
                onFraction = { /* live label only; no redraw mid-drag */ },
                onCommit = { f -> onSetSimplify(fractionToMetres(f).toDouble()) },
            )
            Spacer(Modifier.weight(1f))
            // Live speed at the playback marker.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    liveSpeed?.let { "$it" } ?: "--",
                    color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                )
                Text("kmph", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 11.sp)
            }
        }
    }
}

/** Replace normal spaces with a thin space (U+2009) so a stat value's number and
 *  unit ("70 km", "1h 42m", "40 kmph") sit closer together in the compact card.
 *  Shared with the ride-list card. */
internal fun String.tightSpaces(): String = replace(' ', '\u2009')

/** Wall-clock time (HH:mm:ss, local) of a recorded fix's epoch millis. */
private fun formatClockTime(epochMs: Long): String =
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Orange)
            .clickable { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Ktm.OnAccent, modifier = Modifier.size(24.dp))
    }
}

/** Max distance (m) between a map tap and the track for the tap to seek playback. */
private const val SEEK_TAP_MAX_M = 100.0
