package com.navigator.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** Two to four distinct options, where a picker dialog used to be. The selection is shown by the container, not a tick. */
@Composable
fun <T> KtmSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.size in 2..4) { "A segmented control holds 2 to 4 options, got ${options.size}" }
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = a.container, activeContentColor = a.onContainer, activeBorderColor = a.container,
        inactiveContainerColor = c.surf, inactiveContentColor = c.fg, inactiveBorderColor = c.line2,
        disabledActiveContainerColor = c.surf3, disabledActiveContentColor = c.fg3, disabledActiveBorderColor = c.line,
        disabledInactiveContainerColor = c.surf, disabledInactiveContentColor = c.fg3, disabledInactiveBorderColor = c.line,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { haptics.toggle(true); onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size, KtmShapes.Control),
                colors = colors,
                enabled = enabled,
                border = BorderStroke(1.dp, if (option == selected) a.container else c.line2),
                icon = {},
                label = { Text(label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
            )
        }
    }
}
