package com.navigator.app.ui.shell

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideEvents
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideSession
import com.navigator.app.ui.components.ActivityStrip
import com.navigator.app.ui.components.ActivityStripState
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.screens.formatElapsed
import kotlinx.coroutines.delay

/**
 * The activity strip for a ride the rider started with Record. It shows on every tab above the bar;
 * a running trip and mirroring get their own strips in later batches. Stop asks first, and the
 * outcome (saved or too short) comes back as a snackbar.
 */
@Composable
fun RecordingStrip(feedback: Feedback, onOpenActivity: () -> Unit, onOpenRides: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val session by RideSession.state.collectAsState()
    val finish by RideEvents.lastFinish.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmStop by remember { mutableStateOf(false) }
    var awaitingResult by remember { mutableStateOf(false) }

    LaunchedEffect(session.active, session.paused) {
        while (session.active && !session.paused) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
        nowMs = System.currentTimeMillis()
    }
    LaunchedEffect(session.active) { if (session.active) awaitingResult = false }
    LaunchedEffect(finish) {
        val f = finish ?: return@LaunchedEffect
        if (!awaitingResult) return@LaunchedEffect
        awaitingResult = false
        when (f) {
            is RideFinish.Saved -> feedback.snackbar(
                resources.getString(
                    R.string.ride_saved,
                    DistanceFormatter.format(f.ride.distanceMeters, DistanceUnits.METRIC),
                    formatElapsed(f.ride.durationSeconds * 1000L),
                ),
                actionLabel = resources.getString(R.string.ride_saved_view),
                onAction = onOpenRides,
            )
            RideFinish.Discarded -> feedback.snackbar(resources.getString(R.string.ride_too_short))
        }
    }

    if (session.active && session.manual) {
        val elapsed = formatElapsed(session.elapsedMs(nowMs))
        val distance = DistanceFormatter.format(session.distanceMeters, DistanceUnits.METRIC)
        ActivityStrip(
            state = ActivityStripState.Recording(
                title = if (session.paused) stringResource(R.string.recording_strip_paused_title)
                else stringResource(R.string.recording_strip_title, elapsed),
                detail = if (session.paused) stringResource(R.string.recording_strip_paused_detail, elapsed, distance)
                else stringResource(R.string.recording_strip_detail, distance),
                paused = session.paused,
            ),
            onOpen = onOpenActivity,
            modifier = Modifier.padding(8.dp),
            onTogglePause = { togglePause(context, session.paused) },
            onStop = { confirmStop = true },
        )
    }

    if (confirmStop) {
        KtmDialog(
            title = stringResource(R.string.stop_recording_title),
            message = stringResource(R.string.stop_recording_message),
            onDismiss = { confirmStop = false },
            confirm = DialogAction(stringResource(R.string.stop_recording_confirm), {
                confirmStop = false
                awaitingResult = true
                RideEvents.clear()
                RideRecordingService.stop(context)
            }, ButtonStyle.Danger),
            dismiss = DialogAction(stringResource(R.string.stop_recording_keep), { confirmStop = false }),
        )
    }
}

private fun togglePause(context: Context, paused: Boolean) {
    if (paused) RideRecordingService.resume(context) else RideRecordingService.pause(context)
}
