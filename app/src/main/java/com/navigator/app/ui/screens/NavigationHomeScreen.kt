package com.navigator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import com.navigator.app.ui.theme.edgePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.navigation.ForceNightMode
import com.google.android.libraries.navigation.NavigationView
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.RoutePreview
import com.navigator.app.nav.destination.RoutesClient
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideEvents
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import com.navigator.app.ride.displayAvgKmh
import com.navigator.app.ui.components.CircleBackButton
import com.navigator.app.ui.components.ConnectIconPill
import com.navigator.app.ui.components.ConnectPill
import com.navigator.app.ui.components.Eyebrow
import com.navigator.app.ui.components.IconPill
import com.navigator.app.ui.components.KtmOutlineButton
import com.navigator.app.ui.components.KtmPrimaryButton
import com.navigator.app.ui.components.SquareMapControl
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.Motion
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs

/** Transition between [NavStage] chrome. The full-screen SEARCH panel slides up
 *  from / down to the bottom; every other stage change is a gentle fade + small
 *  vertical drift so cards/overlays never hard-cut. */
private fun stageTransition(initial: NavStage, target: NavStage): ContentTransform = when {
    target == NavStage.SEARCH -> Motion.bottomSheetTransition(rising = true)
    initial == NavStage.SEARCH -> Motion.bottomSheetTransition(rising = false)
    else -> Motion.driftTransition()
}

private const val START_DEBOUNCE_MS = 1_500L

/** Label of the synthetic destination built by "Ride back". */
private const val RIDE_BACK_LABEL = "Trip start"

/** Route preview polyline colors (ARGB). */
private val ROUTE_SELECTED = 0xFF1A73E8.toInt() // Google route blue
private val ROUTE_ALT = 0xFF7C93B0.toInt()      // desaturated blue alternate

/**
 * Phone-first map home (see docs/architecture.md §3).
 *
 * A single persistent Nav SDK [NavigationView] is the map surface across every
 * stage. [NavStage.BROWSE] shows the map + search bar + Connect pill; tapping
 * search opens [NavStage.SEARCH] (recents, favorites, live autocomplete);
 * picking a place goes to [NavStage.CONFIRM] (red pin + place card → Get
 * Directions / Save). Route preview + on-phone active guidance land in P3/P4.
 */
@Composable
fun NavigationHomeScreen(
    retainedNav: RetainedNavigationView,
    onOpenConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartNavigation: (NavDestination) -> Unit,
    onExit: () -> Unit = {},
    onOpenReplay: (String) -> Unit = {},
    sharedLink: String? = null,
    onSharedLinkConsumed: () -> Unit = {},
    pendingPlace: SavedPlace? = null,
    onPendingPlaceConsumed: () -> Unit = {},
    quickRoute: String? = null,
    onQuickRouteConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val appSettings = remember { com.navigator.app.settings.AppSettings(context) }
    // Speed-band colouring scheme for the trip-finished route (Advanced settings).
    val speedBands = remember {
        com.navigator.app.ride.RideMetrics.SpeedBands(
            appSettings.speedBandBaseKmh, appSettings.speedBandStepKmh, appSettings.speedBandCount,
        )
    }
    // When true, active nav shows Google's stock chrome instead of our overlays.
    val fullSdkNavUi = remember { appSettings.fullSdkNavUi }

    val available = remember { GoogleNavSdkController.isAvailable(context) }
    var navReady by remember { mutableStateOf(false) }
    var navError by remember { mutableStateOf(false) }
    var prepareAttempt by remember { mutableIntStateOf(0) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapBearing by remember { mutableFloatStateOf(0f) }

    // Warm up the Nav SDK (key + ToS + Navigator); retriable via prepareAttempt.
    LaunchedEffect(prepareAttempt) {
        if (!available) return@LaunchedEffect
        if (activity == null) {
            AppLogger.log("Nav", "NavHome: no Activity from context (${context.javaClass.simpleName}); can't prepare map")
            navError = true
            return@LaunchedEffect
        }
        navError = false
        GoogleNavSdkController.prepare(
            activity,
            onReady = { navReady = true },
            onError = { navError = true },
        )
    }

    // Watchdog: if neither ready nor error after a while, surface it in the log.
    LaunchedEffect(prepareAttempt, navReady, navError) {
        if (available && !navReady && !navError) {
            delay(8000)
            if (!navReady && !navError) {
                AppLogger.log("Nav", "NavHome: map still not ready after 8s (getNavigator no callback) — tap to retry")
            }
        }
    }

    // ---- Flow state -------------------------------------------------------
    val store = remember { PlacesStore(context) }
    // Planning-flow state lives in a ViewModel: it survives activity recreation and (the
    // planning part) process death. See NavFlow.kt for the pure transitions.
    val flow: NavFlowViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var stage by flow::stage
    var selected by flow::selected
    DisposableEffect(flow) {
        flow.attach()
        onDispose {
            // Leaving the screen for good resets the flow; a configuration change must not.
            flow.detach(configChange = activity?.isChangingConfigurations == true)
        }
    }
    var recents by remember { mutableStateOf(store.recents()) }
    var favorites by remember { mutableStateOf(store.favorites()) }

    // Live GPS speed (km/h) for the guidance speedometer; null when no recent fix.
    // Sourced independently of the BLE service so it works during standalone nav.
    val navSpeedKmh = rememberNavSpeedKmh(active = stage == NavStage.NAVIGATING)
    val locationReady = rememberLocationReady()

    // Compass-driven "you are here" marker for the non-navigating map stages
    // (browse / place confirm / route preview). During active navigation the SDK's
    // own follow-puck is used instead (correct travel-direction chevron), and the
    // trip-finished stage shows the finished route with start/end pins only.
    val showUserMarker = stage == NavStage.BROWSE ||
        stage == NavStage.SEARCH ||
        stage == NavStage.CONFIRM ||
        stage == NavStage.PREVIEW
    val userMarker = remember {
        UserLocationMarker(context, LocationMarkerStyle.fromId(appSettings.locationMarkerStyle))
    }
    // Heading as a State object read only inside the effect (never in
    // composition), so a ~16 Hz sensor tick rotates the marker beam without
    // recomposing the whole nav screen.
    val deviceHeadingState = rememberDeviceHeadingState(active = showUserMarker)
    val deviceLatLng = rememberDeviceLatLng(active = showUserMarker)
    // Keyed on `stage` too so every stage change (incl. returning to BROWSE after a
    // preview that cleared the map) re-asserts the marker; update() re-adds it if
    // the shared map was cleared. Heading is collected via snapshotFlow inside so
    // its updates mutate the marker off the composition path.
    LaunchedEffect(googleMap, showUserMarker, stage, deviceLatLng) {
        val gm = googleMap
        if (gm == null || !showUserMarker) {
            userMarker.remove()
            return@LaunchedEffect
        }
        val ll = deviceLatLng ?: return@LaunchedEffect
        snapshotFlow { deviceHeadingState.value }.collect { heading ->
            userMarker.update(gm, ll, heading)
        }
    }

    // ---- Preview state ----------------------------------------------------
    var previewRoutes by remember { mutableStateOf<List<RoutePreview>>(emptyList()) }
    var selectedRoute by remember { mutableIntStateOf(0) }
    // The stops previewRoutes were computed for (leg lines are only valid against these).
    var routesForStops by remember { mutableStateOf<List<SavedPlace>>(emptyList()) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }
    // Intermediate stops (up to MAX_STOPS), in visit order, shown/edited in the
    // preview. Feeds the Routes API `intermediates` and the SDK waypoint list.
    var stops by flow::stops
    // Where a search pick should go: the destination (normal) or an added stop.
    var searchTarget by flow::searchTarget
    // True when the CONFIRM card was reached from the search list (Back returns there).
    var confirmFromSearch by flow::confirmFromSearch
    var resolvingLink by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // The rider stepped away from the active-navigation view (Back / minimise)
    // without ending the trip; suppresses auto-resume so they stay on the map,
    // and drives the "Resume navigation" pill. Reset when a new trip starts or
    // the rider explicitly resumes.
    var leftNavManually by flow::leftNavManually
    // Confirm before actually ending an in-progress trip.
    var showEndConfirm by remember { mutableStateOf(false) }
    var lastStartAtMs by flow::lastStartAtMs
    // Destination awaiting "replace the current trip?" confirmation.
    var confirmReplaceFor by remember { mutableStateOf<SavedPlace?>(null) }

    // ---- Trip-finished state ---------------------------------------------
    val rideStore = remember { RideStore(context) }
    var tripStartMs by flow::tripStartMs
    var finishedDurationSec by flow::finishedDurationSec
    // The just-finished ride (route + stats), its points, and whether we've
    // learned the recording outcome yet (Saved / Discarded / no-recording).
    var finishedRide by remember { mutableStateOf<RecordedRide?>(null) }
    var finishedPoints by remember { mutableStateOf<List<RidePoint>?>(null) }
    var finishResolved by remember { mutableStateOf(false) }
    var finishCardHeightPx by remember { mutableIntStateOf(0) }
    val rideFinish by RideEvents.lastFinish.collectAsState()

    // Active navigation session state (drives the NAVIGATING stage / arrival).
    val navState by GoogleNavSdkProvider.state.collectAsState()
    // Why the requested trip couldn't start (route failure / navigator unavailable).
    val startFailure by GoogleNavSdkProvider.startFailure.collectAsState()
    // Last destination handed to the SDK, so the failure card can Retry it.
    var lastStartedDest by flow::lastStartedDest

    // Internet reachability, for the offline banner during active navigation.
    val isOnline by ConnectivityMonitor.isOnline.collectAsState()
    // After a short offline+rerouting spell, tell the rider rerouting is paused
    // (the SDK keeps guiding the existing route but can't recompute until online).
    var rerouteOfflinePaused by remember { mutableStateOf(false) }
    LaunchedEffect(isOnline, navState.sessionState) {
        rerouteOfflinePaused = false
        if (!isOnline && navState.sessionState == NavSessionState.REROUTING) {
            delay(5_000)
            rerouteOfflinePaused = true
        }
    }

    // One-shot arrival latch for the current trip. Set the moment we finish (via
    // proximity or the SDK's arrival callback) and reset only when a new trip
    // starts, so continued movement past the destination can't re-run the
    // proximity auto-finish or otherwise re-trigger arrival handling.
    var arrivalLatched by flow::arrivalLatched

    // Idle-fallback bookkeeping: the smallest remaining-distance seen this trip
    // and the wall-clock time it last meaningfully decreased. Used to finish a
    // trip that stalls near (but not exactly at) the destination without an SDK
    // arrival callback. Reset when a new trip starts (see startTo/backToBrowse).
    var arrivalProgress by flow::arrivalProgress

    // ---- Search state -----------------------------------------------------
    val auth = remember { PlacesClient.androidAuth(context) }
    // Refreshed whenever search opens and after a fresh fix in the preview, so distances,
    // autocomplete bias and preview bounds don't use a stale launch-time position.
    var origin by remember { mutableStateOf(lastLocation(context)) }
    var sessionToken by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var query by flow::query
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    // In-flight place-details lookup; cancelled when the rider backs out so it can't
    // jump to CONFIRM later.
    var detailsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3) { results = emptyList(); searchFailed = false; loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        val res = PlacesClient.autocompleteResult(
            input = q, sessionToken = sessionToken, auth = auth,
            originLat = origin?.first, originLng = origin?.second,
        )
        results = res.items
        searchFailed = res.failed
        loading = false
    }

    // ---- Category search (chips in the search panel) --------------------------
    var categoryUi by remember { mutableStateOf(CategoryUiState()) }
    LaunchedEffect(categoryUi.active, stage) {
        val c = categoryUi.active
        if (c == null || stage != NavStage.SEARCH) return@LaunchedEffect
        // Along the route when adding a stop to a live trip or to the previewed trip; else around you.
        val route: List<Pair<Double, Double>>? = when (searchTarget) {
            SearchTarget.TRIP_STOP -> flow.tripRoute?.let { r ->
                // Only what is still ahead of the rider.
                (origin ?: lastLocation(context))?.let { com.navigator.app.nav.destination.PolylineCodec.aheadOf(r, it.first, it.second) } ?: r
            }
            SearchTarget.STOP -> previewRoutes.getOrNull(selectedRoute)?.points?.map { it.latitude to it.longitude }
            SearchTarget.DESTINATION -> null
        }
        categoryUi = categoryUi.copy(loading = true, failed = false, results = emptyList(), alongRoute = route != null)
        val o = origin ?: lastLocation(context)
        val res = PlacesClient.searchCategory(c, auth, o, route)
        categoryUi = categoryUi.copy(loading = false, failed = res.failed, results = res.items)
    }

    // Fetch the preview routes (incl. alternates, or a single multi-leg route when
    // there are stops) when entering PREVIEW or when the stops change.
    LaunchedEffect(stage, selected, stops) {
        if (stage != NavStage.PREVIEW) return@LaunchedEffect
        val p = selected ?: return@LaunchedEffect
        // Record the recent as soon as the rider opens the preview for a place -
        // reaching this screen is a strong signal of intent, even if they don't
        // hit Start. addRecent de-dupes by coords, so re-adding at Start is safe.
        // ("Ride back" destinations are synthetic: they shouldn't litter the recents.)
        if (p.label != RIDE_BACK_LABEL) {
            store.addRecent(p)
            recents = store.recents()
        }
        previewRoutes = emptyList(); selectedRoute = 0; previewFailed = false
        // Compute from a FRESH fix so route-token origins match the SDK's GPS at
        // Start (a stale origin makes the SDK snap to the nearest/fastest route).
        previewLoading = true
        val o = (withTimeoutOrNull(4000) { freshLocation(context) }) ?: origin ?: lastLocation(context)
        if (o == null) { previewLoading = false; previewFailed = true; return@LaunchedEffect }
        origin = o
        val stopsForRoutes = stops
        val routes = RoutesClient.computeRoutes(
            o, p.lat to p.lng, auth, appSettings.vehicleMode,
            intermediates = stopsForRoutes.map { it.lat to it.lng },
        )
        previewLoading = false
        routesForStops = stopsForRoutes
        previewRoutes = routes
        previewFailed = routes.isEmpty()
    }

    // Reflect the current stage on the map (pin on CONFIRM; pin + routes on PREVIEW).
    LaunchedEffect(stage, selected, googleMap, previewRoutes, selectedRoute, stops) {
        val gm = googleMap ?: return@LaunchedEffect
        // The finished screen owns the map (draws the ride track); don't clear it.
        if (stage == NavStage.TRIP_FINISHED) return@LaunchedEffect
        gm.setOnPolylineClickListener(null)
        gm.clear()
        // NOTE: do NOT early-return when `selected` is null (e.g. returning to
        // BROWSE): the user-marker re-add at the end must always run after clear().
        val p = selected
        val ll = p?.let { LatLng(it.lat, it.lng) }
        when (stage) {
            NavStage.CONFIRM -> if (p != null && ll != null) {
                gm.addMarker(MarkerOptions().position(ll).title(p.label))
                gm.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15f))
            }
            NavStage.PREVIEW -> if (p != null && ll != null) {
                // Numbered stop markers (1..n), then the destination pin on top.
                stops.forEachIndexed { i, s ->
                    gm.addMarker(
                        MarkerOptions().position(LatLng(s.lat, s.lng))
                            .title("Stop ${i + 1}${s.label?.let { ": $it" } ?: ""}"),
                    )
                }
                gm.addMarker(MarkerOptions().position(ll).title(p.label))
                if (previewRoutes.isNotEmpty()) {
                    // With stops there are no alternates (single multi-leg route).
                    previewRoutes.forEachIndexed { i, r ->
                        if (i != selectedRoute) {
                            gm.addPolyline(
                                PolylineOptions().addAll(r.points).color(ROUTE_ALT).width(9f),
                            ).apply { tag = i; isClickable = true }
                        }
                    }
                    val sel = previewRoutes.getOrNull(selectedRoute)
                    if (sel != null) {
                        gm.addPolyline(
                            PolylineOptions().addAll(sel.points).color(ROUTE_SELECTED).width(14f),
                        ).apply { tag = selectedRoute; isClickable = true }
                        val b = LatLngBounds.builder()
                        sel.points.forEach { b.include(it) }
                        origin?.let { b.include(LatLng(it.first, it.second)) }
                        stops.forEach { b.include(LatLng(it.lat, it.lng)) }
                        runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120)) }
                    }
                    gm.setOnPolylineClickListener { poly -> (poly.tag as? Int)?.let { selectedRoute = it } }
                } else {
                    gm.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 14f))
                }
            }
            else -> {}
        }
        // gm.clear() above dropped the user marker; drop the stale handle so the
        // marker effect re-adds it, and re-add now if we already have a fix.
        userMarker.remove()
        if (showUserMarker) deviceLatLng?.let { userMarker.update(gm, it, deviceHeadingState.value) }
    }

    // Draw the just-finished ride's speed-coloured track on the map behind the
    // summary card, framed above the card. Clears the finished padding on return.
    LaunchedEffect(stage, googleMap, finishedPoints, finishCardHeightPx) {
        val gm = googleMap ?: return@LaunchedEffect
        when {
            stage == NavStage.TRIP_FINISHED -> {
                val pts = finishedPoints
                if (!pts.isNullOrEmpty() && finishCardHeightPx > 0) {
                    runCatching { gm.setPadding(0, 0, 0, finishCardHeightPx) }
                    drawRideTrack(gm, pts, speedBands)
                    frameRideTrack(gm, pts)
                }
            }
            // Only reset padding when returning to the browse map (never during
            // NAVIGATING, whose bottom padding is owned by BrowseMap).
            stage == NavStage.BROWSE -> runCatching { gm.setPadding(0, 0, 0, 0) }
            else -> {}
        }
    }

    fun openSearch() {
        categoryUi = CategoryUiState()
        origin = lastLocation(context) ?: origin
        sessionToken = UUID.randomUUID().toString()
        results = emptyList()
        recents = store.recents(); favorites = store.favorites()
        flow.update { it.openSearch(SearchTarget.DESTINATION) }
    }

    /** Open the search panel to add an intermediate stop (returns to PREVIEW). */
    fun openSearchForStop() {
        categoryUi = CategoryUiState()
        origin = lastLocation(context) ?: origin
        sessionToken = UUID.randomUUID().toString()
        results = emptyList()
        recents = store.recents(); favorites = store.favorites()
        flow.update { it.openSearch(SearchTarget.STOP) }
    }

    /** Add a stop to the trip being navigated: re-plan from here via the stop, then on to the destination. */
    fun addStopToTrip(p: SavedPlace) {
        val d = lastStartedDest ?: return
        if (d.stops.isNotEmpty()) return // (stops already passed can't be told apart yet)
        if (arrivalLatched || !navState.sessionState.isActiveNav()) {
            android.widget.Toast.makeText(context, "That trip has already ended", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        flow.tripBeforeStop = d // lets a failed re-plan fall back to the trip the rider had
        // The old route token is for the old waypoints; continuesRide keeps the recording going.
        val nd = d.copy(
            routeToken = null,
            stops = d.stops + com.navigator.app.nav.model.Stop(p.lat, p.lng, p.label),
            continuesRide = true,
        )
        lastStartedDest = nd
        arrivalLatched = false
        arrivalProgress = ArrivalPolicy.Progress.start(System.currentTimeMillis())
        flow.tripRoute = null // the old polyline no longer matches the trip
        onStartNavigation(nd)
        android.widget.Toast.makeText(context, "Stop added: ${p.label}", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Search for a stop while navigating (guidance keeps running underneath). */
    fun openTripStopSearch() {
        val d = lastStartedDest
        when {
            d == null || !navState.sessionState.isActiveNav() -> android.widget.Toast.makeText(
                context, "Start navigating first", android.widget.Toast.LENGTH_SHORT,
            ).show()
            !isOnline -> android.widget.Toast.makeText(
                context, "Adding a stop needs a connection", android.widget.Toast.LENGTH_SHORT,
            ).show()
            d.stops.isNotEmpty() -> android.widget.Toast.makeText(
                context, "Trips that already have stops can't be extended yet", android.widget.Toast.LENGTH_LONG,
            ).show()
            else -> {
                categoryUi = CategoryUiState()
                origin = lastLocation(context) ?: origin
                sessionToken = UUID.randomUUID().toString()
                results = emptyList()
                recents = store.recents(); favorites = store.favorites()
                // Stepping into search must not be treated as the app taking over / ending the trip.
                leftNavManually = true
                flow.update { it.openSearch(SearchTarget.TRIP_STOP) }
            }
        }
    }

    /** Apply a picked place as the destination, an appended preview stop, or a live-trip stop. */
    fun applyPicked(p: SavedPlace) {
        // A late place lookup (or a pick racing the trip's end) must not act outside the search.
        if (stage != NavStage.SEARCH) return
        val wasTripStop = searchTarget == SearchTarget.TRIP_STOP
        flow.update { it.applyPicked(p) }
        if (wasTripStop) {
            leftNavManually = false
            addStopToTrip(p)
        }
    }

    fun choose(p: SavedPlace) = flow.update { it.choose(p) }

    fun backToBrowse() {
        detailsJob?.cancel()
        flow.update { it.backToBrowse() }
        previewRoutes = emptyList(); selectedRoute = 0; previewFailed = false; previewLoading = false
        // Keep the "stepped away" flag while a trip is still running so arrival/auto-resume
        // logic still treats this screen as owning it (only reset once nothing is live).
        if (!navState.sessionState.isActiveNav()) leftNavManually = false
        finishedRide = null; finishedPoints = null; finishResolved = false
    }

    fun startTo(p: SavedPlace) {
        // Ignore a double tap: a second startNavigation would supersede the first mid-compute.
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastStartAtMs < START_DEBOUNCE_MS) return
        lastStartAtMs = nowMs
        GoogleNavSdkProvider.clearStartFailure()
        if (p.label != RIDE_BACK_LABEL) {
            store.addRecent(p)
            recents = store.recents()
        }
        val sel = previewRoutes.getOrNull(selectedRoute)
        // Carry the mode that produced the token so the SDK consumes it with the
        // same mode (else it rejects the token and silently picks its own route).
        val mode = sel?.travelMode ?: appSettings.vehicleMode
        val tripStops = stops.map { com.navigator.app.nav.model.Stop(it.lat, it.lng, it.label) }
        val dest = NavDestination(p.lat, p.lng, p.label, sel?.routeToken, mode, tripStops)
        lastStartedDest = dest
        flow.tripBeforeStop = null
        flow.tripOrigin = origin
        flow.tripRoute = sel?.points?.map { it.latitude to it.longitude }
        onStartNavigation(dest)
        arrivalLatched = false
        arrivalProgress = ArrivalPolicy.Progress.start(System.currentTimeMillis())
        leftNavManually = false
        tripStartMs = System.currentTimeMillis()
        // Clear any prior ride-finish result so the next emission is this trip's.
        RideEvents.clear()
        stage = NavStage.NAVIGATING
    }

    fun retryStart() {
        val dest = lastStartedDest ?: return
        arrivalLatched = false
        arrivalProgress = ArrivalPolicy.Progress.start(System.currentTimeMillis())
        leftNavManually = false
        RideEvents.clear()
        stage = NavStage.NAVIGATING
        onStartNavigation(dest)
    }

    // Leave the active-navigation view but KEEP the trip running (foreground
    // service + dash guidance continue). A "Resume navigation" pill brings it back.
    fun moveAwayFromNav() {
        leftNavManually = true
        stage = NavStage.BROWSE
    }

    // "Ride back": plan the reverse of the finished trip (start point as destination, stops reversed).
    fun rideBack() {
        val o = finishedPoints?.firstOrNull()?.let { it.lat to it.lng } ?: flow.tripOrigin ?: return
        val reversedStops = lastStartedDest?.stops.orEmpty()
            .map { SavedPlace(it.lat, it.lng, it.label ?: "Stop") }
        backToBrowse()
        flow.update { it.rideBack(SavedPlace(o.first, o.second, RIDE_BACK_LABEL), reversedStops) }
    }

    fun resumeNav() {
        searchTarget = SearchTarget.DESTINATION
        leftNavManually = false
        stage = NavStage.NAVIGATING
    }

    // End the trip and show the "Trip finished" confirmation instead of dropping
    // straight back to the map. Keeps `selected` so the screen can name the
    // destination; clears the transient search/preview state and resets the
    // finished-ride state so the screen reloads this trip's result.
    fun finishTrip() {
        if (stage == NavStage.TRIP_FINISHED) return
        // Freeze the duration now; computing it during composition kept growing it.
        finishedDurationSec = if (tripStartMs > 0L) ((System.currentTimeMillis() - tripStartMs) / 1000).toInt() else 0
        arrivalLatched = true
        leftNavManually = false
        GoogleNavSdkController.stop()
        flow.tripBeforeStop = null
        previewRoutes = emptyList(); selectedRoute = 0; previewFailed = false; previewLoading = false
        finishedRide = null; finishedPoints = null; finishResolved = false
        flow.update { it.finishTrip() }
    }

    // Keep the stage in sync with the real session:
    //  - resume the NAVIGATING view whenever a trip is live but we're not showing
    //    it (e.g. the activity was recreated, or the user tapped the notification
    //    after minimising) - the stage is local Compose state that would
    //    otherwise fall back to BROWSE;
    //  - show the "Trip finished" screen once the SDK reports arrival.
    // We "own" the trip on this screen while showing guidance OR while stepped
    // away on the map with a trip still running — arrival should finish it either way.
    val owningTrip = NavFlowRules.owningTrip(stage, leftNavManually)
    LaunchedEffect(navState.sessionState) {
        if (NavFlowRules.shouldAutoResume(navState.sessionState.isActiveNav(), stage, leftNavManually)) {
            stage = NavStage.NAVIGATING
        } else if (owningTrip && navState.sessionState == NavSessionState.ARRIVED) {
            finishTrip()
        }
    }

    // ARRIVED as a one-shot event: the state flow is conflated and can be overwritten
    // (coordinator revert -> STOPPED) before the effect above ever sees ARRIVED. Any
    // in-app trip is owned by this screen, whichever stage the rider is looking at.
    LaunchedEffect(Unit) {
        GoogleNavSdkProvider.tripEnded.collect { end ->
            if (end == GoogleNavSdkProvider.TripEnd.ARRIVED && !arrivalLatched) finishTrip()
        }
    }

    // Notification tap while stepped-away resumes the live guidance view.
    val openNavReq by com.navigator.app.ui.MainActivity.openNavRequest.collectAsState()
    LaunchedEffect(openNavReq) {
        if (openNavReq && navState.sessionState.isActiveNav()) resumeNav()
    }

    // Resolve the just-finished ride once its recording is finalised (Saved with
    // a route + stats, or Discarded/none). A timeout covers recording-disabled so
    // the finished screen never hangs waiting.
    LaunchedEffect(rideFinish, stage) {
        if (stage != NavStage.TRIP_FINISHED) return@LaunchedEffect
        when (val f = rideFinish) {
            is RideFinish.Saved -> {
                finishedRide = f.ride
                finishedPoints = withContext(Dispatchers.IO) { rideStore.points(f.ride.id) }
                finishResolved = true
            }
            RideFinish.Discarded -> {
                finishedRide = null; finishedPoints = null; finishResolved = true
            }
            null -> {}
        }
    }
    LaunchedEffect(stage) {
        if (stage == NavStage.TRIP_FINISHED && !finishResolved) {
            delay(6000)
            if (!finishResolved) finishResolved = true
        }
    }

    // Proximity auto-finish: end guidance and show "Trip finished" as soon as we
    // come within ArrivalPolicy.RADIUS_M of the destination, rather than waiting for the
    // SDK's own (sometimes later) arrival callback. remainingDistanceMeters is the
    // SDK's distance to the final destination, updated ~1 Hz while en route.
    // Also tracks progress for the idle-fallback below: whenever the remaining
    // distance meaningfully decreases we note the time, so a later stall near the
    // destination can finish the trip even if the SDK never reports arrival.
    LaunchedEffect(navState.remainingDistanceMeters, stage) {
        val remaining = navState.remainingDistanceMeters
        val eligible = owningTrip && !arrivalLatched && navState.sessionState.isActiveNav()
        if (!eligible || remaining == null) return@LaunchedEffect

        // Record forward progress (resets the stall timer).
        arrivalProgress = ArrivalPolicy.track(arrivalProgress, remaining, System.currentTimeMillis())

        if (ArrivalPolicy.reached(remaining)) {
            finishTrip()
        }
    }

    // Idle-fallback auto-finish: if we're inside the outer radius but progress has
    // stalled for ArrivalPolicy.STALL_MS (rider parked just shy of the point, or
    // guidance stalled near the end), finish the trip. Ticks on a timer so it
    // fires even when no fresh NavInfo frames arrive (e.g. stuck offline reroute).
    // Keyed on `stage` etc. but NOT on the session state: startTo() enters NAVIGATING
    // while the session is still IDLE, so the loop must outlive that and re-check the
    // live conditions every tick (a loop conditioned on isActiveNav() exited at once
    // and never restarted when ENROUTE arrived).
    LaunchedEffect(stage, arrivalLatched, leftNavManually) {
        while (isActive && !arrivalLatched) {
            delay(5_000)
            val owning = NavFlowRules.owningTrip(stage, leftNavManually)
            if (!owning || !navState.sessionState.isActiveNav()) continue
            val remaining = navState.remainingDistanceMeters ?: continue
            if (ArrivalPolicy.stalledNearDestination(arrivalProgress, remaining, System.currentTimeMillis())) {
                finishTrip()
                break
            }
        }
    }

    // A failed start while the rider stepped away to the map: bring the failure card up.
    LaunchedEffect(startFailure) {
        if (startFailure != null && stage == NavStage.BROWSE) resumeNav()
    }
    // A failed mid-trip "add stop" re-plan must not strand the rider: go back to the trip they had.
    LaunchedEffect(startFailure) {
        val before = flow.tripBeforeStop
        if (startFailure != null && before != null) {
            flow.tripBeforeStop = null
            GoogleNavSdkProvider.clearStartFailure()
            lastStartedDest = before
            arrivalProgress = ArrivalPolicy.Progress.start(System.currentTimeMillis())
            onStartNavigation(before.copy(continuesRide = true, routeToken = null))
            android.widget.Toast.makeText(
                context, "Couldn't add the stop \u2013 continuing your trip", android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    // The re-plan worked: nothing to undo any more.
    LaunchedEffect(navState.sessionState) {
        if (navState.sessionState == NavSessionState.ENROUTE) flow.tripBeforeStop = null
    }

    // A Google Maps link shared into the app -> resolve it and jump to CONFIRM.
    LaunchedEffect(sharedLink) {
        val link = sharedLink ?: return@LaunchedEffect
        resolvingLink = true
        val dest = com.navigator.app.nav.destination.MapsUrlResolver.resolve(link, auth)
        resolvingLink = false
        onSharedLinkConsumed()
        if (dest != null) {
            choose(SavedPlace(dest.lat, dest.lng, dest.label ?: "Shared location"))
        } else {
            android.widget.Toast.makeText(
                context, "Couldn't open that Maps link", android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // A saved place tapped in Saved Places -> drop its pin and open the confirm
    // card (no network lookup needed; it already has coords).
    LaunchedEffect(pendingPlace) {
        val p = pendingPlace ?: return@LaunchedEffect
        onPendingPlaceConsumed()
        choose(p)
    }

    // Home / Work chip or launcher shortcut -> straight to the route preview for that place.
    fun rideTo(place: SavedPlace) {
        // Mid-guidance a shortcut must not tear the planning flow out from under the live trip
        // (the screen would stop owning it); ending/replacing a trip stays an explicit action.
        if (stage == NavStage.NAVIGATING) {
            android.widget.Toast.makeText(
                context, "Navigation in progress \u2013 end it first to plan another ride", android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        backToBrowse()
        flow.update { it.quickRoute(place) }
    }
    LaunchedEffect(quickRoute) {
        val slot = quickRoute ?: return@LaunchedEffect
        onQuickRouteConsumed()
        val want = if (slot == "WORK") com.navigator.app.nav.destination.FavoriteSlot.WORK
        else com.navigator.app.nav.destination.FavoriteSlot.HOME
        val place = store.favorites().firstOrNull { it.slot == want }?.place
        if (place != null) rideTo(place) else android.widget.Toast.makeText(
            context,
            "Set your ${if (want == com.navigator.app.nav.destination.FavoriteSlot.WORK) "Work" else "Home"} place in Saved places",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
    // Home / Work favorites for the browse chips; re-read whenever the map is (re)shown.
    LaunchedEffect(stage) {
        if (stage == NavStage.BROWSE) favorites = store.favorites()
    }

    // Don't leave this screen's map listeners / marker behind when it leaves composition
    // (unless another screen has already taken the shared map over).
    DisposableEffect(googleMap) {
        val gm = googleMap
        onDispose {
            // Our own marker is always ours to remove; the shared listeners only if
            // no other screen has taken the map over.
            runCatching { userMarker.remove() }
            if (gm != null && retainedNav.mapOwner === HomeMapOwner) {
                runCatching {
                    gm.setOnCameraMoveListener(null)
                    gm.setOnMapLongClickListener(null)
                    gm.setOnPolylineClickListener(null)
                }
            }
        }
    }

    // Track map bearing for the compass (shown only when rotated) and let the
    // user long-press the map to drop a destination pin.
    LaunchedEffect(googleMap) {
        val gm = googleMap ?: return@LaunchedEffect
        mapBearing = gm.cameraPosition.bearing
        gm.setOnCameraMoveListener { mapBearing = gm.cameraPosition.bearing }
        // Long-press anywhere (except while the SDK owns the map during guidance)
        // drops a pin and opens the confirm card, like Google Maps.
        gm.setOnMapLongClickListener { ll ->
            if (stage != NavStage.NAVIGATING) {
                choose(SavedPlace(ll.latitude, ll.longitude, "Dropped pin"))
            }
        }
    }



    // One back path for the system Back and the on-screen arrows (they must never disagree).
    fun performBack() {
        if (stage == NavStage.SEARCH) detailsJob?.cancel()
        val outcome = flow.state.back(tripLive = navState.sessionState.isActiveNav())
        flow.update { outcome.state }
        when (outcome.effect) {
            BackEffect.NONE -> {}
            BackEffect.BACK_TO_BROWSE -> backToBrowse()
            BackEffect.MOVE_AWAY_FROM_NAV -> moveAwayFromNav()
            BackEffect.RESUME_NAV -> resumeNav()
            BackEffect.CONFIRM_EXIT -> showExitConfirm = true
        }
    }
    BackHandler { performBack() }

    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        // --- Map surface -----------------------------------------------------
        when {
            available && navReady -> BrowseMap(
                retainedNav = retainedNav,
                navUiEnabled = stage == NavStage.NAVIGATING,
                fullSdkChrome = fullSdkNavUi,
                onMap = { googleMap = it },
            )
            available && !navError -> MapPlaceholder("Preparing map…\n\nTap to retry if this doesn't clear.") {
                navReady = false; navError = false; prepareAttempt++
            }
            available && navError -> MapPlaceholder("Couldn't start the map.\nTap to retry.") {
                navReady = false; navError = false; prepareAttempt++
            }
            else -> MapPlaceholder("Maps need a Google API key.\nSet NAV_SDK_API_KEY in local.properties.")
        }

        // Animated stage chrome (overlays the persistent map above). The map layer
        // stays OUTSIDE this AnimatedContent so it never animates or duplicates.
        AnimatedContent(
            targetState = stage,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { stageTransition(initialState, targetState) },
            label = "navStage",
        ) { s ->
          Box(Modifier.fillMaxSize()) {
            when (s) {
            NavStage.BROWSE -> {
                // Search bar (leaves room on the right for the Settings button).
                Box(
                    modifier = Modifier.fillMaxWidth().edgePadding()
                        .padding(start = 16.dp, end = 74.dp, top = 10.dp),
                ) {
                    SearchBar(modifier = Modifier.fillMaxWidth(), onClick = ::openSearch)
                }
                // Top-right: Settings.
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).edgePadding()
                        .padding(end = 16.dp, top = 10.dp),
                ) {
                    IconPill(OpenDashIcons.Settings, "Settings", onClick = onOpenSettings)
                }
                // Home / Work one-tap routes under the search bar (only the ones that are set).
                val homePlace = favorites.firstOrNull { it.slot == com.navigator.app.nav.destination.FavoriteSlot.HOME }?.place
                val workPlace = favorites.firstOrNull { it.slot == com.navigator.app.nav.destination.FavoriteSlot.WORK }?.place
                val hasChips = homePlace != null || workPlace != null
                if (hasChips) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).edgePadding()
                            .padding(start = 16.dp, top = 68.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // (stage guard: the chips stay tappable during the exit animation)
                        homePlace?.let { QuickPlaceChip("Home", OpenDashIcons.House) { if (stage == NavStage.BROWSE) rideTo(it) } }
                        workPlace?.let { QuickPlaceChip("Work", OpenDashIcons.Briefcase) { if (stage == NavStage.BROWSE) rideTo(it) } }
                    }
                }
                // Position can't be shown/used: say why instead of a silently static map.
                if (!locationReady) {
                    Box(
                        modifier = Modifier.align(Alignment.TopCenter).edgePadding()
                            .padding(top = if (hasChips) 130.dp else 74.dp, start = 16.dp, end = 16.dp),
                    ) {
                        OfflineBanner(
                            text = if (!hasLocationPermission(context)) "Location permission is off" else "Location is off",
                        )
                    }
                }
                // Bottom-right control stack (the app-wide standard location):
                // compass (only when rotated) above the recenter button.
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).edgePadding()
                        .padding(end = 16.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        SquareMapControl(
                            OpenDashIcons.Compass, "Reset orientation to north",
                            tint = Ktm.Danger, rotation = -mapBearing,
                        ) { resetBearing(googleMap) }
                    }
                    if (available && navReady) {
                        SquareMapControl(OpenDashIcons.LocateFixed, "Recenter") { recenter(context, googleMap) }
                    }
                }
                // Bottom-left: record control above the connect pill.
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).edgePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RecordControlsHost()
                    ConnectPill(onClick = onOpenConnect)
                }
                // A trip is still running in the background -> offer to jump back.
                if (navState.sessionState.isActiveNav()) {
                    ResumeNavPill(
                        onClick = ::resumeNav,
                        modifier = Modifier.align(Alignment.BottomCenter).edgePadding()
                            .padding(bottom = 140.dp), // clears the record chip + connect pill column
                    )
                }
            }

            NavStage.SEARCH -> SearchPanel(
                query = query,
                onQuery = {
                    query = it
                    // Typing replaces a category browse.
                    if (it.isNotEmpty() && categoryUi.active != null) categoryUi = CategoryUiState()
                },
                target = searchTarget,
                category = categoryUi,
                onCategory = { c -> categoryUi = if (c == null) CategoryUiState() else CategoryUiState(active = c) },
                onPickCategoryResult = { r -> applyPicked(SavedPlace(r.lat, r.lng, r.name, r.address)) },
                loading = loading || resolving,
                results = results,
                searchFailed = searchFailed,
                recents = recents,
                favorites = favorites,
                origin = origin,
                onBack = ::performBack,
                onPickSuggestion = { s ->
                    if (!resolving) {
                        resolving = true
                        detailsJob = scope.launch {
                            try {
                                val loc = PlacesClient.details(s.placeId, sessionToken, auth)
                                if (loc != null) {
                                    applyPicked(SavedPlace(loc.lat, loc.lng, s.primary, s.secondary))
                                } else {
                                    android.widget.Toast.makeText(
                                        context, "Couldn't load that place", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } finally {
                                resolving = false
                            }
                        }
                    }
                },
                onPickSaved = { applyPicked(it) },
            )

            NavStage.CONFIRM -> {
                val sel = selected
                var savedNow by remember(sel) {
                    mutableStateOf(sel?.let { store.isSaved(it.lat, it.lng) } ?: false)
                }
                Box(Modifier.align(Alignment.BottomCenter)) {
                    ConfirmCard(
                        place = sel,
                        origin = origin,
                        saved = savedNow,
                        onGetDirections = { stage = NavStage.PREVIEW },
                        onToggleSave = {
                            sel?.let {
                                if (savedNow) store.removeFavorite(it.lat, it.lng)
                                else store.saveFavorite(it, FavoriteSlot.OTHER)
                                savedNow = !savedNow
                                favorites = store.favorites()
                            }
                        },
                    )
                }
                // Top-right control column: Clear, then Compass (only when rotated).
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).edgePadding()
                        .padding(end = 16.dp, top = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconPill(OpenDashIcons.Close, "Clear", onClick = ::backToBrowse)
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        SquareMapControl(
                            OpenDashIcons.Compass, "Reset orientation to north",
                            tint = Ktm.Danger, rotation = -mapBearing,
                        ) { resetBearing(googleMap) }
                    }
                }
            }

            NavStage.PREVIEW -> {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    PreviewCard(
                        place = selected,
                        loading = previewLoading,
                        routes = previewRoutes,
                        selectedIndex = selectedRoute,
                        failed = previewFailed,
                        stops = stops,
                        routeStops = routesForStops,
                        origin = origin,
                        onSelect = { selectedRoute = it },
                        onAddStop = ::openSearchForStop,
                        onRemoveStop = { idx -> stops = stops.filterIndexed { i, _ -> i != idx } },
                        onMoveStop = { idx, delta ->
                            val to = idx + delta
                            if (to in stops.indices) {
                                stops = stops.toMutableList().apply { add(to, removeAt(idx)) }
                            }
                        },
                        onStart = {
                            selected?.let { p ->
                                // A trip is already running (rider stepped away to the map):
                                // starting another replaces it, so ask first.
                                val tripLive = GoogleNavSdkProvider.activeTrip.value != null && startFailure == null
                                if (tripLive || navState.sessionState.isActiveNav()) confirmReplaceFor = p else startTo(p)
                            }
                        },
                        onBack = { stage = NavStage.CONFIRM },
                    )
                }
                // Top-right control column: Clear, then Compass (only when rotated).
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).edgePadding()
                        .padding(end = 16.dp, top = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconPill(OpenDashIcons.Close, "Clear", onClick = ::backToBrowse)
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        SquareMapControl(
                            OpenDashIcons.Compass, "Reset orientation to north",
                            tint = Ktm.Danger, rotation = -mapBearing,
                        ) { resetBearing(googleMap) }
                    }
                }
            }

            NavStage.NAVIGATING -> {
                // Custom guidance chrome (most SDK chrome disabled in BrowseMap; the
                // SDK re-center button is kept and pops up on pan, bottom-left). Our
                // overlays: header + lane row top-centre; ETA bar bottom-centre;
                // compass bottom-right; speedometer mid-left. Distinct zones.
                val showLanes = navState.lanes.isNotEmpty() &&
                    (navState.distanceToManeuverMeters ?: Int.MAX_VALUE) <= LANE_HINT_DISTANCE_M

                // Top: maneuver header, then lane guidance directly beneath it.
                // In full-SDK mode Google draws its own header/lanes/speedometer/ETA,
                // so we suppress ours AND keep the top zone clear (the SDK header is
                // full-width there); the offline banner is relocated below.
                startFailure?.let { failure ->
                    RouteFailureCard(
                        failure = failure,
                        canRetry = lastStartedDest != null,
                        onRetry = {
                            GoogleNavSdkProvider.clearStartFailure()
                            retryStart()
                        },
                        onBackToPreview = {
                            GoogleNavSdkProvider.clearStartFailure()
                            GoogleNavSdkController.stop()
                            leftNavManually = false
                            lastStartAtMs = 0L
                            stage = NavStage.PREVIEW
                        },
                        onClose = {
                            GoogleNavSdkProvider.clearStartFailure()
                            GoogleNavSdkController.stop()
                            backToBrowse()
                        },
                        modifier = Modifier.align(Alignment.Center).edgePadding().padding(24.dp),
                    )
                }
                if (!fullSdkNavUi) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).edgePadding()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (startFailure != null) {
                            // The failure card owns the screen.
                        } else if (navState.sessionState == NavSessionState.IDLE ||
                            navState.sessionState == NavSessionState.STOPPED
                        ) {
                            // Route still being computed (token -> retry -> mode fallback can
                            // legitimately take a while): say so instead of a blank header.
                            OfflineBanner(text = "Calculating route…")
                        } else NavGuidanceHeader(
                            nav = navState,
                            // Safeguard: while lanes show, drop the "Then" chip so the
                            // top cluster can't grow tall enough to crowd other elements.
                            hideNextHint = showLanes,
                        )
                        if (showLanes) {
                            Spacer(Modifier.height(8.dp))
                            LaneGuidance(lanes = navState.lanes)
                        }
                        if (!isOnline) {
                            Spacer(Modifier.height(8.dp))
                            OfflineBanner(
                                text = if (rerouteOfflinePaused) {
                                    "Offline · rerouting when back online"
                                } else {
                                    "Offline · guidance continues"
                                },
                            )
                        }
                    }
                }

                if (!fullSdkNavUi) {
                    // Mid-left: speedometer (hidden until we have a GPS fix).
                    navSpeedKmh?.let { kmh ->
                        Speedometer(
                            kmh = kmh,
                            limitKmh = if (appSettings.overspeedEnabled) appSettings.overspeedLimitKmh else null,
                            modifier = Modifier.align(Alignment.CenterStart).edgePadding()
                                .padding(start = 16.dp),
                        )
                    }

                    // Bottom ETA bar (full width, anchored bottom-centre). END asks to
                    // confirm; the minimise button steps away to the map (trip continues).
                    NavGuidanceBottomBar(
                        nav = navState,
                        onEnd = { showEndConfirm = true },
                        onMinimize = ::moveAwayFromNav,
                        modifier = Modifier.align(Alignment.BottomCenter).edgePadding()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                } else {
                    // Full-SDK chrome owns the top (header) and bottom (ETA card).
                    // Keep our minimise/end controls in a compact BOTTOM-LEFT stack
                    // so they never overlap the SDK's full-width header, and relocate
                    // the offline banner to bottom-centre above the SDK ETA card.
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).edgePadding()
                            .padding(start = 16.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SquareMapControl(
                            OpenDashIcons.Close, "End navigation", tint = Ktm.Danger,
                            cornerRadius = Ktm.RadiusCard,
                        ) { showEndConfirm = true }
                        SquareMapControl(
                            OpenDashIcons.ChevronDown, "Back to map",
                            cornerRadius = Ktm.RadiusCard,
                        ) { moveAwayFromNav() }
                    }
                    if (!isOnline) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).edgePadding()
                                .padding(start = 12.dp, end = 12.dp, bottom = 170.dp),
                        ) {
                            OfflineBanner(
                                text = if (rerouteOfflinePaused) {
                                    "Offline · rerouting when back online"
                                } else {
                                    "Offline · guidance continues"
                                },
                            )
                        }
                    }
                }
                // Bottom-right control stack: compass (only when the map is rotated)
                // above the bike-connection button. In full-SDK mode raise it higher
                // so it clears the SDK's own ETA card / re-center button.
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).edgePadding()
                        .padding(end = 16.dp, bottom = if (fullSdkNavUi) 170.dp else 104.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        SquareMapControl(
                            OpenDashIcons.Compass, "Reset orientation to north",
                            tint = Ktm.Danger, rotation = -mapBearing, cornerRadius = Ktm.RadiusCard,
                        ) { resetBearing(googleMap) }
                    }
                    if (navState.sessionState.isActiveNav() && lastStartedDest?.stops?.isEmpty() == true) {
                        SquareMapControl(
                            OpenDashIcons.Search, "Add a stop", cornerRadius = Ktm.RadiusCard,
                        ) { openTripStopSearch() }
                    }
                    ConnectIconPill(onClick = onOpenConnect, cornerRadius = Ktm.RadiusCard)
                }
            }

            NavStage.TRIP_FINISHED -> {
                // Top-left back returns to the browse map (keeps the trip's route
                // on the map behind the summary card).
                Box(
                    modifier = Modifier.align(Alignment.TopStart).edgePadding()
                        .padding(start = 16.dp, top = 10.dp),
                ) {
                    CircleBackButton(onClick = ::backToBrowse)
                }
                var placeSaved by remember(selected) {
                    mutableStateOf(selected?.let { store.isSaved(it.lat, it.lng) } ?: false)
                }
                Box(Modifier.align(Alignment.BottomCenter)) {
                    TripFinishedCard(
                        destinationLabel = selected?.label,
                        ride = finishedRide,
                        avgMode = appSettings.avgSpeedMode,
                        resolved = finishResolved,
                        fallbackDurationSec = finishedDurationSec,
                        onDone = ::backToBrowse,
                        onOpenReplay = { finishedRide?.let { onOpenReplay(it.id) } },
                        placeSaved = placeSaved,
                        onSavePlace = {
                            selected?.let {
                                scope.launch(Dispatchers.IO) {
                                    store.saveFavorite(it, com.navigator.app.nav.destination.FavoriteSlot.OTHER)
                                }
                                placeSaved = true
                                android.widget.Toast.makeText(context, "Place saved", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        canRideBack = finishedPoints?.firstOrNull() != null || flow.tripOrigin != null,
                        onRideBack = ::rideBack,
                        onShareGpx = finishedRide?.let { ride ->
                            {
                                scope.launch {
                                    val intent = withContext(Dispatchers.IO) {
                                        com.navigator.app.ride.GpxShare.buildIntent(context, ride, rideStore)
                                    }
                                    if (intent != null) runCatching { context.startActivity(intent) }
                                }
                            }
                        },
                        modifier = Modifier.onGloballyPositioned {
                            finishCardHeightPx = it.size.height +
                                with(density) { 28.dp.roundToPx() }
                        },
                    )
                }
            }
            }
          }
        }

        if (showExitConfirm) {
            ExitConfirmDialog(
                onConfirm = { showExitConfirm = false; onExit() },
                onDismiss = { showExitConfirm = false },
            )
        }

        confirmReplaceFor?.let { p ->
            ReplaceTripConfirmDialog(
                destination = p.label,
                onConfirm = { confirmReplaceFor = null; startTo(p) },
                onDismiss = { confirmReplaceFor = null },
            )
        }

        if (showEndConfirm) {
            EndNavConfirmDialog(
                onConfirm = { showEndConfirm = false; finishTrip() },
                onDismiss = { showEndConfirm = false },
            )
        }

        // Resolving a shared Google Maps link.
        if (resolvingLink) {
            Box(
                Modifier.fillMaxSize().background(Ktm.Screen.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Ktm.Orange)
                    Spacer(Modifier.height(12.dp))
                    Text("Opening shared location…", color = Ktm.White, fontFamily = BarlowCondensed, fontSize = 16.sp)
                }
            }
        }
    }
}

// ============================ Map ======================================

@Composable
private fun BrowseMap(
    retainedNav: RetainedNavigationView,
    navUiEnabled: Boolean,
    fullSdkChrome: Boolean,
    onMap: (GoogleMap?) -> Unit,
) {
    val context = LocalContext.current
    // Reuse the app-scoped NavigationView so returning to the map home re-attaches
    // an already-loaded map instead of rebuilding it (no reload flash).
    val navView = remember(retainedNav) { retainedNav.getOrCreate() }
    // Follow the app's own theme (reactive), not the phone's — so the in-app
    // Light/Dark/System setting also drives the map and the SDK guidance UI.
    val dark = Ktm.current.isDark
    var mapRef by remember { mutableStateOf<GoogleMap?>(null) }
    // Bottom map padding while navigating so the SDK's re-center button (which pops
    // up bottom-left on pan) sits ABOVE our custom ETA bar, on the same level as the
    // bottom-right compass (which is systemBars + 100dp above the raw bottom).
    val density = LocalDensity.current
    val sysTopPx = WindowInsets.systemBars.getTop(density)
    val sysBottomPx = WindowInsets.systemBars.getBottom(density)
    val navBottomPadPx = sysBottomPx + with(density) { 92.dp.roundToPx() }
    AndroidView(
        // The retained view may still be attached to a previous parent when we
        // re-enter; detach before AndroidView re-adds it to avoid an IAE.
        factory = {
            (navView.parent as? android.view.ViewGroup)?.removeView(navView)
            navView
        },
        modifier = Modifier.fillMaxSize(),
    )
    // (Re)deliver the map to callers on every entry. getMapAsync returns the
    // cached GoogleMap immediately for an already-loaded view, so this is cheap.
    LaunchedEffect(navView) {
        navView.getMapAsync { gm ->
            mapRef = gm
            onMap(gm)
            retainedNav.mapOwner = HomeMapOwner
            gm.uiSettings.isMyLocationButtonEnabled = false // we draw our own recenter
            gm.uiSettings.isCompassEnabled = false          // custom controls instead
            // The SDK's own puck is only used during active navigation (its
            // follow-camera + travel-direction chevron are correct there). While
            // browsing/previewing we draw a compass-driven marker instead, so keep
            // the SDK puck off to avoid two dots (see the screen-level effect).
            if (hasLocationPermission(context)) {
                runCatching { gm.isMyLocationEnabled = navUiEnabled }
            }
            // Only frame the camera the first time — re-entry keeps the user's
            // current pan/zoom instead of snapping back to the start.
            if (!retainedNav.mapInitialized) {
                val start = lastLocation(context)?.let { LatLng(it.first, it.second) }
                    ?: LatLng(12.9716, 77.5946) // Bengaluru fallback
                gm.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 15f))
                retainedNav.mapInitialized = true
            }
        }
    }
    // Map + SDK day/night. Kept in its OWN effect keyed only on `dark`/`mapRef` so
    // it isn't re-slammed on every enter/exit of NAVIGATING (which caused a restyle
    // flash). `dark` already tracks the app theme, which in turn honours the
    // Day/Night nav-theme setting while actively navigating (see MainActivity).
    LaunchedEffect(dark, mapRef) {
        runCatching {
            mapRef?.mapColorScheme = if (dark) MapColorScheme.DARK else MapColorScheme.LIGHT
        }
        runCatching {
            navView.setForceNightMode(
                if (dark) ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY,
            )
        }
    }
    // Guidance engine + chrome + padding, keyed on the nav/chrome state.
    LaunchedEffect(navUiEnabled, mapRef, fullSdkChrome) {
        runCatching { navView.setNavigationUiEnabled(navUiEnabled) }
        // In FULL-SDK mode show Google's own stock guidance chrome; otherwise hide
        // the parts we replace with custom overlays (but always keep the re-center
        // button, which pops up on pan). Wrapped individually so an API mismatch
        // degrades gracefully.
        val stockChrome = fullSdkChrome && navUiEnabled
        runCatching { navView.setHeaderEnabled(stockChrome) }
        runCatching { navView.setEtaCardEnabled(stockChrome) }
        runCatching { navView.setSpeedometerEnabled(stockChrome) }
        runCatching { navView.setRecenterButtonEnabled(true) }
        runCatching { navView.setReportIncidentButtonEnabled(false) }
        runCatching { navView.setTrafficIncidentCardsEnabled(stockChrome) }
        // Map padding so the SDK's stock chrome AND its tilted follow-camera focus
        // are laid out inside the visible safe area (the app is edge-to-edge):
        //  - full-SDK nav: inset by the real system bars (top status bar + bottom
        //    nav bar) so the SDK header/ETA don't hide under the bars and the
        //    perspective isn't computed against an occluded viewport (was the
        //    "distortion" cause);
        //  - custom-chrome nav: bottom only, lifted above our ETA bar;
        //  - browse: none.
        val (padTop, padBottom) = when {
            navUiEnabled && fullSdkChrome -> sysTopPx to sysBottomPx
            navUiEnabled -> 0 to navBottomPadPx
            else -> 0 to 0
        }
        runCatching { mapRef?.setPadding(0, padTop, 0, padBottom) }
        // SDK puck only during active navigation; the compass-driven marker owns
        // browse/preview (screen-level effect) so keep the SDK dot off otherwise.
        if (hasLocationPermission(context)) {
            runCatching { mapRef?.isMyLocationEnabled = navUiEnabled }
        }
    }
    DisposableEffect(Unit) { onDispose { onMap(null) } }
}

/** Owner token for the shared map while the map home is the active screen. */
private object HomeMapOwner

/**
 * "Trip finished" summary card, anchored to the bottom over the map (which shows
 * the ride's speed-coloured route). Shows distance / duration / average speed and
 * offers Done + Open in Replay. When there's no recording to show (recording off
 * or the trip too short) it degrades to a duration-only card.
 */
@Composable
private fun TripFinishedCard(
    destinationLabel: String?,
    ride: RecordedRide?,
    avgMode: com.navigator.app.ride.AvgSpeedMode,
    resolved: Boolean,
    fallbackDurationSec: Int,
    onDone: () -> Unit,
    onOpenReplay: () -> Unit,
    placeSaved: Boolean,
    onSavePlace: () -> Unit,
    canRideBack: Boolean,
    onRideBack: () -> Unit,
    onShareGpx: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().edgePadding()
            .padding(16.dp)
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Ktm.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(OpenDashIcons.Check, null, tint = Ktm.OnAccent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Eyebrow("Trip finished", fontSize = 12, letterSpacing = 2.0)
                Text(
                    destinationLabel?.takeIf { it.isNotBlank() } ?: "Destination reached",
                    color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        when {
            !resolved -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Ktm.Orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Saving ride…", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp)
            }
            ride != null -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TripStat("DISTANCE", DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC))
                TripStat("TIME", formatDuration(ride.durationSeconds))
                TripStat(avgMode.shortLabel, "${ride.displayAvgKmh(avgMode).toInt()} km/h")
                TripStat("MAX", "${ride.maxSpeedKmh.toInt()} km/h")
            }
            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TripStat("TIME", formatDuration(fallbackDurationSec))
                Text(
                    "No route recorded for this trip.",
                    color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }

        // Follow-ups: keep the place, go back the way you came, hand the track to another app.
        if (resolved) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FollowUpAction(if (placeSaved) "Saved \u2713" else "Save place", enabled = !placeSaved, onClick = onSavePlace)
                FollowUpAction("Ride back", enabled = canRideBack, onClick = onRideBack)
                if (onShareGpx != null) FollowUpAction("Share GPX", onClick = onShareGpx)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (ride != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KtmOutlineButton(text = "Open in Replay", modifier = Modifier.weight(1f), onClick = onOpenReplay)
                Spacer(Modifier.width(12.dp))
                KtmPrimaryButton(text = "Done", modifier = Modifier.weight(1f), onClick = onDone)
            }
        } else {
            KtmPrimaryButton(text = "Done", modifier = Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}

/** "Home" / "Work" pill on the browse map: one tap to the route preview. */
@Composable
private fun QuickPlaceChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(
                onClickLabel = "Ride to $label",
                role = androidx.compose.ui.semantics.Role.Button,
            ) { haptics.tap(); onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Ktm.Orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(), color = Ktm.White, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp, maxLines = 1,
        )
    }
}

@Composable
private fun FollowUpAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        color = if (enabled) Ktm.Orange else Ktm.Dim,
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 6.dp),
    )
}

/** One labelled stat in the trip-finished / summary card. */
@Composable
private fun TripStat(label: String, value: String) {
    Column {
        Text(label, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
        Text(value, color = Ktm.White, fontFamily = JetBrainsMono, fontSize = 13.sp)
    }
}

/** Pill shown on the browse map while a trip keeps running in the background. */
@Composable
private fun ResumeNavPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Orange)
            .clickable { haptics.confirm(); onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Navigation, null, tint = Ktm.OnAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Resume navigation", color = Ktm.OnAccent, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun MapPlaceholder(message: String, onRetry: (() -> Unit)? = null) {
    val base = Modifier.fillMaxSize().background(Ktm.Screen)
    Box(
        modifier = if (onRetry != null) base.clickable(onClick = onRetry) else base,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ============================ Preview ==================================

@Composable
private fun PreviewCard(
    place: SavedPlace?,
    loading: Boolean,
    routes: List<RoutePreview>,
    selectedIndex: Int,
    failed: Boolean,
    stops: List<SavedPlace>,
    routeStops: List<SavedPlace>,
    origin: Pair<Double, Double>?,
    onSelect: (Int) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    place ?: return
    Column(
        modifier = Modifier.fillMaxWidth().edgePadding()
            .padding(16.dp)
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TapTarget(onClick = onBack) {
                Icon(OpenDashIcons.ChevronLeft, "Back", tint = Ktm.TextSecondary, modifier = Modifier.size(24.dp))
            }
            Text(place.label, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        }

        // ---- Stops list (Your location -> stops -> destination) --------------
        Spacer(Modifier.height(12.dp))
        // Per-leg time / distance / arrival, when the (multi-stop) route carries a matching breakdown.
        // Only for the stops the routes were computed for: right after a reorder / add / remove the
        // old legs would otherwise show against the new order until the recompute lands.
        val legLines = remember(routes, selectedIndex, stops, routeStops) {
            if (routeStops != stops) emptyList()
            else StopLegs.lines(routes.getOrNull(selectedIndex)?.legs.orEmpty(), stops.size + 1, System.currentTimeMillis())
        }
        // Height of one stop slot (row + leg line), measured, to turn a drag distance into rows.
        var slotPx by remember { mutableStateOf(0f) }
        // The list scrolls when four stops + leg lines outgrow a small screen.
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            StopRow(text = "Your location", color = Ktm.Muted2)
            LegLine(legLines.getOrNull(0))
            stops.forEachIndexed { i, s ->
                androidx.compose.runtime.key(i) {
                    StopSlot(
                        i = i, count = stops.size, label = "Stop ${i + 1}: ${s.label}",
                        legLine = legLines.getOrNull(i + 1), slotPx = slotPx,
                        onSlotSize = { slotPx = it },
                        onMove = { delta -> onMoveStop(i, delta) },
                        onRemove = { onRemoveStop(i) },
                    )
                }
            }
            StopRow(text = place.label, color = Ktm.Orange)
        }
        if (stops.size < MAX_STOPS) {
            Spacer(Modifier.height(6.dp))
            Text(
                "+ Add stop",
                color = Ktm.Orange, fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAddStop)
                    .defaultMinSize(minHeight = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Ktm.Orange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text("Finding routes…", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp)
            }
            routes.isNotEmpty() -> {
                // One selectable chip per route (reliable vs tapping the thin line).
                // With stops the API returns a single multi-leg route (no alternates).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    routes.forEachIndexed { i, r ->
                        RouteChip(
                            durationSeconds = r.durationSeconds,
                            distanceMeters = r.distanceMeters,
                            delaySeconds = r.delaySeconds,
                            label = if (stops.isNotEmpty()) "Route" else if (i == 0) "Fastest" else "Alt ${i}",
                            selected = i == selectedIndex,
                            modifier = Modifier.weight(1f),
                        ) { onSelect(i) }
                    }
                }
            }
            failed -> Text(
                "Couldn't load a route preview. You can still start navigation.",
                color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        KtmPrimaryButton(text = "Start Navigation", onClick = onStart)
    }
}

/** "12 min · 8.4 km · arrive 14:32" between two stops; nothing when the route has no breakdown. */
@Composable
private fun LegLine(text: String?) {
    if (text == null) return
    Text(
        text, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp, maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.dp, bottom = 2.dp),
    )
}

/** One line in the preview stop list: a dot + label, with a drag handle and a remove button on
 *  editable intermediate stops (drag the handle to reorder; TalkBack gets move up/down actions). */
@Composable
private fun StopRow(
    text: String,
    color: Color,
    index: Int = 0,
    count: Int = 0,
    onRemove: (() -> Unit)? = null,
    onMoveBy: ((Int) -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text, color = color, fontFamily = Barlow, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (onRemove != null) {
            dragHandle?.invoke()
            TapTarget(onClick = { onRemove.invoke() }) {
                Icon(OpenDashIcons.Close, "Remove stop", tint = Ktm.Danger, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * One editable stop (row + leg line) as a draggable unit: the whole slot floats above its
 * neighbours while dragged, and the handle's drag distance becomes whole-row moves.
 */
@Composable
private fun StopSlot(
    i: Int,
    count: Int,
    label: String,
    legLine: String?,
    slotPx: Float,
    onSlotSize: (Float) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var dragY by remember { mutableStateOf(0f) }
    val dragging = dragY != 0f
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    // The gesture must not restart when these change mid-drag (leg lines appear, size remeasures).
    val slot by androidx.compose.runtime.rememberUpdatedState(slotPx)
    val index by androidx.compose.runtime.rememberUpdatedState(i)
    val total by androidx.compose.runtime.rememberUpdatedState(count)
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    Column(
        Modifier
            .onSizeChanged { onSlotSize(it.height.toFloat()) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragY }
            .then(if (dragging) Modifier.background(Ktm.SurfaceAlt) else Modifier),
    ) {
        StopRow(
            text = label, color = Ktm.White, index = i, count = count, onRemove = onRemove,
            dragHandle = if (count > 1) {
                {
                    Box(
                        modifier = Modifier.size(48.dp)
                            .semantics {
                                contentDescription = "Reorder stop ${i + 1}"
                                customActions = buildList {
                                    if (i > 0) add(CustomAccessibilityAction("Move up") { onMove(-1); true })
                                    if (i < count - 1) add(CustomAccessibilityAction("Move down") { onMove(1); true })
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { dragY = 0.001f; haptics.tap() },
                                    onDragEnd = {
                                        val delta = StopLegs.dragDelta(dragY, slot, index, total)
                                        dragY = 0f
                                        if (delta != 0) move(delta)
                                    },
                                    onDragCancel = { dragY = 0f },
                                ) { change, drag ->
                                    change.consume()
                                    dragY += drag.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.DragHandle, null, tint = Ktm.TextSecondary, modifier = Modifier.size(24.dp))
                    }
                }
            } else null,
        )
        LegLine(legLine)
    }
}

/** A selectable route option (duration + distance + traffic), highlighted when
 *  selected. Traffic shows the delay vs. free-flow with a colour-coded dot. */
@Composable
private fun RouteChip(
    durationSeconds: Int,
    distanceMeters: Int,
    delaySeconds: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(if (selected) Ktm.Orange else Ktm.Screen)
            .border(1.dp, if (selected) Ktm.Orange else Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label.uppercase(),
            color = if (selected) Ktm.OnAccent else Ktm.Muted2,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp,
        )
        Text(
            formatDuration(durationSeconds),
            color = if (selected) Ktm.OnAccent else Ktm.White,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )
        Text(
            DistanceFormatter.format(distanceMeters, DistanceUnits.METRIC),
            color = if (selected) Ktm.OnAccent else Ktm.Muted2,
            fontFamily = JetBrainsMono, fontSize = 12.sp,
        )
        TrafficLine(delaySeconds = delaySeconds, onAccent = selected)
    }
}

/** Traffic delay indicator: a colour-coded dot + label. Green "On time" under a
 *  minute, otherwise "+N min" shaded light-orange / orange / red by severity. */
@Composable
private fun TrafficLine(delaySeconds: Int, onAccent: Boolean) {
    val delayMin = delaySeconds / 60
    val dotColor = when {
        delayMin < 1 -> Ktm.Green
        delayMin < 15 -> Ktm.Orange
        else -> Ktm.Danger
    }
    val label = if (delayMin < 1) "On time" else "+$delayMin min"
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (onAccent) Ktm.OnAccent else dotColor))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = if (onAccent) Ktm.OnAccent else dotColor,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        )
    }
}

// ============================ Active navigation ========================

/** Confirm before fully closing the app (Back at the map home). */
@Composable
private fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextSecondary,
        title = { Text("Close KTM Navigator?", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = { Text("This stops navigation to the dash and exits the app.", fontFamily = Barlow) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("CLOSE", color = Ktm.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Ktm.TextPrimary) } },
    )
}

/** 48dp (minimum) tap area around a small icon, centred; the icon keeps its visual size. */
@Composable
private fun TapTarget(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Dialog button sized for gloved thumbs (used by the dialogs shown while riding). */
@Composable
private fun BigDialogButton(text: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 56.dp)) {
        Text(text, color = color, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** Confirm before ending an in-progress trip (the END button / dash guidance). */
@Composable
private fun EndNavConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextSecondary,
        title = { Text("End navigation?", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = { Text("This stops guidance to the dash and finishes the trip.", fontFamily = Barlow) },
        confirmButton = { BigDialogButton("END", Ktm.Danger, onConfirm) },
        dismissButton = { BigDialogButton("CANCEL", Ktm.TextPrimary, onDismiss) },
    )
}

@Composable
private fun ReplaceTripConfirmDialog(destination: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ktm.Surface,
        titleContentColor = Ktm.White,
        textContentColor = Ktm.TextSecondary,
        title = { Text("Replace current trip?", fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Your current navigation will stop and a new trip" +
                    (destination?.let { " to $it" } ?: "") + " will start.",
                fontFamily = Barlow,
            )
        },
        confirmButton = { BigDialogButton("REPLACE", Ktm.Orange, onConfirm) },
        dismissButton = { BigDialogButton("KEEP CURRENT", Ktm.TextPrimary, onDismiss) },
    )
}

/** Small pill shown under the guidance header while the device is offline:
 *  guidance keeps running from the already-computed route, but rerouting/traffic
 *  need data. Styled to read as a warning without shouting. */
@Composable
private fun OfflineBanner(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Orange, RoundedCornerShape(Ktm.RadiusButton))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Ktm.Orange))
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            color = Ktm.White,
            fontFamily = Barlow,
            fontSize = 13.sp,
        )
    }
}

/** Shown on the navigation screen when the requested trip could not be started. */
@Composable
private fun RouteFailureCard(
    failure: com.navigator.app.nav.providers.StartFailure,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBackToPreview: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Orange, RoundedCornerShape(Ktm.RadiusButton))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Navigation didn't start", color = Ktm.White, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(failure.message, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (canRetry) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Ktm.RadiusButton)).background(Ktm.Orange)
                    .clickable { haptics.confirm(); onRetry() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Retry", color = Ktm.OnAccent, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Ktm.RadiusButton)).border(1.dp, Ktm.BorderSoft, RoundedCornerShape(Ktm.RadiusButton))
                .clickable { haptics.tap(); onBackToPreview() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Back to route preview", color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Close", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp,
            modifier = Modifier.clickable { haptics.tap(); onClose() }.padding(12.dp),
        )
    }
}

/** A neutral dark circular icon button (Google-style controls). */
@Composable
internal fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val haptics = com.navigator.app.ui.theme.rememberHaptics()
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape)
            .background(Ktm.Surface)
            .border(2.dp, Ktm.BorderSoft, CircleShape)
            .clickable { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Ktm.White, modifier = Modifier.size(22.dp))
    }
}

// ============================ Confirm ==================================

@Composable
private fun ConfirmCard(
    place: SavedPlace?,
    origin: Pair<Double, Double>?,
    saved: Boolean,
    onGetDirections: () -> Unit,
    onToggleSave: () -> Unit,
) {
    place ?: return

    Column(
        modifier = Modifier.fillMaxWidth().edgePadding()
            .padding(16.dp)
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(18.dp),
    ) {
        Text(place.label, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        place.address?.let {
            Text(it, color = Ktm.Muted2, fontFamily = Barlow, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        distanceLabel(origin, place.lat, place.lng)?.let {
            Text(it + " away", color = Ktm.Orange, fontFamily = JetBrainsMono, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KtmPrimaryButton(text = "Get Directions", modifier = Modifier.weight(1f), onClick = onGetDirections)
            Spacer(Modifier.size(12.dp))
            SaveToggle(saved = saved, onClick = onToggleSave)
        }
    }
}

/** Bookmark toggle: outline when unsaved, brand-accent filled when saved. */
@Composable
private fun SaveToggle(saved: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(if (saved) Ktm.Orange else Ktm.Surface)
            .border(1.dp, if (saved) Ktm.Orange else Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (saved) OpenDashIcons.BookmarkFilled else OpenDashIcons.Bookmark,
            contentDescription = if (saved) "Saved" else "Save",
            tint = if (saved) Ktm.OnAccent else Ktm.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ============================ Shared UI ================================

@Composable
private fun SearchBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(Ktm.ControlHeight)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Search, null, tint = Ktm.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text("Enter Destination", color = Ktm.Dim, fontFamily = Barlow, fontSize = 16.sp)
    }
}

/**
 * App-scoped holder that keeps ONE [NavigationView] alive across route changes.
 *
 * The map surface is expensive to spin up (blank frame + tile fetch), so tearing
 * it down every time the user opens Settings and rebuilding it on return caused a
 * sub-second reload flash. Instead this holder is remembered *above* the route
 * switch: the view is created lazily on first map use and only destroyed when the
 * host activity goes away. Returning to the map home just re-attaches the same,
 * already-loaded view (see [rememberRetainedNavigationView]).
 */
class RetainedNavigationView(
    private val context: Context,
    private val lifecycle: Lifecycle,
) {
    var view: NavigationView? = null
        private set

    /** True once the one-time camera framing has run, so re-entry doesn't snap
     *  the camera back to the starting location. */
    var mapInitialized: Boolean = false

    /** Which screen last configured the shared map's listeners/overlays. A screen
     *  being disposed only cleans the map if it is still the owner - with animated
     *  route transitions the NEW screen sets up before the OLD one is disposed. */
    @Volatile var mapOwner: Any? = null

    /** Create the view on first use, bringing it up to the current lifecycle
     *  state; subsequent calls return the same retained instance. */
    fun getOrCreate(): NavigationView {
        view?.let { return it }
        val v = NavigationView(context)
        v.onCreate(Bundle())
        val state = lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) v.onStart()
        if (state.isAtLeast(Lifecycle.State.RESUMED)) v.onResume()
        view = v
        return v
    }

    fun onLifecycleEvent(event: Lifecycle.Event) {
        val v = view ?: return
        when (event) {
            Lifecycle.Event.ON_START -> v.onStart()
            Lifecycle.Event.ON_RESUME -> v.onResume()
            Lifecycle.Event.ON_PAUSE -> v.onPause()
            Lifecycle.Event.ON_STOP -> v.onStop()
            else -> {}
        }
    }

    fun destroy() {
        val v = view ?: return
        runCatching { v.onPause() }
        runCatching { v.onStop() }
        runCatching { v.onDestroy() }
        view = null
        mapInitialized = false
    }
}

/**
 * Remembers a single [RetainedNavigationView] scoped to the caller. Place this at
 * app scope (above the route switch) so the map survives navigation. The view is
 * only instantiated on first [RetainedNavigationView.getOrCreate], so screens that
 * never show the map (e.g. onboarding, the notification-mirror home) pay nothing.
 */
@Composable
fun rememberRetainedNavigationView(): RetainedNavigationView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember(context) { RetainedNavigationView(context, lifecycleOwner.lifecycle) }
    DisposableEffect(lifecycleOwner, holder) {
        val observer = LifecycleEventObserver { _, event -> holder.onLifecycleEvent(event) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
    return holder
}

// ============================ Helpers ==================================

/** Unwrap the hosting Activity from a (possibly wrapped) Compose context. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Format a route duration (seconds) as "M min" or "H hr M min". */
internal fun formatDuration(seconds: Int): String {
    val mins = (seconds + 59) / 60
    return if (mins < 60) "$mins min" else "${mins / 60} hr ${mins % 60} min"
}

private fun recenter(context: Context, gm: GoogleMap?) {
    val map = gm ?: return
    val loc = lastLocation(context) ?: return
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 16f))
}

/** Reset the map to north-up (bearing 0, tilt 0), keeping center + zoom. */
private fun resetBearing(gm: GoogleMap?) {
    val map = gm ?: return
    val cp = map.cameraPosition
    val north = CameraPosition.Builder(cp).bearing(0f).tilt(0f).build()
    map.animateCamera(CameraUpdateFactory.newCameraPosition(north))
}

/** Straight-line distance label from [origin] to a point, or null if unknown. */
internal fun distanceLabel(origin: Pair<Double, Double>?, lat: Double, lng: Double): String? {
    origin ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(origin.first, origin.second, lat, lng, out)
    val meters = out[0].toInt()
    if (meters <= 0) return null
    return "~" + DistanceFormatter.format(meters, DistanceUnits.METRIC)
}

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * A fresh current-location fix (falls back to last-known). Used for the route
 * preview so the Routes API route-token origin matches the SDK's GPS at Start,
 * which is required for the SDK to honour the exact selected route.
 */
private suspend fun freshLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return lastLocation(context)
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return lastLocation(context)
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return lastLocation(context)
    }
    return try {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                cont.resume(loc?.let { it.latitude to it.longitude } ?: lastLocation(context))
            }
            cont.invokeOnCancellation { signal.cancel() }
        }
    } catch (e: SecurityException) {
        lastLocation(context)
    }
}

/** Best-effort last-known location for the initial camera / distances / recenter. */
private fun lastLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        // Newest fix across providers (the first non-null one could be hours stale).
        providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (e: SecurityException) {
        null
    }
}

/** Location permission granted AND the system location switch on; re-checked whenever the screen resumes. */
@Composable
private fun rememberLocationReady(): Boolean {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    fun check(): Boolean {
        if (!hasLocationPermission(context)) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return runCatching { lm.isLocationEnabled }.getOrDefault(true)
    }
    var ready by remember { mutableStateOf(check()) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ready = check()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return ready
}
