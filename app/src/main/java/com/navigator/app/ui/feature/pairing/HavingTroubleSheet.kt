package com.navigator.app.ui.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.navigator.app.ui.theme.KtmTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing

/** One cause, wrapped over as many lines as it needs (a list row title is one line). */
@Composable
private fun TroubleStep(text: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg), verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = KtmTheme.colors.fg2)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg)
    }
}

/** The likely causes, in plain steps, shown on the failure screen and in the sheet. */
@Composable
fun TroubleSteps() {
    TroubleStep(stringResource(R.string.pair_help_ignition), KtmIcons.Bike)
    TroubleStep(stringResource(R.string.pair_help_bluetooth), KtmIcons.Bluetooth)
    TroubleStep(stringResource(R.string.pair_help_close), KtmIcons.Phone)
    TroubleStep(stringResource(R.string.pair_help_forget), KtmIcons.Refresh)
    TroubleStep(stringResource(R.string.pair_help_other_apps), KtmIcons.Dash)
}

/** ON9: the same help from ON6 and from the Bike sheet. Logs are a support tool here, not header chrome. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HavingTroubleSheet(onViewLogs: () -> Unit, onBluetoothSettings: () -> Unit, onDismiss: () -> Unit) {
    KtmModalSheet(
        onDismiss = onDismiss, title = stringResource(R.string.pair_trouble),
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(stringResource(R.string.pair_open_bluetooth), onBluetoothSettings, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal)
                KtmButton(stringResource(R.string.pair_view_logs), { onDismiss(); onViewLogs() }, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
            }
        },
    ) { TroubleSteps() }
}
