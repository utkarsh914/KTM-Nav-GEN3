package com.navigator.app.ui.feature.replay

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.navigator.app.R
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ride.ELEV_UNKNOWN
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.displayAvgKmh
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmFilterChip
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmMenuItem
import com.navigator.app.ui.components.LandscapePanel
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.components.MapControlButton
import com.navigator.app.ui.components.SheetHandle
import com.navigator.app.ui.components.StatTile
import com.navigator.app.ui.components.rideDuration
import com.navigator.app.ui.feature.rides.RideSourceText
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the replay can be asked to do. */
class ReplayActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onEdit: () -> Unit,
    val onTrim: () -> Unit,
    val onDelete: () -> Unit,
    val onToggleStar: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onRestart: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onScrubStart: () -> Unit,
    val onToggleFollow: () -> Unit,
    val onStepSpeed: (Int) -> Unit,
    val onToggleElevation: () -> Unit,
    val onStepSmoothing: (Int) -> Unit,
    val onRecenter: () -> Unit,
    val onResetNorth: () -> Unit,
)

/**
 * The replay's chrome over the map (RP1-RP3, RP6): the floating buttons, the two-level sheet in portrait, the side
 * panel in landscape. Stateless; the map itself is drawn by the screen behind it. [position] is read only by the
 * small pieces that show it, so a playback frame recomposes those and nothing else.
 */
@Composable
fun ReplayContent(
    ui: ReplayUi,
    position: State<Int>,
    bearing: Float,
    avgMode: AvgSpeedMode,
    actions: ReplayActions,
    onInsets: (MapInsets) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ui) {
        ReplayUi.Loading -> Unit
        is ReplayUi.NoTrack -> NoTrack(ui.ride != null, actions.onBack, modifier)
        is ReplayUi.Ready -> BoxWithConstraints(modifier.fillMaxSize()) {
            if (LandscapePanelSpec.isLandscape(maxWidth, maxHeight)) {
                Landscape(ui, position, bearing, avgMode, actions, onInsets)
            } else {
                Portrait(ui, position, bearing, avgMode, actions, onInsets, maxHeight.times(KtmLayout.replaySheetMaxShare))
            }
        }
    }
}

@Composable
private fun NoTrack(hasRide: Boolean, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(KtmSpacing.xs)) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), onBack)
        }
        EmptyState(
            KtmIcons.Route,
            stringResource(if (hasRide) R.string.replay_no_track_title else R.string.replay_not_found_title),
            stringResource(if (hasRide) R.string.replay_no_track_message else R.string.replay_not_found_message),
        )
    }
}

// ---- Portrait: floating buttons + the two-level sheet --------------------------------------------------------------

@Composable
private fun Portrait(
    ui: ReplayUi.Ready,
    position: State<Int>,
    bearing: Float,
    avgMode: AvgSpeedMode,
    actions: ReplayActions,
    onInsets: (MapInsets) -> Unit,
    sheetMax: Dp,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .fillMaxWidth().padding(KtmSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm), verticalAlignment = Alignment.CenterVertically,
        ) {
            MapControlButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            Box(Modifier.weight(1f))
            MapControlButton(KtmIcons.Share, stringResource(R.string.replay_share), actions.onShare)
            OverflowMenu(actions, IconButtonSize.Map)
        }
        MapButtons(
            bearing, actions,
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                .padding(end = KtmSpacing.lg, bottom = with(density) { sheetHeight.toDp() } + KtmSpacing.lg),
        )
        // RP1: the sheet rises into place once the page is up.
        val motion = KtmTheme.motion
        val risen = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = risen, modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.fadeIn(motion.effectsDefault()) + androidx.compose.animation.slideInVertically(motion.spatialDefault()) { if (motion.reduced) 0 else it / 2 },
        ) {
        ReplaySheet(
            expanded = expanded, onExpandedChange = { expanded = it }, maxHeight = sheetMax,
            modifier = Modifier.onSizeChanged { sheetHeight = it.height; onInsets(MapInsets(bottom = it.height)) },
            pinned = {
                Header(ui, actions)
                Readouts(ui, position)
                ReplaySeekBar(position, ui.points.size, ui.seams, ui.mergeSeams, actions.onSeek, actions.onScrubStart)
                ReplayPlayRow(ui.playing, ui.following, ui.speed, actions.onTogglePlay, actions.onRestart, actions.onToggleFollow, actions.onStepSpeed)
            },
            details = { Details(ui, position, avgMode, actions) },
        )
        }
    }
}

/** The sheet: a handle you drag or tap, the pinned part, and the details that scroll below it once it is expanded. */
@Composable
private fun ReplaySheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    maxHeight: Dp,
    modifier: Modifier,
    pinned: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    val c = KtmTheme.colors
    val toggleLabel = stringResource(if (expanded) R.string.replay_sheet_collapse else R.string.replay_sheet_expand)
    val state = stringResource(if (expanded) R.string.replay_sheet_state_expanded else R.string.replay_sheet_state_collapsed)
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    Column(
        modifier.fillMaxWidth().heightIn(max = maxHeight)
            .shadow(KtmElevation.sheet, KtmShapes.SheetTop)
            .background(c.surf, KtmShapes.SheetTop)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        // The bar is 4 dp tall in a 28 dp zone; the touch band is the full 48 dp.
        Box(
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest)
                .pointerInput(expanded) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (abs(drag) > dragThresholdPx()) onExpandedChange(drag < 0) },
                        onVerticalDrag = { _, dy -> drag += dy },
                    )
                }
                .clickable(role = Role.Button, onClickLabel = toggleLabel) { onExpandedChange(!expanded) }
                .semantics {
                    stateDescription = state
                    customActions = listOf(CustomAccessibilityAction(toggleLabel) { onExpandedChange(!expanded); true })
                },
            contentAlignment = Alignment.Center,
        ) { SheetHandle() }
        Column(Modifier.padding(horizontal = KtmSpacing.lg), verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) { pinned() }
        AnimatedVisibility(
            expanded, Modifier.weight(1f, fill = false),
            enter = expandVertically(KtmTheme.motion.spatialDefault()), exit = shrinkVertically(KtmTheme.motion.spatialDefault()),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = KtmSpacing.lg, end = KtmSpacing.lg, top = KtmSpacing.md),
                verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
            ) { details() }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = KtmSpacing.lg))
    }
}

/** A drag shorter than this many touch slops on the handle is a wobble, not a gesture. */
private const val HANDLE_DRAG_SLOPS = 4

private fun PointerInputScope.dragThresholdPx(): Float = viewConfiguration.touchSlop * HANDLE_DRAG_SLOPS

// ---- Landscape: the panel on the left --------------------------------------------------------------------------------

@Composable
private fun Landscape(
    ui: ReplayUi.Ready,
    position: State<Int>,
    bearing: Float,
    avgMode: AvgSpeedMode,
    actions: ReplayActions,
    onInsets: (MapInsets) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        MapButtons(
            bearing, actions,
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)).padding(KtmSpacing.lg),
        )
        LandscapePanel(onFootprint = { onInsets(MapInsets(left = it)) }, gap = KtmSpacing.xs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
                Box(Modifier.weight(1f)) { Title(ui.ride, ui.parts, actions.onEdit, compact = true) }
                StarButton(ui.ride.saved, actions.onToggleStar)
                KtmIconButton(KtmIcons.Share, stringResource(R.string.replay_share), actions.onShare)
                OverflowMenu(actions, IconButtonSize.Rest)
            }
            // RP6: the chart (and, scrolling, the stats) above; the readout, seek bar and play row stay at the foot.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                Details(ui, position, avgMode, actions, chartFirst = true)
            }
            Readouts(ui, position)
            ReplaySeekBar(position, ui.points.size, ui.seams, ui.mergeSeams, actions.onSeek, actions.onScrubStart)
            ReplayPlayRow(ui.playing, ui.following, ui.speed, actions.onTogglePlay, actions.onRestart, actions.onToggleFollow, actions.onStepSpeed)
        }
    }
}

// ---- Pieces ---------------------------------------------------------------------------------------------------------

@Composable
private fun MapButtons(bearing: Float, actions: ReplayActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm), horizontalAlignment = Alignment.End) {
        if (abs(bearing) > NORTH_EPSILON) {
            MapControlButton(KtmIcons.CompassNorth, stringResource(R.string.replay_north), actions.onResetNorth, rotationDegrees = -bearing, tinted = false)
        }
        MapControlButton(KtmIcons.Recenter, stringResource(R.string.replay_recenter), actions.onRecenter)
    }
}

@Composable
private fun OverflowMenu(actions: ReplayActions, size: IconButtonSize) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (size == IconButtonSize.Map) MapControlButton(KtmIcons.More, stringResource(R.string.replay_more), { open = true })
        else KtmIconButton(KtmIcons.More, stringResource(R.string.replay_more), { open = true }, size = size)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = KtmTheme.colors.surf) {
            KtmMenuItem(stringResource(R.string.replay_menu_edit)) { open = false; actions.onEdit() }
            KtmMenuItem(stringResource(R.string.replay_menu_trim)) { open = false; actions.onTrim() }
            KtmMenuItem(stringResource(R.string.replay_menu_delete)) { open = false; actions.onDelete() }
        }
    }
}

@Composable
private fun StarButton(saved: Boolean, onToggle: () -> Unit) {
    val a = KtmTheme.accent
    KtmIconButton(
        if (saved) KtmIcons.StarFilled else KtmIcons.Star,
        stringResource(if (saved) R.string.ride_unstar else R.string.ride_star), onToggle,
        tint = if (saved) a.text else Color.Unspecified,
    )
}

@Composable
private fun Header(ui: ReplayUi.Ready, actions: ReplayActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Title(ui.ride, ui.parts, actions.onEdit, compact = false) }
        StarButton(ui.ride.saved, actions.onToggleStar)
    }
}

/** The name (tap to edit it), the "N parts" badge of a merged ride, and when it was ridden. */
@Composable
private fun Title(ride: RecordedRide, parts: Int, onEdit: () -> Unit, compact: Boolean) {
    val c = KtmTheme.colors
    val name = ride.destinationLabel?.takeIf { it.isNotBlank() } ?: stringResource(RideSourceText.title(ride.source))
    val editLabel = stringResource(R.string.replay_edit_title)
    Column(
        Modifier.heightIn(min = KtmLayout.targetRest).clickable(role = Role.Button, onClickLabel = editLabel, onClick = onEdit),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Text(
                name, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, color = c.fg,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (parts > 1) KtmBadge(pluralStringResource(R.plurals.replay_parts, parts, parts), tone = BadgeTone.Neutral)
        }
        val distance = DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC)
        // RP1: "Thu 19 Nov · 5:51 pm · 6.3 km"; the narrow side panel (RP6) says the day, the distance and the time taken.
        val sub = (if (compact) listOf(ReplayFormat.rideDay(ride.startMs), distance, rideDuration(ride.ridingSeconds))
        else listOf(ReplayFormat.rideDate(ride.startMs), distance)).filter { it.isNotBlank() }.joinToString(" \u00b7 ")
        Text(sub, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The time of day and the speed at the marker, 24 sp each; the speed turns accent while playing. */
@Composable
private fun Readouts(ui: ReplayUi.Ready, position: State<Int>) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val point = ui.points.getOrNull(position.value)
    val kmh = point?.speedKmh?.roundToInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(point?.let { ReplayFormat.timeOfDay(it.timeMs) }.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1)
        Text(
            if (kmh != null) stringResource(R.string.replay_speed_at_marker, kmh) else stringResource(R.string.replay_speed_unknown),
            style = MaterialTheme.typography.headlineSmall, color = if (ui.playing) a.text else c.fg, maxLines = 1,
        )
    }
}

/** What the expanded sheet adds (and the landscape panel scrolls): the note, four stats, the chart, smoothing. */
@Composable
private fun Details(ui: ReplayUi.Ready, position: State<Int>, avgMode: AvgSpeedMode, actions: ReplayActions, chartFirst: Boolean = false) {
    val c = KtmTheme.colors
    val ride = ui.ride
    val note: @Composable () -> Unit = {
        ride.notes?.takeIf { it.isNotBlank() }?.let {
            Text(
                "\u201c$it\u201d", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = c.fg2, modifier = Modifier.fillMaxWidth().padding(top = KtmSpacing.xs),
            )
        }
    }
    // The short side panel shows the chart first (RP6), so the note must not push it out of view.
    if (!chartFirst) note()
    // The side panel is short: the chart (RP6) comes before the stats there.
    if (!chartFirst) Stats(ride, avgMode)
    if (ui.chart != null) {
        // RP2: what the chart shows, with the elevation switch on the same line; climb, descent and the hint under it.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.replay_chart_title), style = MaterialTheme.typography.titleSmall, color = c.fg2, modifier = Modifier.weight(1f))
            if (ui.hasElevation) {
                KtmFilterChip(stringResource(R.string.replay_elevation), ui.showElevation, actions.onToggleElevation, leadingIcon = KtmIcons.Elevation)
            }
        }
        ReplayChart(
            ui.chart, ui.seams, ui.mergeSeams, position, ui.points.size, ui.points.first().timeMs, ui.points.last().timeMs,
            ui.showElevation, actions.onSeek, actions.onScrubStart,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            if (ride.elevGainM != ELEV_UNKNOWN && ride.elevLossM != ELEV_UNKNOWN) {
                Text(stringResource(R.string.replay_climb, ride.elevGainM), style = MaterialTheme.typography.bodySmall, color = c.fg2)
                Text(stringResource(R.string.replay_descent, ride.elevLossM), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.replay_chart_tap), style = MaterialTheme.typography.bodySmall, color = c.fg2)
        }
    }
    if (chartFirst) { note(); Stats(ride, avgMode) }
    Smoothing(ui.smoothingMeters, actions.onStepSmoothing)
}

@Composable
private fun Stats(ride: RecordedRide, mode: AvgSpeedMode) {
    val valueStyle = KtmTheme.type.statValue
    val kmh = stringResource(R.string.replay_unit_kmh)
    // "Riding time" is the time spent moving; a ride from before that was stored has only the elapsed time.
    val moving = ride.movingSeconds >= 0
    Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            StatTile(stringResource(R.string.replay_stat_distance), DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC), Modifier.weight(1f), valueStyle = valueStyle)
            StatTile(
                stringResource(if (moving) R.string.replay_stat_riding else R.string.replay_stat_time),
                rideDuration(ride.ridingSeconds), Modifier.weight(1f), valueStyle = valueStyle,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            StatTile(
                stringResource(if (mode == AvgSpeedMode.OVERALL) R.string.replay_stat_avg_overall else R.string.replay_stat_avg_moving),
                ride.displayAvgKmh(mode).roundToInt().toString(), Modifier.weight(1f), unit = kmh, valueStyle = valueStyle,
            )
            StatTile(stringResource(R.string.replay_stat_max), ride.maxSpeedKmh.roundToInt().toString(), Modifier.weight(1f), unit = kmh, valueStyle = valueStyle)
        }
    }
}

@Composable
private fun Smoothing(meters: Int, onStep: (Int) -> Unit) {
    val c = KtmTheme.colors
    val steps = ReplayUi.SMOOTHING_STEPS
    val text = if (meters == 0) stringResource(R.string.replay_smoothing_off) else stringResource(R.string.replay_smoothing_value, meters)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.replay_smoothing_title), style = MaterialTheme.typography.titleMedium, color = c.fg)
            Text(stringResource(R.string.replay_smoothing_explain), style = MaterialTheme.typography.bodySmall, color = c.fg2)
        }
        MiniStepper(
            text, text, { onStep(-1) }, { onStep(1) },
            stringResource(R.string.replay_smoothing_less), stringResource(R.string.replay_smoothing_more),
            canLess = meters > steps.first(), canMore = meters < steps.last(),
            valueWidth = KtmLayout.targetRest + KtmSpacing.md,
        )
    }
}

private const val NORTH_EPSILON = 0.5f
