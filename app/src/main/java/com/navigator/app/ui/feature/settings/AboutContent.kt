package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.KtmCard
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class AboutActions(
    val onBack: () -> Unit,
    val onLogs: () -> Unit,
    val onCopyLogs: (Boolean) -> Unit,
    val onSymbols: () -> Unit,
    val onCalibration: () -> Unit,
    val onOverlay: (Boolean) -> Unit,
)

/** ST9: developer tools grouped and described in plain words; the overlay shows in debug builds only. Stateless. */
@Composable
fun AboutContent(ui: AboutUi, actions: AboutActions, modifier: Modifier = Modifier) {
    SettingsPageScaffold(stringResource(R.string.about_title), actions.onBack, modifier) {
        // ST9: what this app is and which build, first.
        val c = KtmTheme.colors
        KtmCard(Modifier.padding(horizontal = KtmSpacing.edge)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                Box(
                    Modifier.size(KtmLayout.actionTile).background(KtmTheme.accent.container, KtmShapes.Control),
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Icon(KtmIcons.Navigation, null, tint = KtmTheme.accent.onContainer) }
                Column {
                    Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.titleMedium, color = c.fg)
                    Text(ui.version, style = MaterialTheme.typography.bodySmall, color = c.fg2)
                }
            }
        }
        SectionHeader(stringResource(R.string.about_section_logs))
        ListRow(
            title = stringResource(R.string.about_logs), supporting = stringResource(R.string.about_logs_hint), supportingLines = 2,
            leadingIcon = KtmIcons.Terminal, onClick = actions.onLogs, trailing = { RowChevron() },
        )
        KtmSwitchRow(
            stringResource(R.string.about_copy_logs), ui.copyLogs, actions.onCopyLogs,
            supporting = stringResource(R.string.about_copy_logs_hint), supportingLines = 2, leadingIcon = KtmIcons.Download,
        )

        SectionHeader(stringResource(R.string.about_section_tools))
        ListRow(
            title = stringResource(R.string.about_symbols), supporting = stringResource(R.string.about_symbols_hint), supportingLines = 2,
            leadingIcon = KtmIcons.Dash, onClick = actions.onSymbols, trailing = { RowChevron() },
        )
        ListRow(
            title = stringResource(R.string.about_calibration), supporting = stringResource(R.string.about_calibration_hint), supportingLines = 2,
            leadingIcon = KtmIcons.Wand, onClick = actions.onCalibration, trailing = { RowChevron() },
        )
        if (ui.showOverlayToggle) {
            KtmSwitchRow(
                stringResource(R.string.about_overlay), ui.overlay, actions.onOverlay,
                supporting = stringResource(R.string.about_overlay_hint), supportingLines = 2, leadingIcon = KtmIcons.Eye,
                badge = stringResource(R.string.about_debug_badge),
            )
        }
    }
}
