package com.navigator.app.ui.feature.navigate.pick

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.LandscapePanel
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.components.MapControlButton
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.components.consumeMapTouches
import com.navigator.app.ui.feature.navigate.plan.StopNumberBadge
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.LocalRidingSurface
import com.navigator.app.ui.theme.mapCornerPadding
import com.navigator.app.ui.theme.pageInsets

/**
 * HM7/PL6: a pin fixed in the middle, a pill on top saying what to do, and a sheet naming the nearest street
 * with the action. Over the live map, so panning is what moves the point (no precise tap needed). A stop
 * (PL6) has an accent pin with a "+", its number in the sheet header and a line saying where it goes.
 * Landscape: one panel at the left with the same content (the shared landscape panel), and the pin moves to the
 * middle of the map that is left open ([onFootprint] tells the host how much is covered, so it can pad the map).
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
    /** Guidance kept in view above the hint while a trip runs (the next turn): the trip does not pause for the pick. */
    guidance: (@Composable () -> Unit)? = null,
    /** Px of the map's left side covered by the landscape panel; 0 in portrait. */
    onFootprint: (Int) -> Unit = {},
) {
    val c = KtmTheme.colors
    val report by rememberUpdatedState(onFootprint)
    var footprintPx by remember { mutableIntStateOf(0) }
    val useEnabledNow = useEnabled && state.center != null && !state.resolving
    // The pin sits in the un-inset box so its tip is the map's centre, which is where the point is read.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = LandscapePanelSpec.isLandscape(maxWidth, maxHeight)
        val tallEnough = maxHeight >= KtmLayout.pickGuidanceMinHeight
        // Portrait covers no side of the map; landscape reports its panel once it is laid out.
        LaunchedEffect(landscape) { if (!landscape) { footprintPx = 0; report(0) } }
        if (!landscape) {
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().mapCornerPadding().consumeMapTouches(),
                verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
            ) {
                // On a short screen the guidance would crowd the pin and the sheet: the hint alone stays.
                if (tallEnough) guidance?.invoke()
                HintRow(onBack, hint, pill = true)
            }
        }
        Pin(state, stopNumber, Modifier.align(Alignment.Center).padding(start = with(LocalDensity.current) { footprintPx.toDp() }))
        if (landscape) {
            LandscapePanel(onFootprint = { footprintPx = it; report(it) }) {
                // Scrolls on a short window; the action stays at the foot.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    guidance?.invoke()
                    HintRow(onBack, hint, pill = false)
                    Details(state, title, stopNumber, subtitle)
                }
                UseButton(useLabel, onUse, useEnabledNow)
            }
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .shadow(KtmElevation.sheet, KtmShapes.SheetTop)
                    .background(c.surf, KtmShapes.SheetTop).consumeMapTouches()
                    .padding(horizontal = KtmSpacing.lg).padding(top = KtmSpacing.lg)
                    .pageInsets()
                    .padding(bottom = KtmSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            ) {
                Details(state, title, stopNumber, subtitle)
                Spacer(Modifier.height(KtmSpacing.sm))
                UseButton(useLabel, onUse, useEnabledNow)
            }
        }
    }
}

/** Back, and what to do: a pill over the map, or plain text inside the landscape panel. */
@Composable
private fun HintRow(onBack: () -> Unit, hint: String, pill: Boolean) {
    val c = KtmTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        MapControlButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), onBack)
        val box = if (pill) {
            Modifier.weight(1f).heightIn(min = KtmLayout.targetPrimary).shadow(KtmElevation.onMap, KtmShapes.Card)
                .background(c.surf, KtmShapes.Card).padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm)
        } else Modifier.weight(1f)
        Box(box, contentAlignment = Alignment.Center) {
            Text(
                hint, style = MaterialTheme.typography.titleMedium, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The pin's tip is the point: the icon is lifted by half its height. HM7: it drops in and settles, lifts while the map moves and settles again. */
@Composable
private fun Pin(state: PickOnMapState, stopNumber: Int?, modifier: Modifier) {
    val motion = KtmTheme.motion
    // Mid-trip the pin moves on the riding tier: a plain short tween, no bounce.
    val riding = LocalRidingSurface.current
    val pinLift by animateDpAsState(if (state.resolving) PIN_LIFT else 0.dp, if (riding) motion.ridingQuick() else motion.spatialFast(), label = "pinLift")
    val drop = remember { Animatable(if (motion.reduced) 0f else 1f) }
    LaunchedEffect(Unit) { drop.animateTo(0f, if (riding) motion.ridingQuick() else motion.playful()) }
    Icon(
        if (stopNumber != null) KtmIcons.PinPlus else KtmIcons.Pin, null, tint = KtmTheme.accent.fill,
        modifier = modifier.size(KtmLayout.mapPin).offset { IntOffset(0, (-(KtmLayout.mapPin / 2 + pinLift + KtmLayout.mapPin * 2 * drop.value)).roundToPx()) },
    )
}

/** The heading with the stop's number, the street under the pin, and where a stop goes. */
@Composable
private fun Details(state: PickOnMapState, title: String, stopNumber: Int?, subtitle: String?) {
    val c = KtmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        if (stopNumber != null) {
            // As on the map: a white disc with a dark ring, whatever the theme.
            StopNumberBadge(stopNumber, Modifier.size(KtmLayout.stopMarker))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
            )
            when {
                state.resolving && state.street == null -> Skeleton(Modifier.fillMaxWidth(0.6f).height(KtmSpacing.xl))
                else -> Text(
                    if (state.street != null) stringResource(R.string.pick_near, state.street) else stringResource(R.string.pick_no_street),
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.fg2)
}

@Composable
private fun UseButton(label: String, onUse: () -> Unit, enabled: Boolean) {
    // The primary action of a riding surface is a 64 dp target.
    KtmButton(label, onUse, Modifier.fillMaxWidth(), size = if (LocalRidingSurface.current) ButtonSize.Riding else ButtonSize.Rest, enabled = enabled)
}

/** The pin rises this far while the map is moving. */
private val PIN_LIFT = KtmLayout.pinLift
