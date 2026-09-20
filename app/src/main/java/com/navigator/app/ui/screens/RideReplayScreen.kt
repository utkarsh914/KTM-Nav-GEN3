package com.navigator.app.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    var speedMult by remember { mutableStateOf(1) }
    var following by remember { mutableStateOf(false) }
    // In-session track-simplification distance (Douglas-Peucker epsilon). Keyed on
    // rideId so it resets to the default on every screen entry - deliberately NOT
    // persisted (a per-view "Track detail" knob, not a saved preference). 0 = off.
    var simplifyEpsilonM by remember(rideId) { mutableStateOf(SIMPLIFY_EPSILON_M) }
    var showDetailDialog by remember { mutableStateOf(false) }
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
                // Leave the shared map clean for the next screen that reuses it.
                runCatching {
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
                        // Track detail: in-session Douglas-Peucker distance (or off).
                        // Coarser = smoother zoom on very long rides.
                        SquareMapControl(
                            icon = OpenDashIcons.Settings,
                            contentDescription = "Track detail",
                            onClick = { showDetailDialog = true },
                        )
                    }

                    ReplayControls(
                        title = r.destinationLabel ?: "Ride",
                        dateText = formatRideDate(r.startMs),
                        distanceText = DistanceFormatter.format(r.distanceMeters, DistanceUnits.METRIC),
                        durationText = formatDuration(r.durationSeconds),
                        avgSpeed = r.avgSpeedKmh.toInt(),
                        maxSpeed = r.maxSpeedKmh.toInt(),
                        liveSpeed = liveSpeed?.toInt(),
                        timeText = timeText,
                        progress = if (pts.size > 1) currentIndex.toFloat() / pts.lastIndex else 0f,
                        playing = playing,
                        speedMult = speedMult,
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
                        onCycleSpeed = {
                            val i = SPEED_STEPS.indexOf(speedMult)
                            speedMult = SPEED_STEPS[(i + 1) % SPEED_STEPS.size]
                        },
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

        if (showDetailDialog) {
            TrackDetailDialog(
                selectedEpsilonM = simplifyEpsilonM,
                // Slider commits on release; keep the dialog open so the rider can
                // fine-tune and see the redraw, closing only via DONE.
                onSelect = { simplifyEpsilonM = it },
                onDismiss = { showDetailDialog = false },
            )
        }
    }
}

/**
 * In-session "Track detail" control: a slider for the Douglas-Peucker
 * simplification distance ([TRACK_DETAIL_MAX_M] m range; 0 = off). Not persisted -
 * a per-view knob to trade line detail for zoom smoothness on very long rides.
 *
 * The track is redrawn only when the drag finishes (onValueChangeFinished), not on
 * every tick, so dragging stays smooth on huge rides; the label tracks the live
 * drag value.
 */
@Composable
private fun TrackDetailDialog(
    selectedEpsilonM: Double,
    onSelect: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Live slider position while dragging (whole metres); committed on release.
    var sliderValue by remember(selectedEpsilonM) { mutableStateOf(selectedEpsilonM.toFloat()) }
    val rounded = sliderValue.roundToInt()
    val valueLabel = if (rounded <= 0) "Off (full detail)" else "$rounded m"

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextPrimary,
        title = {
            Text("Track detail", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Simplifies the drawn track. Coarser = smoother zoom on long rides.",
                    color = Ktm.Dim, fontFamily = Barlow, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Simplification", color = Ktm.TextPrimary, fontFamily = Barlow,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        valueLabel, color = Ktm.Orange, fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                    )
                }
                androidx.compose.material3.Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    // Commit (and redraw) only when the drag settles.
                    onValueChangeFinished = { onSelect(sliderValue.roundToInt().toDouble()) },
                    valueRange = 0f..TRACK_DETAIL_MAX_M.toFloat(),
                    // Whole-metre steps across the range.
                    steps = TRACK_DETAIL_MAX_M - 1,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Ktm.Orange,
                        activeTrackColor = Ktm.Orange,
                        inactiveTrackColor = Ktm.Border,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Off", color = Ktm.Dim, fontFamily = Barlow, fontSize = 12.sp)
                    Text("${TRACK_DETAIL_MAX_M} m", color = Ktm.Dim, fontFamily = Barlow, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("DONE", color = Ktm.Orange)
            }
        },
    )
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
    saved: Boolean,
    following: Boolean,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onCycleSpeed: () -> Unit,
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
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(dateText, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp)
            }
            // Save / pin toggle with a subtle pop animation.
            SaveStar(saved = saved, boxSize = 40.dp, iconSize = 24.dp, onToggle = onToggleSave)
        }

        Spacer(Modifier.size(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("DISTANCE", distanceText)
            Stat("TIME", durationText)
            Stat("AVG", "$avgSpeed km/h")
            Stat("MAX", "$maxSpeed km/h")
        }

        Spacer(Modifier.size(12.dp))
        // Current instant's wall-clock time (of the recorded fix) — updates as
        // playback advances or the rider scrubs.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TIME OF DAY",
                color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                fontSize = 10.sp, letterSpacing = 1.sp,
            )
            Text(
                timeText,
                color = Ktm.White, fontFamily = JetBrainsMono, fontSize = 14.sp,
            )
        }

        Spacer(Modifier.size(8.dp))
        // Seek bar: tap to seek, drag to scrub. The touch target is tall for
        // glove use; the visible bar stays thin.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
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

        Spacer(Modifier.size(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundControl(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                desc = if (playing) "Pause" else "Play",
                onClick = onPlayPause,
            )
            Spacer(Modifier.size(12.dp))
            RoundControl(icon = Icons.Filled.Replay, desc = "Restart", onClick = onReplay)
            Spacer(Modifier.size(12.dp))
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
            Spacer(Modifier.size(12.dp))
            // Speed multiplier chip.
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 44.dp)
                    .clip(RoundedCornerShape(Ktm.RadiusButton))
                    .background(Ktm.Screen)
                    .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
                    .clickable { haptics.tap(); onCycleSpeed() },
                contentAlignment = Alignment.Center,
            ) {
                Text("${speedMult}x", color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            // Live speed at the playback marker.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    liveSpeed?.let { "$it" } ?: "--",
                    color = Ktm.Orange, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                )
                Text("km/h", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Ktm.White, fontFamily = JetBrainsMono, fontSize = 13.sp)
    }
}

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


