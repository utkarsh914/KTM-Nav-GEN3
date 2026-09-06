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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import com.navigator.app.ui.components.CircleBackButton
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val BAND_COLORS = mapOf(
    RideMetrics.SpeedBand.SLOW to 0xFF2ECC71.toInt(),      // green
    RideMetrics.SpeedBand.MEDIUM to 0xFFF1C40F.toInt(),    // yellow
    RideMetrics.SpeedBand.FAST to 0xFFE67E22.toInt(),      // orange
    RideMetrics.SpeedBand.VERY_FAST to 0xFFE74C3C.toInt(), // red
)

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
    var currentIndex by remember(rideId) { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var speedMult by remember { mutableStateOf(1) }
    var following by remember { mutableStateOf(false) }
    var mapBearing by remember { mutableStateOf(0f) }
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
    val curPoint = points?.getOrNull(currentIndex)
    val locateButtonVisible = run {
        val p = curPoint ?: return@run false
        val t = camTarget ?: return@run true
        val centered = RideMetrics.haversineMeters(t.latitude, t.longitude, p.lat, p.lng) < CENTER_MATCH_METERS
        val zoomMatch = abs(camZoom - FOLLOW_ZOOM) < ZOOM_MATCH_EPS
        !(centered && zoomMatch)
    }

    fun seekToFraction(fraction: Float) {
        val pts = points ?: return
        if (pts.size < 2) return
        currentIndex = (fraction.coerceIn(0f, 1f) * pts.lastIndex).toInt().coerceIn(0, pts.lastIndex)
        place(currentIndex)
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
                mapBearing = map.cameraPosition.bearing
                camTarget = map.cameraPosition.target
                camZoom = map.cameraPosition.zoom
                val syncCam = {
                    mapBearing = map.cameraPosition.bearing
                    camTarget = map.cameraPosition.target
                    camZoom = map.cameraPosition.zoom
                }
                map.setOnCameraMoveListener { syncCam() }
                map.setOnCameraIdleListener { syncCam() }
                // A user pan/zoom/rotate cancels auto-follow; our own programmatic
                // camera moves report a different reason and are ignored.
                map.setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        following = false
                    }
                }
            }
        }

        // Draw the track (polylines + start/end + moving marker) once ready.
        LaunchedEffect(gm, points) {
            val map = gm ?: return@LaunchedEffect
            val pts = points ?: return@LaunchedEffect
            drawTrack(map, pts)
            currentIndex = 0
            marker = if (pts.isNotEmpty()) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(pts.first().lat, pts.first().lng))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
                )
            } else null
        }

        // Keep the map's bottom padding equal to the control card height so the
        // camera centres (and frames) the track above it, then frame once.
        LaunchedEffect(gm, points, cardHeightPx) {
            val map = gm ?: return@LaunchedEffect
            val pts = points ?: return@LaunchedEffect
            if (pts.isEmpty()) return@LaunchedEffect
            runCatching { map.setPadding(0, 0, 0, cardHeightPx) }
            if (!framed && cardHeightPx > 0) {
                frameTrack(map, pts)
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
                val liveSpeed = pts.getOrNull(currentIndex)?.speedKmh
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
                        if (abs(mapBearing) > 0.5f) {
                            MapControlButton(
                                icon = OpenDashIcons.Compass,
                                desc = "Reset orientation to north",
                                tint = Ktm.Danger,
                                rotation = -mapBearing,
                                onClick = {
                                    gm?.let { map ->
                                        val north = CameraPosition.Builder(map.cameraPosition)
                                            .bearing(0f).tilt(0f).build()
                                        runCatching { map.animateCamera(CameraUpdateFactory.newCameraPosition(north)) }
                                    }
                                },
                            )
                        }
                        // Zoom-to-current — hidden once already centred at follow zoom.
                        if (locateButtonVisible) {
                            MapControlButton(
                                icon = OpenDashIcons.LocateFixed,
                                desc = "Zoom to current position",
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
                        durationText = formatDuration(r.durationSeconds),
                        avgSpeed = r.avgSpeedKmh.toInt(),
                        maxSpeed = r.maxSpeedKmh.toInt(),
                        liveSpeed = liveSpeed?.toInt(),
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
                    .clickable(onClick = onToggleFollow),
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
                    .clickable(onClick = onCycleSpeed),
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

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Orange)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Ktm.OnAccent, modifier = Modifier.size(24.dp))
    }
}

/** A neutral rounded-square map control matching the navigation screen chrome. */
@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: androidx.compose.ui.graphics.Color = Ktm.White,
    rotation: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(Ktm.ControlHeight)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, desc,
            tint = tint,
            modifier = Modifier.size(22.dp).rotate(rotation),
        )
    }
}

/** Clear the map and draw the speed-coloured track + start/end markers (no camera). */
private fun drawTrack(map: GoogleMap, pts: List<RidePoint>) {
    map.clear()
    if (pts.isEmpty()) return
    // Split into consecutive same-band runs so each is one coloured polyline.
    var runStart = 0
    var runBand = RideMetrics.band(pts[0].speedKmh)
    for (i in 1..pts.lastIndex) {
        val b = RideMetrics.band(pts[i].speedKmh)
        if (b != runBand) {
            addRun(map, pts.subList(runStart, i + 1), runBand)
            runStart = i
            runBand = b
        }
    }
    addRun(map, pts.subList(runStart, pts.size), runBand)

    map.addMarker(
        MarkerOptions().position(LatLng(pts.first().lat, pts.first().lng)).title("Start")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
    )
    map.addMarker(
        MarkerOptions().position(LatLng(pts.last().lat, pts.last().lng)).title("End")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
    )
}

/** Frame the whole track in the (padded) viewport. */
private fun frameTrack(map: GoogleMap, pts: List<RidePoint>) {
    if (pts.isEmpty()) return
    val bounds = LatLngBounds.builder()
    pts.forEach { bounds.include(LatLng(it.lat, it.lng)) }
    runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 140)) }
}

private fun addRun(map: GoogleMap, run: List<RidePoint>, band: RideMetrics.SpeedBand) {
    if (run.size < 2) return
    val color = BAND_COLORS[band] ?: 0xFF2ECC71.toInt()
    map.addPolyline(
        PolylineOptions()
            .addAll(run.map { LatLng(it.lat, it.lng) })
            .color(color)
            .width(12f),
    )
}
