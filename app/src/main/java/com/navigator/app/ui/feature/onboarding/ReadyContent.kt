package com.navigator.app.ui.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.navigator.app.R
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.feature.settings.SettingsPage
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets

class ReadyActions(val onOpenPage: (SettingsPage) -> Unit, val onStart: () -> Unit)

/** ON10: the dash greeting as proof, what was chosen (each row opens its setting), and Start riding. Stateless. */
@Composable
fun ReadyContent(ui: ReadyUi, actions: ReadyActions, modifier: Modifier = Modifier) {
    val time = remember { EtaFormatter.format(System.currentTimeMillis()) }
    val greeting = if (ui.userName.isBlank()) stringResource(R.string.onb_greeting_blank) else stringResource(R.string.onb_greeting, ui.userName)
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = KtmSpacing.edge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ON10: no top bar, nothing to go back to. A tick, who it is for, and the dash saying hello.
            Box(
                Modifier.padding(top = KtmSpacing.xxl).size(KtmLayout.readyHero).background(c.okContainer, KtmShapes.Pill),
                contentAlignment = Alignment.Center,
            ) { Icon(KtmIcons.Done, null, tint = c.okText, modifier = Modifier.size(KtmLayout.readyGlyph)) }
            Text(
                if (ui.userName.isBlank()) stringResource(R.string.ready_title_blank) else stringResource(R.string.ready_title, ui.userName),
                style = MaterialTheme.typography.headlineLarge, color = c.fg, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }.padding(top = KtmSpacing.lg),
            )
            Text(
                stringResource(if (ui.connected) R.string.ready_message_connected else R.string.ready_message_waiting),
                style = MaterialTheme.typography.bodyLarge, color = c.fg2, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = KtmSpacing.sm, bottom = KtmSpacing.lg),
            )
            DashPreview(DashPreviewState.Message(time, stringResource(R.string.dash_preview_banner_app), greeting))
            Column(Modifier.padding(top = KtmSpacing.lg).fillMaxWidth().background(c.surf, KtmShapes.Card)) {
                ListRow(
                    title = ui.bikeName ?: stringResource(R.string.ready_bike_paired), leadingIcon = KtmIcons.Bike,
                    onClick = { actions.onOpenPage(SettingsPage.BIKE_DASH) },
                    trailing = {
                        KtmBadge(
                            stringResource(if (ui.connected) R.string.ready_connected else R.string.ready_not_connected),
                            tone = if (ui.connected) BadgeTone.Ok else BadgeTone.Neutral,
                        )
                    },
                )
                ListRow(
                    title = stringResource(if (ui.engine == NavEngine.IN_APP) R.string.onb_engine_inapp else R.string.onb_engine_mirror),
                    leadingIcon = KtmIcons.Navigation, onClick = { actions.onOpenPage(SettingsPage.NAVIGATION) },
                    trailing = { Text(stringResource(R.string.ready_change), style = MaterialTheme.typography.labelLarge, color = KtmTheme.accent.text) },
                )
                if (ui.optionalMissing > 0) {
                    ListRow(
                        title = pluralStringResource(R.plurals.ready_optional_left, ui.optionalMissing, ui.optionalMissing),
                        leadingIcon = KtmIcons.Permission, onClick = { actions.onOpenPage(SettingsPage.PERMISSIONS) }, trailing = { RowChevron() },
                    )
                }
            }
        }
        KtmButton(
            stringResource(R.string.ready_start), actions.onStart,
            Modifier.fillMaxWidth().pageInsets().padding(KtmSpacing.edge),
        )
    }
}
