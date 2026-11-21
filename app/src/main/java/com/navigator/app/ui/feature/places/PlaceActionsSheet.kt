package com.navigator.app.ui.feature.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.TonalIconBadge
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** PC5: everything a place can do, in one list with Remove last and in red. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaceActionsSheet(target: PlaceTarget, inApp: Boolean, onAction: (PlaceAction) -> Unit, onDismiss: () -> Unit, distance: String? = null) {
    val c = KtmTheme.colors
    val place = target.place
    KtmModalSheet(onDismiss = onDismiss) {
        // PC5: which place this is: its icon, its name, and where it is and how far.
        Row(
            Modifier.padding(horizontal = KtmSpacing.lg).padding(bottom = KtmSpacing.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            TonalIconBadge(
                when ((target as? PlaceTarget.Slot)?.slot) {
                    FavoriteSlot.HOME -> KtmIcons.Home
                    FavoriteSlot.WORK -> KtmIcons.Work
                    else -> KtmIcons.Bookmark
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    place.label.ifBlank { place.address.orEmpty() }, style = MaterialTheme.typography.titleMedium, color = c.fg, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                val sub = listOfNotNull(place.address?.takeIf { it.isNotBlank() && it != place.label }, distance).joinToString(" \u00b7 ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PlaceActions.forTarget(target, inApp).forEach { action ->
            ListRow(
                title = stringResource(action.label()), leadingIcon = action.icon(),
                supporting = if (action == PlaceAction.OpenInMaps) stringResource(R.string.places_action_maps_hint) else null,
                destructive = action == PlaceAction.Remove, onClick = { onAction(action) },
            )
        }
    }
}

private fun PlaceAction.label(): Int = when (this) {
    PlaceAction.Change -> R.string.places_action_change
    PlaceAction.Directions -> R.string.places_action_directions
    PlaceAction.ShowOnMap -> R.string.places_action_show
    PlaceAction.OpenInMaps -> R.string.places_action_maps
    PlaceAction.SetHome -> R.string.places_action_set_home
    PlaceAction.SetWork -> R.string.places_action_set_work
    PlaceAction.Remove -> R.string.places_action_remove
}

private fun PlaceAction.icon(): ImageVector = when (this) {
    PlaceAction.Change -> KtmIcons.Edit
    PlaceAction.Directions -> KtmIcons.Navigation
    PlaceAction.ShowOnMap -> KtmIcons.Map
    PlaceAction.OpenInMaps -> KtmIcons.External
    PlaceAction.SetHome -> KtmIcons.Home
    PlaceAction.SetWork -> KtmIcons.Work
    PlaceAction.Remove -> KtmIcons.Delete
}
