package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.feature.permissions.PermissionRow
import com.navigator.app.ui.theme.KtmSpacing

class PermissionsActions(val onBack: () -> Unit, val onFix: (PermissionId) -> Unit)

/** ST7: a banner that says what the missing permissions cost, then one row per permission. Stateless. */
@Composable
fun PermissionsContent(ui: PermissionsUi, actions: PermissionsActions, modifier: Modifier = Modifier) {
    // What stops working, for each thing that is off (required first), not just their names.
    val missing = ui.requiredMissing + ui.optionalMissing
    val consequences = missing.map { stringResource(it.consequenceRes) }.joinToString("\n")
    val title = pluralStringResource(R.plurals.perm_banner_title, missing.size, missing.size)
    SettingsPageScaffold(stringResource(R.string.perm_title), actions.onBack, modifier) {
        val bannerModifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.md)
        when {
            ui.requiredMissing.isNotEmpty() -> InfoBanner(consequences, bannerModifier, title = title, tone = BannerTone.Warn)
            ui.optionalMissing.isNotEmpty() -> InfoBanner(consequences, bannerModifier, title = title, tone = BannerTone.Warn)
            else -> InfoBanner(stringResource(R.string.perm_banner_ok), bannerModifier, tone = BannerTone.Info)
        }
        ui.items.forEachIndexed { i, it ->
            if (i > 0) ListDivider()
            PermissionRow(it, onFix = { actions.onFix(it.id) })
        }
    }
}
