package com.navigator.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    @Test fun backgroundConnectsWaitLongerThanDirectOnes() {
        assertEquals(ReconnectPolicy.STUCK_CONNECTING_MS, ReconnectPolicy.stuckLimitMs(false))
        assertEquals(ReconnectPolicy.STUCK_BACKGROUND_MS, ReconnectPolicy.stuckLimitMs(true))
        assertTrue(ReconnectPolicy.STUCK_BACKGROUND_MS > ReconnectPolicy.STUCK_CONNECTING_MS)
    }

    @Test fun scanIsBoundedWellUnderAndroidsScanDowngrade() {
        assertTrue(ReconnectPolicy.SCAN_WINDOW_MS in 10_000L..5 * 60_000L)
    }

    @Test fun presenceSignalPreemptsOnlyARunningScanWithNoLink() {
        assertTrue(ReconnectPolicy.shouldPreemptScan(bikePresent = true, scanning = true, hasGatt = false))
        assertFalse(ReconnectPolicy.shouldPreemptScan(bikePresent = false, scanning = true, hasGatt = false))
        assertFalse(ReconnectPolicy.shouldPreemptScan(bikePresent = true, scanning = false, hasGatt = false))
        assertFalse(ReconnectPolicy.shouldPreemptScan(bikePresent = true, scanning = true, hasGatt = true))
    }

    @Test fun presenceSignalRecyclesOnlyAParkedBackgroundConnect() {
        assertTrue(ReconnectPolicy.shouldPreemptBackgroundConnect(true, true, hasGatt = true, linkUp = false))
        // Our own LE link coming up (ACL_CONNECTED from our connect) must not churn.
        assertFalse(ReconnectPolicy.shouldPreemptBackgroundConnect(true, true, hasGatt = true, linkUp = true))
        assertFalse(ReconnectPolicy.shouldPreemptBackgroundConnect(true, false, hasGatt = true, linkUp = false))
        assertFalse(ReconnectPolicy.shouldPreemptBackgroundConnect(false, true, hasGatt = true, linkUp = false))
        assertFalse(ReconnectPolicy.shouldPreemptBackgroundConnect(true, true, hasGatt = false, linkUp = false))
    }

    private fun retry(
        authenticated: Boolean = false, hasGatt: Boolean = false, linkUp: Boolean = false, background: Boolean = false,
        scanning: Boolean = false, settling: Boolean = false, handshaking: Boolean = false, since: Long = 60_000L,
    ) = ReconnectPolicy.retryNow(authenticated, hasGatt, linkUp, background, scanning, settling, handshaking, since)

    @Test fun retryNowScansAtOnceWhenNothingIsInFlight() {
        assertEquals(ReconnectPolicy.RetryNow.ScanNow, retry())
    }

    @Test fun retryNowRecyclesAParkedBackgroundConnect() {
        assertEquals(ReconnectPolicy.RetryNow.RecycleAndScan, retry(hasGatt = true, background = true))
        assertEquals("a direct connect under way is left alone", ReconnectPolicy.RetryNow.Ignore, retry(hasGatt = true, background = false))
        assertEquals("a live link is left alone", ReconnectPolicy.RetryNow.Ignore, retry(hasGatt = true, linkUp = true, background = true))
    }

    @Test fun retryNowNeverPilesOntoAnAttemptUnderWay() {
        val ignore = ReconnectPolicy.RetryNow.Ignore
        assertEquals(ignore, retry(authenticated = true))
        assertEquals(ignore, retry(scanning = true))
        assertEquals(ignore, retry(settling = true))
        assertEquals(ignore, retry(handshaking = true))
    }

    @Test fun twoTapsCloseTogetherAreOne() {
        assertEquals(ReconnectPolicy.RetryNow.Ignore, retry(since = ReconnectPolicy.RETRY_NOW_MIN_GAP_MS - 1))
        assertEquals(ReconnectPolicy.RetryNow.ScanNow, retry(since = ReconnectPolicy.RETRY_NOW_MIN_GAP_MS))
    }
}
