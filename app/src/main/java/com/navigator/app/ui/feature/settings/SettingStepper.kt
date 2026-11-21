package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.data.settings.IntSpec
import com.navigator.app.ui.components.KtmStepper
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** A labelled stepper for one numeric setting: the range shows under it and the number opens the edit sheet. */
@Composable
fun SettingStepper(
    label: String,
    value: Int,
    spec: IntSpec,
    unit: String,
    onChange: (Int) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    Text(
        label, style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2,
        modifier = Modifier.padding(horizontal = KtmSpacing.lg).padding(top = KtmSpacing.sm, bottom = if (supporting == null) KtmSpacing.sm else 0.dp),
    )
    if (supporting != null) {
        Text(
            supporting, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg).padding(bottom = KtmSpacing.sm),
        )
    }
    KtmStepper(
        value = value, spec = spec, onValueChange = onChange, unit = unit,
        rangeText = stringResource(R.string.stepper_range, spec.min, spec.max, unit).trim(),
        decreaseDescription = stringResource(R.string.stepper_lower, label),
        increaseDescription = stringResource(R.string.stepper_raise, label),
        editDescription = stringResource(R.string.stepper_type, label),
        onEditValue = onEdit, modifier = modifier, enabled = enabled,
    )
}
