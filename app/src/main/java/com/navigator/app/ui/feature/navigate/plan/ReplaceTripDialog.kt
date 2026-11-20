package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmDialog

/**
 * DL3: a trip is running and another is being started. Keeping the current one is the safe choice (back and
 * outside taps do it); "Add as a stop instead" turns the new place into a stop of the running trip.
 */
@Composable
fun ReplaceTripDialog(
    destination: String?,
    onReplace: () -> Unit,
    onKeep: () -> Unit,
    onAddAsStop: () -> Unit,
    riding: Boolean = false,
) {
    KtmDialog(
        title = stringResource(R.string.replace_title),
        message = if (destination != null) stringResource(R.string.replace_message, destination) else stringResource(R.string.replace_message_generic),
        onDismiss = onKeep,
        confirm = DialogAction(stringResource(R.string.replace_confirm), onReplace, ButtonStyle.Danger),
        dismiss = DialogAction(stringResource(R.string.replace_keep), onKeep, ButtonStyle.Neutral),
        riding = riding,
    ) {
        KtmButton(
            stringResource(R.string.replace_add_stop), onAddAsStop, Modifier.fillMaxWidth(),
            style = ButtonStyle.Outline, size = if (riding) ButtonSize.Riding else ButtonSize.Rest,
        )
    }
}
