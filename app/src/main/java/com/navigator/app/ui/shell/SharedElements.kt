package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme

/** The shared-transition scope around the NavHost and the page being shown, for pages that share bounds with another page. */
@OptIn(ExperimentalSharedTransitionApi::class)
class SharedElements(val scope: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalSharedElements = compositionLocalOf<SharedElements?> { null }

/**
 * MO8: a ride's card and its replay page are one container: the card grows into the page and the page
 * shrinks back into its card. The card side remeasures to the growing bounds; the page side keeps its final
 * layout and is scaled, so the map is framed once at its real size. A no-op without [SharedElements]
 * (previews, tests) and with "Remove animations", where the pages just crossfade.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.rideContainer(rideId: String, remeasure: Boolean = true): Modifier {
    val shared = LocalSharedElements.current ?: return this
    val motion = KtmTheme.motion
    if (motion.reduced) return this
    return with(shared.scope) {
        this@rideContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "ride-$rideId"),
            animatedVisibilityScope = shared.visibility,
            boundsTransform = { _, _ -> motion.spatialDefault() },
            resizeMode = if (remeasure) SharedTransitionScope.ResizeMode.RemeasureToBounds else SharedTransitionScope.ResizeMode.scaleToBounds(),
            clipInOverlayDuringTransition = OverlayClip(KtmShapes.Card),
        )
    }
}
