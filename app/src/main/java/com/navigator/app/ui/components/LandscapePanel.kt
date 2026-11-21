package com.navigator.app.ui.components

import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.remember
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme

/** The one landscape panel (§3.3): its width and the part of the map it leaves open. */
object LandscapePanelSpec {
    fun width(windowWidthDp: Float): Dp = KtmLayout.panelWidth(windowWidthDp)

    /** Where the open map starts: the safe-area inset, the margin, the panel and the margin after it. */
    fun footprint(windowWidthDp: Float, leftInset: Dp): Dp =
        leftInset + KtmLayout.panelInset + width(windowWidthDp) + KtmLayout.panelInset

    fun isLandscape(width: Dp, height: Dp): Boolean = width > height
}

/** The window is narrower than [KtmLayout.panelNarrowBelow]: the 780 dp landscape case, where the 320 dp panel is used. */
@Composable
fun isNarrowWindow(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    // No size yet (a preview, a test): the wide layout.
    return widthPx > 0 && with(LocalDensity.current) { widthPx.toDp() } < KtmLayout.panelNarrowBelow
}

/**
 * The panel every landscape screen shares: 352 dp (320 below 800 dp wide), 8 dp from the safe area on the cutout
 * side, 12 dp padding and gap. It stays on the left, also in reverse landscape (the inset moves to the right).
 * [onFootprint] reports how much of the map's left side it covers (px), so the camera frames the open part.
 * Place it in a box that fills the screen.
 */
@Composable
fun LandscapePanel(
    modifier: Modifier = Modifier,
    color: Color = KtmTheme.colors.surf,
    /** Applied to the panel itself (not the empty area around it). */
    panelModifier: Modifier = Modifier,
    /** Space between the panel's children; the plan sheets bring their own spacing. */
    gap: Dp = 0.dp,
    onFootprint: (leftPx: Int) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val report by rememberUpdatedState(onFootprint)
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val leftInset = with(density) { safe.getLeft(density, LayoutDirection.Ltr).toDp() }
        val footprintPx = with(density) { LandscapePanelSpec.footprint(maxWidth.value, leftInset).roundToPx() }
        LaunchedEffect(footprintPx) { report(footprintPx) }
        // The panel slides in from the cutout side as the map tilts (NL1).
        val motion = KtmTheme.motion
        val entered = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = entered,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(motion.effectsDefault()) + slideInHorizontally(motion.ridingSpatial()) { if (motion.reduced) 0 else -it },
        ) {
        Column(
            Modifier
                .windowInsetsPadding(safe.only(WindowInsetsSides.Left + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .padding(KtmLayout.panelInset)
                .width(LandscapePanelSpec.width(maxWidth.value))
                .fillMaxHeight()
                .shadow(KtmElevation.sheet, KtmShapes.Card)
                .background(color, KtmShapes.Card)
                // Touches on the card never fall through to the map beneath it.
                .pointerInput(Unit) { detectTapGestures { } }
                .then(panelModifier)
                .padding(KtmLayout.panelPadding),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
        }
    }
}
