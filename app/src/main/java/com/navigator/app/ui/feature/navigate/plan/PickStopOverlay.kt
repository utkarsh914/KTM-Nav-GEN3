package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.feature.navigate.pick.AndroidReverseGeocoder
import com.navigator.app.ui.feature.navigate.pick.PickOnMapController
import com.navigator.app.ui.feature.navigate.pick.PickOnMapScreen
import com.navigator.app.ui.map.MapController

/**
 * PL6: choose a stop by moving the map under a fixed pin. The route stays drawn and the existing stops stay
 * numbered; the sheet names the street, the stop number it will get and where it goes.
 */
@Composable
fun PickStopOverlay(
    map: MapController,
    number: Int,
    destination: String,
    canAdd: Boolean,
    onBack: () -> Unit,
    onUse: (SavedPlace) -> Unit,
    /** The next turn, kept in view while a trip runs. */
    guidance: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(map) { PickOnMapController(scope, map, AndroidReverseGeocoder(context)) }
    val fallback = stringResource(R.string.stop_new)
    val state by controller.state.collectAsState()
    DisposableEffect(controller) {
        // The camera keeps the route in view: the pin is at the middle of the screen, where the point is read.
        controller.start()
        onDispose { controller.stop(); map.setPadding(0, 0, 0, 0) }
    }
    PickOnMapScreen(
        state = state, onBack = onBack, onUse = { controller.use(fallback)?.let(onUse) },
        hint = stringResource(R.string.stop_pick_title), title = fallback, stopNumber = number,
        subtitle = stringResource(R.string.stop_goes_in_before, destination),
        useLabel = stringResource(if (canAdd) R.string.stop_add_here else R.string.route_stop_limit),
        useEnabled = canAdd,
        guidance = guidance,
        // The whole map is the canvas in portrait; in landscape the panel's side is not, so the pin moves to the open part.
        onFootprint = { map.setPadding(it, 0, 0, 0) },
    )
}
