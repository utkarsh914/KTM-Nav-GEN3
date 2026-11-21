package com.navigator.app.ui.feature.navigate.pick

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.MapControlButton
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.components.consumeMapTouches
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.heading

/**
 * HM7/PL6: a pin fixed in the middle, a pill on top saying what to do, and a sheet naming the nearest street
 * with the action. Over the live map, so panning is what moves the point (no precise tap needed). A stop
 * (PL6) has an accent pin with a "+", its number in the sheet header and a line saying where it goes.
 */
@Composable
fun PickOnMapScreen(
    state: PickOnMapState,
    onBack: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the pill on top asks of the rider. */
    hint: String = stringResource(R.string.pick_hint),
    /** The sheet's heading: "Dropped pin", or "New stop". */
    title: String = stringResource(R.string.pick_dropped_pin),
    /** The stop number the point will get; null for a plain pin. */
    stopNumber: Int? = null,
    /** A line under the street ("Goes in before Toit. You can reorder it in the stop list."). */
    subtitle: String? = null,
    useLabel: String = stringResource(R.string.pick_use),
    useEnabled: Boolean = true,
) {
    val c = KtmTheme.colors
    // The pin sits in the un-inset box so its tip is the map's centre, which is where the point is read.
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(KtmSpacing.lg).consumeMapTouches(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            MapControlButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), onBack)
            Box(
                Modifier.weight(1f).height(KtmLayout.targetPrimary).shadow(KtmElevation.onMap, KtmShapes.Card)
                    .background(c.surf, KtmShapes.Card).padding(horizontal = KtmSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    hint, style = MaterialTheme.typography.titleMedium, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        // The pin's tip is the point: lift the icon by half its height.
        // HM7: the pin drops in and settles; it lifts while the map moves and settles again when it stops.
        val motion = KtmTheme.motion
        val pinLift by androidx.compose.animation.core.animateDpAsState(
            if (state.resolving) PIN_LIFT else 0.dp, motion.spatialFast(), label = "pinLift",
        )
        val drop = remember { androidx.compose.animation.core.Animatable(if (motion.reduced) 0f else 1f) }
        androidx.compose.runtime.LaunchedEffect(Unit) { drop.animateTo(0f, motion.spatialDefault()) }
        Icon(
            if (stopNumber != null) KtmIcons.PinPlus else KtmIcons.Pin, null, tint = KtmTheme.accent.fill,
            modifier = Modifier.align(Alignment.Center).size(KtmLayout.mapPin).offset { androidx.compose.ui.unit.IntOffset(0, (-(KtmLayout.mapPin / 2 + pinLift + KtmLayout.mapPin * 2 * drop.value)).roundToPx()) },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(KtmElevation.sheet, KtmShapes.SheetTop)
                .background(c.surf, KtmShapes.SheetTop).consumeMapTouches()
                .padding(horizontal = KtmSpacing.lg).padding(top = KtmSpacing.lg)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = KtmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                if (stopNumber != null) {
                    Box(
                        Modifier.size(KtmLayout.stopMarker).background(KtmTheme.accent.fill, KtmShapes.Pill),
                        contentAlignment = Alignment.Center,
                    ) { Text(stopNumber.toString(), style = MaterialTheme.typography.labelLarge, color = KtmTheme.accent.onFill) }
                }
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        title, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                    )
                    when {
                        state.resolving && state.street == null -> Skeleton(Modifier.fillMaxWidth(0.6f).height(KtmSpacing.lg))
                        state.street != null -> Text(
                            stringResource(R.string.pick_near, state.street), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.fg2)
            Spacer(Modifier.height(KtmSpacing.sm))
            KtmButton(useLabel, onUse, Modifier.fillMaxWidth(), enabled = useEnabled && state.center != null && !state.resolving)
        }
    }
}

/** The pin rises this far while the map is moving. */
private val PIN_LIFT = KtmLayout.pinLift
