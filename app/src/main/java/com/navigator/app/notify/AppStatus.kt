package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.providers.NavLiveContentBuilder
import com.navigator.app.ride.formatElapsed
import com.navigator.app.trip.TripPhase
import com.navigator.app.trip.isLive
import java.time.ZoneId

/** What the one app notification is about right now, in priority order (D34). */
enum class StatusKind { TRIP, FAILED, RECORDING, MIRRORING, BIKE, READY }

enum class StatusAction { OPEN, MUTE_BEEPS, UNMUTE_BEEPS, END_TRIP, PAUSE, RESUME, STOP_RECORDING, TURN_ON, REPAIR, QUIT }

/** Everything the app notification is built from, so its content is a pure function of it. */
data class AppStatusInput(
    val phase: TripPhase,
    /** The trip's figures; also carries the recording, overspeed, beeps and route marks. */
    val trip: LiveInput,
    /** The bike link's state; null when the link service is not running. */
    val connection: ConnectionNotice? = null,
    val bikeName: String? = null,
    /** Mirror mode with Google Maps guiding (the engine in use is mirror and its session is running). */
    val mirrorActive: Boolean = false,
    val mirrorToTurn: String? = null,
    /** Largest remaining distance seen this trip, for the progress bar when the route length is unknown. */
    val trackedTotalM: Int = 0,
    val liveUpdates: Boolean = true,
    /** A trip, a recording or a mirrored trip is running: Quit asks first, so its button opens the app. */
    val quitNeedsConfirm: Boolean = false,
)

data class AppStatusContent(
    val kind: StatusKind,
    val title: String,
    val text: String?,
    val subText: String? = null,
    val actions: List<StatusAction>,
    /** Asks Android 16 for the promoted slot (a Live Update). */
    val promoted: Boolean = false,
    val chip: String? = null,
    val turn: TurnIcon? = null,
    val overspeedKmh: Int? = null,
    /** When a running recording's chronometer reads zero; null when none shows. */
    val chronometerBaseMs: Long? = null,
    /** (done, total) metres of the trip, when it can be drawn. */
    val progress: Pair<Int, Int>? = null,
    val stopPoints: List<Int> = emptyList(),
    val quitNeedsConfirm: Boolean = false,
)

object AppStatusBuilder {
    fun build(input: AppStatusInput, res: Resources, zone: ZoneId = ZoneId.systemDefault()): AppStatusContent {
        val session = input.trip.recording
        val base = when {
            input.phase.isLive -> trip(input, res, zone)
            input.phase == TripPhase.FAILED -> failed(input, res)
            // A mirrored trip records itself: the mirror line then carries the recording.
            session.active && !input.mirrorActive -> recording(input, res)
            input.mirrorActive -> mirroring(input, res)
            input.connection != null -> bike(input, res)
            else -> ready(res)
        }
        return base.copy(quitNeedsConfirm = input.quitNeedsConfirm)
    }

    private fun trip(input: AppStatusInput, res: Resources, zone: ZoneId): AppStatusContent {
        val live = LiveUpdateBuilder.build(input.trip.copy(updating = input.phase == TripPhase.UPDATING), res, zone)
        val state = input.trip.nav
        // The marks are measured once, at the start: after a reroute that adds distance they no longer fit.
        val marksFit = input.trip.routeTotalM > 0 && (state.remainingDistanceMeters ?: 0) <= input.trip.routeTotalM
        val total = if (marksFit) input.trip.routeTotalM else input.trackedTotalM
        val progress = NavLiveContentBuilder.progress(total, state.remainingDistanceMeters)
        return AppStatusContent(
            kind = StatusKind.TRIP, title = live.title, text = live.text, subText = live.subText,
            actions = live.actions.map {
                when (it) {
                    LiveAction.OPEN -> StatusAction.OPEN
                    LiveAction.MUTE_BEEPS -> StatusAction.MUTE_BEEPS
                    LiveAction.UNMUTE_BEEPS -> StatusAction.UNMUTE_BEEPS
                    LiveAction.END_TRIP -> StatusAction.END_TRIP
                }
            },
            promoted = input.liveUpdates, chip = live.chip, turn = live.turn,
            overspeedKmh = input.trip.overspeedKmh.takeIf { live.overspeed },
            progress = progress,
            stopPoints = if (marksFit && progress != null) input.trip.stopMarks.filter { it in 1 until progress.second } else emptyList(),
        )
    }

    private fun failed(input: AppStatusInput, res: Resources) = AppStatusContent(
        kind = StatusKind.FAILED, title = res.getString(R.string.start_failed_title),
        text = input.trip.destination?.takeIf { it.isNotBlank() }, actions = listOf(StatusAction.OPEN),
    )

    private fun recording(input: AppStatusInput, res: Resources): AppStatusContent {
        val session = input.trip.recording
        val now = input.trip.nowMs
        val content = RecordingNotice.build(session, now, RecordingMode.ALONE, input.trip.dashConnected, res)
        return AppStatusContent(
            kind = StatusKind.RECORDING, title = content.title, text = content.text,
            actions = listOf(if (content.paused) StatusAction.RESUME else StatusAction.PAUSE, StatusAction.STOP_RECORDING),
            promoted = input.liveUpdates,
            chronometerBaseMs = (now - session.elapsedMs(now)).takeIf { !content.paused },
        )
    }

    private fun mirroring(input: AppStatusInput, res: Resources): AppStatusContent {
        val session = input.trip.recording
        val now = input.trip.nowMs
        val rec = session.takeIf { it.active }?.let {
            if (it.paused) res.getString(R.string.live_rec_paused) else res.getString(R.string.live_rec, formatElapsed(it.elapsedMs(now)))
        }
        val name = input.bikeName?.takeIf { it.isNotBlank() }
        return AppStatusContent(
            kind = StatusKind.MIRRORING,
            title = if (name == null) res.getString(R.string.status_mirroring) else res.getString(R.string.status_mirroring_named, name),
            text = listOfNotNull(input.mirrorToTurn, rec).joinToString(" \u00b7 ").ifEmpty { null },
            actions = listOf(StatusAction.QUIT),
        )
    }

    private fun bike(input: AppStatusInput, res: Resources): AppStatusContent {
        val content = ConnectionNoticeContent.of(input.connection ?: ConnectionNotice.CONNECTING, res, input.bikeName)
        return AppStatusContent(
            kind = StatusKind.BIKE, title = content.title, text = content.text,
            actions = content.actions.map {
                when (it) {
                    ConnectionAction.TURN_ON -> StatusAction.TURN_ON
                    ConnectionAction.REPAIR -> StatusAction.REPAIR
                    ConnectionAction.QUIT -> StatusAction.QUIT
                }
            },
        )
    }

    private fun ready(res: Resources) = AppStatusContent(
        kind = StatusKind.READY, title = res.getString(R.string.status_ready_title), text = res.getString(R.string.status_ready_text),
        actions = listOf(StatusAction.QUIT),
    )
}
