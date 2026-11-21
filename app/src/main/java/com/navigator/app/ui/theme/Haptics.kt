package com.navigator.app.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * App-wide haptic vocabulary — one place so every tap/toggle/confirm feels the
 * same. Built on the platform [View.performHapticFeedback] (stable across
 * Compose versions).
 *
 * Usage:
 * ```
 * val haptics = rememberHaptics()
 * Modifier.clickable { haptics.tap(); onClick() }
 * ```
 * The shared components already call these, so most screens
 * get consistent haptics for free.
 */
class Haptics(private val view: View) {

    private fun perform(constant: Int) {
        runCatching {
            // The system's own haptics setting decides; the view's flag is on by default.
            view.performHapticFeedback(constant)
        }
    }

    /** Light tick for a normal tap on a button/row/icon. */
    fun tap() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Toggle switched on/off: the system's own on and off feedback. */
    fun toggle(on: Boolean) = perform(
        if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF,
    )

    /** Positive confirmation (accept / save / start). */
    fun confirm() = perform(
        HapticFeedbackConstants.CONFIRM,
    )

    /** Destructive or blocked action (delete, end trip, a tap that can't proceed). */
    fun reject() = perform(HapticFeedbackConstants.REJECT)

    /** Stepper value change and sheet detent snap. */
    fun segmentTick() = perform(HapticFeedbackConstants.SEGMENT_TICK)

    /** Fine ticks while dragging a seek bar or clock. */
    fun clockTick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Long-press recognised (enter multi-select, drop a pin). */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
