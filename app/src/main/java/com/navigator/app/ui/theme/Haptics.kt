package com.navigator.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * App-wide haptic vocabulary — one place so every tap/toggle/confirm feels the
 * same. Built on the platform [View.performHapticFeedback] (stable across
 * Compose versions) with graceful fallbacks on older API levels.
 *
 * Usage:
 * ```
 * val haptics = rememberHaptics()
 * Modifier.clickable { haptics.tap(); onClick() }
 * ```
 * The shared components in `KtmComponents` already call these, so most screens
 * get consistent haptics for free.
 */
class Haptics(private val view: View) {

    private fun perform(constant: Int) {
        runCatching {
            view.performHapticFeedback(
                constant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        }
    }

    /** Light tick for a normal tap on a button/row/icon. */
    fun tap() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Toggle switched on/off — a crisp tick either way. */
    fun toggle(on: Boolean) = perform(
        if (on) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK,
    )

    /** Positive confirmation (accept / save / start). */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK,
    )

    /** Destructive / rejecting action (delete, close, end navigation). */
    fun warn() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
    )

    /** Long-press recognised (enter multi-select, drop a pin). */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
