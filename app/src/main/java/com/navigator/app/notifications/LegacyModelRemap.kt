package com.navigator.app.notifications

import com.navigator.app.ble.BccuProtocol.TurnIcon

/**
 * Runtime correction for the roundabout classes of the currently-shipped
 * maneuver model, which was trained BEFORE the RAB_SECT section->angle law was
 * hardware-verified (see docs/ENGINEERING_NOTES.md). Its training labels
 * (ml/train_maneuver_model.py, pre-v2) were:
 *
 * - RH (clockwise) bank: slight_right -> N2 and sharp_right -> N6 - swapped
 *   (verified: N = 8 - angle/22.5, so slight right = N6, sharp right = N2).
 * - LH (counter-clockwise) bank: the RH section numbers reused unmirrored
 *   (verified LH law: N = 8 + angle/22.5), so every left/right exit is wrong.
 * - Roundabout U-turn -> N16, whereas every other path shows the plain U-turn
 *   arrow (product decision).
 *
 * Applied only to an UNVERSIONED labels file; a model retrained with the fixed
 * script writes a `#labels-v2` header and bypasses this entirely. Delete once
 * a v2 model ships.
 */
object LegacyModelRemap {

    /** Header line written by the fixed training script. */
    const val V2_HEADER = "#labels-v2"

    private val MAP: Map<Int, TurnIcon> = mapOf(
        // RH: swap slight/sharp right.
        27 to TurnIcon.RAB_SECT_6_RH,   // trained "slight right" -> N6
        31 to TurnIcon.RAB_SECT_2_RH,   // trained "sharp right"  -> N2
        41 to TurnIcon.UTURN_RIGHT,     // clockwise roundabout U-turn
        // LH: mirror (trained with RH numbering).
        43 to TurnIcon.RAB_SECT_10_LH,  // slight right (+45)  -> N10
        45 to TurnIcon.RAB_SECT_12_LH,  // right (+90)         -> N12
        47 to TurnIcon.RAB_SECT_14_LH,  // sharp right (+135)  -> N14
        51 to TurnIcon.RAB_SECT_6_LH,   // slight left (-45)   -> N6
        53 to TurnIcon.RAB_SECT_4_LH,   // left (-90)          -> N4
        55 to TurnIcon.RAB_SECT_2_LH,   // sharp left (-135)   -> N2
        57 to TurnIcon.UTURN_LEFT,      // counter-clockwise roundabout U-turn
    )

    /** Correct a legacy-model output code; non-roundabout codes pass through. */
    fun correct(binary: Int): TurnIcon = MAP[binary] ?: TurnIcon.fromBinary(binary)
}
