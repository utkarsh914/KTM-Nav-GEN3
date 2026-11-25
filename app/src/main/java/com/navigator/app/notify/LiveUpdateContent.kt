package com.navigator.app.notify

import android.content.res.Resources
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ride.RideSessionState
import com.navigator.app.ride.formatElapsed
import java.time.ZoneId

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
    /** The alert's limit in km/h, to say what the speed is over; null leaves it out. */
    val overspeedLimitKmh: Int? = null,
    val beepsOn: Boolean = true,
    /** Metres from the start of the route to each intermediate stop. */
    val stopMarks: List<Int> = emptyList(),
    /** Route length in metres, when known from the route itself. */
    val routeTotalM: Int = 0,
    /** Between guided legs (a stop was added or removed and the route is being re-planned): the last figures stay. */
    val updating: Boolean = false,
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
    fun build(input: LiveInput, res: Resources, zone: ZoneId = ZoneId.systemDefault()): LiveUpdateContent {
        val state = input.nav
        val etaMs = state.remainingTimeSeconds?.takeIf { it >= 0 }?.let { input.nowMs + it * 1000L }
        val etaClock = etaMs?.let { ClockFormat.local(it, zone) }
        val toTurn = state.distanceToManeuverMeters?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) }
        val remaining = state.remainingDistanceMeters?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) }
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
            input.updating -> res.getString(R.string.live_updating)
            !enroute -> res.getString(R.string.live_navigating)
            else -> turnLine
        }
        val text = when {
            overspeed -> listOfNotNull(
                input.overspeedLimitKmh?.let { res.getString(R.string.live_alert_at, it) }, turnLine.ifEmpty { tripLine }.ifEmpty { null },
            ).joinToString(SEP)
            enroute -> tripLine.ifEmpty { res.getString(R.string.live_guidance_active) }
            rerouting -> tripLine.ifEmpty { res.getString(R.string.live_finding_route) }
            input.updating -> tripLine.ifEmpty { res.getString(R.string.live_finding_route) }
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
        // The unit goes before the number does: "125" says more than "125 km/".
        overspeedKmh != null -> fit(res.getString(R.string.live_chip_speed, overspeedKmh)) ?: overspeedKmh.toString()
        rerouting -> res.getString(R.string.live_chip_reroute)
        // A decimal is the first thing to go: "123 km" is still true, "123.4 k" is not.
        toTurn != null -> fit(toTurn) ?: fit(toTurn.replace(DECIMALS, ""))
        recording != null -> recording
        else -> eta?.let { fit(it) ?: fit(it.replace(MERIDIEM, "")) }
    }?.takeIf { it.length <= MAX }

    private fun fit(text: String): String? = text.takeIf { it.length <= MAX }

    private val DECIMALS = Regex("""\.\d+""")
    private val MERIDIEM = Regex("""\s*[ap]\.?m\.?$""", RegexOption.IGNORE_CASE)
}
