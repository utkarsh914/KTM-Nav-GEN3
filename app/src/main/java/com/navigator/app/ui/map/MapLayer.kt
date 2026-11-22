package com.navigator.app.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Hosts the app-scoped [RetainedNavigationView] once, in the shell behind the pages, and nothing else: it is never
 * re-parented, faded, scaled or clipped. Map pages are transparent over it and take it with [MapClaim]; every other
 * page paints its own ground over it. Nothing is composed until a page first claims the map.
 */
@Composable
fun MapLayer(retained: RetainedNavigationView, modifier: Modifier = Modifier) {
    val view = retained.view ?: return
    AndroidView(
        factory = {
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        modifier = modifier.fillMaxSize(),
    )
}
