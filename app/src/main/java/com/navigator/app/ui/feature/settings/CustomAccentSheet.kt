package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.Hsv
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmColorPicker
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmTextField
import com.navigator.app.ui.theme.KtmDarkColors
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmLightColors
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.customAccent
import com.navigator.app.ui.theme.hexOf

/**
 * ST12: pick any colour with the shade panel and hue slider, or type its hex. A live check shows how text in the colour
 * will read; when the colour itself is too close to the background the app quietly shifts the text tone and says so.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomAccentSheet(initial: Color, dark: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val c = KtmTheme.colors
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    val start = Hsv.of(initial)
    var hue by rememberSaveable { mutableFloatStateOf(start.hue) }
    var sat by rememberSaveable { mutableFloatStateOf(start.sat) }
    var value by rememberSaveable { mutableFloatStateOf(start.value) }
    var hex by rememberSaveable { mutableStateOf(hexOf(initial).removePrefix("#")) }
    val color = Hsv(hue, sat, value).toColor()
    val valid = HexInput.parse(hex) != null
    val check = AccentCheck.of(color, c.bg, dark)
    val onDark = AccentCheck.of(color, KtmDarkColors.bg, true)
    val onLight = AccentCheck.of(color, KtmLightColors.bg, false)
    val scheme = customAccent(color).tones.forTheme(dark)
    val textTone = scheme.text
    // The lowest of the three: the one that matters when a colour is adjusted for only one theme.
    val ratio = "%.1f".format(minOf(check.textContrast, onDark.textContrast, onLight.textContrast))
    // What was typed is saved as typed: the HSV round trip can land one step off.
    val save = { HexInput.parse(hex)?.let { onSave(hexOf(it)) }; Unit }

    KtmModalSheet(onDismiss = onDismiss) {
        Column(Modifier.imePadding().padding(horizontal = KtmSpacing.lg), verticalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.custom_title), style = MaterialTheme.typography.headlineSmall, color = c.fg,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                KtmIconButton(KtmIcons.Close, stringResource(R.string.common_close), onDismiss)
            }
            KtmColorPicker(
                Hsv(hue, sat, value),
                onChange = { hue = it.hue; sat = it.sat; value = it.value; hex = hexOf(it.toColor()).removePrefix("#") },
                panelDescription = stringResource(R.string.custom_panel_cd), hueDescription = stringResource(R.string.custom_hue_cd),
            )
            // ST12: the hex field with the colour as a 56 dp square beside it.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmTextField(
                    hex, {
                        hex = HexInput.clean(it)
                        HexInput.parse(hex)?.let { parsed -> Hsv.of(parsed).let { h -> hue = h.hue; sat = h.sat; value = h.value } }
                    },
                    label = stringResource(R.string.custom_hex), modifier = Modifier.weight(1f),
                    error = if (valid) null else stringResource(R.string.custom_hex_error),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                )
                Box(Modifier.size(KtmLayout.targetPrimary).background(color, KtmShapes.Control).border(KtmLayout.hairline, c.line2, KtmShapes.Control))
            }
            if (check.adjusted || onDark.adjusted || onLight.adjusted) {
                Text(stringResource(R.string.custom_sample), style = MaterialTheme.typography.titleMedium, color = textTone)
                Text(stringResource(R.string.custom_adjusted, ratio), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    androidx.compose.material3.Icon(KtmIcons.Done, null, tint = c.okText, modifier = Modifier.size(KtmSpacing.lg))
                    Text(
                        stringResource(R.string.custom_ok_both, "%.1f".format(onDark.textContrast), "%.1f".format(onLight.textContrast)),
                        style = MaterialTheme.typography.bodySmall, color = c.okText,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(stringResource(R.string.common_cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Outline)
                // Filled with the chosen colour, so the rider sees what the app's buttons will be.
                Box(
                    Modifier.weight(1f).height(KtmLayout.targetPrimary)
                        .background(if (valid) scheme.fill else c.surf3, KtmShapes.Control)
                        .clickable(enabled = valid, role = Role.Button) { haptics.tap(); save() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.custom_save), style = MaterialTheme.typography.titleMedium, color = if (valid) scheme.onFill else c.fg3)
                }
            }
        }
    }
}
