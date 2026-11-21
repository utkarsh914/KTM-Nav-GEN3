package com.navigator.app.ui.feature.navigate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.ActivityStrip
import com.navigator.app.ui.components.ActivityStripState
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.IconButtonStyle
import com.navigator.app.ui.components.KtmFilterChip
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowValue
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.feature.navigate.search.labelRes
import com.navigator.app.ui.shell.tripStripBounds
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** Fixed measurements of the collapsed sheet, so the peek height can be computed without measuring. */
object WhereToMetrics {
    private val tileRow = KtmLayout.targetRiding
    private val tileGap = KtmSpacing.sm

    /** A tile is at least [tileHeight]; its two lines of text outgrow that at a large font, so the rows grow with the font scale. */
    fun tileRowHeight(fontScale: Float): Dp = maxOf(tileRow, KtmLayout.tileTextBlock * fontScale + KtmSpacing.lg)

    /** Handle, field, two tile rows and the bottom margin; plus the activity row when a trip runs. */
    fun peekHeight(hasActivity: Boolean, fontScale: Float = 1f): Dp =
        KtmLayout.sheetHandleZone + KtmLayout.targetPrimary + KtmSpacing.md + tileRowHeight(fontScale) * 2 + tileGap + KtmSpacing.lg +
            (if (hasActivity) ACTIVITY_ROW + KtmSpacing.md else 0.dp)

    val tileHeight: Dp get() = tileRow
    private val ACTIVITY_ROW = KtmLayout.activityStrip
}

/** Everything the Where-to sheet reports back. */
class WhereToActions(
    val onSearch: () -> Unit,
    val onVoice: () -> Unit,
    val onTile: (TileAction) -> Unit,
    val onPlace: (SavedPlace) -> Unit,
    val onResumeTrip: () -> Unit,
    val onCategory: (PlaceCategory) -> Unit,
    val onRemoveRecent: (SavedPlace) -> Unit,
    /** Add a stop to the minimised trip (SE4). */
    val onAddStop: (() -> Unit)? = null,
)

/**
 * The browse sheet's content: the running trip, the Where to? field with voice, the 2 × 2 tiles, and
 * (once dragged up) Saved and Recent. Stateless; the collapsed part is [WhereToMetrics.peekHeight] tall.
 */
@Composable
fun WhereToSheetContent(
    state: NavigateUiState,
    voiceAvailable: Boolean,
    actions: WhereToActions,
    modifier: Modifier = Modifier,
    /** Left and right; a card that pads itself (the landscape panel) passes 0. */
    horizontalPadding: Dp = KtmSpacing.lg,
    /** False while the map failed or is not available: Pick on map says so and dims (HM4). */
    mapUsable: Boolean = true,
) {
    val saved = state.favorites.filter { it.slot == FavoriteSlot.OTHER }.map { it.place }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = horizontalPadding),
    ) {
        state.activity?.let { card ->
            ActivityStrip(
                state = ActivityStripState.Trip(
                    title = card.titleText(),
                    detail = card.detailText(),
                    glyph = card.glyph,
                ),
                onOpen = actions.onResumeTrip,
                onResume = actions.onResumeTrip,
                onAddStop = actions.onAddStop,
                modifier = Modifier.tripStripBounds(),
            )
            Spacer(Modifier.height(KtmSpacing.md))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            SearchField(onClick = actions.onSearch, modifier = Modifier.weight(1f))
            if (voiceAvailable) {
                KtmIconButton(
                    KtmIcons.Mic, stringResource(R.string.navigate_voice), actions.onVoice,
                    style = IconButtonStyle.Tonal, size = IconButtonSize.Map, circular = true,
                )
            }
        }
        Spacer(Modifier.height(KtmSpacing.md))
        TileGrid(state.tiles, mapUsable, actions.onTile)
        Spacer(Modifier.height(KtmSpacing.lg))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            items(PlaceCategory.entries.toList(), key = { it.name }) { category ->
                KtmFilterChip(stringResource(category.labelRes()), selected = false, onClick = { actions.onCategory(category) })
            }
        }
        Spacer(Modifier.height(KtmSpacing.sm))

        if (saved.isEmpty() && state.recents.isEmpty()) {
            Text(
                stringResource(R.string.navigate_empty_hint), style = MaterialTheme.typography.bodyMedium,
                color = KtmTheme.colors.fg2, modifier = Modifier.padding(vertical = KtmSpacing.sm),
            )
        } else {
            if (saved.isNotEmpty()) {
                SectionHeader(stringResource(R.string.navigate_saved_header))
                saved.forEach { PlaceRow(it, KtmIcons.Bookmark, state.origin, actions.onPlace) }
            }
            if (state.recents.isNotEmpty()) {
                SectionHeader(stringResource(R.string.navigate_recent_header))
                state.recents.forEach { PlaceRow(it, KtmIcons.History, state.origin, actions.onPlace, onRemove = { r -> actions.onRemoveRecent(r) }) }
            }
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(KtmSpacing.lg))
    }
}

@Composable
internal fun ActivityCardModel.titleText(): String = when {
    distance != null && road != null -> stringResource(R.string.navigate_activity_title_both, distance, road)
    distance != null -> distance
    road != null -> road
    else -> destination
}

@Composable
internal fun ActivityCardModel.detailText(): String =
    if (eta != null) stringResource(R.string.navigate_activity_to_eta, destination, eta)
    else stringResource(R.string.navigate_activity_to, destination)

@Composable
private fun SearchField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier.height(KtmLayout.targetPrimary).clip(KtmShapes.Pill).background(c.surf2)
            .clickable(role = Role.Button) { haptics.tap(); onClick() }
            .padding(horizontal = KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Icon(KtmIcons.Search, null, tint = c.fg2, modifier = Modifier.size(KtmLayout.iconMedium))
        Text(stringResource(R.string.navigate_field_hint), style = MaterialTheme.typography.bodyLarge, color = c.fg2, maxLines = 1)
    }
}

@Composable
private fun TileGrid(tiles: List<WhereToTile>, mapUsable: Boolean, onTile: (TileAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        tiles.chunked(2).forEach { pair ->
            // The two tiles of a row are as tall as the taller one (a long label at a large font).
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                pair.forEach { tile -> TileCell(tile, mapUsable, onTile, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun TileCell(tile: WhereToTile, mapUsable: Boolean, onTile: (TileAction) -> Unit, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    val action = tile.action
    val unset = tile.place == null && tile.kind != TileKind.PickOnMap
    val needsMap = tile.kind == TileKind.PickOnMap && !mapUsable
    val (name, shownCaption) = tileText(tile)
    val caption = if (needsMap) stringResource(R.string.navigate_tile_pick_needs_map) else shownCaption
    val enabled = action != TileAction.None && !needsMap
    Row(
        modifier.heightIn(min = WhereToMetrics.tileHeight).clip(KtmShapes.Card)
            .let { if (unset) it.border(KtmLayout.outlineThin, c.line2, KtmShapes.Card) else it.background(c.surf2) }
            .alpha(if (enabled) 1f else KtmLayout.disabledAlpha)
            .clickable(enabled = enabled, role = Role.Button) { haptics.tap(); onTile(action) }
            .padding(horizontal = KtmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        TileBadge(tileIcon(tile.kind))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** HM1: an accent-container circle with a 20 dp glyph. */
@Composable
private fun TileBadge(icon: ImageVector) {
    val a = KtmTheme.accent
    Box(Modifier.size(KtmLayout.appIcon).background(a.container, KtmShapes.Pill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = a.onContainer, modifier = Modifier.size(KtmLayout.tileIcon))
    }
}

@Composable
private fun tileText(tile: WhereToTile): Pair<String, String> {
    val set = stringResource(R.string.navigate_tile_set_caption)
    return when (tile.kind) {
        TileKind.Home -> if (tile.place == null) stringResource(R.string.navigate_tile_set_home) to set
        else stringResource(R.string.navigate_tile_home) to (tile.distance ?: tile.place.label)
        TileKind.Work -> if (tile.place == null) stringResource(R.string.navigate_tile_set_work) to set
        else stringResource(R.string.navigate_tile_work) to (tile.distance ?: tile.place.label)
        TileKind.LastTrip -> if (tile.place == null) stringResource(R.string.navigate_tile_last_trip) to stringResource(R.string.navigate_tile_last_none)
        else tile.place.label to (tile.distance ?: stringResource(R.string.navigate_tile_last_trip))
        TileKind.PickOnMap -> stringResource(R.string.navigate_tile_pick) to stringResource(R.string.navigate_tile_pick_caption)
    }
}

private fun tileIcon(kind: TileKind) = when (kind) {
    TileKind.Home -> KtmIcons.Home
    TileKind.Work -> KtmIcons.Work
    TileKind.LastTrip -> KtmIcons.History
    TileKind.PickOnMap -> KtmIcons.PinPlus
}

@Composable
private fun PlaceRow(
    place: SavedPlace,
    icon: ImageVector,
    origin: Pair<Double, Double>?,
    onPlace: (SavedPlace) -> Unit,
    onRemove: ((SavedPlace) -> Unit)? = null,
) {
    ListRow(
        title = place.label,
        supporting = place.address,
        leadingIcon = icon,
        onClick = { onPlace(place) },
        onLongClick = onRemove?.let { remove -> { remove(place) } },
        onLongClickLabel = onRemove?.let { stringResource(R.string.search_remove_recent) },
        trailing = { StraightLine.label(origin, place)?.let { RowValue(it) } },
    )
}
