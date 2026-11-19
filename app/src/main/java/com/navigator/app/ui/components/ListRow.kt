package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** Row height rule: 56 one line, 64 two lines, 80 three lines, 72 with a thumbnail or large leading. No other heights. */
fun listRowHeight(supportingLines: Int, hasThumbnail: Boolean = false): Dp = when {
    hasThumbnail -> KtmLayout.rowThumb
    supportingLines <= 0 -> KtmLayout.row1
    supportingLines == 1 -> KtmLayout.row2
    else -> KtmLayout.row3
}

/**
 * The one list row: leading tonal icon (or any [leading] content), title, supporting text and a
 * trailing slot (value, switch, chevron). Tappable when [onClick] is set.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingLines: Int = if (supporting == null) 0 else 1,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    hasThumbnail: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button) { haptics.tap(); onClick() }
    } else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = listRowHeight(supportingLines, hasThumbnail))
            .padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg),
    ) {
        when {
            leading != null -> leading()
            leadingIcon != null -> TonalIconBadge(leadingIcon)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting, style = MaterialTheme.typography.bodySmall, color = c.fg2,
                    maxLines = supportingLines.coerceAtLeast(1), overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** The 40 dp tonal square that leads a row (an icon on the second container tone). */
@Composable
fun TonalIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).background(KtmTheme.colors.surf3, KtmShapes.Control),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = KtmTheme.accent.text, modifier = Modifier.size(24.dp)) }
}

/** A secondary value on the right of a row. */
@Composable
fun RowValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2, maxLines = 1)
}

/** Chevron marking a row that opens something. */
@Composable
fun RowChevron() {
    Icon(KtmIcons.ChevronRight, null, tint = KtmTheme.colors.fg3, modifier = Modifier.size(24.dp))
}

/** Divider inset to the text, past the leading icon. */
@Composable
fun ListDivider(modifier: Modifier = Modifier, inset: Dp = KtmLayout.dividerInset) {
    HorizontalDivider(modifier.padding(start = inset), color = KtmTheme.colors.line)
}
