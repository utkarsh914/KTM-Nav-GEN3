package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.navigator.app.R
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmRadio
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmPinColors
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

class RouteSheetActions(
    val onBack: () -> Unit,
    val onSelect: (Int) -> Unit,
    val onAddStop: () -> Unit,
    val onRemoveStop: (Int) -> Unit,
    val onMoveStop: (index: Int, delta: Int) -> Unit,
    val onStart: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * PL2-PL4: the route preview. Routes are 64 dp rows (time on its own line, then distance and traffic in words
 * and colour); with stops there is a single route and the summary moves into the header. Stops are a numbered
 * list the rider can reorder by dragging the grip or from its ⋮ menu. Loading is a skeleton, a failure still
 * lets the rider start ("Start anyway"). Stateless; it sits in a [PlanPanel].
 */
@Composable
fun RouteSheet(state: RouteSheetState, actions: RouteSheetActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val fill = LocalFillPanel.current
    Column(modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxHeight() else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    state.destination, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                val withStops = state.stops.isNotEmpty()
                val sub = if (withStops) state.summary else stringResource(R.string.route_from_location)
                if (sub != null) {
                    Text(
                        sub, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = if (withStops) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                    )
                }
            }
            // PL2: a labelled tonal button; with stops, adding one is a row in the list (PL3).
            if (state.stops.isEmpty()) {
                KtmButton(
                    stringResource(R.string.route_add_stop_button), actions.onAddStop, style = ButtonStyle.Tonal,
                    size = ButtonSize.Small, icon = KtmIcons.Add,
                )
            }
        }
        Column(
            Modifier.weight(1f, fill = fill).verticalScroll(rememberScrollState()).padding(top = KtmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            if (state.stops.isNotEmpty()) StopsList(state, actions)
            when {
                state.loading -> if (state.stops.isEmpty()) RouteSkeleton()
                state.failed -> RouteFailed(actions.onRetry)
                state.options.isNotEmpty() && state.stops.isEmpty() -> RouteOptions(state, actions.onSelect)
            }
        }
        Spacer(Modifier.height(KtmSpacing.lg))
        KtmButton(
            stringResource(if (state.failed && !state.loading) R.string.route_start_anyway else R.string.route_start),
            actions.onStart, Modifier.fillMaxWidth(), size = ButtonSize.Riding, icon = KtmIcons.Navigation,
        )
    }
}

/** PL4: what went wrong and what still works, with a way to try again. */
@Composable
private fun RouteFailed(onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.md), horizontalAlignment = Alignment.Start) {
        InfoBanner(
            message = stringResource(R.string.route_failed), title = stringResource(R.string.route_failed_title),
            tone = BannerTone.Bad,
        )
        KtmButton(
            stringResource(R.string.route_try_again), onRetry, style = ButtonStyle.Outline, size = ButtonSize.Small,
            icon = KtmIcons.Refresh,
        )
    }
}

@Composable
private fun StopsList(state: RouteSheetState, actions: RouteSheetActions) {
    val c = KtmTheme.colors
    // Height of one stop slot (row + leg line), measured, to turn a drag distance into rows.
    var slotPx by remember { mutableFloatStateOf(0f) }
    // The stop that was just moved lights up for a moment, so a reorder is visible even between similar names.
    var lit by remember { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(lit) {
        if (lit != null) { kotlinx.coroutines.delay(LIT_MS); lit = null }
    }
    val moving = RouteSheetActions(
        actions.onBack, actions.onSelect, actions.onAddStop, actions.onRemoveStop,
        { index, delta -> lit = index + delta; actions.onMoveStop(index, delta) }, actions.onStart, actions.onRetry,
    )
    val line = c.line2
    Column(
        Modifier.drawBehind {
            // The timeline joins the markers: from the middle of the first to the middle of the last.
            val x = KtmLayout.stopMarker.toPx() / 2
            val inset = KtmLayout.row1.toPx() / 2
            drawLine(line, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = KtmLayout.timelineStroke.toPx())
        },
    ) {
        StopLine(stringResource(R.string.route_your_location), null, MaterialTheme.typography.bodyMedium, c.fg2) {
            Box(Modifier.size(KtmSpacing.xl).background(c.surf, KtmShapes.Pill), contentAlignment = Alignment.Center) {
                Box(Modifier.size(KtmSpacing.md).background(c.fg3, KtmShapes.Pill))
            }
        }
        state.stops.forEachIndexed { i, label ->
            androidx.compose.runtime.key(i) {
                StopSlot(
                    index = i, count = state.stops.size, label = label, legLine = state.legLines.getOrNull(i),
                    slotPx = slotPx, onSlotSize = { slotPx = it }, actions = moving, lit = lit == i,
                )
            }
        }
        AddStopRow(state, actions.onAddStop)
        StopLine(state.destination, state.legLines.getOrNull(state.stops.size), MaterialTheme.typography.titleMedium, c.fg) {
            Box(Modifier.background(c.surf, KtmShapes.Pill).padding(KtmSpacing.xs)) {
                Icon(KtmIcons.Flag, null, tint = KtmTheme.accent.text, modifier = Modifier.size(KtmSpacing.lg))
            }
        }
    }
}

/** PL3: where a new stop goes, on the same rhythm as the stops. States the limit up front. */
@Composable
private fun AddStopRow(state: RouteSheetState, onAdd: () -> Unit) {
    val a = KtmTheme.accent
    val c = KtmTheme.colors
    val enabled = state.canAddStop
    val tint = if (enabled) a.text else c.fg3
    Row(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.row1).clip(KtmShapes.Control)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onAdd) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Box(
            Modifier.size(KtmLayout.stopMarker).background(c.surf, KtmShapes.Pill).border(KtmLayout.outline, tint, KtmShapes.Pill),
            contentAlignment = Alignment.Center,
        ) { Icon(KtmIcons.Add, null, tint = tint, modifier = Modifier.size(KtmSpacing.lg)) }
        Text(
            if (enabled) stringResource(R.string.route_add_stop_row) else stringResource(R.string.route_stop_limit),
            style = MaterialTheme.typography.titleMedium, color = tint, modifier = Modifier.weight(1f),
        )
        if (enabled) {
            Text(
                pluralStringResource(R.plurals.route_stops_left, state.stopsLeft, state.stopsLeft),
                style = MaterialTheme.typography.bodySmall, color = c.fg2,
            )
        }
    }
}

/** One fixed point of the trip: its marker (28 dp column), its name and, under it, the leg that ends there. */
@Composable
private fun StopLine(
    text: String,
    legLine: String?,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    marker: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.row1), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Box(Modifier.size(KtmLayout.stopMarker), contentAlignment = Alignment.Center) { marker() }
        NameAndLeg(text, style, color, legLine, Modifier.weight(1f))
    }
}

/** The place's name with, under it, the leg that ends there: arrival, time and distance from the previous point. */
@Composable
private fun NameAndLeg(
    name: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    legLine: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (legLine != null) {
            Text(
                legLine, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val LIT_MS = 900L

/** The stop's number as on the map: a white disc with a dark ring and number, whatever the theme. */
@Composable
internal fun StopNumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.background(KtmPinColors.paper, KtmShapes.Pill).border(KtmLayout.pinRing, KtmPinColors.ink, KtmShapes.Pill),
        contentAlignment = Alignment.Center,
    ) { Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = KtmPinColors.ink) }
}

/** One editable stop (row + leg line) as a draggable unit; the grip's drag distance becomes whole-row moves. */
@Composable
private fun StopSlot(
    index: Int,
    count: Int,
    label: String,
    legLine: String?,
    slotPx: Float,
    onSlotSize: (Float) -> Unit,
    actions: RouteSheetActions,
    lit: Boolean,
) {
    val c = KtmTheme.colors
    var dragY by remember { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    val dragging = dragY != 0f
    val haptics = rememberHaptics()
    // The gesture must not restart when these change mid-drag (leg lines appear, the size is measured again).
    val slot by rememberUpdatedState(slotPx)
    val i by rememberUpdatedState(index)
    val total by rememberUpdatedState(count)
    val move by rememberUpdatedState(actions.onMoveStop)
    val number = index + 1
    val moveUp = stringResource(R.string.route_move_up)
    val moveDown = stringResource(R.string.route_move_down)
    val reorder = stringResource(R.string.route_reorder, number)
    Column(
        Modifier
            .onSizeChanged { onSlotSize(it.height.toFloat()) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragY }
            .then(
                when {
                    dragging -> Modifier.background(c.surf3, KtmShapes.Control)
                    lit -> Modifier.background(KtmTheme.accent.container, KtmShapes.Control)
                    else -> Modifier
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.row1), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            StopNumberBadge(number, Modifier.size(KtmLayout.stopMarker))
            NameAndLeg(label, MaterialTheme.typography.titleMedium, c.fg, legLine, Modifier.weight(1f))
            if (count > 1) {
                Box {
                    Box(
                        Modifier.size(KtmLayout.targetRest)
                            .semantics {
                                contentDescription = reorder
                                role = Role.Button
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(moveUp) { actions.onMoveStop(index, -1); true })
                                    if (index < count - 1) add(CustomAccessibilityAction(moveDown) { actions.onMoveStop(index, 1); true })
                                }
                            }
                            .clickable { menu = true }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { dragY = 0.001f; haptics.tap() },
                                    onDragEnd = {
                                        val delta = StopLegs.dragDelta(dragY, slot, i, total)
                                        dragY = 0f
                                        if (delta != 0) move(i, delta)
                                    },
                                    onDragCancel = { dragY = 0f },
                                ) { change, drag ->
                                    change.consume()
                                    dragY += drag.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(KtmIcons.Reorder, null, tint = c.fg2, modifier = Modifier.size(KtmSpacing.xl)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (index > 0) DropdownMenuItem(text = { Text(moveUp) }, onClick = { menu = false; actions.onMoveStop(index, -1) })
                        if (index < count - 1) DropdownMenuItem(text = { Text(moveDown) }, onClick = { menu = false; actions.onMoveStop(index, 1) })
                    }
                }
            }
            KtmIconButton(KtmIcons.Close, stringResource(R.string.route_remove_stop, number), { actions.onRemoveStop(index) })
        }
    }
}

@Composable
private fun RouteOptions(state: RouteSheetState, onSelect: (Int) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
        state.options.forEachIndexed { i, option ->
            RouteOptionRow(option, selected = i == state.selected, onClick = { onSelect(i) })
        }
    }
}

@Composable
private fun RouteOptionRow(option: RouteOptionUi, selected: Boolean, onClick: () -> Unit) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val badge = when (option.badge) {
        RouteBadge.Fastest -> stringResource(R.string.route_fastest)
        RouteBadge.Alternative -> stringResource(R.string.route_alternative, option.alternative)
        RouteBadge.Only -> stringResource(R.string.route_only)
    }
    val delay = when (option.delayTone) {
        DelayTone.OnTime -> stringResource(R.string.route_on_time)
        else -> stringResource(R.string.route_delay, option.delayMinutes)
    }
    val delayColor = when (option.delayTone) {
        DelayTone.OnTime -> c.okText
        DelayTone.Minor -> c.warn
        DelayTone.Heavy -> c.bad
    }
    val description = stringResource(R.string.route_option_description, badge, option.time, option.distance, delay)
    // PL2: a radio, the time with "Fastest" at the end of the line, then distance · road · delay in words and colour.
    Row(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.row2).clip(KtmShapes.Control)
            .background(if (selected) a.container else c.surf2, KtmShapes.Control)
            .then(if (selected) Modifier.border(KtmLayout.outline, a.fill, KtmShapes.Control) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton) { haptics.tap(); onClick() }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        KtmRadio(selected)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                Text(
                    option.time, style = MaterialTheme.typography.headlineSmall, color = if (selected) a.onContainer else c.fg,
                    modifier = Modifier.weight(1f),
                )
                if (option.badge == RouteBadge.Fastest) KtmBadge(badge, tone = BadgeTone.Accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(option.distance, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1)
                // Words as well as colour: the delay reads without telling the colours apart.
                Text(delay, style = MaterialTheme.typography.labelMedium, color = delayColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RouteSkeleton() {
    val loading = stringResource(R.string.route_loading_description)
    Column(
        Modifier.semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        repeat(2) {
            Skeleton(Modifier.fillMaxWidth().height(KtmLayout.row2), KtmShapes.Control)
        }
    }
}
