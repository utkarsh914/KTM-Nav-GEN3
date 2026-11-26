package com.navigator.app.ui.feature.navigate.plan

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.navigator.app.ui.theme.KtmMotionSpecs
import kotlinx.coroutines.delay
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
    /** How much of the map's left side the landscape panel covers: the host pads the map for it (the one owner of padding). */
    onFootprint: (Int) -> Unit,
    /** Mid-trip the stop goes in before [destination] (the next stop or the destination) and the stop list is not on screen. */
    midTrip: Boolean = false,
    /** No internet: a stop can't be added whatever the limit says. */
    offline: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(map) { PickOnMapController(scope, map, AndroidReverseGeocoder(context)) }
    val fallback = stringResource(R.string.stop_new)
    val state by controller.state.collectAsState()
    DisposableEffect(controller) {
        // The camera keeps the route in view: the pin is at the middle of the screen, where the point is read.
        controller.start()
        // No padding reset here: the stage that is shown next pads the map itself (a reset is two relayouts and a shift).
        onDispose { controller.stop() }
    }
    // The map takes the padding a moment after the panel reports it (it settles first): read the pin's point after that.
    var footprint by remember { mutableIntStateOf(-1) }
    LaunchedEffect(footprint) {
        if (footprint < 0) return@LaunchedEffect
        map.awaitPaddingSettled()
        delay(KtmMotionSpecs.INSETS_SETTLE_MS * 2)
        withFrameNanos { }
        controller.refresh()
    }
    PickOnMapScreen(
        state = state, onBack = onBack, onUse = { controller.use(fallback)?.let(onUse) },
        hint = stringResource(R.string.stop_pick_title), title = fallback, stopNumber = number,
        subtitle = stringResource(if (midTrip) R.string.stop_goes_in_before_trip else R.string.stop_goes_in_before, destination),
        useLabel = stringResource(pickUseLabel(canAdd, offline)),
        useEnabled = canAdd && !offline,
        guidance = guidance,
        // The whole map is the canvas in portrait; in landscape the panel's side is not, so the pin moves to the open part.
        onFootprint = { footprint = it; onFootprint(it) },
    )
}

/** The reason the button can't add comes first: no internet, then the stop limit. */
@StringRes
internal fun pickUseLabel(canAdd: Boolean, offline: Boolean): Int = when {
    offline -> R.string.trip_stop_offline
    canAdd -> R.string.stop_add_here
    else -> R.string.route_stop_limit
}
