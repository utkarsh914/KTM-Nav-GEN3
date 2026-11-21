package com.navigator.app.ui.feature.pairing

import com.navigator.app.data.connection.ConnectionPhase

enum class BlockReason { NO_PERMISSION, BLUETOOTH_OFF }

/** The bike the rider picked, and when the attempt started. */
data class Attempt(val address: String, val name: String?, val startedAtMs: Long)

/** What the pairing screen is showing (ON6 to ON9). */
sealed interface PairingPhase {
    /** ON8: nothing can be scanned until the cause is fixed. */
    data class Blocked(val reason: BlockReason) : PairingPhase

    /** ON6 */
    data object Scanning : PairingPhase

    /** ON7: waiting for the rider to confirm on the dash. [securing] once the link is up and the keys are being agreed. */
    data class Confirming(val bike: Attempt, val elapsedSec: Int, val securing: Boolean) : PairingPhase

    /** ON9 */
    data object Failed : PairingPhase
}

/** The pure rules for which pairing screen shows. */
object PairingPolicy {
    /** An attempt that never authenticates ends here, and the rider gets help rather than a silent rescan. */
    const val TIMEOUT_MS = 120_000L

    fun phase(
        permissionGranted: Boolean,
        bluetoothOn: Boolean,
        attempt: Attempt?,
        failed: Boolean,
        nowMs: Long,
        link: ConnectionPhase,
        linkUp: Boolean = false,
    ): PairingPhase = when {
        !permissionGranted -> PairingPhase.Blocked(BlockReason.NO_PERMISSION)
        !bluetoothOn -> PairingPhase.Blocked(BlockReason.BLUETOOTH_OFF)
        failed -> PairingPhase.Failed
        attempt != null -> PairingPhase.Confirming(
            attempt, elapsedSec(attempt.startedAtMs, nowMs),
            // "Securing" only once the link is up: until then the dash has not been reached (ON7).
            securing = linkUp && (link == ConnectionPhase.HANDSHAKE_FIRST_PAIR || link == ConnectionPhase.CONNECTING),
        )
        else -> PairingPhase.Scanning
    }

    fun elapsedSec(startedAtMs: Long, nowMs: Long): Int = (maxOf(0L, nowMs - startedAtMs) / 1000).toInt()

    fun timedOut(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs >= TIMEOUT_MS

    /** m:ss for the timer on ON7. */
    fun clock(seconds: Int): String = com.navigator.app.ride.formatElapsed(seconds * 1000L)
}
