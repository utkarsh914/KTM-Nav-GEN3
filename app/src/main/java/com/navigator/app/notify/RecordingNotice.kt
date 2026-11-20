package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.R
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideSessionState
import com.navigator.app.ride.formatElapsed

/** Where a recording's notification sits (SY2, SY4). */
enum class RecordingMode {
    /** Manual recording with no trip: the promoted slot, with Pause / Stop visible. */
    ALONE,

    /** An in-app trip is guiding: its Live Update carries the recording, so this is a silent row the system requires. */
    WITH_TRIP,

    /** Mirror mode: Google Maps has its own card, so this stays a silent one-liner. */
    MIRRORING,
}

data class RecordingContent(val title: String, val text: String, val promoted: Boolean, val showActions: Boolean, val paused: Boolean)

object RecordingNotice {
    /** What decides the mode: a live in-app trip wins, then a mirrored trip, else the recording is on its own. */
    fun mode(tripGuiding: Boolean, source: String?): RecordingMode = when {
        tripGuiding -> RecordingMode.WITH_TRIP
        source == com.navigator.app.ride.RideSource.MIRROR -> RecordingMode.MIRRORING
        else -> RecordingMode.ALONE
    }

    fun build(session: RideSessionState, nowMs: Long, mode: RecordingMode, dashConnected: Boolean?, res: Resources): RecordingContent {
        val distance = DistanceFormatter.format(session.distanceMeters, DistanceUnits.METRIC)
        val dash = dashConnected?.let { res.getString(if (it) R.string.live_dash_connected else R.string.live_dash_offline) }
        val paused = session.paused
        val promoted = mode == RecordingMode.ALONE
        val text = when {
            paused -> res.getString(R.string.rec_paused_hint)
            promoted -> listOfNotNull(distance, dash).joinToString(" \u00b7 ")
            else -> listOf(formatElapsed(session.elapsedMs(nowMs)), distance).joinToString(" \u00b7 ")
        }
        return RecordingContent(
            title = res.getString(if (paused) R.string.notice_rec_paused else R.string.notice_rec_title),
            text = text, promoted = promoted, showActions = promoted, paused = paused,
        )
    }
}
