package com.navigator.app.ui.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmStep
import com.navigator.app.ui.components.StepState
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

/**
 * ON9: the likely causes as three numbered steps, then what to do next. Try again is only offered where there is
 * something to try again (a failed pairing); logs are a support tool here, not header chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HavingTroubleSheet(
    onViewLogs: () -> Unit,
    onShareLog: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
    onTryAgain: (() -> Unit)? = null,
) {
    KtmModalSheet(
        onDismiss = onDismiss,
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                if (onTryAgain != null) KtmButton(stringResource(R.string.pair_try_again), { onDismiss(); onTryAgain() }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    KtmButton(
                        stringResource(R.string.logs_share), onShareLog, Modifier.weight(1f), style = ButtonStyle.Outline,
                        size = ButtonSize.Rest, icon = KtmIcons.Share,
                    )
                    KtmButton(
                        stringResource(R.string.pair_view_log), { onDismiss(); onViewLogs() }, Modifier.weight(1f), style = ButtonStyle.Outline,
                        size = ButtonSize.Rest, icon = KtmIcons.List,
                    )
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pair_trouble), style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                KtmIconButton(KtmIcons.Close, stringResource(R.string.common_close), onDismiss)
            }
            KtmStep(1, stringResource(R.string.pair_help_ignition), StepState.Active)
            KtmStep(2, stringResource(R.string.pair_help_forget), StepState.Active)
            KtmStep(3, stringResource(R.string.pair_help_close), StepState.Active)
            InfoBanner(stringResource(R.string.pair_help_stuck), Modifier.padding(top = KtmSpacing.sm))
            KtmButton(
                stringResource(R.string.pair_open_bluetooth), onBluetoothSettings, Modifier.fillMaxWidth(), style = ButtonStyle.Text,
            )
        }
    }
}
