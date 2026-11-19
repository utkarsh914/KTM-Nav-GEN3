package com.navigator.app.ui.feature.navigate.search

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
import androidx.compose.ui.text.style.TextOverflow
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
import com.navigator.app.ui.components.highlightedText
import com.navigator.app.ui.components.RowValue
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.feature.navigate.ActivityCardModel
import com.navigator.app.ui.feature.navigate.StraightLine
import com.navigator.app.ui.feature.navigate.detailText
import com.navigator.app.ui.feature.navigate.titleText
import com.navigator.app.ui.theme.KtmIcons
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
    is SearchMode.Pick -> if (slot == FavoriteSlot.HOME) R.string.search_hint_home else R.string.search_hint_work
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
        modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = KtmSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
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
                    color = KtmTheme.accent.text,
                )
            }
            if (activity != null && actions.onResumeTrip != null && mode != SearchMode.TripStop) {
                ActivityStrip(
                    ActivityStripState.Trip(activity.titleText(), activity.detailText()),
                    onOpen = actions.onResumeTrip, onResume = actions.onResumeTrip,
                )
            }
            if (state.query.isBlank()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
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
    val saved = state.favorites.filter { it.slot == FavoriteSlot.OTHER }.map { it.place }
    if (saved.isEmpty() && state.recents.isEmpty()) {
        Text(
            stringResource(R.string.navigate_empty_hint), style = MaterialTheme.typography.bodyMedium,
            color = KtmTheme.colors.fg2, modifier = Modifier.padding(KtmSpacing.sm),
        )
        return
    }
    LazyColumn {
        if (saved.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.navigate_saved_header)) }
            items(saved, key = { "s-${it.lat},${it.lng}" }) { PlaceRow(it, KtmIcons.Bookmark, state.origin, actions.onPlace) }
        }
        if (state.recents.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.navigate_recent_header)) }
            items(state.recents, key = { "r-${it.lat},${it.lng}" }) {
                PlaceRow(it, KtmIcons.History, state.origin, actions.onPlace, onRemove = { actions.onRemoveRecent(it) })
            }
        }
    }
}

@Composable
private fun ResultList(state: PlaceSearchState, actions: PlaceSearchActions) {
    when {
        // Offline first: no skeleton rows for an answer that cannot come.
        state.offline -> Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            InfoBanner(stringResource(R.string.search_offline), tone = BannerTone.Info, icon = KtmIcons.Offline)
            if (state.localResults.isEmpty()) NoMatches()
            else LazyColumn { items(state.localResults, key = { "l-${it.lat},${it.lng}" }) { PlaceRow(it, KtmIcons.Pin, state.origin, actions.onPlace) } }
        }
        state.loading && state.suggestions.isEmpty() -> Column { repeat(4) { SkeletonRow() } }
        state.suggestions.isEmpty() -> NoMatches()
        else -> LazyColumn(Modifier.alpha(if (state.resolving) 0.5f else 1f)) {
            items(state.suggestions, key = { it.placeId }) { s ->
                MatchRow(s, state.query) { if (!state.resolving) actions.onSuggestion(s) }
            }
        }
    }
}

@Composable
private fun CategoryList(state: PlaceSearchState, actions: PlaceSearchActions) {
    when {
        state.loading -> Column { repeat(4) { SkeletonRow() } }
        state.categoryFailed -> InfoBanner(stringResource(R.string.search_failed), tone = BannerTone.Bad)
        state.categoryResults.isEmpty() -> Text(
            stringResource(R.string.search_nothing_nearby), style = MaterialTheme.typography.bodyMedium,
            color = KtmTheme.colors.fg2, modifier = Modifier.padding(KtmSpacing.sm),
        )
        else -> LazyColumn {
            item { SectionHeader(stringResource(if (state.alongRoute) R.string.search_along_route else R.string.search_near_you)) }
            items(state.categoryResults, key = { "${it.lat},${it.lng},${it.name}" }) { r ->
                ListRow(
                    title = r.name, supporting = r.address, leadingIcon = KtmIcons.Pin,
                    onClick = { actions.onCategoryResult(r) },
                    trailing = { r.distanceMeters?.takeIf { state.origin != null }?.let { RowValue(DistanceFormatter.format(it, DistanceUnits.METRIC)) } },
                )
            }
        }
    }
}

@Composable
private fun NoMatches() {
    Text(
        stringResource(R.string.search_no_matches), style = MaterialTheme.typography.bodyMedium,
        color = KtmTheme.colors.fg2, modifier = Modifier.padding(KtmSpacing.sm),
    )
}

@Composable
private fun PlaceRow(
    place: SavedPlace,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
