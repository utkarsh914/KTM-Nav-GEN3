package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import com.navigator.app.ui.shell.LocalTripShared
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import com.navigator.app.ui.components.KtmScrim
import com.navigator.app.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.edgePadding

class RidingActions(
    val onOpenSheet: () -> Unit,
    val onMapButton: () -> Unit,
    val onNorthUp: () -> Unit,
    val onDismissSheet: () -> Unit,
)

/**
 * NV1-NV5, portrait: the maneuver card with the riding chip under it at the top, the one map control and the trip
 * bar at the bottom. Everything else lives in the trip sheet. [onInsets] reports how much of the map these cover
 * (top, bottom, in px) so Google's logo, the puck and the follow camera stay clear of them.
 */
@Composable
fun RidingScreen(
    maneuver: ManeuverUi,
    bar: TripBarUi,
    chip: ChipSource?,
    recenter: Boolean,
    bearing: Float,
    actions: RidingActions,
    onInsets: (topPx: Int, bottomPx: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** MO2: the trip sheet, grown out of the trip bar while [sheetOpen]. */
    sheetOpen: Boolean = false,
    sheet: @Composable ColumnScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val sys = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val sysTop = sys.getTop(density)
    val sysBottom = sys.getBottom(density)
    val gap = with(density) { KtmSpacing.sm.roundToPx() }
    val size = remember { IntArray(2) }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val sheetMax = maxHeight * SHEET_MAX_FRACTION
    val close = stringResource(R.string.trip_sheet_close)
    // The scrim covers the whole window (edges included) and closes the sheet on a tap.
    AnimatedVisibility(sheetOpen, enter = fadeIn(KtmTheme.motion.effectsDefault()), exit = fadeOut(KtmTheme.motion.effectsFast())) {
        Box(
            Modifier.fillMaxSize().background(KtmScrim)
                .pointerInput(Unit) { detectTapGestures { actions.onDismissSheet() } }
                .semantics { contentDescription = close; role = Role.Button; onClick { actions.onDismissSheet(); true } },
        )
    }
    Box(Modifier.fillMaxSize().edgePadding().padding(horizontal = KtmSpacing.ridingEdge, vertical = KtmSpacing.sm)) {
        // MO1: the card drops in from the top, then the bar rises from the bottom a beat later; no overshoot, at most 250 ms. Reduced motion: a short crossfade.
        val motion = KtmTheme.motion
        val entered = remember { MutableTransitionState(false).apply { targetState = true } }
        // MO6: minimising slides the card up and out while the bar shrinks into the strip.
        val stageScope = LocalTripShared.current?.visibility
        val cardExit = if (stageScope == null || motion.reduced) Modifier else with(stageScope) {
            Modifier.animateEnterExit(enter = EnterTransition.None, exit = fadeOut(motion.effectsFast()) + slideOutVertically(motion.ridingSpatial()) { -it })
        }
        AnimatedVisibility(
            visibleState = entered,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().then(cardExit),
            enter = fadeIn(motion.ridingFade(CARD_DELAY_MS)) + slideInVertically(motion.ridingSpatial(CARD_DELAY_MS)) { if (motion.reduced) 0 else -it },
        ) {
            // Behind the open trip sheet the card is dimmed with the map and silent for screen readers.
            val cardAlpha by androidx.compose.animation.core.animateFloatAsState(if (sheetOpen) CARD_DIMMED else 1f, motion.effectsDefault(), label = "cardDim")
            Column(Modifier.graphicsLayer { alpha = cardAlpha }.then(if (sheetOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                ManeuverCard(maneuver, Modifier.onSizeChanged { size[0] = it.height + gap + sysTop; onInsets(size[0], size[1]) })
                RidingChipHost(chip)
            }
        }
        AnimatedVisibility(
            visibleState = entered,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(motion.ridingFade(BAR_DELAY_MS)) + slideInVertically(motion.ridingSpatial(BAR_DELAY_MS)) { if (motion.reduced) 0 else it },
        ) {
          Column(Modifier.fillMaxWidth().onSizeChanged { if (!sheetOpen) { size[1] = it.height + gap + sysBottom; onInsets(size[0], size[1]) } }) {
            // The map control gives way to the sheet: it can't be tapped, or focused, behind it.
            AnimatedVisibility(!sheetOpen, Modifier.align(Alignment.End), enter = fadeIn(motion.effectsDefault()), exit = fadeOut(motion.effectsFast())) {
                Column {
                    RidingMapButton(recenter = recenter, bearing = bearing, onTap = actions.onMapButton, onNorthUp = actions.onNorthUp)
                    Spacer(Modifier.height(KtmSpacing.sm))
                }
            }
            TripBarSheet(bar, sheetOpen, actions.onOpenSheet, actions.onDismissSheet, sheetMax, sheet = sheet)
          }
        }
    }
    }
}

/** MO1: the card drops in after the sheet has gone, and the bar rises a beat after it. */
private const val CARD_DELAY_MS = 100
private const val BAR_DELAY_MS = 150

/** The open trip sheet takes at most this much of the screen height (the card above stays visible). */
private const val SHEET_MAX_FRACTION = 0.72f

/** The maneuver card behind the open sheet: about what a 60 % scrim leaves of it. */
private const val CARD_DIMMED = 0.4f
