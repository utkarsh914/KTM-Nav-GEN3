package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.navigator.app.ui.components.KtmScrim
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmTheme

/*
 * Page motion. Tabs fade through (the map stays put); pushed pages share the X axis: the new page slides in 30 dp and
 * fades in, the page underneath slides back a little and dims. All of it runs on the tokens of KtmMotionSpecs: the page
 * spring for travel, and effects springs for the fades, which are faster than the travel. During a predictive back
 * swipe the seekable transition spreads those over the gesture, so the leaving page has faded out by about a third of
 * the swipe (Android's fade-through) while it keeps shrinking and following the finger. Exits are faster than enters.
 */

/** MO11: a pushed page enters and leaves 30 dp sideways. */
internal val PUSH_OFFSET = KtmLayout.pushOffset

/** MO10: a tab settles in from this scale. */
private const val TAB_SCALE = 0.92f

/** The leaving page shrinks to this in a back swipe (Android's back preview). */
private const val BACK_SCALE = 0.9f

/** MO8: the ride card and the replay page share their bounds, so the pages only fade around that. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isRideContainerMove(): Boolean =
    (initialState.destination.hasRoute<Route.Rides>() && targetState.destination.hasRoute<Route.RideReplay>()) ||
        (initialState.destination.hasRoute<Route.RideReplay>() && targetState.destination.hasRoute<Route.Rides>())

/** Tab to tab, and the end of first run into the app: the old screen fades out, then the new one settles in (MO10, ON10). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch(): Boolean =
    (initialState.destination.tab() != null || initialState.destination.hasRoute<Route.Ready>()) && targetState.destination.tab() != null

// With "Remove animations" every move is a plain fade: no offset, no scale (the tokens collapse to short fades).
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(motion: KtmMotionSpecs, slidePx: Int): EnterTransition = when {
    motion.reduced || isRideContainerMove() -> fadeIn(motion.effectsDefault())
    tabSwitch() -> fadeIn(motion.stage(KtmMotionSpecs.TAB_FADE_DELAY_MS)) + scaleIn(motion.page(), initialScale = TAB_SCALE)
    else -> slideInHorizontally(motion.page()) { slidePx } + fadeIn(motion.effectsDefault())
}

/** The page underneath a push slides back a little and dims (see [PageHost]); it does not fade, so nothing shows through. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(motion: KtmMotionSpecs, slidePx: Int): ExitTransition = when {
    motion.reduced || isRideContainerMove() -> fadeOut(motion.effectsFast())
    tabSwitch() -> fadeOut(motion.effectsFast())
    else -> slideOutHorizontally(motion.page()) { -slidePx }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(motion: KtmMotionSpecs, slidePx: Int): EnterTransition = when {
    motion.reduced || isRideContainerMove() -> fadeIn(motion.effectsDefault())
    tabSwitch() -> fadeIn(motion.stage(KtmMotionSpecs.TAB_FADE_DELAY_MS)) + scaleIn(motion.page(), initialScale = TAB_SCALE)
    else -> slideInHorizontally(motion.page()) { -slidePx }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(motion: KtmMotionSpecs, slidePx: Int): ExitTransition = when {
    motion.reduced || isRideContainerMove() -> fadeOut(motion.effectsFast())
    tabSwitch() -> fadeOut(motion.effectsFast())
    // Also what a predictive back swipe previews: the page shrinks, follows the finger and fades out early (MO11).
    else -> slideOutHorizontally(motion.page()) { slidePx } + fadeOut(motion.effectsFast()) + scaleOut(motion.page(), targetScale = BACK_SCALE)
}

/** The nav controller, so a page can tell it is covered by one pushed over it. Provided by [KtmApp]. */
internal val LocalPageNav = staticCompositionLocalOf<NavHostController?> { null }

/** A page of the app: the one place a page gets the dim that goes with a push. */
internal inline fun <reified T : Any> NavGraphBuilder.page(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry ->
    val scope = this
    PageHost(scope, entry) { scope.content(entry) }
}

/** True when [id] is in the back stack but not on top: another page was pushed over it (a popped page is already leaving). */
internal fun isCovered(stackIds: List<String>, id: String): Boolean = id in stackIds && stackIds.lastOrNull() != id

/**
 * Dims the page while another page is pushed over it (a plain overlay: a page that hosts the map is never scaled,
 * clipped or blurred for this). A page that is popped, or a tab that is switched away from, is not dimmed.
 */
@Composable
internal fun PageHost(scope: AnimatedContentScope, entry: NavBackStackEntry, content: @Composable () -> Unit) {
    val nav = LocalPageNav.current
    val stack by (nav?.visibleEntries ?: return content()).collectAsState()
    // Tabs fade through each other: no dim between two of them.
    val tabToTab = entry.destination.tab() != null && stack.lastOrNull()?.destination.tab() != null
    val covered = isCovered(stack.map { it.id }, entry.id) && !tabToTab
    val motion = KtmTheme.motion
    val dim by scope.transition.animateFloat(transitionSpec = { motion.effectsDefault() }, label = "pageDim") { state ->
        if (state == EnterExitState.PostExit && covered && !motion.reduced) KtmLayout.pageDim else 0f
    }
    Box {
        content()
        // Read while drawing: no recomposition per frame, nothing drawn at rest.
        Box(Modifier.matchParentSize().drawBehind { if (dim > 0f) drawRect(KtmScrim.copy(alpha = dim)) })
    }
}
