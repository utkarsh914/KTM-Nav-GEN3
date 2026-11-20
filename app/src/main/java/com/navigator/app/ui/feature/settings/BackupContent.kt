package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class BackupActions(val onBack: () -> Unit, val onBackup: () -> Unit, val onRestore: () -> Unit, val restore: RestoreActions)

/** ST8: two big actions and a readable list of what a backup holds, with the restore sheet over it. Stateless. */
@Composable
fun BackupContent(ui: BackupUi, actions: BackupActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    SettingsPageScaffold(stringResource(R.string.backup_title), actions.onBack, modifier) {
        Column(Modifier.padding(horizontal = KtmSpacing.edge), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            KtmButton(stringResource(R.string.backup_action_backup), actions.onBackup, Modifier.fillMaxWidth(), icon = KtmIcons.Upload, loading = ui.busy)
            KtmButton(
                stringResource(R.string.backup_action_restore), actions.onRestore, Modifier.fillMaxWidth(),
                style = ButtonStyle.Tonal, icon = KtmIcons.Download, enabled = !ui.busy,
            )
        }
        SectionHeader(stringResource(R.string.backup_included))
        ListRow(title = stringResource(R.string.backup_inc_rides), supporting = stringResource(R.string.backup_inc_rides_hint), leadingIcon = KtmIcons.Route)
        ListRow(title = stringResource(R.string.backup_inc_places), supporting = stringResource(R.string.backup_inc_places_hint), leadingIcon = KtmIcons.Bookmark)
        ListRow(title = stringResource(R.string.backup_inc_settings), supporting = stringResource(R.string.backup_inc_settings_hint), leadingIcon = KtmIcons.Settings)
        Text(
            stringResource(R.string.backup_not_included), style = MaterialTheme.typography.bodySmall, color = c.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        )
        Text(
            stringResource(R.string.backup_restore_rule), style = MaterialTheme.typography.bodySmall, color = c.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg),
        )
    }
    ui.restore?.let { RestoreSheet(it, actions.restore) }
}
