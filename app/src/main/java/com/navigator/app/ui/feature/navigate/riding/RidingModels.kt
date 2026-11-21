package com.navigator.app.ui.feature.navigate.riding

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ui.shell.RidingChipMessage
import com.navigator.app.ui.theme.SunTimes
import com.navigator.app.ui.theme.isNightTime
import java.time.ZoneId

/** What sits under the instruction, inside the same card: lanes near a turn, else the turn after it. */
sealed interface ManeuverFooter {
    data object None : ManeuverFooter
    data class Then(val icon: TurnIcon) : ManeuverFooter
    data class Lanes(val lanes: List<LaneInfo>) : ManeuverFooter
}

sealed interface ManeuverUi {
    /** The card's place before the first instruction arrives, so the layout does not jump (NV5). */
    data object Starting : ManeuverUi

    /**
     * [instruction] is the sentence without the road, and [road] starts its own bold line (what the rider
     * matches against signs). [exit] is the exit number or name, formatted by the card. While [rerouting]
     * the last instruction stays, dimmed, under a spinner.
     */
    data class Active(
        val icon: TurnIcon,
        val distance: String?,
        val instruction: String,
        val road: String?,
        val exit: String?,
        val footer: ManeuverFooter,
        val rerouting: Boolean,
    ) : ManeuverUi
}

object ManeuverModel {
    /** Lane arrows replace the "Then" band once the turn is this close. */
    const val LANES_WITHIN_M = 400

    fun from(nav: NormalizedNavigationState): ManeuverUi {
        if (nav.sessionState == NavSessionState.IDLE || nav.sessionState == NavSessionState.STOPPED) return ManeuverUi.Starting
        val rerouting = nav.sessionState == NavSessionState.REROUTING
        val (instruction, road) = splitRoad(nav.fullInstruction, nav.roadName)
        val exit = nav.exitNumber?.takeIf { it.isNotBlank() } ?: nav.roundaboutExit?.toString()
        val near = nav.distanceToManeuverMeters?.let { it <= LANES_WITHIN_M } ?: false
        val next = nav.nextManeuver
        val footer = when {
            rerouting -> ManeuverFooter.None
            nav.lanes.isNotEmpty() && near -> ManeuverFooter.Lanes(nav.lanes)
            next != null && next != NormalizedManeuver.UNKNOWN ->
                ManeuverFooter.Then(KtmManeuverMapping.toTurnIcon(next, drivingSide = nav.drivingSide))
            else -> ManeuverFooter.None
        }
        return ManeuverUi.Active(
            icon = KtmManeuverMapping.resolveIcon(nav.maneuver, nav.roundaboutRotation, nav.drivingSide, nav.fullInstruction),
            distance = nav.distanceToManeuverMeters?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) },
            instruction = instruction,
            road = road,
            exit = exit,
            footer = footer,
            rerouting = rerouting,
        )
    }

    /** "Turn right onto Foo St" + "Foo St" -> ("Turn right onto", "Foo St"). The road is never broken across lines. */
    fun splitRoad(full: String?, road: String?): Pair<String, String?> {
        val text = full?.takeIf { it.isNotBlank() }
        val name = road?.takeIf { it.isNotBlank() }
        if (text == null) return "" to name
        if (name != null) {
            val at = text.lastIndexOf(name, ignoreCase = true)
            if (at >= 0) {
                val before = text.substring(0, at).trim().trimEnd(',', ':').trim()
                val after = text.substring(at + name.length).trim().trimStart(',', ':').trim()
                return listOf(before, after).filter { it.isNotEmpty() }.joinToString(" ") to name
            }
        }
        return text to name
    }
}

/** Remaining time as the bar shows it: minutes, hours and minutes, or whole hours from ten hours on. */
data class TimeLeft(val hours: Int, val minutes: Int) {
    enum class Kind { Minutes, HoursMinutes, Hours }

    val kind: Kind get() = when {
        hours == 0 -> Kind.Minutes
        hours < 10 -> Kind.HoursMinutes
        else -> Kind.Hours
    }

    companion object {
        fun of(seconds: Int): TimeLeft {
            val total = (seconds.coerceAtLeast(0) + 59) / 60
            return TimeLeft(total / 60, total % 60)
        }
    }
}

data class TripBarUi(
    /** [NO_SPEED] until there is a fix. */
    val speed: String,
    val speedKnown: Boolean,
    val overspeed: Boolean,
    /** The limit that was crossed: shown in the red block, only when it matters. */
    val alert: Int?,
    val timeLeft: TimeLeft?,
    val distance: String?,
    val arrival: String?,
    /** 0 at the start, 1 at the destination. */
    val progress: Float,
    /** Shown instead of the time while guidance starts (NV5). */
    val destination: String?,
    val starting: Boolean,
)

object TripBarModel {
    const val NO_SPEED = "–"

    fun from(
        nav: NormalizedNavigationState,
        speedKmh: Float?,
        limitKmh: Int?,
        initialRemainingMeters: Int?,
        nowMs: Long,
        destination: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TripBarUi {
        // The shown (whole) speed against the limit: 90.4 with a limit of 90 is "90", not red.
        val shown = speedKmh?.toInt()?.coerceAtLeast(0)
        val over = shown != null && limitKmh != null && shown > limitKmh
        val seconds = nav.remainingTimeSeconds
        return TripBarUi(
            speed = shown?.toString() ?: NO_SPEED,
            speedKnown = shown != null,
            overspeed = over,
            alert = if (over) limitKmh else null,
            timeLeft = seconds?.let(TimeLeft::of),
            distance = nav.remainingDistanceMeters?.let { DistanceFormatter.format(it, DistanceUnits.METRIC) },
            arrival = seconds?.let { EtaFormatter.format(it, nowMs, zone) },
            progress = progress(nav.remainingDistanceMeters, initialRemainingMeters),
            destination = destination,
            starting = seconds == null,
        )
    }

    fun progress(remaining: Int?, initial: Int?): Float {
        if (remaining == null || initial == null || initial <= 0) return 0f
        return (1f - remaining.toFloat() / initial).coerceIn(0f, 1f)
    }
}

/** Where the map button stands: Overview (see the whole route), or Recenter after the rider moved the map. */
data class MapButtonState(val panned: Boolean = false, val overview: Boolean = false, val pannedAtMs: Long = 0L) {
    val recenter: Boolean get() = panned || overview
}

enum class MapButtonEffect { None, Follow, ShowOverview }

sealed interface MapButtonEvent {
    data class Pan(val nowMs: Long) : MapButtonEvent
    data object Tap : MapButtonEvent
    data class Speed(val kmh: Float, val nowMs: Long) : MapButtonEvent
    data object Reset : MapButtonEvent
}

object MapButtonMachine {
    const val FOLLOW_ABOVE_KMH = 15f

    /** A panned map goes back to following only once the bike has been moving for this long since the pan. */
    const val FOLLOW_AFTER_MS = 10_000L

    fun reduce(state: MapButtonState, event: MapButtonEvent): Pair<MapButtonState, MapButtonEffect> = when (event) {
        is MapButtonEvent.Pan -> MapButtonState(panned = true, overview = false, pannedAtMs = event.nowMs) to MapButtonEffect.None
        MapButtonEvent.Tap ->
            if (state.recenter) MapButtonState() to MapButtonEffect.Follow
            else MapButtonState(overview = true) to MapButtonEffect.ShowOverview
        is MapButtonEvent.Speed ->
            // Overview is a deliberate look at the route: only a hand-panned map snaps back on its own.
            if (state.panned && event.kmh > FOLLOW_ABOVE_KMH && event.nowMs - state.pannedAtMs >= FOLLOW_AFTER_MS)
                MapButtonState() to MapButtonEffect.Follow
            else state to MapButtonEffect.None
        MapButtonEvent.Reset -> MapButtonState() to MapButtonEffect.None
    }
}

/** What the riding chip slot shows. A timed message from an event beats the persistent offline chip. */
sealed interface ChipSource {
    data class Timed(val message: RidingChipMessage) : ChipSource

    /** Offline: first guidance continues, then after the rerouting wait, reroutes when back online. */
    data class Offline(val reroutePaused: Boolean) : ChipSource
}

object RidingChips {
    fun pick(timed: RidingChipMessage?, offline: Boolean, reroutePaused: Boolean): ChipSource? = when {
        timed != null -> ChipSource.Timed(timed)
        offline -> ChipSource.Offline(reroutePaused)
        else -> null
    }
}

/** Day or night for the riding surfaces: the sun at the last known place, or the fixed 06:00-18:00 rule without one. */
object DayNightRule {
    fun isNight(nowMs: Long, here: Pair<Double, Double>?, hourOfDay: Int = java.time.LocalTime.now().hour): Boolean =
        if (here != null) SunTimes.isNight(nowMs, here.first, here.second) else isNightTime(hourOfDay)
}
