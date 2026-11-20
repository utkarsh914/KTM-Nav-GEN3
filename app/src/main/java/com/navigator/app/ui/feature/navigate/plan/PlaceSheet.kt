package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** What the place sheet shows (PL1). [tripRunning] switches Directions to "Replace trip…" and offers Add as stop. */
data class PlaceSheetState(
    val title: String,
    val address: String?,
    /** "3.2 km away", or null when the position is unknown. */
    val distance: String?,
    val saved: Boolean,
    val tripRunning: Boolean,
)

class PlaceSheetActions(
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onToggleSave: () -> Unit,
    val onDirections: () -> Unit,
    val onAddAsStop: () -> Unit,
)

/**
 * PL1: the chosen place. Back returns to the search or the map, ✕ clears to the map, so there is always a
 * way out. Save is a labelled button that reads "Saved" once saved; tapping it again removes the place.
 * Stateless: the panel it sits in is [PlanPanel].
 */
@Composable
fun PlaceSheet(state: PlaceSheetState, actions: PlaceSheetActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            Text(
                state.title, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = KtmSpacing.xs).semantics { heading() },
            )
            KtmIconButton(KtmIcons.Close, stringResource(R.string.common_close), actions.onClose)
        }
        if (state.address != null) {
            Text(
                state.address, style = MaterialTheme.typography.bodyMedium, color = c.fg2, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = KtmSpacing.sm),
            )
        }
        if (state.distance != null) {
            Text(
                stringResource(R.string.place_away, state.distance), style = MaterialTheme.typography.labelMedium,
                color = KtmTheme.accent.text, modifier = Modifier.padding(horizontal = KtmSpacing.sm, vertical = KtmSpacing.xs),
            )
        }
        Spacer(Modifier.height(KtmSpacing.lg))
        KtmButton(
            stringResource(if (state.tripRunning) R.string.place_replace_trip else R.string.place_directions),
            actions.onDirections, Modifier.fillMaxWidth(), icon = KtmIcons.Navigation,
        )
        Spacer(Modifier.height(KtmSpacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            val savedText = stringResource(R.string.place_saved_description)
            KtmButton(
                stringResource(if (state.saved) R.string.place_saved else R.string.place_save), actions.onToggleSave,
                Modifier.weight(1f).semantics { if (state.saved) stateDescription = savedText },
                style = ButtonStyle.Tonal, icon = if (state.saved) KtmIcons.BookmarkFilled else KtmIcons.Bookmark,
            )
            if (state.tripRunning) {
                KtmButton(
                    stringResource(R.string.place_add_as_stop), actions.onAddAsStop, Modifier.weight(1f),
                    style = ButtonStyle.Tonal, icon = KtmIcons.PinPlus,
                )
            }
        }
    }
}
