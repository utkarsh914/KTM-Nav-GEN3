package com.navigator.app.nav

import com.navigator.app.ble.BccuProtocol
import com.navigator.app.nav.ktm.KtmNavigationEncoder
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationCoordinatorTest {

    private class FakeProvider(override val id: String) : NavigationProvider {
        override val state = MutableStateFlow(NormalizedNavigationState.IDLE)
        var detached = 0
        override fun attach() {}
        override fun detach() { detached++ }
    }

    private class RecordingOutput : DashOutput {
        val log = mutableListOf<String>()
        override fun setNavState(guidanceOn: Boolean, gpsIconOn: Boolean) { log += "nav" }
        override fun turnIcon(icon: BccuProtocol.TurnIcon) { log += "icon:${icon.name}" }
        override fun guidance(distance: String?, road: String?, eta: String?, remaining: String?) {
            distance?.let { log += "dist:$it" }
            road?.let { log += "road:$it" }
        }
        override fun clearNow() { log += "clearNow" }
        override fun clearDebounced() { log += "clearDebounced" }
        override fun offlineBanner(show: Boolean) {}
    }

    private val scope = TestScope()
    private var wall = 1_000_000L
    private val fallback = FakeProvider("notification")
    private val sdk = FakeProvider("sdk")
    private val out = RecordingOutput()
    private val coordinator = NavigationCoordinator(
        scope = scope,
        fallbackProvider = fallback,
        output = out,
        encoder = KtmNavigationEncoder(
            zone = ZoneId.of("UTC"),
            minIntervalMs = 1000,
            clockMs = { scope.testScheduler.currentTime },
        ),
        isOnline = MutableStateFlow(true),
        wallClockMs = { wall },
    )

    private fun enroute(dist: Int, road: String = "Main St", at: Long = wall) = NormalizedNavigationState(
        sessionState = NavSessionState.ENROUTE,
        maneuver = NormalizedManeuver.LEFT,
        distanceToManeuverMeters = dist,
        roadName = road,
        producedAtMs = at,
    )

    @Test fun staleStoppedFromPreviousTripDoesNotRevert() {
        coordinator.start()
        sdk.state.value = NormalizedNavigationState(NavSessionState.STOPPED, producedAtMs = wall - 60_000)
        coordinator.setProvider(sdk)
        scope.runCurrent()
        assertSame(sdk, coordinator.activeProvider)
        assertEquals(0, sdk.detached) // new trip not killed
        sdk.state.value = enroute(300)
        scope.runCurrent()
        assertTrue(out.log.contains("icon:QUITE_LEFT"))
    }

    @Test fun genuineTripEndRevertsToFallback() {
        coordinator.start()
        coordinator.setProvider(sdk)
        sdk.state.value = enroute(300)
        scope.runCurrent()
        sdk.state.value = NormalizedNavigationState(NavSessionState.STOPPED, producedAtMs = wall)
        scope.runCurrent()
        assertSame(fallback, coordinator.activeProvider)
    }

    @Test fun freshFailureBeforeGoingLiveReverts() {
        coordinator.start()
        coordinator.setProvider(sdk)
        scope.runCurrent()
        wall += 5_000
        sdk.state.value = NormalizedNavigationState(NavSessionState.STOPPED, producedAtMs = wall)
        scope.runCurrent()
        assertSame(fallback, coordinator.activeProvider)
    }

    @Test fun switchingProviderClearsOldGuidance() {
        coordinator.start()
        fallback.state.value = enroute(300)
        scope.runCurrent()
        out.log.clear()
        coordinator.setProvider(sdk)
        assertEquals("clearNow", out.log.first())
    }

    @Test fun stopDoesNotDetachRoutingProvider() {
        coordinator.start()
        coordinator.setProvider(sdk)
        scope.runCurrent()
        coordinator.stop()
        assertEquals(0, sdk.detached)
    }

    @Test fun throttledDistanceIsFlushedAfterWindow() {
        coordinator.start()
        fallback.state.value = enroute(300)
        scope.runCurrent()
        scope.advanceTimeBy(200)
        fallback.state.value = enroute(250)
        scope.runCurrent()
        assertFalse(out.log.contains("dist:250 m"))
        scope.advanceTimeBy(1_100)
        scope.runCurrent()
        assertTrue("trailing flush must send the held value: ${out.log}", out.log.contains("dist:250 m"))
    }
}
