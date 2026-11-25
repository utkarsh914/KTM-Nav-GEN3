package com.navigator.app.nav.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartRequestsTest {
    @Test fun aNewStartMakesTheEarlierOneStale() {
        val r = StartRequests()
        val first = r.begin()
        assertTrue(r.isCurrent(first))
        val second = r.begin()
        assertFalse(r.isCurrent(first))
        assertTrue(r.isCurrent(second))
    }

    @Test fun invalidateDropsThePendingStart() {
        val r = StartRequests()
        val id = r.begin()
        r.invalidate()
        assertFalse(r.isCurrent(id))
    }
}
