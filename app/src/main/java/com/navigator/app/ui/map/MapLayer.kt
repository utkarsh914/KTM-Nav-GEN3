package com.navigator.app.ui.map

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.navigator.app.ui.theme.KtmMotionSpecs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Hosts the app-scoped [RetainedNavigationView] once, behind the whole shell, and nothing else: it is never
 * re-parented, faded, scaled or clipped. Map pages are transparent over it and take it with [MapClaim]; every other
 * page paints its own ground over it. Nothing is composed until a page first claims the map.
 */
@Composable
fun MapLayer(retained: RetainedNavigationView, shellInsets: () -> MapInsets, modifier: Modifier = Modifier) {
    // The layer is as big as the window and never follows the bar sliding in or out (a resize every frame re-allocates
    // the map's buffers and blocks the UI thread on the map's render thread); what the shell covers is padding instead,
    // sent once it has held still.
    LaunchedEffect(retained) {
        var first = true
        snapshotFlow(shellInsets).distinctUntilChanged().collectLatest { insets ->
            if (!first) { retained.shellSettled = false; delay(KtmMotionSpecs.INSETS_SETTLE_MS) }
            first = false
            retained.setShellInsets(insets)
            retained.shellSettled = true
        }
    }
    val view = retained.view ?: return
    val claimed = retained.claims > 0
    AndroidView(
        factory = {
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        modifier = modifier.fillMaxSize(),
        // Covered by a page that is not a map page, the map must not be reachable by touch exploration either.
        update = {
            it.importantForAccessibility =
                if (claimed) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        },
    )
}
