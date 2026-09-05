package com.navigator.app.ui.screens

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ui.components.TurnIconGlyph
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

// Active-navigation guidance UI extracted from NavigationHomeScreen. Same
// package, so these `internal` helpers are called directly by the main screen.

/** Google-style dark teal-green used for the custom maneuver header. */
private val NAV_GREEN = Color(0xFF0E6E5B)
private val NAV_GREEN_DIM = Color(0xFF0A5648)

/** Show lane guidance only within this distance of the maneuver (like Google). */
internal const val LANE_HINT_DISTANCE_M = 400

/**
 * Custom top maneuver header replacing the SDK's green banner. Renders below the
 * status bar (caller applies systemBarsPadding). Shows the current turn icon,
 * distance-to-maneuver, the instruction/road, an optional exit-number chip, and a
 * compact "Then <icon>" hint for the following step. During REROUTING it shows a
 * "Rerouting…" label with the retained maneuver dimmed. Corner radius matches the
 * other nav controls (RadiusCard).
 *
 * @param hideNextHint suppress the "Then" chip (used while the lane row is shown,
 *   so the top cluster can't grow tall enough to crowd other elements).
 */
@Composable
internal fun NavGuidanceHeader(
    nav: NormalizedNavigationState,
    hideNextHint: Boolean = false,
) {
    val rerouting = nav.sessionState == NavSessionState.REROUTING
    val contentAlpha = if (rerouting) 0.45f else 1f
    val icon = KtmManeuverMapping.toTurnIcon(nav.maneuver, nav.roundaboutRotation, nav.drivingSide)
    val distance = nav.distanceToManeuverMeters?.let { DistanceFormatter.format(it, nav.units) }
    // Prefer the SDK's full instruction; fall back to the plain road name.
    val instruction = nav.fullInstruction?.takeIf { it.isNotBlank() } ?: nav.roadName
    val exitLabel = when {
        !nav.exitNumber.isNullOrBlank() -> "Exit ${nav.exitNumber}"
        nav.roundaboutExit != null -> "Exit ${nav.roundaboutExit}"
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Ktm.RadiusCard))
                .background(NAV_GREEN)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TurnIconRef(icon, size = 44.dp, color = Color.White.copy(alpha = contentAlpha))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (rerouting) {
                    Text(
                        "Rerouting…", color = Color.White, fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (distance != null) {
                            Text(
                                distance, color = Color.White, fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold, fontSize = 24.sp,
                            )
                        }
                        if (exitLabel != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                exitLabel,
                                color = NAV_GREEN,
                                fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (!instruction.isNullOrBlank()) {
                    Text(
                        instruction,
                        color = Color.White.copy(alpha = 0.92f * contentAlpha), fontFamily = Barlow,
                        fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val next = nav.nextManeuver
        if (!hideNextHint && !rerouting && next != null && next != NormalizedManeuver.UNKNOWN) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(bottomStart = Ktm.RadiusCard, bottomEnd = Ktm.RadiusCard))
                    .background(NAV_GREEN_DIM)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Then", color = Color.White.copy(alpha = 0.9f), fontFamily = Barlow,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(10.dp))
                TurnIconRef(KtmManeuverMapping.toTurnIcon(next), size = 26.dp, color = Color.White)
            }
        }
    }
}

/**
 * Lane guidance strip shown under the header near a maneuver. Recommended lanes
 * (those that lead to the upcoming turn) are drawn bright; the rest are dimmed.
 */
@Composable
internal fun LaneGuidance(lanes: List<LaneInfo>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lanes.forEach { lane ->
            val tint = if (lane.recommended) Ktm.White else Ktm.Dim
            // A lane may allow several directions; show each as a small arrow.
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                val shapes = lane.directions.ifEmpty { listOf(LaneShape.STRAIGHT) }
                shapes.forEach { shape ->
                    TurnIconGlyph(laneShapeIcon(shape), size = 22.dp, color = tint)
                }
            }
        }
    }
}

/** Map a [LaneShape] to the closest dash [TurnIcon] arrow glyph. */
private fun laneShapeIcon(shape: LaneShape): TurnIcon = when (shape) {
    LaneShape.STRAIGHT -> TurnIcon.GO_STRAIGHT
    LaneShape.SLIGHT_LEFT -> TurnIcon.LIGHT_LEFT
    LaneShape.LEFT -> TurnIcon.QUITE_LEFT
    LaneShape.SHARP_LEFT -> TurnIcon.HEAVY_LEFT
    LaneShape.UTURN_LEFT -> TurnIcon.UTURN_LEFT
    LaneShape.SLIGHT_RIGHT -> TurnIcon.LIGHT_RIGHT
    LaneShape.RIGHT -> TurnIcon.QUITE_RIGHT
    LaneShape.SHARP_RIGHT -> TurnIcon.HEAVY_RIGHT
    LaneShape.UTURN_RIGHT -> TurnIcon.UTURN_RIGHT
    LaneShape.UNKNOWN -> TurnIcon.GO_STRAIGHT
}

/**
 * Live GPS speed (km/h) while [active], from a self-contained GPS listener. Works
 * independently of the BLE overspeed service (which only runs when connected), so
 * the guidance speedometer is available during standalone navigation too. Returns
 * null until the first fix with a speed arrives.
 */
@Composable
internal fun rememberNavSpeedKmh(active: Boolean): Float? {
    val context = LocalContext.current
    var speed by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(active) {
        if (!active || !hasLocationPermission(context)) {
            speed = null
            return@DisposableEffect onDispose { }
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        // Full LocationListener impl (not a SAM lambda): pre-API-30 the extra
        // callbacks are abstract, so a lambda would AbstractMethodError at runtime.
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (loc.hasSpeed()) speed = loc.speed * 3.6f
            }
            @Deprecated("Deprecated in API 29 but still abstract on older devices")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { speed = null }
        }
        runCatching {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }
        onDispose {
            runCatching { lm?.removeUpdates(listener) }
            speed = null
        }
    }
    return speed
}

/** Small speedometer pill: current speed in km/h. */
@Composable
internal fun Speedometer(kmh: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            kmh.toInt().coerceAtLeast(0).toString(),
            color = Ktm.TextPrimary, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 28.sp,
        )
        Text(
            "km/h", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp,
        )
    }
}

/**
 * Custom bottom guidance bar replacing the SDK's ETA card. The END (X) button
 * sits inside the bar on the left; remaining time / distance / arrival are
 * left-aligned beside it. Corner radius matches the header and controls.
 */
@Composable
internal fun NavGuidanceBottomBar(
    nav: NormalizedNavigationState,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingTime = nav.remainingTimeSeconds
    val duration = remainingTime?.let { formatDuration(it) }
    val eta = remainingTime?.let { EtaFormatter.format(it, System.currentTimeMillis()) }
    val remDist = nav.remainingDistanceMeters?.let { DistanceFormatter.format(it, nav.units) }
    val sub = listOfNotNull(remDist, eta).joinToString("  ·  ")
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(OpenDashIcons.Close, "End navigation", onClick = onEnd)
        // Centered info: the END button on the left is balanced by an equal-width
        // spacer on the right so the text is centered within the whole bar.
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                duration ?: "On the way",
                color = Ktm.TextPrimary, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 27.sp,
            )
            if (sub.isNotBlank()) {
                Text(
                    sub, color = Ktm.TextSecondary, fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(Ktm.ControlHeight)) // balances the 52dp END button
    }
}
