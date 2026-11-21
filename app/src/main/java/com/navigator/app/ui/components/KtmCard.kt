package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** A flat container on surf with a hairline border (tone, not shadow). Holds no destructive actions. */
@Composable
fun KtmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Column(
        modifier
            .clip(KtmShapes.Card)
            .background(c.surf)
            .border(1.dp, c.line, KtmShapes.Card)
            .let { if (onClick != null) it.clickable(enabled = enabled, role = Role.Button) { haptics.tap(); onClick() } else it }
            .padding(KtmSpacing.lg),
        content = content,
    )
}
