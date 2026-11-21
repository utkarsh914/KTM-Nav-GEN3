package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingLines: Int = if (supporting == null) 0 else 1,
    /** Replaces [title] when set: for a name with the matched text in bold (see [highlightedText]). */
    annotatedTitle: AnnotatedString? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    hasThumbnail: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** A press-and-hold action (remove a recent place); also offered to screen readers as a custom action. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    /** A row that removes something: its title and icon are the danger colour. */
    destructive: Boolean = false,
    /** Something here needs fixing: the icon and the supporting text turn amber. */
    attention: Boolean = false,
    /** The title in the accent, semibold: an action that sets something up ("Set home"). */
    accentTitle: Boolean = false,
) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    val clickable = if (onClick != null) {
        Modifier.combinedClickable(
            role = Role.Button,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick?.let { long -> { haptics.tap(); long() } },
            onClick = { haptics.tap(); onClick() },
        )
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
            leadingIcon != null -> TonalIconBadge(leadingIcon, tint = if (destructive) c.bad else if (attention) c.warn else KtmTheme.accent.text)
        }
        Column(Modifier.weight(1f)) {
            Text(
                annotatedTitle ?: AnnotatedString(title),
                style = if (accentTitle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (destructive) c.bad else if (accentTitle) KtmTheme.accent.text else c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting, style = MaterialTheme.typography.bodySmall, color = if (attention) c.warn else c.fg2,
                    maxLines = supportingLines.coerceAtLeast(1), overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** The 40 dp tonal square that leads a row (an icon on the second container tone). */
@Composable
fun TonalIconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = KtmTheme.accent.text) {
    Box(
        modifier.size(KtmLayout.badgeTile).background(KtmTheme.colors.surf3, KtmShapes.Control),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(KtmLayout.iconMedium)) }
}

/** The 40 dp outlined circle that leads an empty slot (Home or Work not set yet): an outlined "+". */
@Composable
fun OutlinedIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.size(KtmLayout.badgeTile).border(1.dp, KtmTheme.colors.line2, KtmShapes.Pill),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = KtmTheme.accent.text, modifier = Modifier.size(KtmLayout.iconMedium)) }
}

/** A secondary value on the right of a row. */
@Composable
fun RowValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2, maxLines = 1)
}

/** Chevron marking a row that opens something. */
@Composable
fun RowChevron() {
    Icon(KtmIcons.ChevronRight, null, tint = KtmTheme.colors.fg3, modifier = Modifier.size(KtmLayout.iconMedium))
}

/** Divider inset to the text: past the leading icon, or ([withIcon] false, rows with no icon) flush with the row's own padding. */
@Composable
fun ListDivider(modifier: Modifier = Modifier, withIcon: Boolean = true) {
    HorizontalDivider(modifier.padding(start = if (withIcon) KtmLayout.dividerInset else KtmSpacing.lg), color = KtmTheme.colors.line)
}

/** [text] with the characters in [match] in bold; the style lives here so feature code sets none. */
fun highlightedText(text: String, match: IntRange?): AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        if (match == null) { append(text); return@buildAnnotatedString }
        append(text.substring(0, match.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(match.first, match.last + 1))
        }
        append(text.substring(match.last + 1))
    }
