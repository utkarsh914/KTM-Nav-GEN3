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
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/**
 * HM7: a pin fixed in the middle, the nearest street's name below, Use this point. Over the live map, so
 * panning is what moves the point (no precise tap needed).
 */
@Composable
fun PickOnMapScreen(
    state: PickOnMapState,
    onBack: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.navigate_tile_pick),
    /** A line under the street ("Stop 2 · goes before Toit"). */
    subtitle: String? = null,
    useLabel: String = stringResource(R.string.pick_use),
    useEnabled: Boolean = true,
) {
    val c = KtmTheme.colors
    // The pin sits in the un-inset box so its tip is the map's centre, which is where the point is read.
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(KtmSpacing.lg).shadow(KtmElevation.onMap, KtmShapes.Pill)
                .background(c.surf, KtmShapes.Pill),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
        ) {
            KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), onBack)
            Text(
                title, style = MaterialTheme.typography.titleLarge, color = c.fg,
                modifier = Modifier.padding(end = KtmSpacing.lg),
            )
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
            KtmIcons.Pin, null, tint = KtmTheme.accent.fill,
            modifier = Modifier.align(Alignment.Center).size(KtmLayout.mapPin).offset { androidx.compose.ui.unit.IntOffset(0, (-(KtmLayout.mapPin / 2 + pinLift + KtmLayout.mapPin * 2 * drop.value)).roundToPx()) },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth().padding(KtmSpacing.lg)
                .shadow(KtmElevation.sheet, KtmShapes.Card).background(c.surf, KtmShapes.Card).padding(KtmSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                when {
                    state.resolving && state.street == null -> Skeleton(Modifier.fillMaxWidth(0.6f).height(KtmSpacing.xl))
                    state.street != null -> Text(
                        state.street, style = MaterialTheme.typography.titleLarge, color = c.fg,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    else -> Text(stringResource(R.string.pick_hint), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.fg2)
            KtmButton(useLabel, onUse, Modifier.fillMaxWidth(), enabled = useEnabled && state.center != null && !state.resolving)
        }
    }
}

/** The pin rises this far while the map is moving. */
private val PIN_LIFT = KtmLayout.pinLift
