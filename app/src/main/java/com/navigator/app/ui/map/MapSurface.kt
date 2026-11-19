package com.navigator.app.ui.map

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.navigator.app.ui.screens.hasLocationPermission
import com.navigator.app.ui.screens.lastKnownLocation

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
    fullSdkChrome: Boolean,
    dark: Boolean,
) {
    val context = LocalContext.current
    val navView = remember(retained) { retained.getOrCreate() }
    val density = LocalDensity.current
    val sysTopPx = WindowInsets.systemBars.getTop(density)
    val sysBottomPx = WindowInsets.systemBars.getBottom(density)
    // Bottom padding while guiding so the SDK's re-center button (which pops up bottom-left on pan)
    // sits above our custom ETA bar.
    val navBottomPadPx = sysBottomPx + with(density) { 92.dp.roundToPx() }

    AndroidView(
        // The retained view may still be attached to a previous parent when we re-enter; detach
        // before AndroidView re-adds it to avoid an IllegalArgumentException.
        factory = {
            (navView.parent as? android.view.ViewGroup)?.removeView(navView)
            navView
        },
        modifier = Modifier.fillMaxSize(),
    )

    // (Re)deliver the map on every entry. getMapAsync returns the cached GoogleMap at once for an
    // already-loaded view, so this is cheap.
    LaunchedEffect(navView) {
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

    // Guidance engine + chrome + padding, keyed on the guidance/chrome state. Padding keeps the
    // SDK's chrome and its tilted follow camera inside the visible area on an edge-to-edge screen:
    // full-SDK guidance insets by the system bars, custom chrome lifts the bottom above our ETA
    // bar, browsing has none.
    LaunchedEffect(navUiEnabled, ready, fullSdkChrome) {
        if (!ready) return@LaunchedEffect
        controller.setGuidanceChrome(guidance = navUiEnabled, stockChrome = fullSdkChrome && navUiEnabled)
        val (padTop, padBottom) = when {
            navUiEnabled && fullSdkChrome -> sysTopPx to sysBottomPx
            navUiEnabled -> 0 to navBottomPadPx
            else -> 0 to 0
        }
        controller.setPadding(0, padTop, 0, padBottom)
        if (hasLocationPermission(context)) controller.setPuck(navUiEnabled)
    }
    DisposableEffect(controller) { onDispose { controller.detach() } }
}

