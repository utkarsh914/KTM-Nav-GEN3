package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
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
    /** The radio leads the row (a sheet of choices) instead of trailing it (a setting). */
    radioAtStart: Boolean = false,
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
        leading = if (radioAtStart) ({ KtmRadio(selected, enabled) }) else null,
        trailing = if (radioAtStart) null else ({ KtmRadio(selected, enabled) }),
    )
}

/** One of a few big choices as a card (ST3 engine): the radio leads, the chosen card is tinted and outlined in the accent. */
@Composable
fun KtmChoiceCard(
    title: String,
    supporting: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val a = KtmTheme.accent
    Row(
        modifier.fillMaxWidth().clip(KtmShapes.Card)
            .background(if (selected) a.container else KtmTheme.colors.surf)
            .border(if (selected) KtmLayout.outline else KtmLayout.hairline, if (selected) a.fill else KtmTheme.colors.line, KtmShapes.Card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.toggle(true); onSelect() })
            .heightIn(min = KtmLayout.targetPrimary).padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        KtmRadio(selected)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = KtmTheme.colors.fg)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = if (selected) a.onContainer else KtmTheme.colors.fg2)
        }
    }
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
