package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/**
 * A 56 dp control floating over the map (recenter, compass, overview). The only controls with a
 * shadow. [accent] fills it with the accent (Recenter once the rider has panned); [rotationDegrees]
 * turns the glyph (compass).
 */
@Composable
fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    rotationDegrees: Float = 0f,
    /** False for a glyph that carries its own colours (the compass's red north needle). */
    tinted: Boolean = true,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val container = if (accent) a.fill else c.surf
    val content = if (accent) a.onFill else c.fg
    Box(
        modifier = modifier
            .size(KtmLayout.targetPrimary)
            .shadow(KtmElevation.onMap, KtmShapes.Card)
            .clip(KtmShapes.Card)
            .background(container)
            .let { if (accent) it else it.border(1.dp, c.line, KtmShapes.Card) }
            .clickable(role = Role.Button, indication = ripple(color = content), interactionSource = null) {
                haptics.tap()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = if (tinted) content else Color.Unspecified, modifier = Modifier.size(KtmLayout.iconMedium).rotate(rotationDegrees)) }
}
