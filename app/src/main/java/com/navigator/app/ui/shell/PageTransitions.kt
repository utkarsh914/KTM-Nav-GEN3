package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.navigator.app.ui.theme.KtmLayout

// ---- Transitions: tabs fade through (the map stays put); pushed pages share the X axis. ----

private const val FADE_MS = 200
private const val SLIDE_MS = 300

/** MO10: the old tab fades out, then the new one fades and settles in. */
private const val TAB_OUT_MS = 90
private const val TAB_IN_MS = 210

/** MO11: a pushed page enters and leaves 30 dp sideways. */
internal val PUSH_OFFSET = KtmLayout.pushOffset
private const val TAB_SCALE = 0.92f
private const val BACK_SCALE = 0.9f

/** MO8: the ride card and the replay page share their bounds, so the pages only fade around that. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isRideContainerMove(): Boolean =
    (initialState.destination.hasRoute<Route.Rides>() && targetState.destination.hasRoute<Route.RideReplay>()) ||
        (initialState.destination.hasRoute<Route.RideReplay>() && targetState.destination.hasRoute<Route.Rides>())

/** Tab to tab, and the end of first run into the app: the old screen fades out, then the new one settles in (MO10, ON10). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch(): Boolean =
    (initialState.destination.tab() != null || initialState.destination.hasRoute<Route.Ready>()) && targetState.destination.tab() != null

// With "Remove animations" every move becomes a crossfade.
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(reduced: Boolean, slidePx: Int): EnterTransition = when {
    reduced -> fadeIn(tween(FADE_MS, delayMillis = FADE_MS / 2))
    isRideContainerMove() -> fadeIn(tween(FADE_MS))
    tabSwitch() -> fadeIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS)) + scaleIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS), initialScale = TAB_SCALE)
    else -> slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { slidePx } + fadeIn(tween(FADE_MS))
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(reduced: Boolean, slidePx: Int): ExitTransition = when {
    reduced -> fadeOut(tween(FADE_MS / 2))
    tabSwitch() -> fadeOut(tween(TAB_OUT_MS))
    isRideContainerMove() -> fadeOut(tween(FADE_MS))
    else -> slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -slidePx } + fadeOut(tween(FADE_MS))
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(reduced: Boolean, slidePx: Int): EnterTransition = when {
    reduced -> fadeIn(tween(FADE_MS, delayMillis = FADE_MS / 2))
    isRideContainerMove() -> fadeIn(tween(FADE_MS))
    tabSwitch() -> fadeIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS)) + scaleIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS), initialScale = TAB_SCALE)
    else -> slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -slidePx } + fadeIn(tween(FADE_MS))
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(reduced: Boolean, slidePx: Int): ExitTransition = when {
    reduced -> fadeOut(tween(FADE_MS / 2))
    tabSwitch() -> fadeOut(tween(TAB_OUT_MS))
    isRideContainerMove() -> fadeOut(tween(FADE_MS))
    // Also what a predictive back swipe previews: the page shrinks and follows the finger (MO11).
    else -> slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { slidePx } + fadeOut(tween(FADE_MS)) + scaleOut(tween(SLIDE_MS, easing = FastOutSlowInEasing), targetScale = BACK_SCALE)
}
