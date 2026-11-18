package com.navigator.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideSessionState
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.rememberHaptics

/** "7:05" / "1:02:09" for a recorded duration. */
internal fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Idle state on the browse map: start recording a ride without a destination. */
@Composable
internal fun RecordPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(role = Role.Button) { haptics.confirm(); onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Ktm.Danger))
        Spacer(Modifier.width(8.dp))
        Text(
            "RECORD", color = Ktm.White, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp,
        )
    }
}

/** Live recording status with pause/resume and stop. Shown while any ride is being recorded. */
@Composable
internal fun RecordingChip(
    state: RideSessionState,
    nowMs: Long,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, if (state.paused) Ktm.Orange else Ktm.Danger, RoundedCornerShape(Ktm.RadiusButton))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (state.paused) Ktm.Orange else Ktm.Danger))
        Spacer(Modifier.width(8.dp))
        Text(
            formatElapsed(state.elapsedMs(nowMs)), color = Ktm.White,
            fontFamily = JetBrainsMono, fontSize = 14.sp, maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (state.paused) "PAUSED" else DistanceFormatter.format(state.distanceMeters, DistanceUnits.METRIC),
            color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp, maxLines = 1,
        )
        ChipButton(
            icon = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            description = if (state.paused) "Resume recording" else "Pause recording",
            onClick = { haptics.tap(); onTogglePause() },
        )
        ChipButton(Icons.Filled.Stop, "Stop recording", onClick = { haptics.tap(); onStop() })
    }
}

@Composable
private fun ChipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Ktm.White, modifier = Modifier.size(22.dp)) }
}

/** Confirm before ending a recording (it is finalised, or discarded when too short). */
@Composable
internal fun StopRecordingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextSecondary,
        title = { Text("Stop recording?", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = { Text("The ride is saved to your history, unless it is shorter than the keep limits in Settings.", fontFamily = Barlow) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("STOP", color = Ktm.Danger, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("KEEP RECORDING", color = Ktm.TextPrimary, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
    )
}

/**
 * Self-contained record control for the browse map: owns the live session subscription, the
 * 1 s ticker, the stop dialog and the saved/discarded toast, so none of that recomposes the
 * big map-home screen.
 */
@Composable
internal fun RecordControlsHost(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session by com.navigator.app.ride.RideSession.state.collectAsState()
    val finish by com.navigator.app.ride.RideEvents.lastFinish.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showStop by remember { mutableStateOf(false) }
    // Set when the rider stops here, so the saved/discarded outcome is toasted once.
    var awaitingResult by remember { mutableStateOf(false) }

    LaunchedEffect(session.active, session.paused) {
        while (session.active && !session.paused) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
        nowMs = System.currentTimeMillis()
    }
    // A new recording supersedes any pending "stopped" expectation (e.g. stopped from the notification).
    LaunchedEffect(session.active) { if (session.active) awaitingResult = false }
    LaunchedEffect(finish) {
        if (!awaitingResult) return@LaunchedEffect
        val f = finish ?: return@LaunchedEffect
        awaitingResult = false
        val msg = when (f) {
            is com.navigator.app.ride.RideFinish.Saved ->
                "Ride saved \u00b7 " + DistanceFormatter.format(f.ride.distanceMeters, DistanceUnits.METRIC) +
                    " \u00b7 " + formatElapsed(f.ride.durationSeconds * 1000L)
            com.navigator.app.ride.RideFinish.Discarded -> "Ride too short to keep"
        }
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    if (session.active) {
        RecordingChip(
            state = session,
            nowMs = nowMs,
            onTogglePause = {
                if (session.paused) com.navigator.app.ride.RideRecordingService.resume(context)
                else com.navigator.app.ride.RideRecordingService.pause(context)
            },
            onStop = { showStop = true },
            modifier = modifier,
        )
    } else {
        RecordPill(
            modifier = modifier,
            onClick = {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                when {
                    !granted -> android.widget.Toast.makeText(
                        context, "Allow location to record rides", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    !com.navigator.app.ride.RideRecordingService.startManual(context) ->
                        android.widget.Toast.makeText(
                            context, "Couldn't start recording", android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
            },
        )
    }

    if (showStop) {
        StopRecordingDialog(
            onConfirm = {
                showStop = false
                awaitingResult = true
                com.navigator.app.ride.RideEvents.clear()
                com.navigator.app.ride.RideRecordingService.stop(context)
            },
            onDismiss = { showStop = false },
        )
    }
}
