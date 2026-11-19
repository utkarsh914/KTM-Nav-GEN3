package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

enum class IconButtonStyle { Plain, Tonal, Accent }

/** 48 dp at rest (12 dp corners), 56 dp on the map (16 dp corners). */
enum class IconButtonSize(val box: Dp) { Rest(KtmLayout.targetRest), Map(KtmLayout.targetPrimary) }

/** Icon-only button. [contentDescription] is required: there is no label to fall back on. [circular] is for round actions in rows. */
@Composable
fun KtmIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Plain,
    size: IconButtonSize = IconButtonSize.Rest,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    circular: Boolean = false,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val container = when {
        style == IconButtonStyle.Tonal -> c.surf3
        style == IconButtonStyle.Accent -> a.fill
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> c.fg3
        tint != Color.Unspecified -> tint
        style == IconButtonStyle.Accent -> a.onFill
        else -> c.fg
    }
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(size.box)
            .clip(if (circular) KtmShapes.Pill else if (size == IconButtonSize.Map) KtmShapes.Card else KtmShapes.Control)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, indication = ripple(color = content), interactionSource = null) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = content, modifier = Modifier.size(24.dp)) }
}
