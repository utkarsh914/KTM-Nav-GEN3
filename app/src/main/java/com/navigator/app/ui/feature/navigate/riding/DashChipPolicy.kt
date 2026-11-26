package com.navigator.app.ui.feature.navigate.riding

import com.navigator.app.data.connection.BikeStatus

/** What the dash link is doing, as far as a rider on a trip cares. */
enum class DashClass { UP, TRYING, DOWN, NEEDS_REPAIR, BLUETOOTH_OFF, NOT_PAIRED }

enum class DashChip { LOST_RETRYING, LOST, BLUETOOTH_OFF, BACK }

/** What the riding screen does about a change of [DashClass]. */
sealed interface DashChipStep {
    data object Nothing : DashChipStep

    /** Show [chip] if the class is still the same after [delayMs]. */
    data class After(val delayMs: Long, val chip: DashChip) : DashChipStep

    data class Now(val chip: DashChip) : DashChipStep
}

/**
 * The red chip says the dash is gone, so it waits out a blip: only a link still down after [DOWN_GRACE_MS] gets one, in
 * words that fit what the link is doing. The green one comes only after a red one was shown.
 */
object DashChipPolicy {
    const val DOWN_GRACE_MS = 3_000L

    fun classOf(status: BikeStatus): DashClass = when (status) {
        is BikeStatus.Connected -> DashClass.UP
        is BikeStatus.Connecting, is BikeStatus.Reconnecting -> DashClass.TRYING
        is BikeStatus.Disconnected -> DashClass.DOWN
        is BikeStatus.NeedsRepair -> DashClass.NEEDS_REPAIR
        BikeStatus.BluetoothOff -> DashClass.BLUETOOTH_OFF
        BikeStatus.NotPaired -> DashClass.NOT_PAIRED
    }

    fun step(now: DashClass, lostShown: Boolean): DashChipStep = when (now) {
        DashClass.UP -> if (lostShown) DashChipStep.Now(DashChip.BACK) else DashChipStep.Nothing
        DashClass.TRYING -> DashChipStep.After(DOWN_GRACE_MS, DashChip.LOST_RETRYING)
        DashClass.DOWN, DashClass.NEEDS_REPAIR -> DashChipStep.After(DOWN_GRACE_MS, DashChip.LOST)
        DashClass.BLUETOOTH_OFF -> DashChipStep.After(DOWN_GRACE_MS, DashChip.BLUETOOTH_OFF)
        DashClass.NOT_PAIRED -> DashChipStep.Nothing
    }
}
