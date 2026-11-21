package com.navigator.app.ui.feature.navigate.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.shell.LocalOpenPlaces
import com.navigator.app.ui.theme.KtmMotionSpecs
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.tween
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.nav.destination.CategoryResult
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.ui.components.ActivityStrip
import com.navigator.app.ui.components.ActivityStripState
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmFilterChip
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmSearchField
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.consumeMapTouches
import com.navigator.app.ui.components.highlightedText
import com.navigator.app.ui.components.RowValue
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.feature.navigate.ActivityCardModel
import com.navigator.app.ui.feature.navigate.StraightLine
import com.navigator.app.ui.feature.navigate.detailText
import com.navigator.app.ui.feature.navigate.titleText
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class PlaceSearchActions(
    val onBack: () -> Unit,
    val onQuery: (String) -> Unit,
    val onVoice: () -> Unit,
    val onCategory: (PlaceCategory?) -> Unit,
    val onSuggestion: (PlaceSuggestion) -> Unit,
    val onPlace: (SavedPlace) -> Unit,
    val onCategoryResult: (CategoryResult) -> Unit,
    val onRemoveRecent: (SavedPlace) -> Unit,
    val onResumeTrip: (() -> Unit)?,
    /** "Pick on map" row of the empty list (adding a stop); null when the mode has none. */
    val onPickOnMap: (() -> Unit)? = null,
    /** Pick mode: save where the rider is now. */
    val onUseCurrent: (() -> Unit)? = null,
    /** Search again: after the connection came back, or a failed category load. */
    val onRetry: () -> Unit = {},
)

@StringRes
fun PlaceCategory.labelRes(): Int = when (this) {
    PlaceCategory.FUEL -> R.string.search_category_fuel
    PlaceCategory.FOOD -> R.string.search_category_food
    PlaceCategory.COFFEE -> R.string.search_category_coffee
    PlaceCategory.PARKING -> R.string.search_category_parking
    PlaceCategory.ATM -> R.string.search_category_atm
    PlaceCategory.HOSPITAL -> R.string.search_category_hospital
    PlaceCategory.PHARMACY -> R.string.search_category_pharmacy
    PlaceCategory.EV -> R.string.search_category_ev
}

@StringRes
private fun SearchMode.hint(): Int = when (this) {
    SearchMode.Destination -> R.string.navigate_field_hint
    SearchMode.Stop, SearchMode.TripStop -> R.string.search_hint_stop
    is SearchMode.Pick -> when (slot) {
        FavoriteSlot.HOME -> R.string.search_hint_home
        FavoriteSlot.WORK -> R.string.search_hint_work
        FavoriteSlot.OTHER -> R.string.search_hint_add
    }
}

/**
 * The one place search (SE1-SE3, SE5): a 56 dp field with voice or clear, category chips, then saved and
 * recent places, typed results (one line each, the match in bold), or category results. Offline it still
 * searches the rider's own places. Stateless; [PlaceSearchRoute] hosts it.
 */
@Composable
fun PlaceSearchScreen(
    state: PlaceSearchState,
    mode: SearchMode,
    voiceAvailable: Boolean,
    actions: PlaceSearchActions,
    modifier: Modifier = Modifier,
    activity: ActivityCardModel? = null,
    focusRequester: FocusRequester? = null,
) {
    val c = KtmTheme.colors
    Column(
        modifier.fillMaxSize().background(c.bg).consumeMapTouches().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.padding(horizontal = KtmSpacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            KtmSearchField(
                value = state.query,
                onValueChange = actions.onQuery,
                placeholder = stringResource(mode.hint()),
                modifier = Modifier.weight(1f),
                focusRequester = focusRequester,
                trailing = {
                    when {
                        state.query.isNotEmpty() ->
                            KtmIconButton(KtmIcons.Close, stringResource(R.string.search_clear), { actions.onQuery("") })
                        voiceAvailable ->
                            KtmIconButton(KtmIcons.Mic, stringResource(R.string.navigate_voice), actions.onVoice)
                    }
                },
            )
        }
        Column(Modifier.padding(top = KtmSpacing.md), verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            if (mode == SearchMode.TripStop) {
                Text(
                    stringResource(R.string.search_add_stop_header), style = MaterialTheme.typography.titleLarge,
                    color = KtmTheme.accent.text, modifier = Modifier.padding(horizontal = KtmSpacing.lg),
                )
            }
            if (activity != null && actions.onResumeTrip != null && mode != SearchMode.TripStop) {
                ActivityStrip(
                    ActivityStripState.Trip(activity.titleText(), activity.detailText(), activity.glyph),
                    onOpen = actions.onResumeTrip, onResume = actions.onResumeTrip, modifier = Modifier.padding(horizontal = KtmSpacing.lg),
                )
            }
            if (state.query.isBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), contentPadding = PaddingValues(horizontal = KtmSpacing.lg)) {
                    items(PlaceCategory.entries.toList(), key = { it.name }) { category ->
                        KtmFilterChip(
                            stringResource(category.labelRes()), selected = state.category == category,
                            onClick = { actions.onCategory(if (state.category == category) null else category) },
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = KtmSpacing.sm)) {
            when (state.view) {
                SearchView.Category -> CategoryList(state, actions)
                SearchView.Results -> ResultList(state, actions)
                SearchView.Browse -> BrowseList(state, mode, actions)
            }
        }
    }
}

@Composable
private fun BrowseList(state: PlaceSearchState, mode: SearchMode, actions: PlaceSearchActions) {
    val saved = SearchPolicy.browseFavorites(state.favorites, mode)
    val pickOnMap = actions.onPickOnMap
    val useCurrent = actions.onUseCurrent
    if (saved.isEmpty() && state.recents.isEmpty()) {
        Column {
            if (useCurrent != null) UseCurrentRow(useCurrent)
            if (pickOnMap != null) PickOnMapRow(pickOnMap)
            Text(
                stringResource(R.string.navigate_empty_hint), style = MaterialTheme.typography.bodyMedium,
                color = KtmTheme.colors.fg2, modifier = Modifier.padding(KtmSpacing.lg),
            )
        }
        return
    }
    LazyColumn {
        if (useCurrent != null) item { UseCurrentRow(useCurrent) }
        if (pickOnMap != null) item { PickOnMapRow(pickOnMap) }
        if (saved.isNotEmpty()) {
            item {
                val openPlaces = LocalOpenPlaces.current
                // Pick mode already is the Places tab's own flow, so it has no link there.
                val link = openPlaces.takeIf { mode !is SearchMode.Pick }
                SectionHeader(
                    stringResource(R.string.navigate_saved_header),
                    action = stringResource(R.string.navigate_saved_see_all).takeIf { link != null },
                    onAction = link?.let { go -> { actions.onBack(); go() } },
                )
            }
            itemsIndexed(saved, key = { _, it -> "s-${it.slot}-${it.place.lat},${it.place.lng}" }) { index, it ->
                if (index > 0) ListDivider()
                val icon = when (it.slot) {
                    FavoriteSlot.HOME -> KtmIcons.Home
                    FavoriteSlot.WORK -> KtmIcons.Work
                    FavoriteSlot.OTHER -> KtmIcons.Bookmark
                }
                PlaceRow(it.place, icon, state.origin, actions.onPlace)
            }
        }
        if (state.recents.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.navigate_recent_header), trailing = stringResource(R.string.search_straight_line).takeIf { state.origin != null }) }
            itemsIndexed(state.recents, key = { _, it -> "r-${it.lat},${it.lng}" }) { index, it ->
                if (index > 0) ListDivider()
                PlaceRow(it, KtmIcons.History, state.origin, actions.onPlace, onRemove = { actions.onRemoveRecent(it) })
            }
        }
    }
}

@Composable
private fun UseCurrentRow(onClick: () -> Unit) {
    ListRow(
        title = stringResource(R.string.search_use_current), supporting = stringResource(R.string.search_use_current_caption),
        leadingIcon = KtmIcons.Crosshair, onClick = onClick,
    )
}

@Composable
private fun PickOnMapRow(onClick: () -> Unit) {
    ListRow(
        title = stringResource(R.string.navigate_tile_pick), supporting = stringResource(R.string.navigate_tile_pick_caption),
        leadingIcon = KtmIcons.Pin, onClick = onClick,
    )
}

@Composable
private fun ResultList(state: PlaceSearchState, actions: PlaceSearchActions) {
    when {
        // Offline first: no skeleton rows for an answer that cannot come.
        state.offline -> Column {
            InfoBanner(
                stringResource(R.string.search_offline_message), Modifier.padding(horizontal = KtmSpacing.lg), title = stringResource(R.string.search_offline_title),
                tone = BannerTone.Warn, icon = KtmIcons.Offline,
            )
            KtmButton(
                stringResource(R.string.search_try_again), actions.onRetry, Modifier.padding(start = KtmSpacing.lg, top = KtmSpacing.md),
                style = ButtonStyle.Outline, size = ButtonSize.Small, icon = KtmIcons.Refresh,
            )
            if (state.localResults.isEmpty()) NoMatches(state.query)
            else LazyColumn {
                item { SectionHeader(stringResource(R.string.search_local_header)) }
                itemsIndexed(state.localResults, key = { _, it -> "l-${it.lat},${it.lng}" }) { index, it ->
                    if (index > 0) ListDivider()
                    PlaceRow(it, KtmIcons.Pin, state.origin, actions.onPlace)
                }
            }
        }
        state.loading && state.suggestions.isEmpty() -> Column { repeat(4) { SkeletonRow(announce = it == 0) } }
        state.suggestions.isEmpty() -> NoMatches(state.query)
        else -> LazyColumn(Modifier.alpha(if (state.resolving) KtmLayout.disabledAlpha else 1f)) {
            itemsIndexed(state.suggestions, key = { _, s -> s.placeId }) { index, s ->
                // SE2: results fade in 20 ms after one another.
                Column(Modifier.staggeredFade(this, index, KtmTheme.motion)) {
                    if (index > 0) ListDivider()
                    MatchRow(s, state.query) { if (!state.resolving) actions.onSuggestion(s) }
                }
            }
        }
    }
}

@Composable
private fun CategoryList(state: PlaceSearchState, actions: PlaceSearchActions) {
    when {
        state.loading -> Column { repeat(4) { SkeletonRow(announce = it == 0) } }
        state.categoryFailed -> Column {
            InfoBanner(stringResource(R.string.search_failed), Modifier.padding(horizontal = KtmSpacing.lg), tone = BannerTone.Bad)
            KtmButton(
                stringResource(R.string.search_try_again), actions.onRetry, Modifier.padding(start = KtmSpacing.lg, top = KtmSpacing.md),
                style = ButtonStyle.Outline, size = ButtonSize.Small, icon = KtmIcons.Refresh,
            )
        }
        state.categoryResults.isEmpty() -> Text(
            stringResource(R.string.search_nothing_nearby), style = MaterialTheme.typography.bodyMedium,
            color = KtmTheme.colors.fg2, modifier = Modifier.padding(KtmSpacing.lg),
        )
        else -> LazyColumn {
            item { SectionHeader(stringResource(if (state.alongRoute) R.string.search_along_route else R.string.search_near_you)) }
            itemsIndexed(state.categoryResults, key = { _, r -> "${r.lat},${r.lng},${r.name}" }) { index, r ->
                Column(Modifier.staggeredFade(this, index, KtmTheme.motion)) {
                    if (index > 0) ListDivider()
                    ListRow(
                        title = r.name, supporting = r.address, leadingIcon = KtmIcons.Pin,
                        onClick = { actions.onCategoryResult(r) },
                        trailing = { r.distanceMeters?.takeIf { state.origin != null }?.let { RowValue(DistanceFormatter.format(it, DistanceUnits.METRIC)) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoMatches(query: String) {
    Text(
        if (query.isBlank()) stringResource(R.string.search_no_matches) else stringResource(R.string.search_nothing_for, query.trim()),
        style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
        modifier = Modifier.padding(KtmSpacing.lg).semantics { this.liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun PlaceRow(
    place: SavedPlace,
    icon: ImageVector,
    origin: Pair<Double, Double>?,
    onPlace: (SavedPlace) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    ListRow(
        title = place.label, supporting = place.address, leadingIcon = icon,
        onClick = { onPlace(place) },
        onLongClick = onRemove,
        onLongClickLabel = onRemove?.let { stringResource(R.string.search_remove_recent) },
        trailing = { StraightLine.label(origin, place)?.let { RowValue(it) } },
    )
}

/** One line per result: the name with what was typed in bold, the address after it, cut off at the end. */
@Composable
private fun MatchRow(s: PlaceSuggestion, query: String, onClick: () -> Unit) {
    ListRow(
        title = s.primary,
        annotatedTitle = highlightedText(s.primary, SearchPolicy.matchRange(s.primary, query)),
        supporting = s.secondary,
        leadingIcon = KtmIcons.Pin,
        onClick = onClick,
        trailing = { s.distanceMeters?.takeIf { it > 0 }?.let { RowValue(DistanceFormatter.format(it, DistanceUnits.METRIC)) } },
    )
}

private const val STAGGER_MS = 20
private const val STAGGER_ROWS = 10

/** SE2: a result fades in 20 ms after the one above it; with "Remove animations" they just appear, and don't move. */
private fun Modifier.staggeredFade(scope: LazyItemScope, index: Int, motion: KtmMotionSpecs): Modifier = with(scope) {
    animateItem(
        fadeInSpec = if (motion.reduced) motion.effectsFast() else tween(KtmMotionSpecs.EFFECTS_DEFAULT_MS, delayMillis = index.coerceAtMost(STAGGER_ROWS) * STAGGER_MS),
        placementSpec = if (motion.reduced) null else motion.spatialDefault(),
    )
}
