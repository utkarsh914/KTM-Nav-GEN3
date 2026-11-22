package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmTheme

/**
 * A pushed page with no map under it: it gets rounded corners (and the shrink of a back swipe) while it leaves, like
 * the system's back preview. Pages that host the map are not wrapped: a scale or clip layer over the Nav SDK's map view
 * is not worth the risk. Keep [isListPage] in step with the routes registered here.
 */
internal inline fun <reified T : Any> NavGraphBuilder.listPage(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = page<T>(listPage = true, content)

/** The destinations registered with [listPage]. */
internal fun NavDestination.isListPage(): Boolean =
    hasRoute<Route.SettingsPage>() || hasRoute<Route.DashApps>() || hasRoute<Route.NavApp>() || hasRoute<Route.CallAudio>() ||
        hasRoute<Route.Logs>() || hasRoute<Route.SymbolTest>() || hasRoute<Route.TurnCalibration>() || hasRoute<Route.EditRide>()

/** The destinations that show the shared map layer through a transparent page: [MapClaim] callers (Navigate hosts it only when there is a map). */
internal fun NavDestination.isMapPage(): Boolean =
    hasRoute<Route.Navigate>() || hasRoute<Route.RideReplay>() || hasRoute<Route.TrimRide>() || hasRoute<Route.PickPlace>()

/** Rounds the corners of whatever it is applied to while the page leaves (also what the dim is drawn inside). */
@Composable
internal fun Modifier.leavingCorners(scope: AnimatedContentScope): Modifier {
    val motion = KtmTheme.motion
    val reduced = motion.reduced
    val radius = scope.transition.animateDp(transitionSpec = { motion.effectsDefault() }, label = "pageCorner") { state ->
        if (state == EnterExitState.PostExit && !reduced) KtmLayout.pageLeaveCorner else 0.dp
    }
    // Read in the layer, not in composition: no recomposition per frame, and no clip at rest.
    return graphicsLayer {
        val r = radius.value
        shape = RoundedCornerShape(r)
        clip = r > 0.dp
    }
}
