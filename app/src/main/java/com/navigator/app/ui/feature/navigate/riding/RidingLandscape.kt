package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.snap
import androidx.compose.animation.SizeTransform
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ui.components.LandscapePanel
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

/** The values behind the trip controls, shown as the landscape panel's second face (NL4). */
@androidx.compose.runtime.Immutable
class TripControlsUi(
    val beeps: Boolean,
    val messages: Boolean,
    val dashStatus: String?,
    val canAddStop: Boolean,
    val nextStop: String?,
)

/**
 * NL1-NL3, NL6: the riding screen on the mount. One panel on the cutout side with the green guidance block on top
 * (extra height goes to the instruction; lanes replace the Then band) and the trip bar fixed at its foot. The open
 * map to its right has the only map control at the bottom right and the riding chips at the top centre, never over
 * the panel. Tapping the trip bar flips the same panel into the trip controls ([controls] non-null, NL4) instead of
 * covering the map with a sheet; ⌄, [onFlipBack] and ten idle seconds flip it back.
 * [onInsets] reports what the panel covers so Google's logo, the puck and the follow camera stay in the open map.
 */
@Composable
fun RidingLandscape(
    maneuver: ManeuverUi,
    bar: TripBarUi,
    chip: ChipSource?,
    recenter: Boolean,
    bearing: Float,
    narrow: Boolean,
    controls: TripControlsUi?,
    actions: RidingActions,
    controlActions: TripSheetActions,
    onInsets: (MapInsets) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var footprintPx by remember { mutableIntStateOf(0) }
    val idleMs = remember { mutableLongStateOf(0L) }
    val flipBack by rememberUpdatedState(controlActions.onDismiss)
    val open = controls != null
    val motion = KtmTheme.motion
    // With a screen reader running, the controls stay until dismissed: a panel that flips away while it is being read is worse than one left open.
    val context = androidx.compose.ui.platform.LocalContext.current
    val screenReader by rememberTouchExploration(context)
    LaunchedEffect(open, screenReader) {
        if (!open || screenReader) return@LaunchedEffect
        idleMs.longValue = 0L
        while (true) {
            delay(ControlsIdle.CHECK_EVERY_MS)
            idleMs.longValue += ControlsIdle.CHECK_EVERY_MS
            if (ControlsIdle.shouldFlipBack(idleMs.longValue)) { flipBack(); break }
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Right + WindowInsetsSides.Bottom))
                // The footprint already counts the left inset.
                .padding(start = with(density) { footprintPx.toDp() }).padding(KtmSpacing.ridingEdge),
        ) {
            Box(Modifier.align(Alignment.TopCenter)) { RidingChipHost(chip) }
            RidingMapButton(
                recenter = recenter, bearing = bearing, onTap = actions.onMapButton, onNorthUp = actions.onNorthUp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        LandscapePanel(
            color = KtmTheme.riding.surface,
            gap = KtmLayout.panelPadding,
            panelModifier = Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        idleMs.longValue = 0L
                    }
                }
            },
            onFootprint = { footprintPx = it; onInsets(MapInsets(left = it)) },
        ) {
            AnimatedContent(
                targetState = controls,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { panelFlip(motion, toControls = targetState != null) },
                contentKey = { it != null },
                label = "panelFlip",
            ) { shown ->
                // Each face is a pane of its own, so a screen reader announces the flip and moves to it.
                val paneName = stringResource(if (shown == null) R.string.riding_guidance_pane else R.string.trip_sheet_title)
                Column(Modifier.fillMaxSize().semantics { paneTitle = paneName }, verticalArrangement = Arrangement.spacedBy(KtmLayout.panelPadding)) {
                    if (shown == null) {
                        ManeuverCard(maneuver, Modifier.weight(1f), ManeuverMetrics.landscape(narrow))
                        TripBar(bar, actions.onOpenSheet)
                    } else {
                        TripControlsPanelBody(maneuver, shown, controlActions)
                    }
                }
            }
        }
    }
}

/** MO5: shared axis Y. The guidance slides up and out while the controls rise in; the reverse going back. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<TripControlsUi?>.panelFlip(motion: KtmMotionSpecs, toControls: Boolean): ContentTransform {
    val dir = if (toControls) 1 else -1
    return ((fadeIn(motion.effectsDefault()) + slideInVertically(motion.ridingSpatial()) { if (motion.reduced) 0 else dir * it / 8 }) togetherWith
        (fadeOut(motion.effectsFast()) + slideOutVertically(motion.ridingSpatial()) { if (motion.reduced) 0 else -dir * it / 8 })).using(SizeTransform(clip = true) { _, _ -> snap() })
}

/** NL4: a compact green row keeps the next turn visible; the actions of NV7 follow; End trip stays at the foot. */
@Composable
private fun ColumnScope.TripControlsPanelBody(maneuver: ManeuverUi, ui: TripControlsUi, actions: TripSheetActions) {
    CompactGuidance(maneuver, actions.onDismiss)
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        TripControlsBody(ui.beeps, ui.messages, ui.dashStatus, ui.canAddStop, ui.nextStop, actions)
    }
    EndTripButton(actions.onEnd)
}

@Composable
private fun CompactGuidance(maneuver: ManeuverUi, onBack: () -> Unit) {
    val r = KtmTheme.riding
    val haptics = rememberHaptics()
    val back = stringResource(R.string.riding_controls_back)
    Row(
        Modifier.fillMaxWidth().clip(KtmShapes.Card).background(r.guidance).padding(start = KtmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        when (maneuver) {
            ManeuverUi.Starting -> Text(
                stringResource(R.string.riding_starting), style = MaterialTheme.typography.titleMedium, color = r.onGuidance,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            is ManeuverUi.Active -> {
                if (maneuver.rerouting) CircularProgressIndicator(Modifier.size(KtmLayout.iconRiding * 0.8f), color = r.onGuidance, strokeWidth = KtmSpacing.xs)
                else TurnIconRef(maneuver.icon, size = KtmLayout.iconRiding, color = r.onGuidance)
                Column(Modifier.weight(1f)) {
                    if (maneuver.rerouting) {
                        Text(
                            stringResource(R.string.riding_rerouting), style = MaterialTheme.typography.headlineSmall, color = r.onGuidance,
                            maxLines = 1, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else if (maneuver.distance != null) Text(maneuver.distance, style = MaterialTheme.typography.headlineSmall, color = r.onGuidance, maxLines = 1)
                    val line = listOfNotNull(maneuver.instruction.takeIf { it.isNotBlank() }, maneuver.road).joinToString(" ")
                    if (line.isNotEmpty() && !maneuver.rerouting) Text(line, style = MaterialTheme.typography.bodyLarge, color = r.onGuidance, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(
            Modifier.size(KtmLayout.targetPrimary).clickable(role = Role.Button) { haptics.tap(); onBack() }
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) { Icon(KtmIcons.ChevronDown, null, tint = r.onGuidance) }
    }
}

/** Whether a screen reader is running right now; follows it being switched on or off mid-ride. */
@Composable
private fun rememberTouchExploration(context: android.content.Context): androidx.compose.runtime.State<Boolean> {
    val manager = remember { context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    val on = remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on.value = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}
