package com.navigator.app.ui.components

import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon

/**
 * Maps each dash [TurnIcon] to the **official KTM Gen-3 app's** own maneuver
 * icon, ported byte-for-byte from the decompiled `KTMconnect` app's
 * `direction_*` vector drawables (path data lifted from its `strings.xml`).
 * The maneuver→icon correspondence follows the official app's own mapping
 * (`com.ktm.bikeapp.util.tbt` : EasyRight→LIGHT, Right→QUITE, SharpRight→HEAVY,
 * roundabout-by-exit→RAB_SECT_n, ExitRight→LEAVE_HIGHWAY, …), so these are the
 * authoritative KTM glyphs rather than our own hand-drawn approximations.
 *
 * Caveat: the bike **dash firmware** renders some codes more generically than
 * the phone app does (e.g. every roundabout section shows as one roundabout
 * sign, and QUITE reads visually slighter than the phone's 90° glyph). These
 * are the phone app's icons - the closest official reference available without
 * the firmware's own glyph assets. Returns null for codes with no direct
 * official icon, where the caller falls back to the drawn [TurnIconGlyph].
 */
object TurnIconArt {

    fun drawableFor(icon: TurnIcon): Int? = when (icon) {
        TurnIcon.GO_STRAIGHT -> R.drawable.dash_go_straight
        TurnIcon.CHANGE_LINE -> R.drawable.dash_go_straight
        TurnIcon.KEEP_MIDDLE -> R.drawable.dash_go_straight
        // "Start of ride" / heading off - the walking-person glyph reads as
        // "begin", and is what the connect greeting uses too.
        TurnIcon.START, TurnIcon.HEAD_TO -> R.drawable.dash_pedestrian

        TurnIcon.LIGHT_RIGHT -> R.drawable.dash_turn_45_right
        TurnIcon.LIGHT_LEFT -> R.drawable.dash_turn_45_left
        TurnIcon.QUITE_RIGHT -> R.drawable.dash_turn_90_right
        TurnIcon.QUITE_LEFT -> R.drawable.dash_turn_90_left
        TurnIcon.HEAVY_RIGHT -> R.drawable.dash_turn_135_right
        TurnIcon.HEAVY_LEFT -> R.drawable.dash_turn_135_left

        TurnIcon.UTURN_RIGHT -> R.drawable.dash_turn_180_right
        TurnIcon.UTURN_LEFT -> R.drawable.dash_turn_180_left

        TurnIcon.KEEP_RIGHT, TurnIcon.HIGHWAY_KEEP_RIGHT -> R.drawable.dash_keep_right
        TurnIcon.KEEP_LEFT, TurnIcon.HIGHWAY_KEEP_LEFT -> R.drawable.dash_keep_left

        // Enter-highway previews are hand-drawn to resemble what the dash draws
        // (verified from photos): 14 = ramp on the right bending left, 15 = ramp
        // on the left bending right.
        TurnIcon.ENTER_HIGHWAY_RIGHT_LANE -> R.drawable.dash_highway_right
        TurnIcon.ENTER_HIGHWAY_LEFT_LANE -> R.drawable.dash_highway_left
        TurnIcon.LEAVE_HIGHWAY_RIGHT_LANE -> R.drawable.dash_exit_right
        TurnIcon.LEAVE_HIGHWAY_LEFT_LANE -> R.drawable.dash_exit_left

        TurnIcon.END, TurnIcon.PASS_STATION -> R.drawable.dash_finish
        TurnIcon.FERRY -> R.drawable.dash_ferry

        else -> when {
            // Roundabout sections get the official KTM per-exit-angle glyph
            // (`direction_round_45..360` and their `_left` mirrors), so each
            // section shows a proper roundabout logo with the exit at a distinct
            // angle instead of one generic sign.
            icon.name.startsWith("RAB_SECT") || icon.name.startsWith("ROUNDABOUT") -> roundDrawable(icon.name)
            else -> null // UNKNOWN / UNDEFINED -> drawn fallback
        }
    }

    /**
     * Pick the roundabout glyph for a RAB_SECT_<n>_RH/LH or ROUNDABOUT_<n>(_LH)
     * code. The official app has 8 exit-angle icons per side, drawn by the angle
     * you **sweep around the circle** before exiting: 45°..360° in 45° steps.
     *
     * This lines up with the dash's hardware-verified section law (see
     * [com.navigator.app.nav.ktm.KtmManeuverMapping.roundaboutSection]): on the
     * RH (clockwise) block N=1 is the sharpest right … N=8 straight-through …
     * N=16 a U-turn, i.e. sweep ≈ N·22.5°. Two sections map to each 45° glyph, so
     * `idx = (N-1)/2` puts N=4 (90° right) → 90° glyph, N=8 (straight) → 180°,
     * N=12 (left) → 270°, N=16 (U-turn) → 360°. `_LH` = left-hand-traffic
     * (mirrored) and uses the mirrored glyph set.
     */
    private fun roundDrawable(name: String): Int {
        val n = Regex("(\\d+)").find(name)?.value?.toIntOrNull() ?: 2
        val left = name.endsWith("_LH")
        // sections 1..16 -> one of 8 sweep-angle glyphs (two sections per step).
        val idx = ((n - 1) / 2).coerceIn(0, 7)
        return if (left) LEFT_ROUND[idx] else RIGHT_ROUND[idx]
    }

    private val RIGHT_ROUND = intArrayOf(
        R.drawable.dash_round_45_right, R.drawable.dash_round_90_right,
        R.drawable.dash_round_135_right, R.drawable.dash_round_180_right,
        R.drawable.dash_round_225_right, R.drawable.dash_round_270_right,
        R.drawable.dash_round_315_right, R.drawable.dash_round_360_right,
    )
    private val LEFT_ROUND = intArrayOf(
        R.drawable.dash_round_45_left, R.drawable.dash_round_90_left,
        R.drawable.dash_round_135_left, R.drawable.dash_round_180_left,
        R.drawable.dash_round_225_left, R.drawable.dash_round_270_left,
        R.drawable.dash_round_315_left, R.drawable.dash_round_360_left,
    )
}
