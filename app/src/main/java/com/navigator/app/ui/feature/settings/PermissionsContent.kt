package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.feature.permissions.PermissionRow
import com.navigator.app.ui.theme.KtmSpacing

class PermissionsActions(val onBack: () -> Unit, val onFix: (PermissionId) -> Unit)

/** ST7: a banner that says what the missing permissions cost, then one row per permission. Stateless. */
@Composable
fun PermissionsContent(ui: PermissionsUi, actions: PermissionsActions, modifier: Modifier = Modifier) {
    val required = ui.requiredMissing.map { stringResource(it.titleRes) }.joinToString()
    val optional = ui.optionalMissing.map { stringResource(it.titleRes) }.joinToString()
    SettingsPageScaffold(stringResource(R.string.perm_title), actions.onBack, modifier) {
        val bannerModifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.md)
        when {
            ui.requiredMissing.isNotEmpty() -> InfoBanner(
                stringResource(R.string.perm_banner_required, required), bannerModifier,
                title = stringResource(R.string.perm_banner_required_title), tone = BannerTone.Warn,
            )
            ui.optionalMissing.isNotEmpty() -> InfoBanner(
                stringResource(R.string.perm_banner_optional, optional), bannerModifier,
                title = stringResource(R.string.perm_banner_optional_title),
            )
            else -> InfoBanner(stringResource(R.string.perm_banner_ok), bannerModifier, tone = BannerTone.Info)
        }
        ui.items.forEach { PermissionRow(it, onFix = { actions.onFix(it.id) }) }
    }
}
