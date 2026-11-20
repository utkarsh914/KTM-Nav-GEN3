package com.navigator.app.ui.feature.navigate

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import com.navigator.app.R
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.BikeStatusPill
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.MapControlButton
import com.navigator.app.ui.components.SheetHandle
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What the browse screen asks of its host. */
class NavigateBrowseActions(
    val sheet: WhereToActions,
    val onBike: () -> Unit,
    val onFixLocation: () -> Unit,
    val onRetryMap: () -> Unit,
    val onUseMirror: () -> Unit,
    val onRecenter: () -> Unit,
    val onResetNorth: () -> Unit,
    /** The map area not covered by the sheet or side panel changed: (left, bottom) in px. Applied once the map is ready. */
    val onMapInsets: (left: Int, bottom: Int) -> Unit,
)

/** The map is padded only after the sheet has stopped moving, so dragging does not re-layout the SDK map every frame. */
private const val INSETS_SETTLE_MS = 100L

/**
 * Browse on the map: the Where-to sheet (a draggable bottom sheet, or a side panel in landscape), the
 * bike pill top-left, the location banner, the map-state card, and Record / compass / recenter riding
 * the sheet's top edge. [recordControls] is the existing Record control until it is rebuilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigateBrowse(
    state: NavigateUiState,
    location: LocationState,
    mapState: MapState,
    mapBearing: Float,
    voiceAvailable: Boolean,
    actions: NavigateBrowseActions,
    recordControls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted above the layout branches so turning the phone keeps the sheet where it was.
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val mapReady = mapState == MapState.Ready
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val landscape = maxWidth > maxHeight
        if (landscape) {
            val panelWidth = KtmLayout.panelWidth(maxWidth.value)
            // The map is framed by what the panel and its margins cover, measured: the shell's rail and the cutout
            // inset are part of it without being counted twice.
            var coveredPx by remember { mutableIntStateOf(0) }
            LaunchedEffect(coveredPx, mapReady) {
                if (mapReady && coveredPx > 0) actions.onMapInsets(coveredPx, 0)
            }
            Row(Modifier.fillMaxSize()) {
                // HM10: the Where-to panel is the same floating card as every other landscape panel.
                Column(
                    Modifier.onSizeChanged { coveredPx = it.width }
                        .windowInsetsPadding(safe.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                        .padding(KtmLayout.panelInset)
                        .width(panelWidth).fillMaxHeight()
                        .shadow(KtmElevation.sheet, KtmShapes.Card)
                        .background(KtmTheme.colors.surf, KtmShapes.Card)
                        .clip(KtmShapes.Card),
                ) {
                    WhereToSheetContent(state, voiceAvailable, actions.sheet)
                }
                Overlays(
                    state, location, mapState, mapBearing, actions, recordControls,
                    sheetLiftPx = 0, controlsVisible = true,
                    insets = safe.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            val containerPx = with(density) { maxHeight.roundToPx() }
            val navBar = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
            val peek = WhereToMetrics.peekHeight(state.activity != null) + navBar
            val sheetMaxHeight = maxHeight - statusTop - KtmLayout.sheetHandleZone
            var visiblePx by remember { mutableIntStateOf(with(density) { peek.roundToPx() }) }
            LaunchedEffect(scaffoldState, containerPx) {
                snapshotFlow { runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrNull() }
                    .collect { offset -> if (offset != null) visiblePx = (containerPx - offset).toInt().coerceAtLeast(0) }
            }
            val pad = SheetPadding.mapBottomPx(visiblePx, containerPx)
            // The latest actions: a tile tapped while the sheet settles changes the stage, and the old
            // callback must not then put the sheet's padding on the next stage's map.
            val currentActions by rememberUpdatedState(actions)
            LaunchedEffect(pad, mapReady) {
                if (!mapReady) return@LaunchedEffect
                delay(INSETS_SETTLE_MS)
                currentActions.onMapInsets(0, pad)
            }
            val expanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
            BackHandler(enabled = expanded) { scope.launch { scaffoldState.bottomSheetState.partialExpand() } }
            val expandLabel = stringResource(if (expanded) R.string.navigate_sheet_collapse else R.string.navigate_sheet_expand)
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = peek,
                sheetContainerColor = KtmTheme.colors.surf,
                sheetShape = KtmShapes.SheetTop,
                sheetShadowElevation = KtmElevation.sheet,
                // Tapping the handle toggles the sheet too: the way to open it without dragging.
                sheetDragHandle = {
                    Box(
                        Modifier.clickable(role = Role.Button, onClickLabel = expandLabel) {
                            scope.launch {
                                if (expanded) scaffoldState.bottomSheetState.partialExpand()
                                else scaffoldState.bottomSheetState.expand()
                            }
                        },
                    ) { SheetHandle() }
                },
                containerColor = Color.Transparent,
                sheetContent = {
                    // A long list stops below the status bar instead of running under it.
                    WhereToSheetContent(
                        state, voiceAvailable, actions.sheet,
                        Modifier.heightIn(max = sheetMaxHeight),
                    )
                },
            ) {
                Overlays(
                    state, location, mapState, mapBearing, actions, recordControls,
                    sheetLiftPx = pad, controlsVisible = SheetPadding.controlsVisible(visiblePx, containerPx),
                    insets = safe.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.End),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun Overlays(
    state: NavigateUiState,
    location: LocationState,
    mapState: MapState,
    mapBearing: Float,
    actions: NavigateBrowseActions,
    recordControls: @Composable () -> Unit,
    sheetLiftPx: Int,
    controlsVisible: Boolean,
    insets: WindowInsets,
    modifier: Modifier,
) {
    val edge = KtmSpacing.edge
    val locationOk = location == LocationState.Ok
    Box(modifier.windowInsetsPadding(insets)) {
        Column(
            Modifier.align(Alignment.TopStart).padding(edge),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            // MO7: the pill pulses once as the greeting grows out of it.
            val motion = KtmTheme.motion
            val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
            val greeted = state.greeting != null
            // ...and once more as the greeting shrinks back into it.
            var wasGreeted by remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(greeted) {
                val pulses = greeted || wasGreeted
                wasGreeted = greeted
                if (pulses && !motion.reduced) {
                    pulse.animateTo(PILL_PULSE, motion.spatialFast())
                    pulse.animateTo(1f, motion.spatialDefault())
                }
            }
            BikeStatusPill(
                label = stringResource(state.bike.label, state.bike.labelArg.orEmpty()),
                tone = state.bike.tone,
                modifier = Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value; transformOrigin = TransformOrigin(0f, 0.5f) },
                onClick = actions.onBike,
                stateDescription = stringResource(state.bike.state),
            )
            if (!locationOk) LocationBanner(location, actions.onFixLocation, Modifier.fillMaxWidth(0.9f))
        }

        MapStateCard(mapState, actions, Modifier.align(Alignment.Center).padding(edge))

        // HM8: grows out of the bike pill (top-left), settles lower in the open map, then shrinks back.
        val motionSpecs = KtmTheme.motion
        AnimatedVisibility(
            visible = state.greeting != null,
            enter = fadeIn(motionSpecs.effectsDefault()) + scaleIn(motionSpecs.spatialDefault(), initialScale = GREETING_FROM, transformOrigin = TransformOrigin(0.1f, 0f)) +
                slideInVertically(motionSpecs.spatialDefault()) { if (motionSpecs.reduced) 0 else -it * 2 },
            exit = fadeOut(motionSpecs.effectsDefault()) + scaleOut(motionSpecs.spatialDefault(), targetScale = GREETING_FROM, transformOrigin = TransformOrigin(0.1f, 0f)) +
                slideOutVertically(motionSpecs.spatialDefault()) { if (motionSpecs.reduced) 0 else -it * 2 },
            modifier = Modifier.align(Alignment.TopStart).padding(start = edge, top = edge + KtmLayout.targetRest * 2),
        ) {
            val hello = state.greeting
            if (hello != null) ConnectGreeting(hello)
        }

        // Riding the sheet's top edge: lifted by how much of the map it covers, gone once the sheet takes over.
        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).offset { IntOffset(0, -sheetLiftPx) }.padding(edge),
        ) {
            Dimmed(!locationOk) { recordControls() }
        }
        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).offset { IntOffset(0, -sheetLiftPx) }.padding(edge),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                if (mapState == MapState.Ready && abs(mapBearing) > 0.5f) {
                    MapControlButton(
                        KtmIcons.CompassNorth, stringResource(R.string.navigate_reset_north), actions.onResetNorth,
                        rotationDegrees = -mapBearing, tinted = false,
                    )
                }
                if (mapState == MapState.Ready) {
                    Dimmed(!locationOk, stringResource(R.string.navigate_recenter)) {
                        MapControlButton(KtmIcons.Recenter, stringResource(R.string.navigate_recenter), onClick = actions.onRecenter)
                    }
                }
            }
        }
    }
}

/**
 * A control that cannot work right now (no location): dimmed, inert, and announced as disabled with the
 * reason, instead of failing with a message when tapped.
 */
@Composable
private fun Dimmed(dimmed: Boolean, label: String? = null, content: @Composable () -> Unit) {
    if (!dimmed) {
        content()
        return
    }
    val reason = stringResource(R.string.navigate_needs_location)
    // The control's own click action is cleared too: it would otherwise stay focusable and activatable by TalkBack.
    val semantics = if (label != null) {
        Modifier.clearAndSetSemantics { role = Role.Button; contentDescription = label; disabled(); stateDescription = reason }
    } else {
        // A group of legacy controls (until B11): announced as one disabled item.
        Modifier.semantics(mergeDescendants = true) { disabled(); stateDescription = reason }
    }
    Box(Modifier.alpha(0.4f).then(semantics)) {
        content()
        // Swallows the touch.
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

@Composable
private fun LocationBanner(location: LocationState, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val permission = location == LocationState.PermissionOff
    InfoBanner(
        title = stringResource(if (permission) R.string.navigate_location_permission_title else R.string.navigate_location_off_title),
        message = stringResource(if (permission) R.string.navigate_location_permission_message else R.string.navigate_location_off_message),
        tone = BannerTone.Warn,
        modifier = modifier,
        action = {
            KtmButton(
                stringResource(if (permission) R.string.navigate_location_permission_action else R.string.navigate_location_off_action),
                onFix, style = ButtonStyle.Tonal, size = ButtonSize.Small,
            )
        },
    )
}

@Composable
private fun MapStateCard(mapState: MapState, actions: NavigateBrowseActions, modifier: Modifier = Modifier) {
    when (mapState) {
        MapState.Ready -> Unit
        MapState.Preparing -> InfoBanner(stringResource(R.string.navigate_map_loading), modifier)
        MapState.Failed -> InfoBanner(
            title = stringResource(R.string.navigate_map_failed_title),
            message = stringResource(R.string.navigate_map_failed_message),
            tone = BannerTone.Bad,
            modifier = modifier,
            action = { KtmButton(stringResource(R.string.navigate_map_retry), actions.onRetryMap, style = ButtonStyle.Tonal, size = ButtonSize.Small) },
        )
        MapState.Unavailable -> InfoBanner(
            title = stringResource(R.string.navigate_map_unavailable_title),
            message = stringResource(R.string.navigate_map_unavailable_message),
            modifier = modifier,
            action = { KtmButton(stringResource(R.string.navigate_map_unavailable_action), actions.onUseMirror, style = ButtonStyle.Tonal, size = ButtonSize.Small) },
        )
    }
}

@Composable
private fun ConnectGreeting(greeting: GreetingUi) {
    val c = KtmTheme.colors
    val text = if (greeting.name.isBlank()) stringResource(R.string.navigate_greeting_anonymous)
    else stringResource(R.string.navigate_greeting, greeting.name)
    androidx.compose.foundation.layout.Row(
        Modifier.shadow(KtmElevation.onMap, KtmShapes.Pill).background(c.surf, KtmShapes.Pill)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = KtmSpacing.xl, vertical = KtmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        androidx.compose.material3.Icon(KtmIcons.Bike, null, tint = KtmTheme.accent.text)
        androidx.compose.material3.Text(text, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1)
    }
}

private const val PILL_PULSE = 1.08f
private const val GREETING_FROM = 0.4f
