package com.navigator.app.ble

import java.util.UUID

/**
 * GATT layout and wire-format tables for the BCCU BLE protocol, confirmed
 * byte-exact from decompiled source of two independent apps. See
 * BCCU_BLE_PROTOCOL.md for the full write-up and provenance.
 */
object BccuProtocol {

    private fun uuid(suffix: String) = UUID.fromString("71ced1ac-$suffix-44f5-9454-806ff70b3e02")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val MAIN_SERVICE: UUID = uuid("0700")
    val AUTH_REQUEST: UUID = uuid("0701")
    val AUTH_REPLY: UUID = uuid("0702")
    val NAVIGATION_STATE: UUID = uuid("0703")
    val TURN_ICON: UUID = uuid("0704")
    val TURN_DISTANCE: UUID = uuid("0705")
    val TURN_INFO: UUID = uuid("0706")
    val TURN_ROAD: UUID = uuid("0707")
    val ETA: UUID = uuid("0708")
    val REMAINING_DISTANCE: UUID = uuid("0709")
    val NOTIFICATION: UUID = uuid("070a")
    val TBT_NAV_REQUEST: UUID = uuid("070b")
    val TBT_NAV_RESPONSE: UUID = uuid("070c")

    val BASE_SERVICE: UUID = uuid("0000")
    val BASE_GET_VIN_REQUEST: UUID = uuid("0001")
    val BASE_VIN: UUID = uuid("0002")

    /** Standard Bluetooth SIG Device Information service (model/serial/fw/hw/sw revision). */
    val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    // Undocumented services seen in the live GATT dump of bike KTM3638 but
    // absent from both reference apps' source (BCCU_BLE_PROTOCOL.md §9.1).
    // 0200 exposes six R/W/w/N/I characteristics (0201-0206) - the best
    // remaining lead for streaming telemetry on a bike without PRPC (0600).
    val MYSTERY_SERVICE_0200: UUID = uuid("0200")
    val MYSTERY_SERVICE_0300: UUID = uuid("0300")

    val RCM_SERVICE: UUID = uuid("0100")

    // Protobuf/telemetry RPC channel - confirmed from BleRpcTransport.java:
    // requests -> 0601, responses -> 0602, telemetry notifications -> 0603.
    // Same session frame()+AES scheme as MAIN_SERVICE (BCcuClient.tryEncrypt(tryFrameMessage(...))).
    val PRPC_SERVICE: UUID = uuid("0600")
    val PRPC_REQUEST: UUID = uuid("0601")
    val PRPC_RESPONSE: UUID = uuid("0602")
    val PRPC_NOTIFICATION: UUID = uuid("0603")

    // RPC opcodes, confirmed from CUKT_bCCU.json "opcodes" section.
    const val RPC_OPCODE_GET_VALUE = 1
    const val RPC_OPCODE_SET_VALUE = 2
    const val RPC_OPCODE_TELEMETRY_CONFIGURE = 3
    const val RPC_OPCODE_TELEMETRY_CONTROL = 4

    const val TELEMETRY_CMD_STOP = 0
    const val TELEMETRY_CMD_START = 1
    const val TELEMETRY_CMD_RESET = 2

    // NOTE: The PRPC/telemetry runtime (frame builders, notification parser and
    // the on-connect streaming in BccuConnectionService) was removed as dead code
    // - the decoded values were never surfaced. The constants/UUIDs above are kept
    // as byte-exact wire-format reference (from RPCRequestBuilder / PRpcClient) so
    // the feature can be reinstated later without re-reverse-engineering it.

    // Handshake REQUEST codes the DASH sends (byte[2] of a decrypted 16-byte
    // control message). Confirmed byte-exact from the official KTM Connect SDK
    // (com.ktm.mobsdk.bccu.BCcuAuth.Request.Companion.fromRawValue):
    //   0        -> AppIdKeyArrStatus  ("do you already have this bike's key array?")
    //   1        -> ComputeKeyArr      ("derive + store a fresh 16-key array")
    //   16..31   -> AssignKeyIndex     (use key index = code & 0x0F for the session)
    const val CMD_HELLO = 0            // = AppIdKeyArrStatus request
    const val CMD_GENERATE_KEYS = 1    // = ComputeKeyArr request
    const val CMD_KEY_ACK_BASE = 16    // 16 | keyIndex (0-15)

    // App STATUS codes WE send back (byte[4] of our reply), confirmed byte-exact
    // from com.ktm.mobsdk.bccu.BCcuAuth.ResponseAppStatus. The dash decides
    // whether to show its physical "add device" prompt from THIS status: reply
    // VALID_KEY_ARRAY when we already hold the bike's key array and it resumes
    // silently (AssignKeyIndex); reply INITIAL_CONNECTION and it prompts the
    // rider and issues ComputeKeyArr to derive a fresh array.
    const val STATUS_INITIAL_CONNECTION = 0
    const val STATUS_VALID_KEY_ARRAY = 1
    const val STATUS_KEY_ARRAY_GENERATED = 2
    const val STATUS_KEY_ARRAY_ERROR = 3
    const val STATUS_KEY_CONFIRMATION_ERROR = 4

    enum class TurnIcon(val binary: Int) {
        UNKNOWN(0), UNDEFINED(1), GO_STRAIGHT(2), UTURN_RIGHT(3), UTURN_LEFT(4),
        KEEP_RIGHT(5), LIGHT_RIGHT(6), QUITE_RIGHT(7), HEAVY_RIGHT(8), KEEP_MIDDLE(9),
        KEEP_LEFT(10), LIGHT_LEFT(11), QUITE_LEFT(12), HEAVY_LEFT(13),
        ENTER_HIGHWAY_RIGHT_LANE(14), ENTER_HIGHWAY_LEFT_LANE(15),
        LEAVE_HIGHWAY_RIGHT_LANE(16), LEAVE_HIGHWAY_LEFT_LANE(17),
        HIGHWAY_KEEP_RIGHT(18), HIGHWAY_KEEP_LEFT(19),
        START(20), END(21), FERRY(22), PASS_STATION(23), HEAD_TO(24), CHANGE_LINE(25),
        RAB_SECT_1_RH(26), RAB_SECT_2_RH(27), RAB_SECT_3_RH(28), RAB_SECT_4_RH(29),
        RAB_SECT_5_RH(30), RAB_SECT_6_RH(31), RAB_SECT_7_RH(32), RAB_SECT_8_RH(33),
        RAB_SECT_9_RH(34), RAB_SECT_10_RH(35), RAB_SECT_11_RH(36), RAB_SECT_12_RH(37),
        RAB_SECT_13_RH(38), RAB_SECT_14_RH(39), RAB_SECT_15_RH(40), RAB_SECT_16_RH(41),
        RAB_SECT_1_LH(42), RAB_SECT_2_LH(43), RAB_SECT_3_LH(44), RAB_SECT_4_LH(45),
        RAB_SECT_5_LH(46), RAB_SECT_6_LH(47), RAB_SECT_7_LH(48), RAB_SECT_8_LH(49),
        RAB_SECT_9_LH(50), RAB_SECT_10_LH(51), RAB_SECT_11_LH(52), RAB_SECT_12_LH(53),
        RAB_SECT_13_LH(54), RAB_SECT_14_LH(55), RAB_SECT_15_LH(56), RAB_SECT_16_LH(57);

        companion object {
            fun fromBinary(b: Int): TurnIcon = entries.firstOrNull { it.binary == b } ?: UNKNOWN
        }
    }

    enum class NotificationIcon(val binary: Int) {
        UNKNOWN(0), NOTIFICATION_REROUTING(1), NOTIFICATION_WAYPOINT(2), TARGET_REACHED(3),
        GPS_LOST(4), WARNING(5), INFORMATION(6), SPEED(7);

        companion object {
            fun fromBinary(b: Int): NotificationIcon = entries.firstOrNull { it.binary == b } ?: UNKNOWN
        }
    }

    /** Confirmed from com.ktm.mob.services.etbt.Visibility.binary() - NOT a 0/1 boolean. */
    enum class Visibility(val binary: Int) {
        UNKNOWN(-1), FULL(3), HALF(2), OFF(1)
    }

    /** Confirmed from com.ktm.mob.services.etbt.impl.ble.characteristics.payloads.types.OnOff. */
    enum class OnOff(val binary: Int) {
        OFF(0), ON(1)
    }

    /** Build a TURN_ICON characteristic payload: [visibility][iconByte]. */
    fun buildTurnIconPayload(icon: TurnIcon, visibility: Visibility = Visibility.FULL): ByteArray =
        byteArrayOf(visibility.binary.toByte(), icon.binary.toByte())

    /** Build a NOTIFICATION characteristic payload: [visibility][iconByte][UTF-8 text, max 16 chars]. */
    fun buildNotificationPayload(icon: NotificationIcon, text: String, visibility: Visibility = Visibility.FULL): ByteArray {
        val truncated = if (text.length > 16) text.substring(0, 16) else text
        val textBytes = truncated.toByteArray(Charsets.UTF_8)
        val out = ByteArray(2 + textBytes.size)
        out[0] = visibility.binary.toByte()
        out[1] = icon.binary.toByte()
        System.arraycopy(textBytes, 0, out, 2, textBytes.size)
        return out
    }

    /**
     * Build a NAVIGATION_STATE payload: [byte0: bit0=guidanceOn bit1=gpsIconOn][byte1: volume 0-100 or 255=unset].
     * Confirmed from com.ktm...NavigationState.serialize(). The dash appears to require
     * guidanceOn=ON before it will render TURN_ICON/TURN_INFO/NOTIFICATION content.
     */
    fun buildNavigationStatePayload(guidanceOn: Boolean, gpsIconOn: Boolean, volume: Int = 255): ByteArray {
        val flags = (if (guidanceOn) OnOff.ON.binary else OnOff.OFF.binary) or
            ((if (gpsIconOn) OnOff.ON.binary else OnOff.OFF.binary) shl 1)
        return byteArrayOf(flags.toByte(), (volume and 0xFF).toByte())
    }

    /**
     * Truncation confirmed from com.ktm.mob.utils.StrElipsed.elipse(): if the string
     * already fits, leave it; otherwise cut to maxLen-3 chars and append "...".
     * Operates on String.length() (UTF-16 code units), same as the source.
     */
    private fun elipse(text: String, maxLen: Int): String = when {
        text.length <= maxLen -> text
        maxLen <= 3 -> text.substring(0, maxLen)
        else -> text.substring(0, maxLen - 3) + "..."
    }

    /** Shared [visibility][UTF-8 text] shape used by TURN_DISTANCE/TURN_INFO/TURN_ROAD/ETA/REMAINING_DISTANCE. */
    private fun buildLabelPayload(text: String, maxLen: Int, visibility: Visibility): ByteArray {
        val textBytes = elipse(text, maxLen).toByteArray(Charsets.UTF_8)
        return byteArrayOf(visibility.binary.toByte()) + textBytes
    }

    /**
     * TURN_DISTANCE (0705): distance-to-next-turn label shown in the dash's
     * center guidance view, e.g. "110 m". Confirmed from TurnDistance.java
     * (max 8 chars) - the official app builds the text as "$turnDist $turnDistUnit".
     */
    fun buildTurnDistancePayload(text: String, visibility: Visibility = Visibility.FULL) =
        buildLabelPayload(text, 8, visibility)

    /**
     * TURN_INFO (0706): secondary maneuver text (Sygic's GuidanceManeuver.turnInfo).
     * Confirmed from TurnInfo.java (max 16 chars).
     */
    fun buildTurnInfoPayload(text: String, visibility: Visibility = Visibility.FULL) =
        buildLabelPayload(text, 16, visibility)

    /**
     * TURN_ROAD (0707): street/road name shown in the dash's center guidance
     * view, e.g. "towards 1st Cross Rd". Confirmed from TurnRoad.java (max 32 chars).
     */
    fun buildTurnRoadPayload(text: String, visibility: Visibility = Visibility.FULL) =
        buildLabelPayload(text, 32, visibility)

    /** ETA (0708): arrival time label, e.g. "12:45". Confirmed from Eta.java (max 8 chars). */
    fun buildEtaPayload(text: String, visibility: Visibility = Visibility.FULL) =
        buildLabelPayload(text, 8, visibility)

    /**
     * REMAINING_DISTANCE (0709): total remaining trip distance, e.g. "123 km".
     * Confirmed from Dist2Target.java (max 8 chars).
     */
    fun buildRemainingDistancePayload(text: String, visibility: Visibility = Visibility.FULL) =
        buildLabelPayload(text, 8, visibility)
}
