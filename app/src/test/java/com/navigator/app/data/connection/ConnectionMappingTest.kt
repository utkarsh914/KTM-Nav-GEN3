package com.navigator.app.data.connection

import com.navigator.app.ble.BccuConnectionService.ConnectionState
import com.navigator.app.ble.BccuConnectionService.ConnectionState.AUTHENTICATED
import com.navigator.app.ble.BccuConnectionService.ConnectionState.CONNECTING
import com.navigator.app.ble.BccuConnectionService.ConnectionState.DISCONNECTED
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
}
