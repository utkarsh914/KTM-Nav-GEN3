package com.navigator.app.ui.feature.pairing

import com.navigator.app.data.connection.ConnectionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPolicyTest {
    private val attempt = Attempt("AA", "KTM 1290", startedAtMs = 1_000)

    private fun phase(
        granted: Boolean = true, btOn: Boolean = true, attempt: Attempt? = null, failed: Boolean = false,
        now: Long = 1_000, link: ConnectionPhase = ConnectionPhase.IDLE, linkUp: Boolean = false,
    ) = PairingPolicy.phase(granted, btOn, attempt, failed, now, link, linkUp)

    @Test fun noPermissionBlocksBeforeBluetoothOff() {
        assertEquals(PairingPhase.Blocked(BlockReason.NO_PERMISSION), phase(granted = false, btOn = false))
        assertEquals(PairingPhase.Blocked(BlockReason.BLUETOOTH_OFF), phase(btOn = false))
    }

    @Test fun aBlockWinsOverAFailure() {
        assertEquals(PairingPhase.Blocked(BlockReason.BLUETOOTH_OFF), phase(btOn = false, failed = true))
    }

    @Test fun theListShowsUntilABikeIsPicked() {
        assertEquals(PairingPhase.Scanning, phase())
        assertEquals(PairingPhase.Failed, phase(failed = true))
    }

    @Test fun pickingShowsTheConfirmScreenWithTheElapsedTime() {
        val p = phase(attempt = attempt, now = 66_000) as PairingPhase.Confirming
        assertEquals(65, p.elapsedSec)
        assertEquals("1:05", PairingPolicy.clock(p.elapsedSec))
        assertFalse(phase(attempt = attempt, link = ConnectionPhase.IDLE).let { (it as PairingPhase.Confirming).securing })
        assertTrue((phase(attempt = attempt, link = ConnectionPhase.HANDSHAKE_FIRST_PAIR, linkUp = true) as PairingPhase.Confirming).securing)
    }

    @Test fun securingWaitsForTheLinkToBeUp() {
        fun securing(link: ConnectionPhase, up: Boolean) = (phase(attempt = attempt, link = link, linkUp = up) as PairingPhase.Confirming).securing
        assertFalse("connecting, the dash not reached yet", securing(ConnectionPhase.HANDSHAKE_FIRST_PAIR, up = false))
        assertFalse(securing(ConnectionPhase.CONNECTING, up = false))
        assertTrue(securing(ConnectionPhase.CONNECTING, up = true))
    }

    @Test fun twoMinutesIsTheLimit() {
        assertFalse(PairingPolicy.timedOut(1_000, 120_999))
        assertTrue(PairingPolicy.timedOut(1_000, 121_000))
        // the link came up late: the rider may be confirming on the dash, so the deadline follows it by 75 s
        assertFalse(PairingPolicy.timedOut(1_000, 121_000, linkUpAtMs = 100_000))
        assertFalse(PairingPolicy.timedOut(1_000, 174_999, linkUpAtMs = 100_000))
        assertTrue(PairingPolicy.timedOut(1_000, 175_000, linkUpAtMs = 100_000))
        assertTrue("an early link-up does not shorten it", PairingPolicy.timedOut(1_000, 121_000, linkUpAtMs = 5_000))
    }

    @Test fun aClockGoingBackwardsNeverShowsANegativeTime() {
        assertEquals(0, PairingPolicy.elapsedSec(5_000, 1_000))
    }
}
