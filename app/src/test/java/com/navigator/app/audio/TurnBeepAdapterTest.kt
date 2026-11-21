package com.navigator.app.audio

import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TurnBeepAdapterTest {
    private class Sink : BeepSink {
        val log = mutableListOf<String>()
        var swap = false
        var volume = 0
        var speed: Float? = null
        override fun guidance(meters: Int?, icon: TurnIcon?) { log += "g$meters" }
        override fun reset() { log += "reset" }
        override fun configure(swapChannels: Boolean, volumePercent: Int) { swap = swapChannels; volume = volumePercent }
        override fun speed(kmh: Float?) { speed = kmh }
    }

    private fun nav(session: NavSessionState = NavSessionState.ENROUTE, m: Int? = 300) =
        NormalizedNavigationState(session, maneuver = NormalizedManeuver.RIGHT, distanceToManeuverMeters = m)

    private class Env(scope: CoroutineScope) {
        val sink = Sink()
        val nav = MutableStateFlow(NormalizedNavigationState.IDLE)
        val minimised = MutableStateFlow(false)
        val enabled = MutableStateFlow(true)
        val speed = MutableStateFlow<Float?>(null)
        init { TurnBeepAdapter(scope, nav, minimised, enabled, speed, { true to 40 }, sink) }
    }

    @Test fun onRouteGuidanceFeedsTheDistanceAndSettings() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        e.sink.log.clear()
        e.nav.value = nav(m = 300)
        e.nav.value = nav(m = 250)
        assertEquals(listOf("g300", "g250"), e.sink.log)
        assertTrue(e.sink.swap); assertEquals(40, e.sink.volume)
    }

    @Test fun reroutingEndingAndArrivalSilenceIt() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        for (s in listOf(NavSessionState.REROUTING, NavSessionState.STOPPED, NavSessionState.ARRIVED, NavSessionState.IDLE)) {
            e.nav.value = nav(m = 300)
            e.sink.log.clear()
            e.nav.value = nav(s, 300)
            assertEquals("$s", listOf("reset"), e.sink.log)
        }
    }

    @Test fun noDistanceMeansNoBeep() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        e.nav.value = nav(m = 300)
        e.sink.log.clear()
        e.nav.value = nav(m = null)
        assertEquals(listOf("reset"), e.sink.log)
    }

    @Test fun switchingBeepsOffStopsThemAtOnceAndOnRestoresThem() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        e.nav.value = nav(m = 300)
        e.sink.log.clear()
        e.enabled.value = false
        assertEquals(listOf("reset"), e.sink.log)
        e.sink.log.clear()
        e.enabled.value = true
        assertEquals(listOf("g300"), e.sink.log)
    }

    @Test fun minimiseAndResumeBothReset() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        e.nav.value = nav(m = 300)
        e.sink.log.clear()
        e.minimised.value = true
        assertEquals("minimise silences what we were feeding", listOf("reset"), e.sink.log)
        e.minimised.value = false
        assertEquals("resume resets nothing more; the next frame re-arms it", listOf("reset"), e.sink.log)
        e.nav.value = nav(m = 280)
        assertEquals(listOf("reset", "g280"), e.sink.log)
    }

    @Test fun neverResetsABeeperItIsNotDriving() = runTest(UnconfinedTestDispatcher()) {
        // Mirror-mode trip: in-app guidance stays idle while the notification path beeps for Google Maps.
        val e = Env(backgroundScope)
        e.sink.log.clear()
        e.nav.value = nav(NavSessionState.IDLE, null)
        e.nav.value = nav(NavSessionState.IDLE, null)
        e.enabled.value = false
        e.enabled.value = true
        e.minimised.value = true
        assertEquals(emptyList<String>(), e.sink.log)
    }

    @Test fun anUnrelatedSettingsChangeDoesNotRestartTheBeep() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        e.nav.value = nav(m = 300)
        e.sink.log.clear()
        e.enabled.value = true // same value re-emitted by a snapshot change
        assertEquals(emptyList<String>(), e.sink.log)
    }

    @Test fun speedIsPassedOnForTheStationaryDuck() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(backgroundScope)
        e.speed.value = 2f
        assertEquals(2f, e.sink.speed)
        e.speed.value = null
        assertEquals(null, e.sink.speed)
    }

    @Test fun onlyOneSourceDrivesTheBeeper() {
        assertFalse("the notification path is silent during an in-app trip", BeepSourcePolicy.listenerMayBeep(inAppGuidanceActive = true))
        assertTrue(BeepSourcePolicy.listenerMayBeep(inAppGuidanceActive = false))
    }
}
