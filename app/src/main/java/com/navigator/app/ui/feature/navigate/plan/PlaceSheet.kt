package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.navigator.app.ui.theme.KtmLayout
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
    /** Shown inside the sheet only in the landscape panel, which covers the map's own Back button. */
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onToggleSave: () -> Unit,
    val onDirections: () -> Unit,
    val onAddAsStop: () -> Unit,
)

/**
 * PL1: the chosen place. ✕ clears to the map (Back, a map button above the sheet, returns to the search), so
 * there is always a way out. Directions and Save sit side by side; Save reads "Saved" once saved and tapping
 * it again removes the place. Stateless: the panel it sits in is [PlanPanel].
 */
@Composable
fun PlaceSheet(state: PlaceSheetState, actions: PlaceSheetActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            if (LocalFillPanel.current) KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            Column(Modifier.weight(1f).padding(start = KtmSpacing.sm, top = KtmSpacing.xs)) {
                Text(
                    state.title, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                if (state.address != null) {
                    Text(
                        state.address, style = MaterialTheme.typography.bodyMedium, color = c.fg2, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.distance != null) {
                    Text(
                        stringResource(R.string.place_away, state.distance), style = MaterialTheme.typography.bodySmall,
                        color = c.fg2, modifier = Modifier.padding(top = KtmSpacing.xs),
                    )
                }
            }
            KtmIconButton(KtmIcons.Close, stringResource(R.string.common_close), actions.onClose)
        }
        Spacer(Modifier.height(KtmSpacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            KtmButton(
                stringResource(if (state.tripRunning) R.string.place_replace_trip else R.string.place_directions),
                actions.onDirections, Modifier.weight(1f), icon = KtmIcons.Navigation,
            )
            val savedText = stringResource(R.string.place_saved_description)
            KtmButton(
                stringResource(if (state.saved) R.string.place_saved else R.string.place_save), actions.onToggleSave,
                Modifier.widthIn(min = KtmLayout.placeSaveWidth).semantics { if (state.saved) stateDescription = savedText },
                style = ButtonStyle.Tonal, icon = if (state.saved) KtmIcons.BookmarkFilled else KtmIcons.Bookmark,
            )
        }
        if (state.tripRunning) {
            Spacer(Modifier.height(KtmSpacing.sm))
            KtmButton(
                stringResource(R.string.place_add_as_stop), actions.onAddAsStop, Modifier.fillMaxWidth(),
                style = ButtonStyle.Tonal, icon = KtmIcons.PinPlus,
            )
        }
    }
}
