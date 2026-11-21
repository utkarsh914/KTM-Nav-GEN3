package com.navigator.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** A [ListRow] with a checkbox: the whole row toggles. */
@Composable
fun KtmCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingLines: Int = if (supporting == null) 0 else 1,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    ListRow(
        title = title,
        supporting = supporting,
        supportingLines = supportingLines,
        modifier = modifier.fillMaxWidth().toggleable(
            value = checked, enabled = enabled, role = Role.Checkbox,
            onValueChange = { haptics.toggle(it); onCheckedChange(it) },
        ),
        trailing = { KtmCheckIndicator(checked, enabled) },
    )
}

/** The checkbox itself, for a row that toggles as a whole (the row carries the semantics). */
@Composable
fun KtmCheckIndicator(checked: Boolean, enabled: Boolean = true) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    Checkbox(
        checked = checked, onCheckedChange = null, enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = a.fill, checkmarkColor = a.onFill, uncheckedColor = c.line2,
            disabledCheckedColor = c.surf3, disabledUncheckedColor = c.line,
        ),
    )
}

/** A [ListRow] with a radio button: the whole row selects. */
@Composable
fun KtmRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingLines: Int = if (supporting == null) 0 else 1,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    ListRow(
        title = title,
        supporting = supporting,
        supportingLines = supportingLines,
        modifier = modifier.fillMaxWidth().selectable(
            selected = selected, enabled = enabled, role = Role.RadioButton,
            onClick = { haptics.toggle(true); onSelect() },
        ),
        trailing = { KtmRadio(selected, enabled) },
    )
}

/** The radio mark on its own, for a row that is selectable as a whole. */
@Composable
fun KtmRadio(selected: Boolean, enabled: Boolean = true) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    RadioButton(
        selected = selected, onClick = null, enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = a.fill, unselectedColor = c.line2, disabledSelectedColor = c.surf3, disabledUnselectedColor = c.line,
        ),
    )
}
