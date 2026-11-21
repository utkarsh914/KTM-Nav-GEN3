package com.navigator.app.ui.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmColors
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

enum class PillTone { Connected, Connecting, Offline, NotPaired }

fun pillDotColor(tone: PillTone, c: KtmColors): Color = when (tone) {
    PillTone.Connected -> c.ok
    PillTone.Connecting -> c.warn
    PillTone.Offline -> c.warn
    PillTone.NotPaired -> c.info
}

/** The 48 dp pill that shows the bike's state and opens the Bike sheet. */
@Composable
fun BikeStatusPill(
    label: String,
    tone: PillTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier
            .shadow(KtmElevation.onMap, KtmShapes.Pill)
            .height(KtmLayout.targetRest)
            .clip(KtmShapes.Pill)
            .background(c.surf)
            .semantics { if (stateDescription != null) this.stateDescription = stateDescription }
            .clickable(role = Role.Button) { haptics.tap(); onClick() }
            .padding(start = 14.dp, end = KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Icon(KtmIcons.Bike, null, tint = c.fg, modifier = Modifier.size(KtmLayout.iconInline))
        Text(label, style = KtmTheme.type.buttonCompact, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.size(8.dp).background(pillDotColor(tone, c), CircleShape),
        )
    }
}

/** MR7: the pill folded down to the bike and its status dot, for the short landscape screen. The label stays for screen readers. */
@Composable
fun BikeStatusDot(
    label: String,
    tone: PillTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Box(
        modifier
            .size(KtmLayout.targetRest)
            .clip(CircleShape)
            .background(c.surf)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .clickable(role = Role.Button) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(KtmIcons.Bike, null, tint = c.fg, modifier = Modifier.size(KtmLayout.iconInline))
        Box(Modifier.align(Alignment.TopEnd).padding(KtmSpacing.xs).size(8.dp).background(pillDotColor(tone, c), CircleShape))
    }
}
