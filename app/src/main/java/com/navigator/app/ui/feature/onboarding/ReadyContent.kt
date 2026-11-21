package com.navigator.app.ui.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.feature.settings.SettingsPage
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class ReadyActions(val onOpenPage: (SettingsPage) -> Unit, val onStart: () -> Unit)

/** ON10: the dash greeting as proof, what was chosen (each row opens its setting), and Start riding. Stateless. */
@Composable
fun ReadyContent(ui: ReadyUi, actions: ReadyActions, modifier: Modifier = Modifier) {
    val time = remember { EtaFormatter.format(System.currentTimeMillis()) }
    val greeting = if (ui.userName.isBlank()) stringResource(R.string.onb_greeting_blank) else stringResource(R.string.onb_greeting, ui.userName)
    Column(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        KtmTopBar()
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Text(
                stringResource(R.string.ready_title), style = MaterialTheme.typography.headlineLarge, color = KtmTheme.colors.fg,
                modifier = Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
            )
            Text(
                stringResource(if (ui.connected) R.string.ready_message_connected else R.string.ready_message_waiting),
                style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
                modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.lg),
            )
            DashPreview(
                DashPreviewState.Message(time, stringResource(R.string.dash_preview_banner_app), greeting),
                Modifier.padding(horizontal = KtmSpacing.edge),
            )
            SectionHeader(stringResource(R.string.ready_summary))
            ListRow(
                title = stringResource(R.string.ready_bike), supporting = ui.bikeName ?: stringResource(R.string.ready_bike_paired),
                leadingIcon = KtmIcons.Bike, onClick = { actions.onOpenPage(SettingsPage.BIKE_DASH) }, trailing = { RowChevron() },
            )
            ListRow(
                title = stringResource(R.string.ready_navigation),
                supporting = stringResource(if (ui.engine == NavEngine.IN_APP) R.string.onb_engine_inapp else R.string.onb_engine_mirror),
                leadingIcon = KtmIcons.Navigation, onClick = { actions.onOpenPage(SettingsPage.NAVIGATION) }, trailing = { RowChevron() },
            )
            ListRow(
                title = stringResource(R.string.ready_name),
                supporting = ui.userName.ifBlank { stringResource(R.string.bikedash_name_unset) },
                leadingIcon = KtmIcons.User, onClick = { actions.onOpenPage(SettingsPage.BIKE_DASH) }, trailing = { RowChevron() },
            )
        }
        KtmButton(
            stringResource(R.string.ready_start), actions.onStart,
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(KtmSpacing.edge),
            icon = KtmIcons.Navigation,
        )
    }
}
