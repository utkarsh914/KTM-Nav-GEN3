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
 * DL3: a trip is running and another is being started. Keeping the current one is the safe choice (first, and
 * what Back and outside taps do); "Add as a stop instead" turns the new place into a stop of the running trip.
 * [current] names the running trip's destination, when known.
 */
@Composable
fun ReplaceTripDialog(
    destination: String?,
    onReplace: () -> Unit,
    onKeep: () -> Unit,
    onAddAsStop: () -> Unit,
    current: String? = null,
    riding: Boolean = false,
) {
    KtmDialog(
        title = stringResource(R.string.replace_title),
        message = if (current != null && destination != null) stringResource(R.string.replace_message, current, destination)
        else stringResource(R.string.replace_message_generic),
        onDismiss = onKeep,
        confirm = DialogAction(stringResource(R.string.replace_confirm), onReplace, ButtonStyle.Primary),
        dismiss = DialogAction(stringResource(R.string.replace_keep), onKeep, ButtonStyle.Neutral),
        riding = riding,
        stacked = true,
        footer = {
            KtmButton(
                stringResource(R.string.replace_add_stop), onAddAsStop, Modifier.fillMaxWidth(),
                style = ButtonStyle.Text, size = if (riding) ButtonSize.Riding else ButtonSize.Small,
            )
        },
    )
}
