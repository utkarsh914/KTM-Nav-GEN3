package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.TonalIconBadge
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** What the status block of the Bike sheet says. */
data class BikeStatusText(val icon: ImageVector, @androidx.annotation.StringRes val title: Int, val arg: Int?, @androidx.annotation.StringRes val hint: Int?)

object BikeSheetMapper {
    fun status(status: BikeStatus): BikeStatusText = when (status) {
        is BikeStatus.Connected -> BikeStatusText(KtmIcons.Bike, R.string.bike_sheet_connected, null, null)
        is BikeStatus.Connecting -> BikeStatusText(
            KtmIcons.Bluetooth,
            if (status.firstPair) R.string.bike_sheet_connecting_first else R.string.bike_sheet_connecting, null, null,
        )
        is BikeStatus.Reconnecting -> BikeStatusText(KtmIcons.Bluetooth, R.string.bike_sheet_reconnecting, status.attempt, null)
        is BikeStatus.NeedsRepair -> BikeStatusText(KtmIcons.Warning, R.string.bike_sheet_cant_connect, null, R.string.bike_sheet_cant_connect_hint)
        BikeStatus.BluetoothOff -> BikeStatusText(KtmIcons.BluetoothOff, R.string.bike_sheet_bluetooth_off, null, R.string.bike_sheet_bluetooth_off_hint)
        is BikeStatus.Disconnected, BikeStatus.NotPaired ->
            BikeStatusText(KtmIcons.Bluetooth, R.string.bike_sheet_disconnected, null, R.string.bike_sheet_disconnected_hint)
    }
}

/**
 * "Is my dash OK?" in one place: status in words, what the dash was last told, the two riding
 * toggles and a test send. Re-pair is the primary action only when the app thinks the keys are lost.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BikeSheet(
    state: NavigateUiState,
    dashTime: String,
    onBeeps: (Boolean) -> Unit,
    onMessages: (Boolean) -> Unit,
    onSendTest: (message: String) -> Unit,
    onRePair: () -> Unit,
    onDismiss: () -> Unit,
    /** A trip is guiding (also while riding on screen): the test turn would replace its guidance on the dash. */
    tripRunning: Boolean = state.activity != null,
) {
    var confirmRePair by remember { mutableStateOf(false) }
    val status = state.bikeStatus
    val connected = status is BikeStatus.Connected
    val text = BikeSheetMapper.status(status)
    val name = state.bike.labelArg ?: stringResource(R.string.bike_sheet_title)
    val title = stringResource(text.title, text.arg ?: 0)
    val testMessage = stringResource(
        R.string.bike_test_message,
        state.userName.ifBlank { stringResource(R.string.bike_test_name_fallback) },
    )
    val idle = DashPreviewState.NotConnected(stringResource(R.string.dash_preview_idle_title), stringResource(R.string.dash_preview_idle_hint))
    val offline = DashPreviewState.NotConnected(stringResource(R.string.dash_preview_offline_title), stringResource(R.string.dash_preview_offline_hint))
    val preview = DashPreviewMapper.map(state.dash, connected, dashTime, idle, offline, stringResource(R.string.dash_preview_banner_app))

    KtmModalSheet(
        onDismiss = onDismiss,
        title = name,
        actions = {
            if (status is BikeStatus.NeedsRepair) {
                KtmButton(stringResource(R.string.bike_sheet_repair), { confirmRePair = true }, Modifier.fillMaxWidth())
            } else {
                // The test turn would replace the live guidance on the dash.
                if (tripRunning) {
                    Text(
                        stringResource(R.string.bike_sheet_test_on_trip), style = MaterialTheme.typography.bodyMedium,
                        color = KtmTheme.colors.fg2, modifier = Modifier.padding(bottom = KtmSpacing.sm),
                    )
                }
                KtmButton(
                    stringResource(R.string.bike_sheet_test), { onSendTest(testMessage) }, Modifier.fillMaxWidth(),
                    style = ButtonStyle.Tonal, enabled = connected && !tripRunning,
                )
            }
        },
    ) {
        ListRow(
            title = title,
            supporting = text.hint?.let { stringResource(it) },
            leading = { TonalIconBadge(text.icon) },
        )
        Spacer(Modifier.height(KtmSpacing.md))
        DashPreview(preview)
        Spacer(Modifier.height(KtmSpacing.md))
        Column {
            KtmSwitchRow(
                stringResource(R.string.bike_sheet_beeps), state.beepsOn, onBeeps,
                supporting = stringResource(R.string.bike_sheet_beeps_hint), leadingIcon = KtmIcons.Sound,
            )
            KtmSwitchRow(
                stringResource(R.string.bike_sheet_messages), state.messagesOn, onMessages,
                supporting = stringResource(R.string.bike_sheet_messages_hint), leadingIcon = KtmIcons.Message,
            )
        }
    }

    if (confirmRePair) {
        KtmDialog(
            title = stringResource(R.string.bike_repair_title),
            message = stringResource(R.string.bike_repair_message),
            onDismiss = { confirmRePair = false },
            confirm = DialogAction(stringResource(R.string.bike_sheet_repair), { confirmRePair = false; onRePair() }, ButtonStyle.Primary),
            dismiss = DialogAction(stringResource(R.string.common_cancel), { confirmRePair = false }),
        )
    }
}
