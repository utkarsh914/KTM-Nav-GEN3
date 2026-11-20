package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import com.navigator.app.R
import com.navigator.app.ui.theme.AccentPalettes
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RainbowHint
import com.navigator.app.ui.theme.accentFor
import com.navigator.app.ui.theme.rememberHaptics

/** ST6: the six presets and Custom on one row; 40 dp swatches in 48 dp touch areas. A null id is the KTM orange. */
@Composable
fun AccentSwatches(selectedId: String?, onSelect: (String?) -> Unit, onCustom: () -> Unit, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val custom = AccentNames.isCustom(selectedId)
    val current = accentFor(selectedId).id
    Row(modifier.fillMaxWidth().padding(horizontal = KtmSpacing.sm).selectableGroup(), horizontalArrangement = Arrangement.Center) {
        AccentPalettes.forEach { option ->
            val name = stringResource(AccentNames.res(option.id))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Swatch(
                description = name, selected = !custom && current == option.id, onClick = { onSelect(if (option.id == AccentPalettes.first().id) null else option.id) },
                fill = SolidColor(option.tones.fill), mark = option.tones.onFill,
            )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Swatch(
            description = stringResource(R.string.look_accent_custom_swatch), selected = custom, onClick = onCustom,
            fill = if (custom) SolidColor(accentFor(selectedId).tones.fill) else Brush.sweepGradient(RainbowHint),
            mark = if (custom) accentFor(selectedId).tones.onFill else c.fg,
        )
        }
    }
}

@Composable
private fun Swatch(description: String, selected: Boolean, onClick: () -> Unit, fill: Brush, mark: Color) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Box(
        Modifier.size(KtmLayout.targetRest).clip(CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = { haptics.tap(); onClick() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(KtmLayout.swatch).background(fill, CircleShape)
                .border(if (selected) KtmLayout.swatchRing else KtmLayout.hairline, if (selected) c.fg else c.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(KtmIcons.Done, null, tint = mark)
        }
    }
}
