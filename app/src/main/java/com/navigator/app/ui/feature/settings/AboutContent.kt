package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmIcons

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
            )
        }

        SectionHeader(stringResource(R.string.about_section_app))
        ListRow(title = stringResource(R.string.about_version), supporting = ui.version, leadingIcon = KtmIcons.Info)
    }
}
