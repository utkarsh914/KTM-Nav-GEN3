package com.navigator.app.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val BAND_COLORS = mapOf(
    RideMetrics.SpeedBand.SLOW to 0xFF2ECC71.toInt(),      // green
    RideMetrics.SpeedBand.MEDIUM to 0xFFF1C40F.toInt(),    // yellow
    RideMetrics.SpeedBand.FAST to 0xFFE67E22.toInt(),      // orange
    RideMetrics.SpeedBand.VERY_FAST to 0xFFE74C3C.toInt(), // red
)

/** Longest gap we honour between fixes during playback, so a GPS dropout doesn't
 *  freeze the marker for minutes. */
private const val MAX_STEP_MS = 3000L

/**
 * Replays a recorded ride on the map: a speed-coloured track (green slow → red
 * fast) with start/end markers, a stats card, and animated playback (a marker
 * that walks the track using the recorded timestamps, with play/pause and
 * 1x/2x/4x speed). Reuses the app's retained [NavigationView] surface.
 */
@Composable
fun RideReplayScreen(
    retainedNav: RetainedNavigationView,
    rideId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { RideStore(context) }
    val ride = remember(rideId) { rideId?.let { store.get(it) } }

    var points by remember(rideId) { mutableStateOf<List<RidePoint>?>(null) }
    LaunchedEffect(rideId) {
        points = withContext(Dispatchers.IO) { rideId?.let { store.points(it) } ?: emptyList() }
    }

    var gm by remember { mutableStateOf<GoogleMap?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var currentIndex by remember(rideId) { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var speedMult by remember { mutableStateOf(1) }

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
            }
        }

        // Draw the track once both the map and the points are ready.
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

        // Playback: advance the marker by the recorded inter-fix gaps / speed.
        LaunchedEffect(playing, speedMult, points, gm) {
            val pts = points ?: return@LaunchedEffect
            if (!playing || pts.size < 2) return@LaunchedEffect
            if (currentIndex >= pts.lastIndex) currentIndex = 0
            while (playing && currentIndex < pts.lastIndex) {
                val cur = pts[currentIndex]
                val nxt = pts[currentIndex + 1]
                val gap = (nxt.timeMs - cur.timeMs).coerceIn(0L, MAX_STEP_MS)
                delay(gap / speedMult)
                currentIndex++
                marker?.position = LatLng(pts[currentIndex].lat, pts[currentIndex].lng)
            }
            if (currentIndex >= pts.lastIndex) playing = false
        }

        DisposableEffect(Unit) {
            onDispose {
                // Leave the shared map clean for the next screen that reuses it.
                runCatching { gm?.clear() }
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
                val liveSpeed = pts.getOrNull(currentIndex)?.speedKmh
                ReplayControls(
                    title = ride.destinationLabel ?: "Ride",
                    dateText = formatRideDate(ride.startMs),
                    distanceText = DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC),
                    durationText = formatDuration(ride.durationSeconds),
                    avgSpeed = ride.avgSpeedKmh.toInt(),
                    maxSpeed = ride.maxSpeedKmh.toInt(),
                    liveSpeed = liveSpeed?.toInt(),
                    progress = if (pts.size > 1) currentIndex.toFloat() / pts.lastIndex else 0f,
                    playing = playing,
                    speedMult = speedMult,
                    onPlayPause = {
                        if (currentIndex >= pts.lastIndex) currentIndex = 0
                        playing = !playing
                    },
                    onReplay = {
                        playing = false
                        currentIndex = 0
                        marker?.position = LatLng(pts.first().lat, pts.first().lng)
                    },
                    onCycleSpeed = { speedMult = when (speedMult) { 1 -> 2; 2 -> 4; else -> 1 } },
                    modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
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
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onCycleSpeed: () -> Unit,
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
        Text(
            title,
            color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(dateText, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp)

        Spacer(Modifier.size(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("DISTANCE", distanceText)
            Stat("TIME", durationText)
            Stat("AVG", "$avgSpeed km/h")
            Stat("MAX", "$maxSpeed km/h")
        }

        Spacer(Modifier.size(12.dp))
        // Progress bar.
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Ktm.BorderSoft),
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Ktm.Orange),
            )
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

/** Clear the map and draw the speed-coloured track + start/end markers + frame it. */
private fun drawTrack(map: GoogleMap, pts: List<RidePoint>) {
    map.clear()
    if (pts.isEmpty()) return
    // Split into consecutive same-band runs so each is one coloured polyline.
    var runStart = 0
    var runBand = RideMetrics.band(pts[0].speedKmh)
    val bounds = LatLngBounds.builder()
    pts.forEach { bounds.include(LatLng(it.lat, it.lng)) }
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
