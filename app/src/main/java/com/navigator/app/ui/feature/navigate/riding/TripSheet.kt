package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmSwitch
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.PillTone
import com.navigator.app.ui.components.fullBleed
import com.navigator.app.ui.components.pillDotColor
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

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

/** End trip: danger-tonal, 64 dp, set apart from the rest; it asks first. */
@Composable
fun EndTripButton(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    KtmButton(
        stringResource(R.string.trip_end), onEnd, modifier.fillMaxWidth(),
        style = ButtonStyle.DangerTonal, size = ButtonSize.Riding, icon = KtmIcons.Close,
    )
}

/**
 * NV7: everything the riding screen no longer carries as buttons, shown in the sheet the trip bar grows into
 * ([TripBarSheet]) and in the landscape controls panel (NL4): Add stop, Overview and Minimise are large tiles, the two
 * riding toggles are side-by-side 56 dp tiles, the dash row opens the bike screen. End trip follows, set apart (DL2).
 */
@Composable
fun ColumnScope.TripControlsBody(
    beeps: Boolean,
    messages: Boolean,
    dashStatus: String?,
    canAddStop: Boolean,
    nextStop: String?,
    actions: TripSheetActions,
    dashTone: PillTone = PillTone.Connected,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        KtmButton(
            stringResource(R.string.trip_add_stop), actions.onAddStop, Modifier.weight(1f),
            style = ButtonStyle.Tonal, size = ButtonSize.TileLarge, icon = KtmIcons.PinPlus, enabled = canAddStop,
        )
        KtmButton(
            stringResource(R.string.riding_overview), actions.onOverview, Modifier.weight(1f),
            style = ButtonStyle.Neutral, size = ButtonSize.TileLarge, icon = KtmIcons.Route,
        )
        KtmButton(
            stringResource(R.string.trip_minimise), actions.onMinimise, Modifier.weight(1f),
            style = ButtonStyle.Neutral, size = ButtonSize.TileLarge, icon = KtmIcons.ChevronDown,
        )
    }
    if (!canAddStop) {
        Text(
            stringResource(R.string.trip_add_stop_unavailable), style = MaterialTheme.typography.bodyMedium,
            color = KtmTheme.riding.onSurfaceVariant, modifier = Modifier.padding(top = KtmSpacing.sm),
        )
    }
    Spacer(Modifier.height(KtmSpacing.sm))
    // The two riding toggles are one pair of 56 dp tiles (NV7, NL4), so a glove finds them side by side.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        ToggleTile(stringResource(R.string.trip_beeps), stringResource(R.string.trip_beeps_description), beeps, actions.onBeeps, Modifier.weight(1f))
        ToggleTile(stringResource(R.string.trip_messages), stringResource(R.string.trip_messages_description), messages, actions.onMessages, Modifier.weight(1f))
    }
    Spacer(Modifier.height(KtmSpacing.sm))
    if (nextStop != null) {
        ListRow(
            modifier = Modifier.fullBleed(),
            title = nextStop, supporting = stringResource(R.string.trip_next_stop), leadingIcon = KtmIcons.PinPlus,
            trailing = {
                KtmIconButton(
                    KtmIcons.Close, stringResource(R.string.trip_remove_next_stop), actions.onRemoveNextStop,
                    size = IconButtonSize.Map,
                )
            },
        )
    }
    if (nextStop != null) ListDivider(Modifier.fullBleed())
    ListRow(
        modifier = Modifier.fullBleed(),
        title = dashStatus ?: stringResource(R.string.trip_dash),
        leading = { Box(Modifier.size(KtmSpacing.md).background(pillDotColor(dashTone, KtmTheme.colors), CircleShape)) },
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

/** A riding toggle: a 56 dp tile with its label and a switch; the whole tile toggles. */
@Composable
private fun ToggleTile(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier.heightIn(min = KtmLayout.targetPrimary).clip(KtmShapes.Control).background(KtmTheme.riding.surfaceHigh)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { haptics.toggle(it); onChange(it) })
            .semantics { contentDescription = description }
            .padding(start = KtmSpacing.lg, end = KtmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Text(label, style = KtmTheme.type.ridingLabel, color = KtmTheme.riding.onSurface, maxLines = 2, modifier = Modifier.weight(1f))
        KtmSwitch(checked, onCheckedChange = null)
    }
}
