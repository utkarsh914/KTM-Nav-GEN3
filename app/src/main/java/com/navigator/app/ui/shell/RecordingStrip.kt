package com.navigator.app.ui.shell

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideEvents
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.ride.RideSession
import com.navigator.app.ride.formatElapsed
import com.navigator.app.ui.components.ActivityStrip
import com.navigator.app.ui.components.ActivityStripState
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.theme.KtmSpacing
import kotlinx.coroutines.delay

/**
 * What the recording strip and its follow-up share, held above the strip: the strip leaves composition while the riding
 * screen is up (immersive), and a ride that ends then must still be announced, and a Stop asked for must not be forgotten.
 */
@Stable
class RecordingUi {
    /** Set when a ride the strip answers for starts or is stopped from it: the next finish is announced once. */
    var awaitingResult by mutableStateOf(false)
    var confirmStop by mutableStateOf(false)
}

/**
 * The outcome of the rides the strip answers for (the rider's own and, in mirror mode, the auto-recorded ones), announced
 * whenever they end and however they were stopped, and the Stop confirm. Always composed, strip or not.
 */
@Composable
fun RecordingOutcome(ui: RecordingUi, feedback: Feedback, onOpenRides: () -> Unit, mirrorEngine: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val session by RideSession.state.collectAsState()
    val finish by RideEvents.lastFinish.collectAsState()
    LaunchedEffect(session.active) { if (session.active) ui.awaitingResult = session.manual || mirrorEngine }
    LaunchedEffect(finish) {
        val f = finish ?: return@LaunchedEffect
        if (!ui.awaitingResult) return@LaunchedEffect
        ui.awaitingResult = false
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
    if (ui.confirmStop) {
        KtmDialog(
            title = stringResource(R.string.stop_recording_title),
            message = stringResource(R.string.stop_recording_message),
            onDismiss = { ui.confirmStop = false },
            confirm = DialogAction(stringResource(R.string.stop_recording_confirm), {
                ui.confirmStop = false
                ui.awaitingResult = true
                RideEvents.clear()
                RideRecordingService.stop(context)
            }, ButtonStyle.Danger),
            dismiss = DialogAction(stringResource(R.string.stop_recording_keep), { ui.confirmStop = false }),
        )
    }
}

/** The activity strip for a ride the rider started with Record, and for the rides mirror mode records by itself ([mirrorEngine]). It shows on every tab above the bar. */
@Composable
fun RecordingStrip(ui: RecordingUi, onOpenActivity: () -> Unit, mirrorEngine: Boolean = false) {
    val context = LocalContext.current
    val session by RideSession.state.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session.active, session.paused) {
        while (session.active && !session.paused) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
        nowMs = System.currentTimeMillis()
    }

    if (session.active && (session.manual || mirrorEngine)) {
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
            modifier = Modifier.padding(KtmSpacing.sm),
            onTogglePause = { togglePause(context, session.paused) },
            onStop = { ui.confirmStop = true },
        )
    }
}

private fun togglePause(context: Context, paused: Boolean) {
    if (paused) RideRecordingService.resume(context) else RideRecordingService.pause(context)
}
