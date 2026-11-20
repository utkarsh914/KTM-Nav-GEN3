package com.navigator.app.audio

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** What the adapter drives: the real [TurnBeeper], or a fake in tests. */
interface BeepSink {
    fun guidance(meters: Int?, icon: TurnIcon?)
    fun reset()
    fun configure(swapChannels: Boolean, volumePercent: Int)
    fun speed(kmh: Float?)
}

object TurnBeeperSink : BeepSink {
    override fun guidance(meters: Int?, icon: TurnIcon?) = TurnBeeper.onGuidanceMeters(meters, icon)
    override fun reset() = TurnBeeper.reset()
    override fun configure(swapChannels: Boolean, volumePercent: Int) {
        TurnBeeper.swapChannels = swapChannels
        TurnBeeper.volumePercent = volumePercent
    }
    override fun speed(kmh: Float?) { TurnBeeper.gpsSpeedKmh = kmh }
}

/** Which source may drive the beeper: one at a time, so a turn never beeps twice. */
object BeepSourcePolicy {
    /** The notification path (Google Maps mirrored) stays silent while the app's own guidance is running. */
    fun listenerMayBeep(inAppGuidanceActive: Boolean): Boolean = !inAppGuidanceActive
}

/**
 * Turn beeps for in-app trips (D6): feeds the metres to the next turn and its direction from the app's own
 * guidance into the beeper, as the notification path does for Google Maps. The beeper is reset whenever guidance
 * stops being on-route (end, reroute, arrival), when beeps are switched off, and on minimise or resume, so a stale
 * beep never outlives the turn it was for. The Navigation SDK's own voice guidance is separate and stays on.
 */
class TurnBeepAdapter(
    scope: CoroutineScope,
    nav: Flow<NormalizedNavigationState>,
    minimised: Flow<Boolean>,
    enabled: Flow<Boolean>,
    speedKmh: Flow<Float?>,
    private val config: () -> Pair<Boolean, Int>,
    private val sink: BeepSink,
) {
    /** Whether this adapter is the one currently driving the beeper: it never resets what the notification path started. */
    private var feeding = false

    private fun stop() {
        if (!feeding) return
        feeding = false
        sink.reset()
    }

    init {
        combine(nav, enabled.distinctUntilChanged()) { state, on -> state to on }.onEach { (state, on) ->
            val meters = state.distanceToManeuverMeters
            if (!on || state.sessionState != NavSessionState.ENROUTE || meters == null) {
                stop()
            } else {
                feeding = true
                val (swap, volume) = config()
                sink.configure(swap, volume)
                val icon = KtmManeuverMapping.resolveIcon(state.maneuver, state.roundaboutRotation, state.drivingSide, state.fullInstruction)
                sink.guidance(meters, icon)
            }
        }.launchIn(scope)
        minimised.distinctUntilChanged().onEach { stop() }.launchIn(scope)
        speedKmh.onEach { sink.speed(it) }.launchIn(scope)
    }
}
