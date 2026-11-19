package com.navigator.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme

/**
 * Text field: 13 sp label, a helper line below, and an inline [error] (which replaces the helper and
 * turns the field red). Validation messages belong here, never in a toast.
 */
@Composable
fun KtmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.fg, unfocusedTextColor = c.fg, disabledTextColor = c.fg3, errorTextColor = c.fg,
        focusedContainerColor = c.surf2, unfocusedContainerColor = c.surf2, disabledContainerColor = c.surf2, errorContainerColor = c.surf2,
        cursorColor = a.text, errorCursorColor = c.bad,
        focusedBorderColor = a.text, unfocusedBorderColor = c.line2, disabledBorderColor = c.line, errorBorderColor = c.bad,
        focusedLabelColor = a.text, unfocusedLabelColor = c.fg2, disabledLabelColor = c.fg3, errorLabelColor = c.bad,
        focusedPlaceholderColor = c.fg3, unfocusedPlaceholderColor = c.fg3,
        focusedLeadingIconColor = c.fg2, unfocusedLeadingIconColor = c.fg2,
        focusedSupportingTextColor = c.fg2, unfocusedSupportingTextColor = c.fg2, errorSupportingTextColor = c.bad,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { if (error != null) error(error) },
        enabled = enabled,
        singleLine = singleLine,
        isError = error != null,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        leadingIcon = leadingIcon?.let { { Icon(it, null) } },
        supportingText = (error ?: helper)?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        shape = KtmShapes.Control,
        colors = colors,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
