package com.navigator.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.navigator.app.location.hasLocationPermission
import com.navigator.app.location.lastKnownLocation
import androidx.navigation.NavBackStackEntry
import com.navigator.app.ui.shell.LocalPageEntry
import com.navigator.app.ui.shell.LocalPageNav
import com.navigator.app.ui.shell.backStack
import com.navigator.app.ui.shell.rememberPageSettled
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlin.coroutines.coroutineContext

/** What the riding surfaces cover of the map, in px. */
data class MapInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    operator fun plus(o: MapInsets) = MapInsets(left + o.left, top + o.top, right + o.right, bottom + o.bottom)
}

/**
 * A page takes the one shared map: attaches [controller] to it and follows the arguments for insets, night mode and the
 * SDK chrome. It has no view of its own: the [MapLayer] in the shell hosts the single [RetainedNavigationView] behind
 * every page and never moves it, so a page change cannot re-parent, fade or clip the map. Everything the screens draw
 * goes through [controller]; their controls sit over the layer on transparent pages.
 */
@Composable
fun MapClaim(
    retained: RetainedNavigationView,
    controller: NavSdkMapController,
    navUiEnabled: Boolean,
    /** What the riding screen covers while guiding: the camera and Google's logo stay clear of it. */
    guidanceInsets: () -> MapInsets = NO_INSETS,
    dark: Boolean,
) {
    val context = LocalContext.current
    // Creating the view here (not in the layer) keeps onboarding and the mirror screen free of the map.
    val navView = remember(retained) { retained.getOrCreate() }

    DisposableEffect(retained) {
        retained.claims++
        onDispose { retained.claims-- }
    }

    // The map is taken by the page that is on top of the back stack and has arrived, not by one that is only composing (a
    // back-swipe preview, the page under a push). A page that loses the map and is then on top again takes it back.
    val nav = LocalPageNav.current
    val entry = LocalPageEntry.current
    val stack by (nav?.backStack() ?: remember { MutableStateFlow(emptyList<NavBackStackEntry>()) }).collectAsState()
    val settled by rememberPageSettled()
    val active = MapClaimRule.canClaim(stack.lastOrNull()?.id, entry?.id, settled)

    // (Re)deliver the map whenever this page becomes the active one. getMapAsync returns the cached GoogleMap at once for
    // an already-loaded view, so this is cheap.
    LaunchedEffect(navView, active) {
        if (!active) return@LaunchedEffect
        val me = coroutineContext.job
        controller.arm()
        navView.getMapAsync { gm ->
            // The page may have stopped being the active one while the map was on its way: then it takes nothing.
            if (!me.isActive) return@getMapAsync
            controller.attach(gm, navView)
            // Only frame the camera the first time: re-entry keeps the rider's pan and zoom.
            if (!retained.mapInitialized) {
                val start = lastKnownLocation(context)?.let { LatLng(it.first, it.second) } ?: LatLng(12.9716, 77.5946) // Bengaluru fallback
                gm.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 15f))
                retained.mapInitialized = true
            }
        }
    }

    // Map + SDK day/night in its own effect, keyed only on `dark`, so entering and leaving guidance
    // doesn't re-slam the style (that caused a restyle flash).
    val ready by controller.ready.collectAsState()
    // Every screen's map follows "Remove animations": camera moves jump instead of gliding.
    val reduced = KtmTheme.motion.reduced
    LaunchedEffect(controller, reduced) { controller.setReducedMotion(reduced) }
    // Each time this screen takes the map it sets its night mode, chrome and padding up again.
    val attached by controller.attachments.collectAsState()
    LaunchedEffect(dark, ready, attached) { if (ready) controller.setNightMode(dark) }

    // Guidance engine + chrome + padding, keyed on the guidance state. The padding keeps the SDK's tilted follow
    // camera, its logo and the puck inside the part of the screen our riding surfaces leave open.
    LaunchedEffect(navUiEnabled, ready, attached) {
        if (!ready) return@LaunchedEffect
        controller.setGuidanceChrome(guidance = navUiEnabled)
        if (hasLocationPermission(context)) controller.setPuck(navUiEnabled)
    }
    // Padding on its own: the surfaces change height (lanes appear, text wraps) without the engine being touched again.
    // The panels report their size every frame while they animate: the map follows once they settle.
    // Read only in this flow, never in composition: the host and the screen do not recompose per frame.
    val insets by rememberUpdatedState(guidanceInsets)
    var applied by remember { mutableStateOf<MapInsets?>(null) }
    LaunchedEffect(navUiEnabled, ready) {
        if (!ready) return@LaunchedEffect
        // The controller keeps the last request and replays it when it takes the map: nothing to resend on a re-attach.
        snapshotFlow { if (navUiEnabled) insets() else MapInsets() }
            .settled(KtmMotionSpecs.INSETS_SETTLE_MS)
            .collect {
                if (it == applied) return@collect
                applied = it
                controller.setPadding(it.left, it.top, it.right, it.bottom)
            }
    }
    DisposableEffect(controller) { onDispose { controller.detach() } }
}

private val NO_INSETS: () -> MapInsets = { MapInsets() }
