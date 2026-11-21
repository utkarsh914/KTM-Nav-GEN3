package com.navigator.app.data.connection

import com.navigator.app.ble.BccuConnectionService.ConnectionState

enum class ConnectionPhase { IDLE, CONNECTING, HANDSHAKE_FIRST_PAIR, RECONNECTING, AUTHENTICATED, FAILED }

/** Everything the connection screens need to say about the link, in one value. */
data class ConnectionDetail(
    val bluetoothOn: Boolean = true,
    val scanning: Boolean = false,
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val attempt: Int = 0,
    val repairSuggested: Boolean = false,
    val lastError: String? = null,
    /** The link itself is up, before the handshake finishes. */
    val linkUp: Boolean = false,
)

/** What the rider sees about the bike: one state, with the name where it helps. */
sealed interface BikeStatus {
    data object NotPaired : BikeStatus
    data object BluetoothOff : BikeStatus
    data class Connected(val name: String?) : BikeStatus
    data class Connecting(val name: String?, val firstPair: Boolean) : BikeStatus
    data class Reconnecting(val name: String?, val attempt: Int, val reason: String? = null) : BikeStatus

    /** Paired, but not connected and not trying (service stopped, or ignition off for a while). */
    data class Disconnected(val name: String?) : BikeStatus

    /** Repeated failed handshakes with a known bike: a re-pair is probably needed. */
    data class NeedsRepair(val name: String?) : BikeStatus
}

/** Pure mapping from the raw facts to [ConnectionDetail] and [BikeStatus]. */
object ConnectionMapping {
    /** After this many failed connects to an absent bike the bike is simply not there (parked, ignition off). */
    const val ABSENT_AFTER_FAILURES = 3

    /**
     * The attempt count the rider sees. Handshake recoveries mean the bike is there but not answering, so
     * they count from the first; failed connects to an absent bike count only for the first few, after
     * which the service keeps scanning quietly and the status reads Not connected.
     */
    fun attempt(handshakeRecoveries: Int, absentFailures: Int): Int =
        maxOf(handshakeRecoveries, if (absentFailures >= ABSENT_AFTER_FAILURES) 0 else absentFailures)

    fun detail(
        state: ConnectionState,
        bluetoothOn: Boolean,
        scanning: Boolean,
        attempt: Int,
        repairHint: Boolean,
        paired: Boolean,
        lastError: String?,
        linkUp: Boolean = false,
    ): ConnectionDetail {
        val phase = when {
            !bluetoothOn -> ConnectionPhase.IDLE
            state == ConnectionState.AUTHENTICATED -> ConnectionPhase.AUTHENTICATED
            repairHint -> ConnectionPhase.FAILED
            // Between attempts the service reads DISCONNECTED (rescan delay, backoff, cooldown) but is retrying.
            attempt > 0 -> ConnectionPhase.RECONNECTING
            state == ConnectionState.CONNECTING -> when {
                !paired -> ConnectionPhase.HANDSHAKE_FIRST_PAIR
                else -> ConnectionPhase.CONNECTING
            }
            else -> ConnectionPhase.IDLE
        }
        return ConnectionDetail(bluetoothOn, scanning, phase, attempt, repairHint, lastError, linkUp)
    }

    fun status(detail: ConnectionDetail, bondedAddress: String?, name: String?): BikeStatus = when {
        bondedAddress == null -> BikeStatus.NotPaired
        !detail.bluetoothOn -> BikeStatus.BluetoothOff
        else -> when (detail.phase) {
            ConnectionPhase.AUTHENTICATED -> BikeStatus.Connected(name)
            ConnectionPhase.FAILED -> BikeStatus.NeedsRepair(name)
            ConnectionPhase.CONNECTING -> BikeStatus.Connecting(name, firstPair = false)
            ConnectionPhase.HANDSHAKE_FIRST_PAIR -> BikeStatus.Connecting(name, firstPair = true)
            ConnectionPhase.RECONNECTING -> BikeStatus.Reconnecting(name, detail.attempt, detail.lastError)
            ConnectionPhase.IDLE -> BikeStatus.Disconnected(name)
        }
    }
}
