package com.navigator.app.ui.feature.rides

import com.navigator.app.ui.shell.rideContainer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.data.rides.ThumbPoint
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.KtmCheckboxRow
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmMenuItem
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmRadioRow
import com.navigator.app.ui.components.KtmSearchField
import com.navigator.app.ui.components.KtmSelectionBar
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.LargeTitle
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.components.StateAction
import com.navigator.app.ui.feature.record.RecordFab
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RidesActions(
    val onOpenRide: (String) -> Unit,
    val onToggleStar: (String) -> Unit,
    val onOpenSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onQuery: (String) -> Unit,
    val onSort: (RideSort) -> Unit,
    val onSelect: (String?) -> Unit,
    val onToggleSelected: (String) -> Unit,
    val onSelectAll: (Boolean) -> Unit,
    val onStopSelecting: () -> Unit,
    val onStarSelected: () -> Unit,
    val onMerge: () -> Unit,
    val onDelete: () -> Unit,
    val onImport: () -> Unit,
    val onRecordingSettings: () -> Unit,
    val onRecord: () -> Unit,
    val onTurnOnAutoRecord: () -> Unit,
)

/**
 * RD1-RD6: the Rides tab. A top bar with search, sort and a menu; the list with sticky day headers; a contextual bar
 * while selecting; the empty and no-match states. Stateless.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RidesContent(
    ui: RidesUi?,
    autoRecordOn: Boolean,
    recording: Boolean,
    thumbnail: suspend (RecordedRide) -> List<ThumbPoint>,
    actions: RidesActions,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val c = KtmTheme.colors
    var showSort by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Back to the top when the order or the search starts or ends, not on every keystroke of a refinement.
    val searching = ui?.query?.isNotBlank() == true
    LaunchedEffect(ui?.sort, searching) { listState.scrollToItem(0) }

    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            when {
                ui == null -> RidesBar(actions, hasRides = false, onSort = {})
                ui.selecting -> SelectionBar(ui, actions)
                ui.searchOpen -> SearchBar(ui.query, actions)
                else -> RidesBar(actions, hasRides = ui.body !is RidesBody.Empty, onSort = { showSort = true })
            }
            if (ui == null || (!ui.searchOpen && !ui.selecting)) LargeTitle(stringResource(R.string.rides_title))
            val motionSpecs = KtmTheme.motion
            AnimatedContent(targetState = ui, contentKey = { it?.body?.let { b -> b::class } }, transitionSpec = { fadeIn(motionSpecs.effectsDefault()) togetherWith fadeOut(motionSpecs.effectsFast()) using SizeTransform { _, _ -> snap() } }, label = "loading") { shown ->
                when (val body = shown?.body) {
                    null -> Column { repeat(4) { SkeletonRow(announce = it == 0) } }
                    RidesBody.Empty -> EmptyRides(autoRecordOn, recording, actions)
                    RidesBody.NoMatch -> EmptyState(
                        KtmIcons.Search, stringResource(R.string.rides_no_match_title, shown?.query.orEmpty().trim()),
                        stringResource(R.string.rides_no_match_message),
                        primary = StateAction(stringResource(R.string.rides_clear_search)) { actions.onCloseSearch() },
                    )
                    is RidesBody.Items -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = KtmSpacing.edge, end = KtmSpacing.edge, bottom = FAB_CLEARANCE),
                        verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
                    ) {
                        if (shown.selecting) {
                            item(key = "select-all") {
                                KtmCheckboxRow(
                                    title = stringResource(if (shown.allSelected) R.string.rides_select_none else R.string.rides_select_all),
                                    checked = shown.allSelected, onCheckedChange = actions.onSelectAll,
                                )
                            }
                        }
                        body.items.forEach { item ->
                            when (item) {
                                is RideListItem.Header -> stickyHeader(key = item.key) { DayHeader(item.date, today) }
                                is RideListItem.Ride -> item(key = item.key) {
                                    val id = item.ride.id
                                    RideCard(
                                        item, selecting = shown.selecting, selected = id in shown.selected, thumbnail = thumbnail,
                                        onClick = { if (shown.selecting) actions.onToggleSelected(id) else actions.onOpenRide(id) },
                                        onLongClick = { if (shown.selecting) actions.onToggleSelected(id) else actions.onSelect(id) },
                                        onToggleStar = { actions.onToggleStar(id) },
                                        modifier = Modifier.animateItem(fadeInSpec = KtmTheme.motion.effectsDefault(), placementSpec = KtmTheme.motion.spatialDefault(), fadeOutSpec = KtmTheme.motion.effectsFast()).rideContainer(id),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val canRecord = !recording && ui != null && !ui.selecting && !ui.searchOpen && ui.body !is RidesBody.Empty
        if (canRecord) {
            RecordFab(
                stringResource(R.string.rides_record), actions.onRecord,
                Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                    .padding(KtmSpacing.lg),
            )
        }
    }
    if (showSort && ui != null) {
        KtmModalSheet(onDismiss = { showSort = false }, title = stringResource(R.string.rides_sort_title)) {
            RideSort.options.forEach { option ->
                KtmRadioRow(stringResource(option.label), option == ui.sort, { actions.onSort(option); showSort = false })
            }
        }
    }
}

/** Room under the last card for the Record button. */
private val FAB_CLEARANCE = KtmLayout.targetPrimary + KtmSpacing.lg * 2

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RidesBar(actions: RidesActions, hasRides: Boolean, onSort: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    KtmTopBar {
        if (hasRides) {
            KtmIconButton(KtmIcons.Search, stringResource(R.string.rides_search_open), actions.onOpenSearch)
            KtmIconButton(KtmIcons.Sort, stringResource(R.string.rides_sort_open), onSort)
        }
        Box {
            KtmIconButton(KtmIcons.More, stringResource(R.string.rides_more), { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = KtmTheme.colors.surf) {
                if (hasRides) KtmMenuItem(stringResource(R.string.rides_menu_select)) { menu = false; actions.onSelect(null) }
                KtmMenuItem(stringResource(R.string.rides_menu_import)) { menu = false; actions.onImport() }
                KtmMenuItem(stringResource(R.string.rides_menu_recording)) { menu = false; actions.onRecordingSettings() }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, actions: RidesActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .fillMaxWidth().height(KtmLayout.topBar).padding(start = KtmSpacing.xs, end = KtmSpacing.edge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.rides_search_close), actions.onCloseSearch)
        KtmSearchField(
            query, actions.onQuery, stringResource(R.string.rides_search_hint), Modifier.weight(1f), focusRequester = focus,
            trailing = {
                if (query.isNotEmpty()) KtmIconButton(KtmIcons.Close, stringResource(R.string.rides_search_clear), { actions.onQuery("") })
            },
        )
    }
}

/** The tinted bar that takes the top bar's place while selecting: count, Star, Merge, Delete. */
@Composable
private fun SelectionBar(ui: RidesUi, actions: RidesActions) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    KtmSelectionBar(
        pluralStringResource(R.plurals.rides_selected_count, ui.selected.size, ui.selected.size),
        stringResource(R.string.rides_selection_close), actions.onStopSelecting,
    ) {
        val has = ui.selected.isNotEmpty()
        KtmIconButton(
            if (ui.starWould) KtmIcons.Star else KtmIcons.StarFilled,
            stringResource(if (ui.starWould) R.string.rides_star_selected else R.string.rides_unstar_selected),
            actions.onStarSelected, enabled = has, tint = if (has) a.onContainer else c.fg3,
        )
        if (ui.selected.size >= 2) {
            KtmIconButton(KtmIcons.Merge, stringResource(R.string.rides_merge_selected), actions.onMerge, tint = a.onContainer)
        }
        KtmIconButton(
            KtmIcons.Delete, stringResource(R.string.rides_delete_selected), actions.onDelete, enabled = has,
            tint = if (has) a.onContainer else c.fg3,
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val label = when (RidesListBuilder.dayKind(date, today)) {
        DayKind.Today -> stringResource(R.string.rides_day_today)
        DayKind.Yesterday -> stringResource(R.string.rides_day_yesterday)
        DayKind.ThisYear -> date.format(DAY)
        DayKind.Older -> date.format(DAY_YEAR)
    }
    Text(
        label, style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2,
        modifier = Modifier.fillMaxWidth().background(KtmTheme.colors.bg).padding(top = KtmSpacing.sm, bottom = KtmSpacing.xs)
            .semantics { heading() },
    )
}

private fun dayFormatter(skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), skeleton))

private val DAY: DateTimeFormatter by lazy { dayFormatter("EEEdMMM") }
private val DAY_YEAR: DateTimeFormatter by lazy { dayFormatter("dMMMyyyy") }

@Composable
private fun EmptyRides(autoRecordOn: Boolean, recording: Boolean, actions: RidesActions) {
    EmptyState(
        icon = KtmIcons.Route,
        title = stringResource(R.string.rides_empty_title),
        message = stringResource(if (autoRecordOn) R.string.rides_empty_message else R.string.rides_empty_message_auto_off),
        // A ride is already being recorded: the strip above the tabs owns it, so no second Record button.
        primary = if (recording) null else StateAction(stringResource(R.string.rides_record), actions.onRecord),
        secondary = StateAction(stringResource(R.string.rides_menu_import), actions.onImport),
        tertiary = if (autoRecordOn) null else StateAction(stringResource(R.string.rides_empty_turn_on), actions.onTurnOnAutoRecord),
    )
}
