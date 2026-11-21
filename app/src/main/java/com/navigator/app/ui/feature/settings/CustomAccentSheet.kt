package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.ImeAction
import com.navigator.app.R
import com.navigator.app.ui.components.Hsv
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmColorPicker
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmTextField
import com.navigator.app.ui.theme.KtmLayout
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
    val start = Hsv.of(initial)
    var hue by rememberSaveable { mutableFloatStateOf(start.hue) }
    var sat by rememberSaveable { mutableFloatStateOf(start.sat) }
    var value by rememberSaveable { mutableFloatStateOf(start.value) }
    var hex by rememberSaveable { mutableStateOf(hexOf(initial).removePrefix("#")) }
    val color = Hsv(hue, sat, value).toColor()
    val valid = HexInput.parse(hex) != null
    val check = AccentCheck.of(color, c.bg, dark)
    val textTone = customAccent(color).tones.forTheme(dark).text
    val ratio = "%.1f".format(check.textContrast)
    // What was typed is saved as typed: the HSV round trip can land one step off.
    val save = { HexInput.parse(hex)?.let { onSave(hexOf(it)) }; Unit }

    KtmModalSheet(onDismiss = onDismiss, title = stringResource(R.string.custom_title)) {
        Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            KtmColorPicker(
                Hsv(hue, sat, value),
                onChange = { hue = it.hue; sat = it.sat; value = it.value; hex = hexOf(it.toColor()).removePrefix("#") },
                panelDescription = stringResource(R.string.custom_panel_cd), hueDescription = stringResource(R.string.custom_hue_cd),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                Box(Modifier.size(KtmLayout.targetPrimary).background(color, CircleShape).border(KtmLayout.hairline, c.line2, CircleShape))
                KtmTextField(
                    hex, {
                        hex = HexInput.clean(it)
                        HexInput.parse(hex)?.let { parsed -> Hsv.of(parsed).let { h -> hue = h.hue; sat = h.sat; value = h.value } }
                    },
                    label = stringResource(R.string.custom_hex), modifier = Modifier.weight(1f),
                    error = if (valid) null else stringResource(R.string.custom_hex_error),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
            Text(stringResource(R.string.custom_sample), style = MaterialTheme.typography.titleMedium, color = textTone)
            Text(
                stringResource(if (check.adjusted) R.string.custom_adjusted else R.string.custom_ok, ratio),
                style = MaterialTheme.typography.bodySmall, color = c.fg2,
            )
            KtmButton(stringResource(R.string.custom_save), { save() }, Modifier.fillMaxWidth(), enabled = valid)
        }
    }
}
