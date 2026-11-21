package com.navigator.app.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.navigator.app.location.hasLocationPermission
import com.navigator.app.location.lastKnownLocation

/** What the riding surfaces cover of the map, in px. */
data class MapInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/**
 * Hosts the app-scoped [RetainedNavigationView] and attaches [controller] to its map. Reusing the
 * retained view means returning to the map re-attaches an already-loaded map (no reload flash).
 * Insets, night mode and the SDK chrome follow the arguments; everything the screens draw goes
 * through [controller].
 */
@Composable
fun MapSurface(
    retained: RetainedNavigationView,
    controller: NavSdkMapController,
    navUiEnabled: Boolean,
    /** What the riding screen covers while guiding: the camera and Google's logo stay clear of it. */
    guidanceInsets: MapInsets,
    dark: Boolean,
) {
    val context = LocalContext.current
    val navView = remember(retained) { retained.getOrCreate() }
    val density = LocalDensity.current
    val sysTopPx = WindowInsets.systemBars.getTop(density)
    val sysBottomPx = WindowInsets.systemBars.getBottom(density)
    AndroidView(
        // The retained view may still be attached to a previous parent when we re-enter; detach
        // before AndroidView re-adds it to avoid an IllegalArgumentException.
        factory = {
            (navView.parent as? ViewGroup)?.removeView(navView)
            navView
        },
        modifier = Modifier.fillMaxSize(),
    )

    // (Re)deliver the map on every entry. getMapAsync returns the cached GoogleMap at once for an
    // already-loaded view, so this is cheap.
    LaunchedEffect(navView) {
        controller.arm()
        navView.getMapAsync { gm ->
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
    LaunchedEffect(dark, ready) { if (ready) controller.setNightMode(dark) }

    // Guidance engine + chrome + padding, keyed on the guidance state. The padding keeps the SDK's tilted follow
    // camera, its logo and the puck inside the part of the screen our riding surfaces leave open.
    LaunchedEffect(navUiEnabled, ready) {
        if (!ready) return@LaunchedEffect
        controller.setGuidanceChrome(guidance = navUiEnabled)
        if (hasLocationPermission(context)) controller.setPuck(navUiEnabled)
    }
    // Padding on its own: the surfaces change height (lanes appear, text wraps) without the engine being touched again.
    LaunchedEffect(navUiEnabled, ready, guidanceInsets) {
        if (!ready) return@LaunchedEffect
        val pad = if (navUiEnabled) guidanceInsets else MapInsets()
        controller.setPadding(pad.left, pad.top, pad.right, pad.bottom)
    }
    DisposableEffect(controller) { onDispose { controller.detach() } }
}

