package com.navigator.app.ble

import com.navigator.app.ble.BccuProtocol.NotificationIcon
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ble.BccuProtocol.Visibility
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Snapshot of the exact bytes written to the dash. A change here changes what the bike receives. */
class BccuProtocolPayloadTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun label(vis: Int, text: String) = byteArrayOf(vis.toByte()) + text.toByteArray(Charsets.UTF_8)

    @Test fun uuidLayout() {
        assertEquals("71ced1ac-0700-44f5-9454-806ff70b3e02", BccuProtocol.MAIN_SERVICE.toString())
        assertEquals("71ced1ac-0704-44f5-9454-806ff70b3e02", BccuProtocol.TURN_ICON.toString())
        assertEquals("71ced1ac-070a-44f5-9454-806ff70b3e02", BccuProtocol.NOTIFICATION.toString())
        assertEquals("71ced1ac-070c-44f5-9454-806ff70b3e02", BccuProtocol.TBT_NAV_RESPONSE.toString())
    }

    @Test fun turnIconPayload() {
        assertArrayEquals(bytes(3, 2), BccuProtocol.buildTurnIconPayload(TurnIcon.GO_STRAIGHT))
        assertArrayEquals(bytes(1, 21), BccuProtocol.buildTurnIconPayload(TurnIcon.END, Visibility.OFF))
        assertArrayEquals(bytes(2, 57), BccuProtocol.buildTurnIconPayload(TurnIcon.RAB_SECT_16_LH, Visibility.HALF))
        assertArrayEquals(bytes(0xFF, 0), BccuProtocol.buildTurnIconPayload(TurnIcon.UNKNOWN, Visibility.UNKNOWN))
    }

    @Test fun turnIconBinaryValuesAreStable() {
        val expected = mapOf(
            TurnIcon.UNKNOWN to 0, TurnIcon.UNDEFINED to 1, TurnIcon.GO_STRAIGHT to 2,
            TurnIcon.UTURN_RIGHT to 3, TurnIcon.UTURN_LEFT to 4, TurnIcon.KEEP_RIGHT to 5,
            TurnIcon.LIGHT_RIGHT to 6, TurnIcon.QUITE_RIGHT to 7, TurnIcon.HEAVY_RIGHT to 8,
            TurnIcon.KEEP_MIDDLE to 9, TurnIcon.KEEP_LEFT to 10, TurnIcon.LIGHT_LEFT to 11,
            TurnIcon.QUITE_LEFT to 12, TurnIcon.HEAVY_LEFT to 13,
            TurnIcon.ENTER_HIGHWAY_RIGHT_LANE to 14, TurnIcon.ENTER_HIGHWAY_LEFT_LANE to 15,
            TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE to 16, TurnIcon.LEAVE_HIGHWAY_LEFT_LANE to 17,
            TurnIcon.HIGHWAY_KEEP_RIGHT to 18, TurnIcon.HIGHWAY_KEEP_LEFT to 19,
            TurnIcon.START to 20, TurnIcon.END to 21, TurnIcon.FERRY to 22, TurnIcon.PASS_STATION to 23,
            TurnIcon.HEAD_TO to 24, TurnIcon.CHANGE_LINE to 25,
            TurnIcon.RAB_SECT_1_RH to 26, TurnIcon.RAB_SECT_16_RH to 41,
            TurnIcon.RAB_SECT_1_LH to 42, TurnIcon.RAB_SECT_16_LH to 57,
        )
        expected.forEach { (icon, bin) -> assertEquals(icon.name, bin, icon.binary) }
        assertEquals(58, TurnIcon.entries.size)
        TurnIcon.entries.forEachIndexed { i, icon -> assertEquals(i, icon.binary) }
        assertEquals(TurnIcon.UNKNOWN, TurnIcon.fromBinary(999))
    }

    @Test fun visibilityBinaryValues() {
        assertEquals(-1, Visibility.UNKNOWN.binary)
        assertEquals(3, Visibility.FULL.binary)
        assertEquals(2, Visibility.HALF.binary)
        assertEquals(1, Visibility.OFF.binary)
    }

    @Test fun notificationPayload() {
        assertArrayEquals(
            bytes(3, 3, 0x48, 0x69),
            BccuProtocol.buildNotificationPayload(NotificationIcon.TARGET_REACHED, "Hi"),
        )
        assertArrayEquals(
            bytes(1, 0),
            BccuProtocol.buildNotificationPayload(NotificationIcon.UNKNOWN, "", Visibility.OFF),
        )
    }

    @Test fun notificationTextCappedAt16Units() {
        val p = BccuProtocol.buildNotificationPayload(NotificationIcon.INFORMATION, "A".repeat(20))
        assertEquals(2 + 16, p.size)
        assertEquals(6, p[1].toInt())
    }

    @Test fun navigationStatePayload() {
        assertArrayEquals(bytes(3, 255), BccuProtocol.buildNavigationStatePayload(true, true))
        assertArrayEquals(bytes(1, 255), BccuProtocol.buildNavigationStatePayload(true, false))
        assertArrayEquals(bytes(2, 255), BccuProtocol.buildNavigationStatePayload(false, true))
        assertArrayEquals(bytes(0, 255), BccuProtocol.buildNavigationStatePayload(false, false))
        assertArrayEquals(bytes(3, 50), BccuProtocol.buildNavigationStatePayload(true, true, 50))
    }

    @Test fun labelPayloadsFit() {
        assertArrayEquals(label(3, "110 m"), BccuProtocol.buildTurnDistancePayload("110 m"))
        assertArrayEquals(label(3, "towards NH 48"), BccuProtocol.buildTurnInfoPayload("towards NH 48"))
        assertArrayEquals(label(3, "1st Cross Rd"), BccuProtocol.buildTurnRoadPayload("1st Cross Rd"))
        assertArrayEquals(label(3, "12:45"), BccuProtocol.buildEtaPayload("12:45"))
        assertArrayEquals(label(3, "123 km"), BccuProtocol.buildRemainingDistancePayload("123 km"))
        assertArrayEquals(label(1, ""), BccuProtocol.buildTurnDistancePayload("", Visibility.OFF))
    }

    @Test fun labelPayloadsTruncateWithEllipsis() {
        assertArrayEquals(label(3, "12345..."), BccuProtocol.buildTurnDistancePayload("1234567890"))
        assertArrayEquals(label(3, "12345678"), BccuProtocol.buildEtaPayload("12345678"))
        assertArrayEquals(label(3, "A".repeat(13) + "..."), BccuProtocol.buildTurnInfoPayload("A".repeat(40)))
        assertArrayEquals(label(3, "A".repeat(29) + "..."), BccuProtocol.buildTurnRoadPayload("A".repeat(40)))
        assertArrayEquals(label(2, "12345..."), BccuProtocol.buildRemainingDistancePayload("1234567890", Visibility.HALF))
    }

    @Test fun handshakeConstants() {
        assertEquals(0, BccuProtocol.CMD_HELLO)
        assertEquals(1, BccuProtocol.CMD_GENERATE_KEYS)
        assertEquals(16, BccuProtocol.CMD_KEY_ACK_BASE)
        assertEquals(0, BccuProtocol.STATUS_INITIAL_CONNECTION)
        assertEquals(1, BccuProtocol.STATUS_VALID_KEY_ARRAY)
        assertEquals(2, BccuProtocol.STATUS_KEY_ARRAY_GENERATED)
    }
}
