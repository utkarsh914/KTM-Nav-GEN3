package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.navigator.app.R
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmCard
import com.navigator.app.ui.components.KtmSegmented
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.FontMode
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.ThemeMode

class AppearanceActions(
    val onBack: () -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onRidingScreens: (NavThemeMode) -> Unit,
    val onFont: (FontMode) -> Unit,
    val onAccent: (String?) -> Unit,
    val onCustom: () -> Unit,
)

/** ST6: a live preview card, then theme, riding screens, font and accent as inline controls. Stateless. */
@Composable
fun AppearanceContent(ui: AppearanceUi, actions: AppearanceActions, modifier: Modifier = Modifier) {
    val themeLabels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.look_theme_system), ThemeMode.LIGHT to stringResource(R.string.look_theme_light),
        ThemeMode.DARK to stringResource(R.string.look_theme_dark),
    )
    val ridingLabels = mapOf(NavThemeMode.DAY_NIGHT to stringResource(R.string.look_riding_day_night), NavThemeMode.APP to stringResource(R.string.look_riding_app))
    val fontLabels = mapOf(FontMode.BRAND to stringResource(R.string.look_font_brand), FontMode.SYSTEM to stringResource(R.string.look_font_system))
    SettingsPageScaffold(stringResource(R.string.look_title), actions.onBack, modifier) {
        KtmCard(Modifier.padding(horizontal = KtmSpacing.edge)) {
            Text(stringResource(R.string.look_preview_title), style = MaterialTheme.typography.titleMedium, color = KtmTheme.colors.fg)
            Row(
                Modifier.padding(top = KtmSpacing.md).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
            ) {
                KtmButton(stringResource(R.string.look_preview_button), {}, size = ButtonSize.Small)
                KtmBadge(stringResource(R.string.look_preview_badge), tone = BadgeTone.Accent)
            }
        }

        SectionHeader(stringResource(R.string.look_theme))
        KtmSegmented(
            ThemeMode.entries, ui.theme, actions.onTheme, { themeLabels.getValue(it) }, Modifier.padding(horizontal = KtmSpacing.lg),
        )

        SectionHeader(stringResource(R.string.look_riding))
        KtmSegmented(
            listOf(NavThemeMode.DAY_NIGHT, NavThemeMode.APP), ui.ridingScreens, actions.onRidingScreens, { ridingLabels.getValue(it) },
            Modifier.padding(horizontal = KtmSpacing.lg),
        )
        Text(
            stringResource(R.string.look_riding_hint), style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        )

        SectionHeader(stringResource(R.string.look_font))
        KtmSegmented(
            FontMode.entries, ui.font, actions.onFont, { fontLabels.getValue(it) }, Modifier.padding(horizontal = KtmSpacing.lg),
        )

        SectionHeader(stringResource(R.string.look_accent))
        AccentSwatches(ui.accentId, actions.onAccent, actions.onCustom)
        Text(
            stringResource(AccentNames.res(ui.accentId)), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        )
    }
}
