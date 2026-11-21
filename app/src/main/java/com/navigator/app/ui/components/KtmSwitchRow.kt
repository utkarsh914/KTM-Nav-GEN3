package com.navigator.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.rememberHaptics

/** A [ListRow] with a switch: the whole row toggles. */
@Composable
fun KtmSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingLines: Int = if (supporting == null) 0 else 1,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    /** A small neutral badge before the switch ("Debug"). */
    badge: String? = null,
) {
    val haptics = rememberHaptics()
    ListRow(
        title = title,
        supporting = supporting,
        supportingLines = supportingLines,
        leadingIcon = leadingIcon,
        modifier = modifier.fillMaxWidth().toggleable(
            value = checked, enabled = enabled, role = Role.Switch,
            onValueChange = { haptics.toggle(it); onCheckedChange(it) },
        ),
        trailing = {
            if (badge != null) {
                KtmBadge(badge, tone = BadgeTone.Neutral)
                Spacer(Modifier.width(KtmSpacing.sm))
            }
            KtmSwitch(checked, onCheckedChange = null, enabled = enabled)
        },
    )
}
