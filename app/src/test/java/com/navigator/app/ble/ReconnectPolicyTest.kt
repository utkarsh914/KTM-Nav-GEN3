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
}
