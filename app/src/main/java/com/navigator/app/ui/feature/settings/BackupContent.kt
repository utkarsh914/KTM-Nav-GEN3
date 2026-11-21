package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmCard
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class BackupActions(val onBack: () -> Unit, val onBackup: () -> Unit, val onRestore: () -> Unit, val restore: RestoreActions, val onRestart: () -> Unit = {})

/** ST8: two big actions and a readable list of what a backup holds, with the restore sheet over it. Stateless. */
@Composable
fun BackupContent(ui: BackupUi, actions: BackupActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    SettingsPageScaffold(stringResource(R.string.backup_title), actions.onBack, modifier) {
        Column(Modifier.padding(horizontal = KtmSpacing.edge), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            ActionCard(
                KtmIcons.Upload, stringResource(R.string.backup_action_backup), stringResource(R.string.backup_action_backup_hint),
                actions.onBackup, accent = true, loading = ui.busy && !ui.restoring, enabled = !ui.restoring,
            )
            ActionCard(
                KtmIcons.Download, stringResource(R.string.backup_action_restore), stringResource(R.string.backup_action_restore_hint),
                actions.onRestore, accent = false, loading = ui.restoring, enabled = !ui.busy,
            )
            // The snackbar for this is gone in seconds; the reminder stays until the app has restarted.
            if (ui.restartPending) {
                Text(stringResource(R.string.backup_restart_pending), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                KtmButton(stringResource(R.string.backup_restart_now), actions.onRestart, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal, icon = KtmIcons.Restart)
            }
        }
        // ST8: what is in a backup and what is not, as one card.
        Column(
            Modifier.padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.lg).fillMaxWidth()
                .background(c.surf2, KtmShapes.Card).padding(KtmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            Text(stringResource(R.string.backup_included), style = MaterialTheme.typography.titleSmall, color = c.fg2, modifier = Modifier.semantics { heading() })
            Included(stringResource(R.string.backup_inc_rides), true)
            Included(stringResource(R.string.backup_inc_places), true)
            Included(stringResource(R.string.backup_inc_settings), true)
            Included(stringResource(R.string.backup_inc_pairing), false)
            Text(
                stringResource(R.string.backup_restore_rule), style = MaterialTheme.typography.bodySmall, color = c.fg2,
                modifier = Modifier.padding(top = KtmSpacing.sm),
            )
        }
    }
    ui.restore?.let { RestoreSheet(it, actions.restore) }
}

/** A large action: a 48 dp icon tile, what it does and a line on what it covers; the whole card is the target. */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    accent: Boolean,
    loading: Boolean,
    enabled: Boolean,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    KtmCard(onClick = onClick, enabled = enabled && !loading, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            Box(
                Modifier.size(KtmLayout.actionTile).background(if (accent) a.container else c.surf3, KtmShapes.Control),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (accent) a.onContainer else c.fg) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) c.fg else c.fg3)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            if (loading) CircularProgressIndicator(Modifier.size(KtmSpacing.xl), color = a.fill, strokeWidth = KtmSpacing.xs / 2)
            else RowChevron()
        }
    }
}

/** One line of the card: a tick for what a backup holds, a cross for what it does not. */
@Composable
private fun Included(text: String, included: Boolean) {
    val c = KtmTheme.colors
    val state = stringResource(if (included) R.string.backup_row_included else R.string.backup_row_not_included)
    // The tick or cross is a picture: the state is also said in words.
    Row(Modifier.semantics(mergeDescendants = true) { stateDescription = state }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        Icon(
            if (included) KtmIcons.Done else KtmIcons.Close, null, tint = if (included) c.okText else c.fg3, modifier = Modifier.size(KtmSpacing.xl),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (included) c.fg else c.fg2)
    }
}
