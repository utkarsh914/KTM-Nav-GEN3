package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog

/** DL5: forgetting the bike is a decision, so it asks first. */
@Composable
fun RePairDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    KtmDialog(
        title = stringResource(R.string.bike_repair_title),
        message = stringResource(R.string.bike_repair_message),
        onDismiss = onDismiss,
        confirm = DialogAction(stringResource(R.string.bike_sheet_repair), onConfirm, ButtonStyle.Primary),
        dismiss = DialogAction(stringResource(R.string.common_cancel), onDismiss),
    )
}
