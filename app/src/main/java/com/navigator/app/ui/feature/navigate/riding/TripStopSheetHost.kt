package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.location.lastKnownLocation
import com.navigator.app.nav.destination.CategoryResult
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PolylineCodec
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.trip.TripController
import com.navigator.app.ui.feature.navigate.search.PlacesClientApi

/**
 * SE4's state: the category being looked at and its results, searched along the part of the route still ahead.
 * Adding goes through [TripController.addStop]; a refusal is announced by its event (a chip), and the sheet stays.
 */
@Composable
fun TripStopSheetHost(trip: TripController, onSearchByName: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember { PlacesClientApi(context.applicationContext) }
    val online by ConnectivityMonitor.isOnline.collectAsState()
    var category by remember { mutableStateOf<PlaceCategory?>(null) }
    var results by remember { mutableStateOf<List<CategoryResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Read when the sheet opens: stops left to add as it was then (an add closes the sheet).
    val left = remember { TripStopRules.left(trip.stopsAhead()?.size, trip.state.value.allStops.size) }

    // A second tap before the sheet closes must not re-plan again (and replace "Stop added" with "Still updating").
    var adding by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(category, online, retry) {
        val picked = category ?: run { loading = false; return@LaunchedEffect }
        loading = true; failed = false; results = emptyList()
        val here = lastKnownLocation(context)
        val route = trip.state.value.let { it.route ?: it.routeHint }?.let { r -> here?.let { PolylineCodec.aheadOf(r, it.first, it.second) } ?: r }
        val found = api.category(picked, here, route)
        results = found.items; failed = found.failed; loading = false
    }

    TripStopSheet(
        ui = TripStopUi(left, online, category, loading, failed, results),
        actions = TripStopActions(
            onCategory = { category = if (category == it) null else it },
            onAdd = { r ->
                if (!adding && trip.addStop(SavedPlace(r.lat, r.lng, r.name, r.address))) { adding = true; onDismiss() }
            },
            onSearchByName = onSearchByName,
            onRetry = { retry++ },
            onDismiss = onDismiss,
        ),
    )
}
