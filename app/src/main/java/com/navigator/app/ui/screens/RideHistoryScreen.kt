package com.navigator.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideStore
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ui.components.DeleteConfirmDialog
import com.navigator.app.ui.components.Eyebrow
import com.navigator.app.ui.components.PopIconButton
import com.navigator.app.ui.components.ScreenTopBar
import com.navigator.app.ui.components.SelectableCard
import com.navigator.app.ui.components.SelectionTopBarActions
import com.navigator.app.ui.components.rememberPressScale
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.Motion
import com.navigator.app.ui.theme.OpenDashIcons

/** What the history list is ordered by; direction (asc/desc) is separate. */
enum class RideSortField { DATE, DISTANCE, DURATION, TOP_SPEED }

/** Text style that drops the built-in glyph padding so the stat strip can pack
 *  the value + label tightly together. */
private val TightTextStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

/**
 * Lists recorded rides. A plain tap opens the replay; a long-press enters
 * multi-select mode (selection + search/sort state is hoisted to [MainActivity]
 * so it survives opening a ride and coming back). Deletes ask for confirmation,
 * and a star pins a ride so the history-limit prune never removes it.
 *
 * The list can be searched by place name and sorted by a field + direction; when
 * sorted by date (and not searching) rides are grouped under sticky day headers
 * (Today / Yesterday / date). Filtering, sorting and deletes animate in place.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RideHistoryScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectedIdsChange: (Set<String>) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sortField: RideSortField,
    onSortFieldChange: (RideSortField) -> Unit,
    sortDesc: Boolean,
    onSortDescChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { RideStore(context) }
    var rides by remember { mutableStateOf(store.list()) }
    // Confirmation target: a single ride id, or the sentinel for "delete selected".
    var confirmDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    // Held here (outside AnimatedContent) so it survives the list<->empty swaps
    // and can be reset when the visible set changes.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun refresh() { rides = store.list() }

    fun toggle(id: String) {
        onSelectedIdsChange(if (id in selectedIds) selectedIds - id else selectedIds + id)
    }

    fun enterSelection(id: String) {
        onSelectionModeChange(true)
        onSelectedIdsChange(selectedIds + id)
    }

    fun exitSelection() {
        onSelectionModeChange(false)
        onSelectedIdsChange(emptySet())
    }

    // Search + sort pipeline. Selection acts on the currently visible rides.
    val q = query.trim()
    val filtered = if (q.isEmpty()) rides
        else rides.filter { (it.destinationLabel ?: "").contains(q, ignoreCase = true) }
    val comparator: Comparator<RecordedRide> = when (sortField) {
        RideSortField.DATE -> compareBy { it.startMs }
        RideSortField.DISTANCE -> compareBy { it.distanceMeters }
        RideSortField.DURATION -> compareBy { it.durationSeconds }
        RideSortField.TOP_SPEED -> compareBy { it.maxSpeedKmh }
    }
    val sorted = filtered.sortedWith(if (sortDesc) comparator.reversed() else comparator)
    val visibleIds = sorted.map { it.id }.toSet()
    val validSelected = selectedIds.intersect(visibleIds)
    // Group under day headers only when ordered by date and not searching.
    val grouped = q.isEmpty() && sortField == RideSortField.DATE

    // Smoothly return to the top when the search is cleared or the sort changes.
    // (We intentionally don't scroll on every keystroke while refining a query, so
    //  the per-item filter animations aren't interrupted by a programmatic scroll.)
    val queryCleared = q.isEmpty()
    androidx.compose.runtime.LaunchedEffect(queryCleared, sortField, sortDesc) {
        listState.animateScrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize().background(Ktm.Screen).systemBarsPadding()) {
        ScreenTopBar(
            title = if (selectionMode) "${validSelected.size}/${sorted.size} selected" else "Ride recordings",
            onBack = onBack,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
        ) {
            if (selectionMode) {
                SelectionTopBarActions(
                    allSelected = sorted.isNotEmpty() && validSelected.size == sorted.size,
                    hasSelection = validSelected.isNotEmpty(),
                    onToggleSelectAll = {
                        val allSelected = sorted.isNotEmpty() && validSelected.size == sorted.size
                        onSelectedIdsChange(if (allSelected) emptySet() else visibleIds)
                    },
                    onDelete = { confirmDelete = DeleteTarget.Selected },
                )
            }
        }

        if (rides.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "No rides recorded yet.\nStart a navigation to record one.",
                    color = Ktm.Muted2, fontFamily = Barlow, fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Search + sort toolbar animates in/out with selection mode.
            AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(tween(Motion.Medium)) + expandVertically(tween(Motion.Medium)),
                exit = fadeOut(tween(Motion.Fast)) + shrinkVertically(tween(Motion.Fast)),
            ) {
                RideToolbar(
                    query = query,
                    onQueryChange = onQueryChange,
                    sortField = sortField,
                    onSortFieldChange = onSortFieldChange,
                    sortDesc = sortDesc,
                    onSortDescChange = onSortDescChange,
                )
            }

            AnimatedContent(
                targetState = sorted.isEmpty(),
                transitionSpec = { Motion.driftTransition() },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "ridesContent",
            ) { isEmpty ->
                if (isEmpty) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No rides match \"$q\".",
                            color = Ktm.Muted2, fontFamily = Barlow, fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (grouped) {
                            val groups = sorted.groupBy { rideLocalDate(it.startMs) }
                            groups.forEach { (date, dayRides) ->
                                stickyHeader(key = "hdr-$date") { DateHeader(dayHeaderLabel(date)) }
                                items(dayRides, key = { it.id }) { ride ->
                                    RideCard(
                                        modifier = Modifier.animateItem(),
                                        ride = ride,
                                        subtitle = formatRideDate(ride.startMs),
                                        selectionMode = selectionMode,
                                        selected = ride.id in validSelected,
                                        onTap = { if (selectionMode) toggle(ride.id) else onOpen(ride.id) },
                                        onLongPress = { if (!selectionMode) enterSelection(ride.id) else toggle(ride.id) },
                                        onToggleSave = { toggleSave(ride, store, context, ::refresh) },
                                        onDelete = { confirmDelete = DeleteTarget.Single(ride.id) },
                                    )
                                }
                            }
                        } else {
                            items(sorted, key = { it.id }) { ride ->
                                RideCard(
                                    modifier = Modifier.animateItem(),
                                    ride = ride,
                                    subtitle = formatRideDate(ride.startMs),
                                    selectionMode = selectionMode,
                                    selected = ride.id in validSelected,
                                    onTap = { if (selectionMode) toggle(ride.id) else onOpen(ride.id) },
                                    onLongPress = { if (!selectionMode) enterSelection(ride.id) else toggle(ride.id) },
                                    onToggleSave = { toggleSave(ride, store, context, ::refresh) },
                                    onDelete = { confirmDelete = DeleteTarget.Single(ride.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    RideDeleteDialog(confirmDelete, validSelected, store, ::refresh, ::exitSelection) { confirmDelete = it }
}

/** Toggle a ride's saved/pinned flag and show a brief confirmation toast. */
private fun toggleSave(ride: RecordedRide, store: RideStore, context: android.content.Context, refresh: () -> Unit) {
    val newSaved = !ride.saved
    store.setSaved(ride.id, newSaved)
    refresh()
    android.widget.Toast.makeText(
        context,
        if (newSaved) "Ride saved" else "Ride unsaved",
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}

/** Renders the shared delete-confirmation dialog for the current [target] (if any). */
@Composable
private fun RideDeleteDialog(
    target: DeleteTarget?,
    validSelected: Set<String>,
    store: RideStore,
    refresh: () -> Unit,
    exitSelection: () -> Unit,
    setTarget: (DeleteTarget?) -> Unit,
) {
    target ?: return
    val count = when (target) {
        is DeleteTarget.Single -> 1
        DeleteTarget.Selected -> validSelected.size
    }
    DeleteConfirmDialog(
        title = if (count > 1) "Delete $count rides?" else "Delete this ride?",
        message = "This permanently removes the recorded track. This can't be undone.",
        onConfirm = {
            when (target) {
                is DeleteTarget.Single -> store.delete(target.id)
                DeleteTarget.Selected -> {
                    store.deleteAll(validSelected)
                    exitSelection()
                }
            }
            refresh()
            setTarget(null)
        },
        onDismiss = { setTarget(null) },
    )
}

private sealed interface DeleteTarget {
    data class Single(val id: String) : DeleteTarget
    data object Selected : DeleteTarget
}

/** Search field + sort chip toolbar shown above the list. */
@Composable
private fun RideToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortField: RideSortField,
    onSortFieldChange: (RideSortField) -> Unit,
    sortDesc: Boolean,
    onSortDescChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideSearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(10.dp))
        SortChip(
            sortField = sortField,
            sortDesc = sortDesc,
            onSortFieldChange = onSortFieldChange,
            onSortDescChange = onSortDescChange,
        )
    }
}

/** Editable search field styled to match the browse-map search bar (52dp tall). */
@Composable
private fun RideSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(Ktm.ControlHeight)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Search, null, tint = Ktm.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search rides", color = Ktm.Dim, fontFamily = Barlow, fontSize = 16.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Ktm.White, fontFamily = Barlow, fontSize = 16.sp),
                cursorBrush = SolidColor(Ktm.Orange),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(tween(Motion.Medium)) + scaleIn(tween(Motion.Medium)),
            exit = fadeOut(tween(Motion.Fast)) + scaleOut(tween(Motion.Fast)),
        ) {
            Row {
                Spacer(Modifier.size(8.dp))
                Icon(
                    OpenDashIcons.Close, "Clear search", tint = Ktm.Dim,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).clickable { onQueryChange("") },
                )
            }
        }
    }
}

/** Compact sort control (52dp): shows the active field + a rotating direction
 *  arrow, and opens a dropdown with "Sort by" + "Direction" sections. */
@Composable
private fun SortChip(
    sortField: RideSortField,
    sortDesc: Boolean,
    onSortFieldChange: (RideSortField) -> Unit,
    onSortDescChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    // Descending = arrow points down (0°); ascending flips it up.
    val arrowRotation by animateFloatAsState(if (sortDesc) 0f else 180f, label = "sortArrow")
    Box {
        Row(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .height(Ktm.ControlHeight)
                .clip(RoundedCornerShape(Ktm.RadiusButton))
                .background(Ktm.Surface)
                .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
                .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                    haptics.tap(); expanded = true
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                sortField.label(), color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.size(6.dp))
            Icon(
                OpenDashIcons.ChevronDown, "Sort direction", tint = Ktm.Muted,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuSectionLabel("Sort by")
            RideSortField.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label(),
                            color = if (option == sortField) Ktm.Orange else Ktm.TextPrimary,
                            fontFamily = Barlow, fontSize = 14.sp,
                        )
                    },
                    onClick = { onSortFieldChange(option); expanded = false },
                    trailingIcon = {
                        if (option == sortField) {
                            Icon(OpenDashIcons.Check, null, tint = Ktm.Orange, modifier = Modifier.size(18.dp))
                        }
                    },
                )
            }
            HorizontalDivider(color = Ktm.RowDivider)
            MenuSectionLabel("Direction")
            DirectionItem("Descending", active = sortDesc) { onSortDescChange(true); expanded = false }
            DirectionItem("Ascending", active = !sortDesc) { onSortDescChange(false); expanded = false }
        }
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text.uppercase(), color = Ktm.Dim, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun DirectionItem(label: String, active: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (active) Ktm.Orange else Ktm.TextPrimary, fontFamily = Barlow, fontSize = 14.sp) },
        onClick = onClick,
        trailingIcon = {
            if (active) Icon(OpenDashIcons.Check, null, tint = Ktm.Orange, modifier = Modifier.size(18.dp))
        },
    )
}

/** A sticky day header (Today / Yesterday / date), opaque so cards don't show through. */
@Composable
private fun DateHeader(label: String) {
    Box(Modifier.fillMaxWidth().background(Ktm.Screen).padding(top = 8.dp, bottom = 4.dp)) {
        Eyebrow(label, fontSize = 11, letterSpacing = 2.0, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun RideCard(
    ride: RecordedRide,
    subtitle: String,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectableCard(
        modifier = modifier,
        selected = selected,
        selectionMode = selectionMode,
        onTap = onTap,
        onLongPress = onLongPress,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ride.destinationLabel ?: "Ride",
                        color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(subtitle, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp)
                }
                if (!selectionMode) {
                    SaveStar(saved = ride.saved, boxSize = 32.dp, iconSize = 26.dp, onToggle = onToggleSave)
                    Spacer(Modifier.size(4.dp))
                    PopIconButton(
                        icon = Icons.Filled.Delete, desc = "Delete ride", tint = Ktm.Dim,
                        boxSize = 32.dp, iconSize = 26.dp, onClick = onDelete,
                    )
                } else if (ride.saved) {
                    // Still surface the pinned state while selecting.
                    Icon(Icons.Filled.Star, "Saved", tint = Ktm.Orange, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.size(10.dp))
            RideStatStrip(ride)
        }
    }
}

/** Full-width strip of the ride's key metrics — evenly-spaced columns that fill
 *  the card width so the numbers never wrap. */
@Composable
private fun RideStatStrip(ride: RecordedRide) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatColumn(
            value = DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC),
            label = "DISTANCE", modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatColumn(
            value = formatDurationShort(ride.durationSeconds),
            label = "DURATION", modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatColumn(
            value = "${ride.maxSpeedKmh.toInt()} km/h",
            label = "TOP SPEED", modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, color = Ktm.TextPrimary, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TightTextStyle,
        )
        Text(
            label, color = Ktm.Dim, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, letterSpacing = 1.5.sp, style = TightTextStyle,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.height(26.dp).width(1.dp).background(Ktm.RowDivider))
}

/**
 * Save/pin toggle star with a subtle "pop" on tap: the icon dips then springs
 * back to full size. Shared by the history list and the replay screen.
 */
@Composable
internal fun SaveStar(saved: Boolean, boxSize: Dp, iconSize: Dp, onToggle: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .clickable {
                haptics.toggle(!saved)
                scope.launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder,
            if (saved) "Saved - tap to unsave" else "Save permanently",
            tint = if (saved) Ktm.Orange else Ktm.Dim,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/** e.g. "Sun 6 Sep, 2:32 PM" (date + 12-hour time). */
internal fun formatRideDate(ms: Long): String {
    if (ms <= 0L) return ""
    val dt = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return dt.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))
}

/** Local calendar day a ride started on (for grouping). */
private fun rideLocalDate(ms: Long): java.time.LocalDate =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

/** Day-header text: "Today" / "Yesterday" / "Sun 6 Sep" (year added if not current). */
private fun dayHeaderLabel(date: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

/** Compact duration for the stat strip: "22m" / "1h 12m". */
private fun formatDurationShort(seconds: Int): String {
    val mins = (seconds + 59) / 60
    return if (mins < 60) "${mins}m" else "${mins / 60}h ${mins % 60}m"
}

/** Display name for a sort field. */
private fun RideSortField.label(): String = when (this) {
    RideSortField.DATE -> "Date"
    RideSortField.DISTANCE -> "Distance"
    RideSortField.DURATION -> "Duration"
    RideSortField.TOP_SPEED -> "Top speed"
}
