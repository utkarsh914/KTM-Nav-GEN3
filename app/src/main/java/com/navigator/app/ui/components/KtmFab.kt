package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** The tab's one floating action: a 56 dp accent pill with an icon and a label (Record, Add place). */
@Composable
fun KtmFab(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    Row(
        modifier.shadow(KtmElevation.sheet, KtmShapes.Card).height(KtmLayout.targetPrimary).clip(KtmShapes.Card).background(a.fill)
            .clickable(role = Role.Button) { haptics.tap(); onClick() }
            .padding(start = KtmSpacing.lg, end = KtmLayout.buttonPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Icon(icon, null, tint = a.onFill, modifier = Modifier.size(KtmLayout.iconMedium))
        // The mock's `.r2 .fab`: condensed 700, 18/22, .3 spacing.
        Text(label, style = KtmTheme.type.buttonCompact.copy(fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.3.sp), color = a.onFill, maxLines = 1)
    }
}
