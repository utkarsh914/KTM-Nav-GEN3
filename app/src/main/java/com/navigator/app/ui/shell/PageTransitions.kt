package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.navigator.app.ui.components.KtmScrim
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * Page motion (D28). A pushed page is opaque and slides in over the page below from the right edge, full width, with no
 * alpha; the page below moves back a quarter of the width and dims (see PageDim). The map is one layer behind all the
 * pages (D27), so a map page is transparent: only its controls travel, and a map page underneath only dims. Tabs fade
 * the new tab in over the old one: no scale, no gap. The few moves that cannot slide because the map is between the
 * pages dissolve instead. All of it runs on the tokens of KtmMotionSpecs. A predictive back swipe seeks the same
 * transitions: no shrink, no corners.
 */

/** The page under a push moves back by one over this share of the width. */
private const val UNDERLAY_SHIFT = 4

/**
 * Map pages are transparent over the shared map layer, so what a page change looks like depends on who is a map page:
 * page to page slides; two map pages cross-fade their controls; an opaque page over a map page slides in with no alpha (a fade would show the
 * static map through it); a map page over, or back to, an opaque page dissolves, because that page sits between the map
 * and the map page and a slide would show it through. The ride card's container move keeps the Rides page opaque and
 * still until the replay has covered it, so a stale map never shows in between.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.move() = pageMove(initialState.destination, targetState.destination)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mapToPage() = move().mapToPage

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageToMap() = move().pageToMap

/** Tab to tab, and the end of first run into the app (MO10, ON10). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch() = move().tab

/** MO8: the ride card and the replay page share their bounds; the Rides page itself stays put, the replay fades around them. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isRideContainerMove() = move().rideContainer

/** Two map pages: both are transparent over the same map, so only their controls cross-fade (a slide would show two sets). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.mapToMap() = move().mapToMap

/** What a move between two destinations is, for the transitions and for the fades of [PageHost]. */
internal data class PageMove(val rideContainer: Boolean, val tab: Boolean, val pageToMap: Boolean, val mapToMap: Boolean, val mapToPage: Boolean)

internal fun pageMove(initial: NavDestination, target: NavDestination): PageMove {
    val rideContainer = (initial.hasRoute<Route.Rides>() && target.hasRoute<Route.RideReplay>()) ||
        (initial.hasRoute<Route.RideReplay>() && target.hasRoute<Route.Rides>())
    val tab = (initial.tab() != null || initial.hasRoute<Route.Ready>()) && target.tab() != null
    return PageMove(
        rideContainer = rideContainer,
        tab = tab,
        pageToMap = !initial.isMapPage() && target.isMapPage(),
        mapToMap = initial.isMapPage() && target.isMapPage() && !rideContainer,
        mapToPage = initial.isMapPage() && !target.isMapPage(),
    )
}

/** The length of a page's fade: the default effects token, or the fast one (null: the page does not fade). */
internal enum class PageFade { Default, Fast }

/**
 * Whether the page on one side of a move fades, and how fast. Pages fade without an offscreen layer ([layerFade]), so
 * the transitions in [enterFor] and the others only slide; the fades are decided here, in one place. [pop] is a back
 * move; [entering] is the page that arrives (else the one that leaves). A tab that is left stays under the new one.
 */
internal fun pageFade(move: PageMove, entering: Boolean, pop: Boolean, reduced: Boolean): PageFade? = when {
    !pop && entering -> if (reduced || move.rideContainer || move.tab || move.pageToMap || move.mapToMap) PageFade.Default else null
    !pop -> if (move.pageToMap || move.mapToMap) PageFade.Default else null
    entering -> if (!move.rideContainer && (move.tab || move.mapToPage || move.mapToMap)) PageFade.Default else null
    move.rideContainer -> PageFade.Fast
    move.mapToPage || move.mapToMap -> PageFade.Default
    reduced -> PageFade.Fast
    else -> null
}

// With "Remove animations" every move is a plain fade: no offset (the tokens collapse to short fades). The page that
// stays underneath is never faded out while the other fades in, or the ground would show through between them.
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(motion: KtmMotionSpecs): EnterTransition = when {
    isRideContainerMove() || motion.reduced || tabSwitch() || pageToMap() || mapToMap() -> EnterTransition.None
    else -> slideInHorizontally(motion.page()) { it }
}

/** The page underneath a push moves back a quarter and dims (see [PageHost]); it does not fade, so nothing shows through. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(motion: KtmMotionSpecs): ExitTransition = when {
    pageToMap() || mapToMap() -> holdUntilFaded(motion.effectsDefault())
    isRideContainerMove() || motion.reduced || tabSwitch() -> ExitTransition.None
    initialState.destination.isMapPage() -> ExitTransition.None
    else -> slideOutHorizontally(motion.page()) { -it / UNDERLAY_SHIFT }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(motion: KtmMotionSpecs): EnterTransition = when {
    isRideContainerMove() -> EnterTransition.None
    tabSwitch() || mapToPage() || mapToMap() -> EnterTransition.None
    motion.reduced || targetState.destination.isMapPage() -> EnterTransition.None
    else -> slideInHorizontally(motion.page()) { -it / UNDERLAY_SHIFT }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(motion: KtmMotionSpecs): ExitTransition = when {
    isRideContainerMove() -> holdUntilFaded(motion.effectsFast())
    mapToPage() || mapToMap() -> holdUntilFaded(motion.effectsDefault())
    motion.reduced -> holdUntilFaded(motion.effectsFast())
    tabSwitch() -> ExitTransition.None
    // Also what a predictive back swipe previews: the same slide as the arrow, nothing else.
    else -> slideOutHorizontally(motion.page()) { it }
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

@Composable
internal fun PageHost(scope: AnimatedContentScope, entry: NavBackStackEntry, content: @Composable () -> Unit) {
    // A map page is transparent over the map layer; every other page covers it with the app's ground.
    val ground = if (entry.destination.paintsOwnGround()) Modifier else Modifier.coverGround()
    val settled = remember(scope) { derivedStateOf { !scope.transition.isRunning } }
    CompositionLocalProvider(LocalPageSettled provides settled) {
        Box(ground.pageFade(scope)) {
            content()
            PageDim(scope, entry)
        }
    }
}

/** The fade of [PageHost]'s page, decided by [pageFade] from the move the NavHost is making. */
@Composable
private fun Modifier.pageFade(scope: AnimatedContentScope): Modifier {
    val nav = LocalPageNav.current
    val motion = KtmTheme.motion
    val parent = scope.transition.parentTransition
    val alpha = scope.transition.animateFloat(
        transitionSpec = {
            val fade = fadeOf(parent, nav, entering = targetState == EnterExitState.Visible, reduced = motion.reduced)
            if (fade == PageFade.Fast) motion.effectsFast() else motion.effectsDefault()
        },
        label = "pageFade",
    ) { state ->
        if (state == EnterExitState.Visible || fadeOf(parent, nav, entering = state == EnterExitState.PreEnter, reduced = motion.reduced) == null) 1f else 0f
    }
    // A tab that arrives also settles up into place, on the same progress as its fade (peers fade, they do not slide).
    val rise = with(LocalDensity.current) { KtmSpacing.sm.toPx() }
    return graphicsLayer {
        this.alpha = alpha.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
        if (!motion.reduced && scope.transition.targetState == EnterExitState.Visible && isTabMove(parent)) translationY = (1f - alpha.value) * rise
    }
}

private fun isTabMove(parent: androidx.compose.animation.core.Transition<*>?): Boolean {
    val initial = (parent?.currentState as? NavBackStackEntry)?.destination ?: return false
    val target = (parent.targetState as? NavBackStackEntry)?.destination ?: return false
    return pageMove(initial, target).tab
}

private fun fadeOf(parent: androidx.compose.animation.core.Transition<*>?, nav: NavHostController?, entering: Boolean, reduced: Boolean): PageFade? {
    val initial = (parent?.currentState as? NavBackStackEntry) ?: return null
    val target = (parent.targetState as? NavBackStackEntry) ?: return null
    if (initial.id == target.id) return null
    // In a back move the page that leaves is still in the visible entries, above the one that returns.
    val visible = nav?.visibleEntries?.value.orEmpty()
    val from = visible.indexOfFirst { it.id == initial.id }
    val to = visible.indexOfFirst { it.id == target.id }
    val pop = from >= 0 && to >= 0 && from > to
    return pageFade(pageMove(initial.destination, target.destination), entering, pop, reduced)
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
