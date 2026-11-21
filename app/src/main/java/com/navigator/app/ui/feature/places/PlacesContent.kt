package com.navigator.app.ui.feature.places

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.navigator.app.R
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.KtmCheckIndicator
import com.navigator.app.ui.components.KtmCheckboxRow
import com.navigator.app.ui.components.KtmFab
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmMenuItem
import com.navigator.app.ui.components.KtmSelectionBar
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.LargeTitle
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.OutlinedIconBadge
import com.navigator.app.ui.components.RowValue
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.feature.navigate.StraightLine
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics
import com.navigator.app.data.places.PlacesRepository

class PlacesActions(
    val onOpenSlot: (FavoriteSlot, SavedPlace) -> Unit,
    val onSetSlot: (FavoriteSlot) -> Unit,
    val onSlotMore: (FavoriteSlot, SavedPlace) -> Unit,
    val onOpenPlace: (SavedPlace) -> Unit,
    val onPlaceMore: (SavedPlace) -> Unit,
    val onAdd: () -> Unit,
    val onSelect: (String?) -> Unit,
    val onToggle: (String) -> Unit,
    val onSelectAll: (Boolean) -> Unit,
    val onStopSelecting: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * PC1, PC2, PC4: the Places tab. Home and Work as 64 dp rows (a set row routes there, an empty one says what it is for),
 * the saved places with their distance and a ⋮, an Add place button, and the selection bar. Stateless.
 */
@Composable
fun PlacesContent(ui: PlacesUi, actions: PlacesActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            if (ui.selecting) SelectionBar(ui, actions) else PlacesBar(hasSaved = ui.saved.isNotEmpty(), actions)
            if (!ui.selecting) LargeTitle(stringResource(R.string.places_title))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
                if (ui.selecting) {
                    item(key = "select-all") {
                        KtmCheckboxRow(
                            title = stringResource(if (ui.allSelected) R.string.places_select_none else R.string.places_select_all),
                            checked = ui.allSelected, onCheckedChange = actions.onSelectAll,
                        )
                    }
                } else {
                    item(key = "home") { SlotRow(FavoriteSlot.HOME, ui.home, ui.origin, actions) }
                    item(key = "work") { SlotRow(FavoriteSlot.WORK, ui.work, ui.origin, actions) }
                    item(key = "saved-header") { SectionHeader(stringResource(R.string.places_saved_header)) }
                    if (ui.saved.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.places_empty), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                                modifier = Modifier.padding(horizontal = KtmSpacing.lg),
                            )
                        }
                    }
                }
                items(ui.saved, key = { PlacesRepository.key(it) }) { place ->
                    val key = PlacesRepository.key(place)
                    SavedRow(place, ui, selected = key in ui.selected, actions, Modifier.animateItem(fadeInSpec = KtmTheme.motion.effectsDefault(), placementSpec = KtmTheme.motion.spatialDefault(), fadeOutSpec = KtmTheme.motion.effectsFast()))
                }
            }
        }
        if (!ui.selecting) {
            KtmFab(
                stringResource(R.string.places_add), KtmIcons.Add, actions.onAdd,
                Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                    .padding(KtmSpacing.lg),
            )
        }
    }
}

/** Room under the last row for the Add place button. */
private val FAB_CLEARANCE = KtmLayout.targetPrimary + KtmSpacing.lg * 2

@Composable
private fun PlacesBar(hasSaved: Boolean, actions: PlacesActions) {
    var menu by remember { mutableStateOf(false) }
    KtmTopBar {
        if (hasSaved) {
            Box {
                KtmIconButton(KtmIcons.More, stringResource(R.string.places_more), { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = KtmTheme.colors.surf) {
                    KtmMenuItem(stringResource(R.string.places_menu_select)) { menu = false; actions.onSelect(null) }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(ui: PlacesUi, actions: PlacesActions) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    KtmSelectionBar(
        pluralStringResource(R.plurals.places_selected_count, ui.selected.size, ui.selected.size),
        stringResource(R.string.places_selection_close), actions.onStopSelecting,
    ) {
        val has = ui.selected.isNotEmpty()
        KtmIconButton(
            KtmIcons.Delete, stringResource(R.string.places_delete_selected), actions.onDelete, enabled = has,
            tint = if (has) a.onContainer else c.fg3,
        )
    }
}

@Composable
private fun SlotRow(slot: FavoriteSlot, place: SavedPlace?, origin: Pair<Double, Double>?, actions: PlacesActions) {
    val home = slot == FavoriteSlot.HOME
    val name = stringResource(if (home) R.string.navigate_tile_home else R.string.navigate_tile_work)
    val icon = if (home) KtmIcons.Home else KtmIcons.Work
    val motion = KtmTheme.motion
    // Setting or clearing a slot crossfades the row rather than swapping it.
    AnimatedContent(
        targetState = place, contentKey = { it != null },
        transitionSpec = { fadeIn(motion.effectsDefault()) togetherWith fadeOut(motion.effectsFast()) },
        label = "slotRow",
    ) { shown ->
        if (shown == null) {
            ListRow(
                title = stringResource(if (home) R.string.places_home_unset else R.string.places_work_unset),
                supporting = stringResource(if (home) R.string.places_home_hint else R.string.places_work_hint),
                leading = { OutlinedIconBadge(KtmIcons.Add) },
                onClick = { actions.onSetSlot(slot) },
            )
        } else {
            ListRow(
                title = name, supporting = shown.address ?: shown.label, leadingIcon = icon,
                onClick = { actions.onOpenSlot(slot, shown) },
                trailing = {
                    StraightLine.label(origin, shown)?.let { RowValue(it) }
                    KtmIconButton(KtmIcons.More, stringResource(R.string.places_row_more, name), { actions.onSlotMore(slot, shown) })
                },
            )
        }
    }
}

@Composable
private fun SavedRow(place: SavedPlace, ui: PlacesUi, selected: Boolean, actions: PlacesActions, modifier: Modifier = Modifier) {
    val key = PlacesRepository.key(place)
    val name = place.label.ifBlank { place.address.orEmpty() }
    val haptics = rememberHaptics()
    if (ui.selecting) {
        ListRow(
            title = name, supporting = place.address, leadingIcon = KtmIcons.Bookmark,
            modifier = modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { haptics.toggle(it); actions.onToggle(key) }),
            trailing = { KtmCheckIndicator(selected) },
        )
    } else {
        ListRow(
            title = name, supporting = place.address, leadingIcon = KtmIcons.Bookmark, modifier = modifier,
            onClick = { actions.onOpenPlace(place) },
            onLongClick = { actions.onSelect(key) },
            onLongClickLabel = stringResource(R.string.places_menu_select),
            trailing = {
                StraightLine.label(ui.origin, place)?.let { RowValue(it) }
                KtmIconButton(KtmIcons.More, stringResource(R.string.places_row_more, name), { actions.onPlaceMore(place) })
            },
        )
    }
}
