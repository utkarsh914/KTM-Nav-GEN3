package com.navigator.app.notifications

import com.navigator.app.notifications.MirrorHints.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorHintsTest {

    private fun hints(
        access: Boolean = true, connected: Boolean = true, paused: Boolean = false,
        hasNav: Boolean = false, age: Long? = null, battery: Boolean = true,
    ) = MirrorHints.compute(access, connected, paused, hasNav, age, battery)

    @Test fun missingAccessIsTheOnlyHint() {
        assertEquals(listOf(Kind.NO_ACCESS), hints(access = false, connected = false, hasNav = true, age = 99_000, battery = false))
    }

    @Test fun disconnectedListenerCanBeRebound() {
        assertEquals(listOf(Kind.LISTENER_DISCONNECTED), hints(connected = false, hasNav = true, age = 99_000))
    }

    @Test fun staleOnlyWhileShowingGuidanceAndNotPaused() {
        assertEquals(listOf(Kind.STALE), hints(hasNav = true, age = MirrorHints.STALE_AFTER_MS + 1))
        assertEquals(emptyList<Kind>(), hints(hasNav = true, age = MirrorHints.STALE_AFTER_MS))
        assertEquals(emptyList<Kind>(), hints(hasNav = true, paused = true, age = 120_000))
        assertEquals(emptyList<Kind>(), hints(hasNav = true, age = null))
    }

    @Test fun aStandingRiderIsNotToldMapsWentQuietUntilTwoMinutes() {
        fun at(speed: Float?, age: Long) = MirrorHints.compute(true, true, false, true, age, true, speed)
        assertEquals("standing at a light: 31 s of silence is normal", emptyList<Kind>(), at(0f, 31_000))
        assertEquals(emptyList<Kind>(), at(4.9f, MirrorHints.STALE_STATIONARY_AFTER_MS))
        assertEquals(listOf(Kind.STALE), at(0f, MirrorHints.STALE_STATIONARY_AFTER_MS + 1))
        assertEquals("riding: the normal 30 s", listOf(Kind.STALE), at(60f, 31_000))
        assertEquals("exactly at the limit speed counts as moving", listOf(Kind.STALE), at(MirrorHints.STATIONARY_BELOW_KMH, 31_000))
        assertEquals("speed unknown: the normal threshold", listOf(Kind.STALE), at(null, 31_000))
    }

    @Test fun batteryTipOnlyWhenIdle() {
        assertEquals(listOf(Kind.BATTERY), hints(hasNav = false, battery = false))
        assertEquals(emptyList<Kind>(), hints(hasNav = false, battery = true))
        assertEquals(emptyList<Kind>(), hints(hasNav = true, age = 1000, battery = false))
    }

    @Test fun healthyIsQuiet() {
        assertEquals(emptyList<Kind>(), hints(hasNav = true, age = 2_000, battery = true))
    }
}
