package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.backup.BackupManager
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmCheckboxRow
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class RestoreActions(
    val onConfirm: (RestoreChoice) -> Unit,
    val onChooseAnother: () -> Unit,
    val onDismiss: () -> Unit,
)

/** DL6: what the chosen file holds and what to restore from it. A bad file says so here, inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreSheet(state: RestoreState, actions: RestoreActions) {
    KtmModalSheet(onDismiss = actions.onDismiss, title = stringResource(R.string.restore_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            when (state) {
                RestoreState.Reading -> {
                    Text(stringResource(R.string.restore_reading), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2)
                    SkeletonRow()
                }
                RestoreState.Unreadable -> {
                    InfoBanner(
                        stringResource(R.string.restore_unreadable), tone = BannerTone.Bad,
                        title = stringResource(R.string.restore_unreadable_title),
                    )
                    KtmButton(stringResource(R.string.restore_choose_another), actions.onChooseAnother, Modifier.fillMaxWidth())
                    KtmButton(stringResource(R.string.restore_close), actions.onDismiss, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
                }
                is RestoreState.Ready -> Choices(state.preview, actions)
            }
        }
    }
}

@Composable
private fun Choices(p: BackupManager.Preview, actions: RestoreActions) {
    val start = RestoreChoice.defaultsFor(p)
    var rides by rememberSaveable { mutableStateOf(start.rides) }
    var places by rememberSaveable { mutableStateOf(start.places) }
    var settings by rememberSaveable { mutableStateOf(start.settings) }
    val choice = RestoreChoice(rides, places, settings)
    val none = stringResource(R.string.restore_none)
    Text(stringResource(R.string.restore_intro), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2)
    KtmCheckboxRow(
        stringResource(R.string.restore_rides), rides && p.newRides > 0, { rides = it }, enabled = p.newRides > 0,
        supporting = if (p.ridesInFile == 0) none else stringResource(R.string.restore_rides_detail, p.newRides, p.ridesInFile),
    )
    KtmCheckboxRow(
        stringResource(R.string.restore_places), places && p.places > 0, { places = it }, enabled = p.places > 0,
        supporting = if (p.places == 0) none else stringResource(R.string.restore_places_detail, p.places),
    )
    KtmCheckboxRow(
        stringResource(R.string.restore_settings), settings && p.settings > 0, { settings = it }, enabled = p.settings > 0,
        supporting = if (p.settings == 0) none else stringResource(R.string.restore_settings_detail, p.settings), supportingLines = 2,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        KtmButton(stringResource(R.string.common_cancel), actions.onDismiss, Modifier.weight(1f), style = ButtonStyle.Outline)
        KtmButton(stringResource(R.string.restore_confirm), { actions.onConfirm(choice) }, Modifier.weight(1f), enabled = choice.any)
    }
}
