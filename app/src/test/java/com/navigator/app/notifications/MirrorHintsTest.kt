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

    @Test fun batteryTipOnlyWhenIdle() {
        assertEquals(listOf(Kind.BATTERY), hints(hasNav = false, battery = false))
        assertEquals(emptyList<Kind>(), hints(hasNav = false, battery = true))
        assertEquals(emptyList<Kind>(), hints(hasNav = true, age = 1000, battery = false))
    }

    @Test fun healthyIsQuiet() {
        assertEquals(emptyList<Kind>(), hints(hasNav = true, age = 2_000, battery = true))
    }

    @Test fun ageLabels() {
        assertEquals("just now", MirrorHints.ageLabel(1_000))
        assertEquals("12 s ago", MirrorHints.ageLabel(12_400))
        assertEquals("3 min ago", MirrorHints.ageLabel(185_000))
        assertEquals("just now", MirrorHints.ageLabel(-5))
    }
}
