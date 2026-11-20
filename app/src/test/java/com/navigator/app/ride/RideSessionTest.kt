package com.navigator.app.ride

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionTest {

    @After fun reset() = RideSession.ended()

    @Test fun elapsedCountsOnlyRunningTime() {
        RideSession.started(manual = true, nowMs = 1_000)
        assertEquals(4_000L, RideSession.state.value.elapsedMs(5_000))
        RideSession.paused(5_000)
        assertTrue(RideSession.state.value.paused)
        assertEquals(4_000L, RideSession.state.value.elapsedMs(99_000)) // frozen while paused
        RideSession.resumed(100_000)
        assertEquals(6_000L, RideSession.state.value.elapsedMs(102_000)) // 4 s before + 2 s now
    }

    @Test fun inactiveSessionHasNoElapsedAndIgnoresUpdates() {
        RideSession.paused(1)
        RideSession.resumed(2)
        RideSession.distance(500)
        assertFalse(RideSession.state.value.active)
        assertEquals(0L, RideSession.state.value.elapsedMs(10_000))
        assertEquals(0, RideSession.state.value.distanceMeters)
    }

    @Test fun distanceAndEndReset() {
        RideSession.started(manual = false, nowMs = 0)
        RideSession.distance(1234)
        assertEquals(1234, RideSession.state.value.distanceMeters)
        assertFalse(RideSession.state.value.manual)
        RideSession.ended()
        assertEquals(RideSessionState(), RideSession.state.value)
    }

    @Test fun elapsedFormatting() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("7:05", formatElapsed(425_000))
        assertEquals("1:02:09", formatElapsed(3_729_000))
        assertEquals("0:00", formatElapsed(-5))
    }
}
