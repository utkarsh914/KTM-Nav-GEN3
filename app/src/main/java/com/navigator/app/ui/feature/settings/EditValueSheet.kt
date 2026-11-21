package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.navigator.app.R
import com.navigator.app.data.settings.IntSpec
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.data.settings.SpecValidation
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmTextField
import com.navigator.app.ui.theme.KtmSpacing

/** The message under a number field for what was typed, or null when it is fine (or nothing was typed yet). */
object EditValueText {
    fun error(v: SpecValidation, typed: String, spec: IntSpec): UiText? = when (v) {
        is SpecValidation.Valid -> null
        SpecValidation.NotANumber -> if (typed.isBlank()) null else UiText.Res(R.string.edit_value_range, listOf(spec.min, spec.max))
        is SpecValidation.TooLow, is SpecValidation.TooHigh -> UiText.Res(R.string.edit_value_range, listOf(spec.min, spec.max))
    }
}

/**
 * DL9: type a number when stepping is too slow. The range is the setting's own ([spec]); an out-of-range or non-numeric
 * entry says so under the field and keeps Save off, rather than being silently ignored.
 */
@Composable
fun EditNumberSheet(title: String, initial: Int, spec: IntSpec, unit: String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val validation = spec.validate(text)
    val error = EditValueText.error(validation, text, spec)?.resolve()
    val save = { (validation as? SpecValidation.Valid)?.let { onSave(it.value) }; Unit }
    SheetFrame(title, onDismiss, canSave = validation is SpecValidation.Valid, onSave = save) { focus ->
        KtmTextField(
            text, { text = it.filter { c -> c in '0'..'9' }.take(MAX_DIGITS) }, label = unit, error = error,
            modifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
    }
}

/** DL9 for text: the rider's name. Blank is allowed (it clears the name). */
@Composable
fun EditTextSheet(title: String, label: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val save = { onSave(text.trim()) }
    SheetFrame(title, onDismiss, canSave = true, onSave = save) { focus ->
        KtmTextField(
            text, { text = it.take(SettingSpecs.MAX_USER_NAME) }, label = label, modifier = Modifier.focusRequester(focus),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(title: String, onDismiss: () -> Unit, canSave: Boolean, onSave: () -> Unit, field: @Composable (FocusRequester) -> Unit) {
    val focus = remember { FocusRequester() }
    KtmModalSheet(onDismiss = onDismiss, title = title) {
        // Inside the sheet: the field exists only once the sheet's content is composed.
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            field(focus)
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(stringResource(R.string.edit_value_cancel), onDismiss, Modifier.weight(1f), style = ButtonStyle.Outline)
                KtmButton(stringResource(R.string.edit_value_save), onSave, Modifier.weight(1f), enabled = canSave)
            }
        }
    }
}

private const val MAX_DIGITS = 9
