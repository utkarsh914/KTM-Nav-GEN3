package com.navigator.app.nav.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripGuardTest {

    @Test fun newTripInvalidatesPreviousToken() {
        val g = TripGeneration()
        val a = g.next()
        assertTrue(g.isCurrent(a))
        val b = g.next()
        assertFalse(g.isCurrent(a))
        assertTrue(g.isCurrent(b))
        assertNotEquals(a, b)
    }

    @Test fun invalidateMakesEveryCapturedTokenStale() {
        val g = TripGeneration()
        val t = g.next()
        g.invalidate()
        assertFalse(g.isCurrent(t))
        val frameToken = g.current()
        assertTrue(g.isCurrent(frameToken))
        g.invalidate()
        assertFalse(g.isCurrent(frameToken))
    }

    @Test fun okStarts() {
        assertEquals(RouteResultAction.START, RouteResultPolicy.decide(ok = true, canceled = false, canRetry = true))
        assertEquals(RouteResultAction.START, RouteResultPolicy.decide(ok = true, canceled = false, canRetry = false))
    }

    @Test fun routeCanceledIsNeverRetried() {
        assertEquals(RouteResultAction.IGNORE_CANCELED, RouteResultPolicy.decide(ok = false, canceled = true, canRetry = true))
        assertEquals(RouteResultAction.IGNORE_CANCELED, RouteResultPolicy.decide(ok = false, canceled = true, canRetry = false))
    }

    @Test fun otherFailuresRetryThenFail() {
        assertEquals(RouteResultAction.RETRY, RouteResultPolicy.decide(ok = false, canceled = false, canRetry = true))
        assertEquals(RouteResultAction.FAIL, RouteResultPolicy.decide(ok = false, canceled = false, canRetry = false))
    }
}
