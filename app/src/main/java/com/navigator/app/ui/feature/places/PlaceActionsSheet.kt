package com.navigator.app.ui.feature.places

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.navigator.app.R
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.theme.KtmIcons

/** PC5: everything a place can do, in one list with Remove last and in red. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaceActionsSheet(target: PlaceTarget, inApp: Boolean, onAction: (PlaceAction) -> Unit, onDismiss: () -> Unit) {
    KtmModalSheet(onDismiss = onDismiss, title = target.place.label.ifBlank { target.place.address.orEmpty() }) {
        PlaceActions.forTarget(target, inApp).forEach { action ->
            ListRow(
                title = stringResource(action.label()), leadingIcon = action.icon(),
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
