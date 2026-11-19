package com.navigator.app.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** The accent switch, with the toggle haptic. [onCheckedChange] null makes it display-only (a row owns the tap). */
@Composable
fun KtmSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change -> { on -> haptics.toggle(on); change(on) } },
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = a.onFill, checkedTrackColor = a.fill, checkedBorderColor = a.fill,
            uncheckedThumbColor = c.fg2, uncheckedTrackColor = c.surf3, uncheckedBorderColor = c.line2,
            disabledCheckedThumbColor = c.fg3, disabledCheckedTrackColor = c.surf3, disabledCheckedBorderColor = c.line,
            disabledUncheckedThumbColor = c.fg3, disabledUncheckedTrackColor = c.surf2, disabledUncheckedBorderColor = c.line,
        ),
    )
}
