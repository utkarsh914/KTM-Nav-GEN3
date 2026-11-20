package com.navigator.app.ui.feature.navigate.riding

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
) {
    val density = LocalDensity.current
    val sys = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val sysTop = sys.getTop(density)
    val sysBottom = sys.getBottom(density)
    val gap = with(density) { KtmSpacing.sm.roundToPx() }
    val size = remember { IntArray(2) }
    Box(modifier.fillMaxSize().edgePadding().padding(horizontal = KtmSpacing.ridingEdge, vertical = KtmSpacing.sm)) {
        // MO1: the card drops in from the top, then the bar rises from the bottom a beat later; no overshoot, at most 250 ms. Reduced motion: a short crossfade.
        val motion = KtmTheme.motion
        val entered = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = entered,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(motion.ridingFade(CARD_DELAY_MS)) + slideInVertically(motion.ridingSpatial(CARD_DELAY_MS)) { if (motion.reduced) 0 else -it },
        ) {
            Column {
                ManeuverCard(maneuver, Modifier.onSizeChanged { size[0] = it.height + gap + sysTop; onInsets(size[0], size[1]) })
                RidingChipHost(chip)
            }
        }
        AnimatedVisibility(
            visibleState = entered,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(motion.ridingFade(BAR_DELAY_MS)) + slideInVertically(motion.ridingSpatial(BAR_DELAY_MS)) { if (motion.reduced) 0 else it },
        ) {
          Column(Modifier.fillMaxWidth().onSizeChanged { size[1] = it.height + gap + sysBottom; onInsets(size[0], size[1]) }) {
            RidingMapButton(
                recenter = recenter, bearing = bearing, onTap = actions.onMapButton, onNorthUp = actions.onNorthUp,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(KtmSpacing.sm))
            TripBar(bar, actions.onOpenSheet)
          }
        }
    }
}

/** MO1: the card drops in after the sheet has gone, and the bar rises a beat after it. */
private const val CARD_DELAY_MS = 100
private const val BAR_DELAY_MS = 150
