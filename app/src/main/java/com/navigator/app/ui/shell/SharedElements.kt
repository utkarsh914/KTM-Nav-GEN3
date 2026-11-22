package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
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

/** The riding stage of the Navigate screen: the trip bar and the activity strip in the Where-to sheet share bounds through it (MO6). */
val LocalTripShared = compositionLocalOf<SharedElements?> { null }

/**
 * MO6: minimising turns the trip bar into the activity strip and resuming turns the strip back into the bar. Marks
 * whichever of the two is on screen; a no-op without [LocalTripShared] and with "Remove animations".
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.tripStripBounds(): Modifier {
    val shared = LocalTripShared.current ?: return this
    val motion = KtmTheme.motion
    if (motion.reduced) return this
    return with(shared.scope) {
        this@tripStripBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = TRIP_STRIP_KEY),
            animatedVisibilityScope = shared.visibility,
            enter = fadeIn(motion.effectsFast()),
            exit = fadeOut(motion.effectsFast()),
            // On the riding stage: at most 250 ms, no overshoot.
            boundsTransform = { _, _ -> motion.ridingSpatial<Rect>() },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(KtmShapes.Card),
        )
    }
}

/**
 * M4: the Where to? field and the search panel are one container: the field grows into the panel and the panel shrinks
 * back into the field. Both sides scale their content to the moving bounds (the panel is laid out once, at full size,
 * so its list is never remeasured per frame), clipped to the sheet shape. A no-op without [LocalTripShared] and with
 * "Remove animations"; if the field is not on screen (a search from a shortcut) the panel just fades.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.searchContainer(): Modifier {
    val shared = LocalTripShared.current ?: return this
    val motion = KtmTheme.motion
    if (motion.reduced) return this
    return with(shared.scope) {
        this@searchContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key = SEARCH_KEY),
            animatedVisibilityScope = shared.visibility,
            enter = fadeIn(motion.effectsDefault()),
            exit = fadeOut(motion.effectsFast()),
            boundsTransform = { _, _ -> motion.spatialDefault<Rect>() },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(KtmShapes.Sheet),
        )
    }
}

private const val SEARCH_KEY = "search-container"

private const val TRIP_STRIP_KEY = "trip-strip"
