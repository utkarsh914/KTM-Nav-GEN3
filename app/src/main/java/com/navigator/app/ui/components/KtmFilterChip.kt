package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** Filter or category chip: 44 dp tall inside a 48 dp touch target. Selected = accent container. */
@Composable
fun KtmFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    /** A riding surface: a 56 dp target for a glove. */
    riding: Boolean = false,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val content = if (selected) a.onContainer else c.fg
    Box(
        modifier.heightIn(min = if (riding) KtmLayout.targetPrimary else KtmLayout.targetRest)
            .toggleable(value = selected, role = Role.Checkbox) { haptics.toggle(it); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(if (riding) KtmLayout.targetPrimary - 4.dp else 44.dp)
                .clip(KtmShapes.Control)
                .background(if (selected) a.container else Color.Transparent, KtmShapes.Control)
                .let { if (selected) it else it.border(1.dp, c.line2, KtmShapes.Control) }
                .padding(horizontal = KtmSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, tint = content, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        }
    }
}
