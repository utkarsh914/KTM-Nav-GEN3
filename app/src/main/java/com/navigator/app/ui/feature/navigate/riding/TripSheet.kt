package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class TripSheetActions(
    val onAddStop: () -> Unit,
    val onOverview: () -> Unit,
    val onMinimise: () -> Unit,
    val onBeeps: (Boolean) -> Unit,
    val onMessages: (Boolean) -> Unit,
    val onDash: () -> Unit,
    val onRemoveNextStop: () -> Unit,
    val onEnd: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * NV7: everything the riding screen no longer carries as buttons, shown in the sheet the trip bar grows into
 * ([TripBarSheet]) and in the landscape controls panel: Add stop, Overview and Minimise are large tiles; the two riding
 * toggles are 56 dp rows; the dash row opens the bike screen; End trip is a danger-tonal 64 dp button set apart from
 * the rest and asks first (DL2).
 */

/** End trip: danger-tonal, 64 dp, set apart from the rest; it asks first. */
@Composable
fun EndTripButton(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    KtmButton(
        stringResource(R.string.trip_end), onEnd, modifier.fillMaxWidth(),
        style = ButtonStyle.DangerTonal, size = ButtonSize.Riding, icon = KtmIcons.Stop,
    )
}

/** What the trip sheet and the landscape controls panel both show above End trip (NV7, NL4): same actions, same order. */
@Composable
fun ColumnScope.TripControlsBody(
    beeps: Boolean,
    messages: Boolean,
    dashStatus: String?,
    canAddStop: Boolean,
    nextStop: String?,
    actions: TripSheetActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        KtmButton(
            stringResource(R.string.trip_add_stop), actions.onAddStop, Modifier.weight(1f),
            style = ButtonStyle.Tonal, size = ButtonSize.Tile, icon = KtmIcons.PinPlus, enabled = canAddStop,
        )
        KtmButton(
            stringResource(R.string.riding_overview), actions.onOverview, Modifier.weight(1f),
            style = ButtonStyle.Tonal, size = ButtonSize.Tile, icon = KtmIcons.Route,
        )
        KtmButton(
            stringResource(R.string.trip_minimise), actions.onMinimise, Modifier.weight(1f),
            style = ButtonStyle.Tonal, size = ButtonSize.Tile, icon = KtmIcons.ChevronDown,
        )
    }
    Spacer(Modifier.height(KtmSpacing.md))
    if (nextStop != null) {
        ListRow(
            title = nextStop, supporting = stringResource(R.string.trip_next_stop), leadingIcon = KtmIcons.PinPlus,
            trailing = {
                KtmIconButton(
                    KtmIcons.Close, stringResource(R.string.trip_remove_next_stop), actions.onRemoveNextStop,
                    size = IconButtonSize.Map,
                )
            },
        )
    }
    KtmSwitchRow(stringResource(R.string.trip_beeps), beeps, actions.onBeeps, leadingIcon = KtmIcons.Sound)
    KtmSwitchRow(stringResource(R.string.trip_messages), messages, actions.onMessages, leadingIcon = KtmIcons.Message)
    ListRow(
        title = stringResource(R.string.trip_dash), supporting = dashStatus, leadingIcon = KtmIcons.Bike,
        onClick = actions.onDash,
        trailing = { Icon(KtmIcons.ChevronRight, null, tint = KtmTheme.colors.fg2) },
    )
}

/** DL2: ending is a deliberate act. Keeping the trip is the default (Back and outside taps keep riding). */
@Composable
fun EndTripDialog(onEnd: () -> Unit, onKeep: () -> Unit) {
    KtmDialog(
        title = stringResource(R.string.end_title),
        message = stringResource(R.string.end_message),
        onDismiss = onKeep,
        confirm = DialogAction(stringResource(R.string.end_confirm), onEnd, ButtonStyle.Danger),
        dismiss = DialogAction(stringResource(R.string.end_keep), onKeep, ButtonStyle.Neutral),
        riding = true,
    )
}
