package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

class RouteSheetActions(
    val onBack: () -> Unit,
    val onClose: () -> Unit,
    val onSelect: (Int) -> Unit,
    val onAddStop: () -> Unit,
    val onRemoveStop: (Int) -> Unit,
    val onMoveStop: (index: Int, delta: Int) -> Unit,
    val onStart: () -> Unit,
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
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            Column(Modifier.weight(1f).padding(horizontal = KtmSpacing.xs)) {
                Text(
                    state.destination, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                if (state.stops.isNotEmpty() && state.summary != null) {
                    Text(
                        state.summary, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            KtmIconButton(KtmIcons.Close, stringResource(R.string.common_close), actions.onClose)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            StopsList(state, actions)
            when {
                state.loading -> if (state.stops.isEmpty()) RouteSkeleton()
                state.options.isNotEmpty() && state.stops.isEmpty() -> RouteOptions(state, actions.onSelect)
                state.failed -> Text(
                    stringResource(R.string.route_failed), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                    modifier = Modifier.padding(horizontal = KtmSpacing.sm),
                )
            }
        }
        Spacer(Modifier.height(KtmSpacing.md))
        KtmButton(
            stringResource(if (state.failed && !state.loading) R.string.route_start_anyway else R.string.route_start),
            actions.onStart, Modifier.fillMaxWidth(), size = ButtonSize.Riding, icon = KtmIcons.Navigation,
        )
    }
}

@Composable
private fun StopsList(state: RouteSheetState, actions: RouteSheetActions) {
    val c = KtmTheme.colors
    // Height of one stop slot (row + leg line), measured, to turn a drag distance into rows.
    var slotPx by remember { mutableFloatStateOf(0f) }
    Column {
        StopLine(stringResource(R.string.route_your_location), c.fg2, null)
        LegLine(state.legLines.getOrNull(0))
        state.stops.forEachIndexed { i, label ->
            androidx.compose.runtime.key(i) {
                StopSlot(
                    index = i, count = state.stops.size, label = label, legLine = state.legLines.getOrNull(i + 1),
                    slotPx = slotPx, onSlotSize = { slotPx = it }, actions = actions,
                )
            }
        }
        StopLine(state.destination, KtmTheme.accent.text, KtmIcons.Flag)
    }
    if (state.stops.isEmpty()) {
        // PL2: a labelled tonal button, so adding a stop is findable before any stop exists.
        KtmButton(
            stringResource(R.string.route_add_stop_button), actions.onAddStop, style = ButtonStyle.Tonal, icon = KtmIcons.Add,
        )
    } else {
        ListRow(
            title = if (state.canAddStop) pluralStringResource(R.plurals.route_add_stop_row, state.stopsLeft, state.stopsLeft)
            else stringResource(R.string.route_stop_limit),
            leadingIcon = KtmIcons.PinPlus,
            onClick = if (state.canAddStop) actions.onAddStop else null,
        )
    }
}

@Composable
private fun StopLine(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(KtmLayout.targetRest / 2), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(KtmSpacing.xl))
            else Box(Modifier.size(KtmSpacing.md).background(color, KtmShapes.Pill))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LegLine(text: String?) {
    if (text == null) return
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2, maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = KtmLayout.targetRest / 2, bottom = KtmSpacing.xs),
    )
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
            .then(if (dragging) Modifier.background(c.surf3, KtmShapes.Control) else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KtmLayout.targetRest / 2), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = KtmTheme.accent.text)
            }
            Text(
                label, style = MaterialTheme.typography.bodyLarge, color = c.fg, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
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
        LegLine(legLine)
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
        DelayTone.OnTime -> c.ok
        DelayTone.Minor -> c.warn
        DelayTone.Heavy -> c.bad
    }
    val description = stringResource(R.string.route_option_description, badge, option.time, option.distance, delay)
    Column(
        Modifier.fillMaxWidth().heightIn(min = KtmLayout.row2).clip(KtmShapes.Control)
            .background(if (selected) a.container else Color.Transparent, KtmShapes.Control)
            .selectable(selected = selected, role = Role.RadioButton) { haptics.tap(); onClick() }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = KtmSpacing.md, vertical = KtmSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Text(
                option.time, style = MaterialTheme.typography.headlineSmall,
                color = if (selected) a.onContainer else c.fg,
            )
            if (option.badge == RouteBadge.Fastest) KtmBadge(badge, tone = BadgeTone.Accent)
            else if (option.badge == RouteBadge.Alternative) KtmBadge(badge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(option.distance, style = MaterialTheme.typography.bodySmall, color = c.fg2)
            // Words as well as colour: the delay reads without telling the colours apart.
            Text(delay, style = MaterialTheme.typography.labelMedium, color = delayColor)
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
