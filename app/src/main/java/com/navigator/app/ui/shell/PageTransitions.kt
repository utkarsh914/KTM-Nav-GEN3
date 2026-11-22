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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.navigator.app.ui.components.KtmScrim
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * Page motion. Tabs fade through (the map stays put); pushed pages share the X axis: the new page slides in 30 dp and
 * fades in, the page underneath slides back a little and dims. All of it runs on the tokens of KtmMotionSpecs: the page
 * spring for travel, and effects springs for the fades, which are faster than the travel. During a predictive back
 * swipe the seekable transition spreads those over the gesture, so the leaving page has faded out by about a third of
 * the swipe (Android's fade-through) while it keeps sliding. Only list pages shrink and take corners; a page that hosts
 * the map is never scaled, and the Navigate tab is not scaled either. Exits are faster than enters.
 */

/** MO11: a pushed page enters and leaves 30 dp sideways. */
internal val PUSH_OFFSET = KtmLayout.pushOffset

/** MO10: a tab settles in from this scale. */
private const val TAB_SCALE = 0.92f

/** MO8: the ride card and the replay page share their bounds, so the pages only fade around that. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isRideContainerMove(): Boolean =
    (initialState.destination.hasRoute<Route.Rides>() && targetState.destination.hasRoute<Route.RideReplay>()) ||
        (initialState.destination.hasRoute<Route.RideReplay>() && targetState.destination.hasRoute<Route.Rides>())

/** Tab to tab, and the end of first run into the app: the old screen fades out, then the new one settles in (MO10, ON10). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch(): Boolean =
    (initialState.destination.tab() != null || initialState.destination.hasRoute<Route.Ready>()) && targetState.destination.tab() != null

/** The new tab fades in after the old one and settles in from [TAB_SCALE]; the Navigate tab (the map) only fades. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(motion: KtmMotionSpecs): EnterTransition {
    val fade = fadeIn(motion.stage(KtmMotionSpecs.FADE_THROUGH_DELAY_MS))
    return if (targetState.destination.hasRoute<Route.Navigate>()) fade else fade + scaleIn(motion.page(), initialScale = TAB_SCALE)
}

// With "Remove animations" every move is a plain fade: no offset, no scale (the tokens collapse to short fades).
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(motion: KtmMotionSpecs, slidePx: Int): EnterTransition = when {
    motion.reduced || isRideContainerMove() -> fadeIn(motion.effectsDefault())
    tabSwitch() -> tabEnter(motion)
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
    tabSwitch() -> tabEnter(motion)
    else -> slideInHorizontally(motion.page()) { -slidePx }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(motion: KtmMotionSpecs, slidePx: Int): ExitTransition = when {
    motion.reduced || isRideContainerMove() -> fadeOut(motion.effectsFast())
    tabSwitch() -> fadeOut(motion.effectsFast())
    // Also what a predictive back swipe previews: the list page shrinks and fades out early (a map page only slides and fades; MO11).
    else -> slideOutHorizontally(motion.page()) { slidePx } + fadeOut(motion.effectsFast()) +
        (if (initialState.destination.isListPage()) scaleOut(motion.page(), targetScale = KtmMotionSpecs.BACK_SCALE) else ExitTransition.None)
}

/** The nav controller, so a page can tell it is covered by one pushed over it. Provided by [KtmApp]. */
internal val LocalPageNav = staticCompositionLocalOf<NavHostController?> { null }

/** A page of the app: the one place a page gets the dim that goes with a push (and, for [listPage], its corners). */
internal inline fun <reified T : Any> NavGraphBuilder.page(
    listPage: Boolean = false,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable<T> { entry ->
    val scope = this
    PageHost(scope, entry, listPage) { scope.content(entry) }
}

@Composable
internal fun PageHost(scope: AnimatedContentScope, entry: NavBackStackEntry, listPage: Boolean, content: @Composable () -> Unit) {
    Box(if (listPage) Modifier.leavingCorners(scope) else Modifier) {
        content()
        PageDim(scope, entry)
    }
}

/** True when [id] is in the back stack but not on top: another page was pushed over it. */
internal fun isCovered(stackIds: List<String>, id: String): Boolean = id in stackIds && stackIds.lastOrNull() != id

private fun NavDestination?.isTabLike(): Boolean = this != null && (tab() != null || hasRoute<Route.Ready>())

/** Whether [top] dims [under]: not tab to tab (they fade through) and not the ride card's shared-bounds move. */
internal fun dimsUnder(under: NavDestination, top: NavDestination?): Boolean = when {
    under.isTabLike() && top.isTabLike() -> false
    under.hasRoute<Route.Rides>() && top?.hasRoute<Route.RideReplay>() == true -> false
    else -> true
}

/**
 * The dim of a push: the page underneath goes 25 % darker while it slides back. A plain overlay, drawn inside the page
 * (a page that hosts the map is never scaled, clipped or blurred for this). Coming back by Back it is lit again; the
 * page that is popped, and a tab that is switched away from, are not dimmed. Separate from [PageHost] so that a change
 * of the back stack recomposes this overlay only, not the page.
 */
@Composable
private fun PageDim(scope: AnimatedContentScope, entry: NavBackStackEntry) {
    val nav = LocalPageNav.current
    val visible by (nav?.visibleEntries ?: remember { MutableStateFlow(emptyList<NavBackStackEntry>()) }).collectAsState()
    val top = visible.lastOrNull()?.destination
    val covered = isCovered(visible.map { it.id }, entry.id) && dimsUnder(entry.destination, top)
    // A page coming back from underneath starts dimmed and clears; a tab revisited does not.
    val returning = scope.transition.targetState == EnterExitState.Visible && !entry.destination.isTabLike()
    val dimmedAway = covered || returning
    val motion = KtmTheme.motion
    val dim by scope.transition.animateFloat(transitionSpec = { motion.effectsDefault() }, label = "pageDim") { state ->
        if (state == EnterExitState.PostExit && dimmedAway && !motion.reduced) KtmLayout.pageDim else 0f
    }
    // Read while drawing: no recomposition per frame, nothing drawn at rest.
    Box(Modifier.fillMaxSize().drawBehind { if (dim > 0f) drawRect(KtmScrim.copy(alpha = dim)) })
}
