package com.navigator.app.ui.feature.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionItem
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.theme.KtmIcons

fun PermissionId.icon(): ImageVector = when (this) {
    PermissionId.NEARBY_DEVICES -> KtmIcons.Bluetooth
    PermissionId.LOCATION -> KtmIcons.Crosshair
    PermissionId.LOCATION_ALWAYS -> KtmIcons.Navigation
    PermissionId.NOTIFICATIONS -> KtmIcons.Bell
    PermissionId.NOTIFICATION_ACCESS -> KtmIcons.Message
    PermissionId.BATTERY -> KtmIcons.Battery
}

/**
 * One permission: what it is for, and either Allowed or the button that fixes it. The row Settings uses and
 * onboarding (ON5, B17) will reuse. A missing required one turns amber.
 */
@Composable
fun PermissionRow(item: PermissionItem, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(item.id.titleRes)
    val why = stringResource(item.id.whyRes)
    ListRow(
        title = title,
        supporting = if (item.required) why else "$why · ${stringResource(R.string.perm_optional)}",
        supportingLines = 2,
        leadingIcon = item.id.icon(),
        attention = item.required && !item.granted,
        modifier = modifier,
        trailing = {
            if (item.granted) {
                KtmBadge(stringResource(R.string.perm_allowed), tone = BadgeTone.Ok)
            } else {
                val label = stringResource(if (PermissionFix.opensSettings(item.id)) R.string.perm_open else R.string.perm_allow)
                val description = stringResource(if (PermissionFix.opensSettings(item.id)) R.string.perm_open_cd else R.string.perm_allow_cd, title)
                KtmButton(label, onFix, Modifier.semantics { contentDescription = description }, size = ButtonSize.Small)
            }
        },
    )
}
