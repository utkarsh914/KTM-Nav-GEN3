package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ride.RideSessionState
import com.navigator.app.ride.formatElapsed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The visible buttons on the guidance notification (SY1). */
enum class LiveAction { OPEN, MUTE_BEEPS, UNMUTE_BEEPS, END_TRIP }

/** Everything the guidance notification is built from, so the content is a pure function of it. */
data class LiveInput(
    val nav: NormalizedNavigationState,
    val nowMs: Long,
    val destination: String? = null,
    val recording: RideSessionState = RideSessionState(),
    /** True / false once a bike is paired; null with no bike, so the line says nothing about the dash. */
    val dashConnected: Boolean? = null,
    /** Km/h while the overspeed alert is on, else null. */
    val overspeedKmh: Int? = null,
    val beepsOn: Boolean = true,
    /** Metres from the start of the route to each intermediate stop. */
    val stopMarks: List<Int> = emptyList(),
    /** Route length in metres, when known from the route itself. */
    val routeTotalM: Int = 0,
)

/** What the guidance notification says. [turn] is the dash glyph for the large icon. */
data class LiveUpdateContent(
    val title: String,
    val text: String,
    /** The "● Rec 12:30 · Dash connected" line, or null when there is nothing to add. */
    val subText: String?,
    /** Status-bar chip text for a Live Update (kept very short), or null. */
    val chip: String?,
    val etaMs: Long?,
    val overspeed: Boolean,
    val turn: TurnIcon?,
    val actions: List<LiveAction>,
)

object LiveUpdateBuilder {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    fun build(input: LiveInput, res: Resources, zone: ZoneId = ZoneId.systemDefault()): LiveUpdateContent {
        val state = input.nav
        val etaMs = state.remainingTimeSeconds?.takeIf { it >= 0 }?.let { input.nowMs + it * 1000L }
        val etaClock = etaMs?.let { CLOCK.format(Instant.ofEpochMilli(it).atZone(zone)) }
        val toTurn = state.distanceToManeuverMeters?.let { DistanceFormatter.format(it, state.units) }
        val remaining = state.remainingDistanceMeters?.let { DistanceFormatter.format(it, state.units) }
        val instruction = state.fullInstruction?.takeIf { it.isNotBlank() } ?: state.roadName?.takeIf { it.isNotBlank() }
        val enroute = state.sessionState == NavSessionState.ENROUTE
        val rerouting = state.sessionState == NavSessionState.REROUTING

        val turnLine = listOfNotNull(toTurn, instruction ?: if (enroute) res.getString(R.string.live_continue) else null).joinToString(SEP)
        val tripLine = listOfNotNull(
            input.destination?.takeIf { it.isNotBlank() }, etaClock?.let { res.getString(R.string.live_eta, it) }, remaining,
        ).joinToString(SEP)

        val overspeed = input.overspeedKmh != null && (enroute || rerouting)
        val title = when {
            overspeed -> res.getString(R.string.live_overspeed, input.overspeedKmh)
            rerouting -> res.getString(R.string.live_rerouting)
            !enroute -> res.getString(R.string.live_navigating)
            else -> turnLine
        }
        val text = when {
            overspeed -> turnLine.ifEmpty { tripLine }
            enroute -> tripLine.ifEmpty { res.getString(R.string.live_guidance_active) }
            rerouting -> tripLine.ifEmpty { res.getString(R.string.live_finding_route) }
            else -> res.getString(R.string.live_calculating)
        }

        val recording = input.recording.takeIf { it.active }?.let {
            if (it.paused) res.getString(R.string.live_rec_paused) else res.getString(R.string.live_rec, formatElapsed(it.elapsedMs(input.nowMs)))
        }
        val dash = input.dashConnected?.let { res.getString(if (it) R.string.live_dash_connected else R.string.live_dash_offline) }
        val sub = listOfNotNull(recording, dash).joinToString(SEP).ifEmpty { null }

        return LiveUpdateContent(
            title = title, text = text, subText = sub,
            chip = LiveChips.pick(
                overspeedKmh = input.overspeedKmh.takeIf { overspeed }, toTurn = toTurn.takeIf { enroute },
                // An hour or more no longer fits the chip, and a cut-off time would be wrong: leave it out.
                recording = input.recording.takeIf { it.active && !it.paused }?.let { formatElapsed(it.elapsedMs(input.nowMs)) }
                    ?.takeIf { it.length <= LiveChips.MAX },
                eta = etaClock.takeIf { enroute }, rerouting = rerouting, res = res,
            ),
            etaMs = etaMs, overspeed = overspeed,
            turn = if (enroute) KtmManeuverMapping.resolveIcon(state.maneuver, state.roundaboutRotation, state.drivingSide, state.fullInstruction) else null,
            actions = listOf(LiveAction.OPEN, if (input.beepsOn) LiveAction.MUTE_BEEPS else LiveAction.UNMUTE_BEEPS, LiveAction.END_TRIP),
        )
    }

    private const val SEP = " \u00b7 "
}

/** The single most important thing for the status-bar chip (SY3): overspeed, then the next turn, then recording time. */
object LiveChips {
    /** Longest status-bar chip text the system will show comfortably. */
    const val MAX = 7

    fun pick(
        overspeedKmh: Int?, toTurn: String?, recording: String?, eta: String?, rerouting: Boolean, res: Resources,
    ): String? = when {
        overspeedKmh != null -> res.getString(R.string.live_chip_speed, overspeedKmh)
        rerouting -> res.getString(R.string.live_chip_reroute)
        toTurn != null -> toTurn
        recording != null -> recording
        else -> eta
    }?.take(MAX)
}
