package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class BikeDashActions(
    val onBack: () -> Unit,
    val onRePair: () -> Unit,
    val onShowOnDash: (Boolean) -> Unit,
    val onOpenApps: () -> Unit,
    val onMarquee: (Boolean) -> Unit,
    val onLatinOnly: (Boolean) -> Unit,
    val onOpenPermissions: () -> Unit,
    val onEditName: () -> Unit,
    val onTestMessage: () -> Unit,
    val onTestTurn: () -> Unit,
)

/** ST2: the bike card with Re-pair, what the dash shows, who the dash greets, and the test sends. Stateless. */
@Composable
fun BikeDashContent(ui: BikeDashUi, actions: BikeDashActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    SettingsPageScaffold(stringResource(R.string.bikedash_title), actions.onBack, modifier) {
        if (ui.repairSuggested) {
            InfoBanner(
                message = stringResource(R.string.settings_repair_message), tone = BannerTone.Bad,
                title = stringResource(R.string.settings_repair_title),
                modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.md),
            )
        }
        BikeCardView(ui.bike, onClick = null)
        KtmButton(
            stringResource(R.string.bikedash_repair), actions.onRePair,
            Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge),
            style = if (ui.repairSuggested) ButtonStyle.Danger else ButtonStyle.Tonal,
        )

        // ST2: who the dash greets, what it spells out, which phone notifications it shows, then the test sends.
        SectionHeader(stringResource(R.string.bikedash_section_greeting))
        ListRow(
            title = stringResource(R.string.bikedash_name),
            supporting = ui.userName.ifBlank { stringResource(R.string.bikedash_name_unset) },
            leadingIcon = KtmIcons.User, onClick = actions.onEditName, trailing = { RowChevron() },
        )

        SectionHeader(stringResource(R.string.bikedash_section_text))
        KtmSwitchRow(
            stringResource(R.string.bikedash_latin), ui.latinOnly, actions.onLatinOnly,
            supporting = stringResource(R.string.bikedash_latin_hint), supportingLines = 3, leadingIcon = KtmIcons.TextSize,
        )
        KtmSwitchRow(
            stringResource(R.string.bikedash_marquee), ui.marquee, actions.onMarquee,
            supporting = stringResource(R.string.bikedash_marquee_hint), supportingLines = 2, leadingIcon = KtmIcons.Dash,
        )

        SectionHeader(stringResource(R.string.bikedash_section_dash))
        KtmSwitchRow(
            stringResource(R.string.bikedash_show), ui.showOnDash, actions.onShowOnDash,
            supporting = stringResource(R.string.bikedash_show_hint), supportingLines = 2, leadingIcon = KtmIcons.Message,
        )
        ListRow(
            title = stringResource(R.string.bikedash_apps),
            supporting = if (ui.appCount == 0) stringResource(R.string.bikedash_apps_none)
            else pluralStringResource(R.plurals.bikedash_apps_count, ui.appCount, ui.appCount),
            leadingIcon = KtmIcons.List, onClick = actions.onOpenApps, trailing = { RowChevron() },
        )
        ListRow(
            title = stringResource(R.string.bikedash_access),
            // Granted: the badge says it, once.
            supporting = if (ui.accessGranted) null else stringResource(R.string.bikedash_access_off),
            supportingLines = if (ui.accessGranted) 0 else 2, leadingIcon = KtmIcons.Permission, attention = !ui.accessGranted,
            onClick = if (ui.accessGranted) null else actions.onOpenPermissions,
            trailing = { if (ui.accessGranted) KtmBadge(stringResource(R.string.bikedash_access_on), tone = BadgeTone.Ok) else RowChevron() },
        )

        SectionHeader(stringResource(R.string.bikedash_section_test))
        Column(Modifier.padding(horizontal = KtmSpacing.edge), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(
                    stringResource(R.string.bikedash_test_message), actions.onTestMessage, Modifier.weight(1f),
                    style = ButtonStyle.Tonal, size = ButtonSize.Rest, enabled = ui.connected, icon = KtmIcons.Message,
                )
                KtmButton(
                    stringResource(R.string.bikedash_test_turn), actions.onTestTurn, Modifier.weight(1f),
                    style = ButtonStyle.Tonal, size = ButtonSize.Rest, enabled = ui.connected, icon = KtmIcons.Navigation,
                )
            }
            if (!ui.connected) {
                Text(stringResource(R.string.bikedash_test_offline), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
        }
    }
}
