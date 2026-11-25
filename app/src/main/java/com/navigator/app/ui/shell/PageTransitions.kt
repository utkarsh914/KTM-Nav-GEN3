package com.navigator.app.ui.shell

import android.annotation.SuppressLint
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
import androidx.navigation.NavController
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
import kotlinx.coroutines.flow.StateFlow

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
internal data class PageMove(
    val rideContainer: Boolean, val tab: Boolean, val pageToMap: Boolean, val mapToMap: Boolean, val mapToPage: Boolean,
    /** The NavHost is going back to a page it already had (a pop, or a tab switch that returns to Navigate). */
    val pop: Boolean = false,
)

internal fun pageMove(initial: NavDestination, target: NavDestination, pop: Boolean = false): PageMove {
    val rideContainer = (initial.hasRoute<Route.Rides>() && target.hasRoute<Route.RideReplay>()) ||
        (initial.hasRoute<Route.RideReplay>() && target.hasRoute<Route.Rides>())
    val tab = (initial.tab() != null || initial.hasRoute<Route.Ready>()) && target.tab() != null
    return PageMove(
        rideContainer = rideContainer,
        tab = tab,
        // The ride card's move is its own: Rides stays put and opaque while the replay grows out of the card.
        pageToMap = !initial.isMapPage() && target.isMapPage() && !rideContainer,
        mapToMap = initial.isMapPage() && target.isMapPage() && !rideContainer,
        mapToPage = initial.isMapPage() && !target.isMapPage() && !rideContainer,
        pop = pop,
    )
}

/** The length of a page's fade: the default effects token, or the fast one (null: the page does not fade). */
internal enum class PageFade { Default, Fast }

/**
 * Whether the page on one side of a move fades, and how fast. Pages fade without an offscreen layer ([layerFade]), so
 * the transitions in [enterFor] and the others only slide; the fades are decided here, in one place. [pop] is a back
 * move; [entering] is the page that arrives (else the one that leaves). The full matrix (S = slides, F = fades, D = dims,
 * "-" = nothing; no side slides and fades at once except the dissolves):
 *
 * | move                    | entering                 | leaving                                    |
 * |-------------------------|--------------------------|--------------------------------------------|
 * | push page over page     | S                        | S back a quarter, D                        |
 * | push, tab to tab        | F + rise                 | - (stays under the new tab)                |
 * | push, page to map page  | F (controls)             | F, held (the page dissolves away)          |
 * | push, map to map        | F                        | F, held                                    |
 * | push, map to page       | S                        | - (the map page only dims)                 |
 * | push, ride card         | F                        | - (Rides stays opaque, held until covered) |
 * | pop page                | S back in, lit again     | S out                                      |
 * | pop, map to page        | F                        | F, held                                    |
 * | pop, map to map         | F                        | F, held                                    |
 * | pop, ride card          | -                        | F fast (the replay), held                  |
 * | pop to Navigate (tab)   | - (fully visible)        | F fast on top, held, no rise               |
 * | Remove animations       | push: F; pop: -          | push: -; pop: F fast                       |
 */
internal fun pageFade(move: PageMove, entering: Boolean, pop: Boolean, reduced: Boolean): PageFade? = when {
    !pop && entering -> if (reduced || move.rideContainer || move.tab || move.pageToMap || move.mapToMap) PageFade.Default else null
    !pop -> if (move.pageToMap || move.mapToMap) PageFade.Default else null
    entering -> if (!move.rideContainer && !move.tab && (move.mapToPage || move.mapToMap)) PageFade.Default else null
    move.rideContainer -> PageFade.Fast
    move.tab -> PageFade.Fast
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
    // Rides stays until the replay has covered it, or the stale map would show around the growing card.
    isRideContainerMove() || pageToMap() || mapToMap() -> holdUntilFaded(motion.effectsDefault())
    motion.reduced || tabSwitch() -> ExitTransition.None
    initialState.destination.isMapPage() -> ExitTransition.None
    else -> slideOutHorizontally(motion.page()) { -it / UNDERLAY_SHIFT }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(motion: KtmMotionSpecs): EnterTransition = when {
    isRideContainerMove() || tabSwitch() || mapToPage() || mapToMap() -> EnterTransition.None
    motion.reduced || targetState.destination.isMapPage() -> EnterTransition.None
    else -> slideInHorizontally(motion.page()) { -it / UNDERLAY_SHIFT }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(motion: KtmMotionSpecs): ExitTransition = when {
    isRideContainerMove() -> holdUntilFaded(motion.effectsFast())
    mapToPage() || mapToMap() -> holdUntilFaded(motion.effectsDefault())
    // Back to Navigate from another tab: the old tab dissolves away on top of a Navigate that is already there.
    motion.reduced || tabSwitch() -> holdUntilFaded(motion.effectsFast())
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
    CompositionLocalProvider(LocalPageSettled provides settled, LocalPageEntry provides entry) {
        Box(ground.pageFade(scope)) {
            content()
            PageDim(scope, entry)
        }
    }
}

/** The fade of [PageHost]'s page, decided by [pageFade] from the move the NavHost is making. */
@Composable
private fun Modifier.pageFade(scope: AnimatedContentScope): Modifier {
    val history = LocalPageHistory.current
    val motion = KtmTheme.motion
    val parent = scope.transition.parentTransition
    val alpha = scope.transition.animateFloat(
        transitionSpec = {
            val fade = fadeOf(parent, history, entering = targetState == EnterExitState.Visible, reduced = motion.reduced)
            if (fade == PageFade.Fast) motion.effectsFast() else motion.effectsDefault()
        },
        label = "pageFade",
    ) { state ->
        if (state == EnterExitState.Visible || fadeOf(parent, history, entering = state == EnterExitState.PreEnter, reduced = motion.reduced) == null) 1f else 0f
    }
    // A tab that arrives also settles up into place, on the same progress as its fade (peers fade, they do not slide).
    val rise = with(LocalDensity.current) { KtmSpacing.sm.toPx() }
    return graphicsLayer {
        this.alpha = alpha.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
        if (!motion.reduced && scope.transition.targetState == EnterExitState.Visible && isTabMove(parent, history)) translationY = (1f - alpha.value) * rise
    }
}

private fun isTabMove(parent: androidx.compose.animation.core.Transition<*>?, history: BackStackHistory?): Boolean {
    val initial = parent?.currentState as? NavBackStackEntry ?: return false
    val target = parent.targetState as? NavBackStackEntry ?: return false
    // A tab that returns (back to Navigate) is already there: no rise.
    return pageMove(initial.destination, target.destination).tab && history?.isPop(target.id) != true
}

private fun fadeOf(parent: androidx.compose.animation.core.Transition<*>?, history: BackStackHistory?, entering: Boolean, reduced: Boolean): PageFade? {
    val initial = (parent?.currentState as? NavBackStackEntry) ?: return null
    val target = (parent.targetState as? NavBackStackEntry) ?: return null
    if (initial.id == target.id) return null
    val pop = history?.isPop(target.id) == true
    return pageFade(pageMove(initial.destination, target.destination, pop), entering, pop, reduced)
}

/**
 * Whether the NavHost is going back to a page it already had. The NavHost's own flag is internal, and the leaving page
 * is no use as a witness (a tab switch also removes it from the stack, saving its state, and is still a push). The
 * arriving page is: a back move returns to an entry that was in the stack before the change, a push or a tab switch
 * arrives at one that was not (a restored tab keeps its id but was not in the stack either).
 */
internal class BackStackHistory(private val nav: NavHostController) {
    private var current: Set<String> = nav.backStack().value.map { it.id }.toSet()
    private var before: Set<String> = emptySet()

    private val listener = NavController.OnDestinationChangedListener { controller, _, _ -> record(controller.backStack().value.map { it.id }) }

    internal fun record(ids: List<String>) {
        val now = ids.toSet()
        if (now == current) return
        before = current
        current = now
    }

    fun isPop(targetId: String): Boolean = targetId in before

    fun start() = nav.addOnDestinationChangedListener(listener)
    fun stop() = nav.removeOnDestinationChangedListener(listener)
}

/** The entry whose page is composing, so a map page can tell whether it is the top of the stack. Provided by [PageHost]. */
internal val LocalPageEntry = staticCompositionLocalOf<NavBackStackEntry?> { null }

internal val LocalPageHistory = staticCompositionLocalOf<BackStackHistory?> { null }

/**
 * The real back stack. The public [NavController.visibleEntries] also lists an entry that is being popped (that is what
 * made every Back dim the leaving page), so the stack itself is read; it is library-group API, wrapped here once.
 */
@SuppressLint("RestrictedApi")
internal fun NavController.backStack(): StateFlow<List<NavBackStackEntry>> = currentBackStack

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
    // The back stack, not the visible entries: a page that is being popped is still visible but no longer in the stack,
    // and must not be dimmed as if something covered it.
    val stack by (nav?.backStack() ?: remember { MutableStateFlow(emptyList<NavBackStackEntry>()) }).collectAsState()
    val top = stack.lastOrNull()?.destination
    val covered = isCovered(stack.map { it.id }, entry.id) && dimsUnder(entry.destination, top)
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
