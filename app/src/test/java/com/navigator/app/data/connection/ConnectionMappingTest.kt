package com.navigator.app.data.connection

import com.navigator.app.ble.BccuConnectionService.ConnectionState
import com.navigator.app.ble.BccuConnectionService.ConnectionState.AUTHENTICATED
import com.navigator.app.ble.BccuConnectionService.ConnectionState.CONNECTING
import com.navigator.app.ble.BccuConnectionService.ConnectionState.DISCONNECTED
import com.navigator.app.notify.ConnectionNotice
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionMappingTest {
    private fun phase(
        state: ConnectionState, btOn: Boolean = true, attempt: Int = 0, hint: Boolean = false, paired: Boolean = true,
    ) = ConnectionMapping.detail(state, btOn, false, attempt, hint, paired, null).phase

    @Test fun phaseTable() {
        assertEquals(ConnectionPhase.AUTHENTICATED, phase(AUTHENTICATED))
        assertEquals(ConnectionPhase.CONNECTING, phase(CONNECTING))
        assertEquals(ConnectionPhase.HANDSHAKE_FIRST_PAIR, phase(CONNECTING, paired = false))
        assertEquals(ConnectionPhase.RECONNECTING, phase(CONNECTING, attempt = 2))
        assertEquals("a retry beats the first-pair label", ConnectionPhase.RECONNECTING, phase(CONNECTING, attempt = 1, paired = false))
        assertEquals("between attempts the service reads disconnected but is retrying", ConnectionPhase.RECONNECTING, phase(DISCONNECTED, attempt = 1))
        assertEquals(ConnectionPhase.IDLE, phase(DISCONNECTED))
        assertEquals(ConnectionPhase.FAILED, phase(DISCONNECTED, hint = true))
        assertEquals(ConnectionPhase.FAILED, phase(CONNECTING, hint = true))
        assertEquals("Bluetooth off wins over everything", ConnectionPhase.IDLE, phase(AUTHENTICATED, btOn = false))
    }

    @Test fun detailCarriesTheRawFacts() {
        val d = ConnectionMapping.detail(CONNECTING, true, true, 3, true, true, "boom")
        assertEquals(ConnectionDetail(true, true, ConnectionPhase.FAILED, 3, true, "boom"), d)
    }

    private fun status(phase: ConnectionPhase, addr: String? = "AA", bt: Boolean = true, attempt: Int = 0) =
        ConnectionMapping.status(ConnectionDetail(bluetoothOn = bt, phase = phase, attempt = attempt), addr, "KTM3638")

    @Test fun statusTable() {
        assertEquals(BikeStatus.NotPaired, status(ConnectionPhase.AUTHENTICATED, addr = null))
        assertEquals(BikeStatus.BluetoothOff, status(ConnectionPhase.IDLE, bt = false))
        assertEquals(BikeStatus.Connected("KTM3638"), status(ConnectionPhase.AUTHENTICATED))
        assertEquals(BikeStatus.NeedsRepair("KTM3638"), status(ConnectionPhase.FAILED))
        assertEquals(BikeStatus.Connecting("KTM3638", false), status(ConnectionPhase.CONNECTING))
        assertEquals(BikeStatus.Connecting("KTM3638", true), status(ConnectionPhase.HANDSHAKE_FIRST_PAIR))
        assertEquals(BikeStatus.Reconnecting("KTM3638", 2), status(ConnectionPhase.RECONNECTING, attempt = 2))
        assertEquals(BikeStatus.Disconnected("KTM3638"), status(ConnectionPhase.IDLE))
    }

    @Test fun aParkedBikeStopsReadingAsReconnecting() {
        assertEquals(1, ConnectionMapping.attempt(handshakeRecoveries = 0, absentFailures = 1))
        assertEquals(2, ConnectionMapping.attempt(0, 2))
        assertEquals("absent for a while: quiet scanning, not an attempt count", 0, ConnectionMapping.attempt(0, 3))
        assertEquals(0, ConnectionMapping.attempt(0, 40))
        assertEquals("a bike that answers badly keeps counting", 4, ConnectionMapping.attempt(4, 40))
        assertEquals(ConnectionPhase.IDLE, phase(DISCONNECTED, attempt = ConnectionMapping.attempt(0, 40)))
    }

    private fun detail(
        state: ConnectionState, scanning: Boolean = false, attempt: Int = 0, hint: Boolean = false, bt: Boolean = true,
        linkUp: Boolean = false, absent: Boolean = false, reconnecting: Boolean = false,
    ) = ConnectionMapping.detail(state, bt, scanning, attempt, hint, true, null, linkUp, absent, reconnecting)

    @Test fun aParkedBikeReadsNotConnectedEvenWhileTheQuietScanRuns() {
        val d = detail(CONNECTING, scanning = true, absent = true)
        assertEquals(ConnectionPhase.IDLE, d.phase)
        assertEquals(BikeStatus.Disconnected("KTM3638"), ConnectionMapping.status(d, "AA", "KTM3638"))
        assertEquals(ConnectionNotice.DISCONNECTED, ConnectionMapping.notice(d))
        assertEquals("the bike showing up ends the absence", ConnectionPhase.CONNECTING, detail(CONNECTING, absent = true, linkUp = true).phase)
    }

    @Test fun linksThatDropBeforeAuthenticatingCountAsAttempts() {
        assertEquals(3, ConnectionMapping.attempt(handshakeRecoveries = 1, absentFailures = 0, preAuthDrops = 2))
        assertEquals("they do not hide behind a parked bike's quiet scanning", 2, ConnectionMapping.attempt(0, 40, 2))
        assertEquals(0, ConnectionMapping.attempt(0, 0, 0))
    }

    @Test fun aDropOfAWorkingLinkIsReconnectingInTheAppAndInTheNotice() {
        val d = detail(DISCONNECTED, reconnecting = true)
        assertEquals(BikeStatus.Reconnecting("KTM3638", 0), ConnectionMapping.status(d, "AA", "KTM3638"))
        assertEquals(ConnectionNotice.RECONNECTING, ConnectionMapping.notice(d))
        assertEquals("a parked bike is not 'reconnecting' for ever", ConnectionPhase.IDLE, detail(DISCONNECTED, reconnecting = true, absent = true).phase)
    }

    @Test fun cantConnectSticksWhileTheHintStandsAndTheNoticeFollowsTheApp() {
        for (state in listOf(DISCONNECTED, CONNECTING)) {
            val d = detail(state, hint = true, attempt = 2, reconnecting = true, scanning = true)
            assertEquals(ConnectionNotice.CANT_CONNECT, ConnectionMapping.notice(d))
            assertEquals(BikeStatus.NeedsRepair("KTM3638"), ConnectionMapping.status(d, "AA", "KTM3638"))
        }
        assertEquals(ConnectionNotice.CONNECTED, ConnectionMapping.notice(detail(AUTHENTICATED, hint = true)))
    }

    @Test fun bluetoothOffWinsForTheNoticeToo() {
        assertEquals(ConnectionNotice.BLUETOOTH_OFF, ConnectionMapping.notice(detail(AUTHENTICATED, bt = false, hint = true)))
    }

    @Test fun everyPhaseHasANoticeThatMatchesWhatTheAppSays() {
        assertEquals(ConnectionNotice.CONNECTING, ConnectionMapping.notice(detail(CONNECTING)))
        assertEquals(ConnectionNotice.CONNECTING, ConnectionMapping.notice(ConnectionMapping.detail(CONNECTING, true, false, 0, false, false, null)))
        assertEquals(ConnectionNotice.RECONNECTING, ConnectionMapping.notice(detail(CONNECTING, attempt = 1)))
    }
}
