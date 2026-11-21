package com.navigator.app.ui.feature.navigate

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.libraries.navigation.NavigationView
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.location.freshLocation
import com.navigator.app.location.hasLocationPermission
import com.navigator.app.location.lastKnownLocation
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.RoutesClient
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.net.ConnectivityMonitor
import com.navigator.app.ride.RideEvents
import com.navigator.app.ride.RideStore
import com.navigator.app.trip.FinishedTrip
import com.navigator.app.ui.feature.navigate.ActivityCard
import com.navigator.app.ui.feature.navigate.LocationState
import com.navigator.app.ui.feature.navigate.MapState
import com.navigator.app.ui.feature.navigate.NavigateRoute
import com.navigator.app.ui.feature.navigate.finished.FinishUi
import com.navigator.app.ui.feature.navigate.finished.TripFinishedViewModel
import com.navigator.app.ui.feature.navigate.link.SharedLinkActions
import com.navigator.app.ui.feature.navigate.link.SharedLinkCard
import com.navigator.app.ui.feature.navigate.plan.NewStop
import com.navigator.app.ui.feature.navigate.plan.ReplaceTripDialog
import com.navigator.app.ui.feature.navigate.plan.RoutePreviewViewModel
import com.navigator.app.ui.feature.navigate.plan.RouteSheetMapper
import com.navigator.app.ui.feature.navigate.plan.RoutesApi
import com.navigator.app.ui.feature.navigate.riding.EndTripDialog
import com.navigator.app.ui.feature.navigate.riding.MapButtonEffect
import com.navigator.app.ui.feature.navigate.riding.MapButtonEvent
import com.navigator.app.ui.feature.navigate.riding.MapButtonMachine
import com.navigator.app.ui.feature.navigate.riding.rememberSpeedKmh
import com.navigator.app.ui.feature.navigate.search.PlaceSearchRoute
import com.navigator.app.ui.feature.navigate.search.SearchMode
import com.navigator.app.ui.feature.record.RecordRidePill
import com.navigator.app.ui.map.FollowMode
import com.navigator.app.ui.map.LocationMarkerStyle
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.map.MapStop
import com.navigator.app.ui.map.MapSurface
import com.navigator.app.ui.map.RetainedNavigationView
import com.navigator.app.ui.map.TripPreviewScene
import com.navigator.app.ui.map.rememberDeviceHeadingState
import com.navigator.app.ui.map.rememberDeviceLatLng
import com.navigator.app.ui.map.rememberMapController
import com.navigator.app.ui.shell.ChipTone
import com.navigator.app.ui.shell.LocalTripShared
import com.navigator.app.ui.shell.SharedElements
import com.navigator.app.ui.shell.rememberNightNow
import com.navigator.app.ui.shell.resolveSurfaceDark
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RidingTheme
import com.navigator.app.ui.theme.accentFor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transition between [NavStage] chrome. The full-screen SEARCH panel slides up
 *  from / down to the bottom; every other stage change is a gentle fade + small
 *  vertical drift so cards/overlays never hard-cut. */
private fun stageTransition(initial: NavStage, target: NavStage, motion: KtmMotionSpecs): ContentTransform = when {
    // The riding screen has its own choreography (MO1, MO6): around it the stage only fades, with no drift and no spring.
    initial == NavStage.NAVIGATING || target == NavStage.NAVIGATING ->
        fadeIn(motion.effectsDefault()) togetherWith fadeOut(motion.effectsFast())
    target == NavStage.SEARCH ->
        (slideInVertically(motion.spatialDefault()) { if (motion.reduced) 0 else it } + fadeIn(motion.effectsDefault())) togetherWith fadeOut(motion.effectsFast())
    initial == NavStage.SEARCH ->
        fadeIn(motion.effectsDefault()) togetherWith (slideOutVertically(motion.spatialDefault()) { if (motion.reduced) 0 else it } + fadeOut(motion.effectsFast()))
    else ->
        (fadeIn(motion.effectsDefault()) + slideInVertically(motion.spatialDefault()) { if (motion.reduced) 0 else it / 12 }) togetherWith
            (fadeOut(motion.effectsFast()) + slideOutVertically(motion.spatialDefault()) { if (motion.reduced) 0 else -it / 12 })
}


/**
 * Phone-first map home (see docs/architecture.md §3).
 *
 * A single persistent Nav SDK [NavigationView] is the map surface across every
 * stage. [NavStage.BROWSE] shows the map + search bar + Connect pill; tapping
 * search opens [NavStage.SEARCH] (recents, favorites, live autocomplete);
 * picking a place goes to [NavStage.CONFIRM] (pin + place card → Directions / Save),
 * then [NavStage.PREVIEW] (routes and stops), [NavStage.NAVIGATING] and [NavStage.TRIP_FINISHED].
 */
@Composable
fun NavigateHost(
    retainedNav: RetainedNavigationView,
    onOpenConnect: () -> Unit,
    onOpenPlaces: () -> Unit = {},
    onOpenReplay: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenRecordingSettings: () -> Unit = onOpenSettings,
    sharedLink: String? = null,
    onSharedLinkConsumed: () -> Unit = {},
    pendingPlace: SavedPlace? = null,
    pendingDirections: Boolean = false,
    onPendingPlaceConsumed: () -> Unit = {},
    quickRoute: String? = null,
    onQuickRouteConsumed: () -> Unit = {},
    /** The foreground notification was tapped: resume the live guidance view if a trip is running. */
    openNavRequest: Boolean = false,
    /** The notification's End trip: show the end-trip confirm over the live trip. */
    confirmEndRequest: Boolean = false,
    onConfirmEndConsumed: () -> Unit = {},
    /** The Where to? shortcut: open the search. */
    searchRequest: Boolean = false,
    onSearchRequestConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val feedback = com.navigator.app.ui.shell.LocalFeedback.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    val appSettings = remember { context.appContainer.settings.legacy }
    // Speed-band colouring scheme for the trip-finished route (Advanced settings).
    val speedBands = remember {
        com.navigator.app.ride.RideMetrics.SpeedBands(
            appSettings.speedBandBaseKmh, appSettings.speedBandStepKmh, appSettings.speedBandCount,
        )
    }

    val available = remember { GoogleNavSdkController.isAvailable(context) }
    var navReady by remember { mutableStateOf(false) }
    var navError by remember { mutableStateOf(false) }
    var prepareAttempt by remember { mutableIntStateOf(0) }
    val map = rememberMapController(retainedNav, LocationMarkerStyle.fromId(appSettings.locationMarkerStyle))
    val mapReady by map.ready.collectAsState()
    val mapBearing by map.bearing.collectAsState()

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
                AppLogger.log("Nav", "NavHome: map still not ready after 8s (getNavigator gave no callback) - offering Try again")
                navError = true // the Navigate screen turns this into the failure card with Try again; a late ready still wins
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

    // Live GPS speed (km/h) for the guidance speedometer; null when no recent fix.
    // Sourced independently of the BLE service so it works during standalone nav.
    val navSpeedKmh = rememberSpeedKmh(active = stage == NavStage.NAVIGATING)
    val container = context.appContainer
    val settingsSnap by container.settings.snapshot.collectAsState()
    // Day/Night applies to the riding surfaces and the map while riding; everything else keeps the app's theme.
    val night by rememberNightNow()
    val appDark = KtmTheme.colors.isDark
    val ridingDark = resolveSurfaceDark(appDark, settingsSnap.ridingScreens, ridingNow = true, night = night)
    val mapDark = resolveSurfaceDark(appDark, settingsSnap.ridingScreens, ridingNow = stage == NavStage.NAVIGATING, night = night)
    // What the riding surfaces cover of the map (top, bottom in px), and whether the map button reads Recenter.
    var ridingInsets by remember { mutableStateOf(MapInsets()) }
    val riding = remember { RidingOverlayState() }
    var mapButton by riding::mapButton
    var showTripSheet by riding::showTripSheet
    var showTripStops by riding::showTripStops
    var addStopOnResume by remember { mutableStateOf(false) }
    var showBike by riding::showBike
    var initialRemainingM by remember { mutableStateOf<Int?>(null) }
    val locationReady = rememberLocationReady()

    // Compass-driven "you are here" marker for the non-navigating map stages
    // (browse / place confirm / route preview). During active navigation the SDK's
    // own follow-puck is used instead (correct travel-direction chevron), and the
    // trip-finished stage shows the finished route with start/end pins only.
    val showUserMarker = stage == NavStage.BROWSE ||
        stage == NavStage.SEARCH ||
        stage == NavStage.CONFIRM ||
        stage == NavStage.PREVIEW
    // Heading as a State object read only inside the effect (never in
    // composition), so a ~16 Hz sensor tick rotates the marker beam without
    // recomposing the whole nav screen.
    val deviceHeadingState = rememberDeviceHeadingState(active = showUserMarker)
    val deviceLatLng = rememberDeviceLatLng(active = showUserMarker)
    // Keyed on `stage` too so every stage change (incl. returning to BROWSE after a
    // preview that cleared the map) re-asserts the marker; showUserMarker re-adds it if
    // the shared map was cleared. Heading is collected via snapshotFlow inside so
    // its updates mutate the marker off the composition path.
    LaunchedEffect(mapReady, showUserMarker, stage, deviceLatLng) {
        if (!mapReady || !showUserMarker) {
            map.hideUserMarker()
            return@LaunchedEffect
        }
        val ll = deviceLatLng ?: return@LaunchedEffect
        snapshotFlow { deviceHeadingState.value }.collect { heading ->
            map.showUserMarker(MapPoint(ll.latitude, ll.longitude), heading)
        }
    }

    // ---- Preview state ----------------------------------------------------
    // The routes (and which is selected) belong to the RoutePreviewViewModel; the stops stay with the flow.
    val appContext = remember(context) { context.applicationContext }
    val routeVm: RoutePreviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = RoutePreviewViewModel(
                    api = RoutesApi { o, d, mode, via -> RoutesClient.computeRoutes(o, d, PlacesClient.androidAuth(appContext), mode, via) },
                    freshLocation = { freshLocation(appContext) },
                    lastKnown = { lastKnownLocation(appContext) },
                    mode = { appContext.appContainer.settings.legacy.vehicleMode },
                ) as T
            }
        },
    )
    val routePreview by routeVm.state.collectAsState()
    // Planning-sheet state: a place picked on the preview map (long press, place label) waiting to be added,
    // and Pick on map for a stop.
    // Both survive a rotation (and the process being recreated), like the rest of the plan flow.
    var stopCandidate by rememberSaveable(stateSaver = SavedPlaceSaver) { mutableStateOf<SavedPlace?>(null) }
    var pickingStop by rememberSaveable { mutableStateOf(false) }
    // How much of the map the plan sheet covers (left, bottom in px), so the camera frames the route in the rest.
    var planInsets by remember { mutableStateOf(0 to 0) }
    // Bumped when the sheet settles at a new height so the camera frames the route again (not for the proposed-stop sheet).
    var fitKey by remember { mutableIntStateOf(0) }
    val lastFit = remember { arrayOfNulls<Any>(1) }
    // Intermediate stops (up to MAX_STOPS), in visit order, shown/edited in the
    // preview. Feeds the Routes API `intermediates` and the SDK waypoint list.
    var stops by flow::stops
    // Where a search pick should go: the destination (normal) or an added stop.
    var searchTarget by flow::searchTarget
    // True when the CONFIRM card was reached from the search list (Back returns there).
    var confirmFromSearch by flow::confirmFromSearch
    // The trip itself (start, add stop, end, arrival detection) belongs to the app-scoped
    // TripController, so it survives leaving this screen.
    val trip = remember { context.appContainer.trip }
    val tripState by trip.state.collectAsState()
    // The rider stepped away from the active-navigation view (Back / minimise)
    // without ending the trip; suppresses auto-resume so they stay on the map,
    // and drives the "Resume navigation" pill.
    val leftNavManually = tripState.minimised
    // Confirm before actually ending an in-progress trip.
    var showEndConfirm by riding::showEndConfirm
    // A new trip asked for while another runs: DL3 (replace, keep, or add the place as a stop).
    var replaceRequest by remember { mutableStateOf<ReplaceRequest?>(null) }

    // ---- Trip-finished state ---------------------------------------------
    val rideStore = remember { RideStore(context) }
    var recordingTurnedOn by remember { mutableStateOf(false) }
    val stopFallback = resources.getString(R.string.trip_stop_unnamed)
    val rideBackLabel = resources.getString(R.string.ride_back_label)
    // The just-finished ride (route + stats) and what the recorder made of it; what the panel covers of the map.
    val finished: TripFinishedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            addInitializer(TripFinishedViewModel::class) {
                TripFinishedViewModel(
                    finish = RideEvents.lastFinish,
                    loadPoints = { id -> withContext(Dispatchers.IO) { rideStore.points(id) } },
                    recordingEnabled = { appSettings.rideRecordingEnabled },
                )
            }
        },
    )
    val finishState by finished.state.collectAsState()
    var finishInsets by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Active navigation session state (drives the NAVIGATING stage / arrival).
    val navState by GoogleNavSdkProvider.state.collectAsState()
    // Why the requested trip couldn't start (route failure / navigator unavailable).
    val startFailure by GoogleNavSdkProvider.startFailure.collectAsState()
    val places = remember { context.appContainer.places }
    val savedPlaces by places.favorites.collectAsState()
    /** A trip is running (even stepped away from), so starting another asks first. */
    fun tripRunning(): Boolean = !tripState.arrivalLatched &&
        ((GoogleNavSdkProvider.activeTrip.value != null && startFailure == null) || navState.sessionState.isActiveNav())

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

    // ---- Search state -----------------------------------------------------
    val auth = remember { PlacesClient.androidAuth(context) }
    // Refreshed whenever search opens and after a fresh fix in the preview, so distances,
    // autocomplete bias and preview bounds don't use a stale launch-time position.
    var origin by remember { mutableStateOf(lastKnownLocation(context)) }
    // The category chip the search opens with (from the browse sheet); null for a plain search.
    var searchCategory by remember { mutableStateOf<PlaceCategory?>(null) }
    // Fetch the preview routes (incl. alternates, or a single multi-leg route when
    // there are stops) when entering PREVIEW or when the stops change.
    LaunchedEffect(stage, selected, stops) {
        if (stage != NavStage.PREVIEW) return@LaunchedEffect
        val p = selected ?: return@LaunchedEffect
        // Record the recent as soon as the rider opens the preview for a place -
        // reaching this screen is a strong signal of intent, even if they don't
        // hit Start. addRecent de-dupes by coords, so re-adding at Start is safe.
        // ("Ride back" destinations are synthetic: they shouldn't litter the recents.)
        if (p.label != rideBackLabel) {
            store.addRecent(p)
        }
        routeVm.load(p, stops)
    }
    LaunchedEffect(routePreview.origin) { routePreview.origin?.let { origin = it } }

    // Reflect the current stage on the map (pin on CONFIRM; pin + routes on PREVIEW).
    val pinStyle = com.navigator.app.ui.map.PreviewPinStyle(
        accent = KtmTheme.accent.fill.toArgb(), onAccent = KtmTheme.accent.onFill.toArgb(),
        surface = KtmTheme.colors.surf.toArgb(), onSurface = KtmTheme.colors.fg.toArgb(), outline = KtmTheme.colors.line2.toArgb(),
    )
    LaunchedEffect(planInsets, stage) {
        // The card measures after the stage arrives: frame again once its size is known (the pin goes to the open map's middle).
        val framed = (stage == NavStage.PREVIEW && stopCandidate == null && !pickingStop) || stage == NavStage.CONFIRM
        if (framed) {
            delay(250)
            if (stage == NavStage.CONFIRM || stage == NavStage.PREVIEW) fitKey++
        }
    }
    LaunchedEffect(stage, selected, mapReady, routePreview.routes, routePreview.selected, stops, pickingStop, fitKey, stopCandidate) {
        if (!mapReady) return@LaunchedEffect
        // The finished screen owns the map (draws the ride track); don't clear it.
        if (stage == NavStage.TRIP_FINISHED) return@LaunchedEffect
        // NOTE: do NOT early-return when `selected` is null (e.g. returning to BROWSE): the
        // user-marker re-add at the end must always run after the clear.
        map.clearOverlays()
        val p = selected
        when (stage) {
            NavStage.CONFIRM, NavStage.PREVIEW -> {
                // Framed in what the sheet leaves open (PL2); picking a stop uses the whole map (the pin is at its middle).
                if (pickingStop) map.setPadding(0, 0, 0, 0) else map.setPadding(planInsets.first, 0, 0, planInsets.second)
            }
            else -> {}
        }
        // The camera is moved only when what is framed changed, not when a spot is proposed or a sheet resizes.
        val frameKey = listOf(stage, selected, routePreview.routes, routePreview.selected, stops, pickingStop, fitKey)
        // While routes load there is nothing to frame (the camera stays where it is); it moves when they arrive.
        val reframe = !pickingStop && frameKey != lastFit[0] && !(stage == NavStage.PREVIEW && routePreview.routes.isEmpty())
        lastFit[0] = frameKey
        when (stage) {
            // Only the camera waits for a change of what is framed: a card that grows must not re-centre a panned map.
            NavStage.CONFIRM -> if (p != null) map.showPlace(MapPoint(p.lat, p.lng), p.label, moveCamera = reframe)
            NavStage.PREVIEW -> if (p != null) {
                map.showTripPreview(
                    TripPreviewScene(
                        destination = MapPoint(p.lat, p.lng),
                        destinationTitle = p.label,
                        stops = stops.mapIndexed { i, st ->
                            MapStop(MapPoint(st.lat, st.lng), resources.getString(R.string.map_stop_marker, i + 1, st.label))
                        },
                        // With stops there are no alternates (single multi-leg route).
                        routes = routePreview.routes.map { r -> r.points.map { MapPoint(it.latitude, it.longitude) } },
                        selectedRoute = routePreview.selected,
                        frameAlso = listOfNotNull(origin?.let { MapPoint(it.first, it.second) }) +
                            stops.map { MapPoint(it.lat, it.lng) },
                        routeLabels = RouteSheetMapper.callouts(routePreview.routes, stops.size),
                        style = pinStyle,
                        pending = stopCandidate?.let { MapPoint(it.lat, it.lng) },
                        reframe = reframe,
                    ),
                )
            }
            else -> {}
        }
        if (showUserMarker) deviceLatLng?.let { map.showUserMarker(MapPoint(it.latitude, it.longitude), deviceHeadingState.value) }
    }
    LaunchedEffect(map) { map.polylineClicks.collect { routeVm.select(it) } }
    // Place labels are tappable only while planning the route: a tap adds that place as a stop (PL6).
    LaunchedEffect(mapReady, stage) { map.setPoiListening(mapReady && stage == NavStage.PREVIEW) }
    DisposableEffect(map) { onDispose { map.setPoiListening(false) } }
    LaunchedEffect(stage) { if (stage != NavStage.PREVIEW) { stopCandidate = null; pickingStop = false } }
    val newStopLabel = resources.getString(R.string.stop_new)
    val droppedPinLabel = resources.getString(R.string.pick_dropped_pin)
    LaunchedEffect(map) {
        map.poiClicks.collect { poi ->
            if (stage == NavStage.PREVIEW && !pickingStop) {
                stopCandidate = SavedPlace(poi.point.lat, poi.point.lng, NewStop.title(poi.name, newStopLabel))
            }
        }
    }

    // Draw the just-finished ride's speed-coloured track on the map behind the
    // summary card, framed above the card. Clears the finished padding on return.
    LaunchedEffect(stage, mapReady, finishState.points, finishInsets) {
        if (!mapReady) return@LaunchedEffect
        when {
            stage == NavStage.TRIP_FINISHED -> {
                val pts = finishState.points
                val covered = finishInsets
                if (!pts.isNullOrEmpty() && covered != null) {
                    map.setPadding(covered.first, 0, 0, covered.second)
                    map.showTrack(pts, speedBands)
                } else if (pts.isNullOrEmpty() && finishState.ui is FinishUi.NoRecording) {
                    // No track to frame: leave the riding camera's tilt and bearing behind.
                    map.resetNorth()
                }
            }
            // The place and route sheets pad the map to what they leave open (above); search and the pin use all of it
            // (the browse sheet pads it itself; NAVIGATING is owned by MapSurface).
            stage == NavStage.SEARCH -> map.setPadding(0, 0, 0, 0)
            else -> {}
        }
    }

    // The search itself (typing, categories, voice, offline) is PlaceSearchRoute's; these only change the stage.
    fun openSearch(initialQuery: String = "", category: PlaceCategory? = null) {
        origin = lastKnownLocation(context) ?: origin
        searchCategory = category
        flow.update { it.openSearch(SearchTarget.DESTINATION).copy(query = initialQuery) }
    }

    /** Open the search panel to add an intermediate stop (returns to PREVIEW). */
    fun openSearchForStop() {
        origin = lastKnownLocation(context) ?: origin
        searchCategory = null
        flow.update { it.openSearch(SearchTarget.STOP) }
    }

    /** Search for a stop while navigating (guidance keeps running underneath). */
    fun openTripStopSearch() {
        // A refusal is announced by the TripController's event, not here.
        if (!trip.canSearchForStop()) return
        origin = lastKnownLocation(context) ?: origin
        searchCategory = null
        // Stepping into search must not be treated as the app taking over / ending the trip.
        trip.setMinimised(true)
        flow.update { it.openSearch(SearchTarget.TRIP_STOP) }
    }

    /** Apply a picked place as the destination, an appended preview stop, or a live-trip stop. */
    fun applyPicked(p: SavedPlace) {
        // A late place lookup (or a pick racing the trip's end) must not act outside the search.
        if (stage != NavStage.SEARCH) return
        val wasTripStop = searchTarget == SearchTarget.TRIP_STOP
        flow.update { it.applyPicked(p) }
        if (wasTripStop) {
            trip.setMinimised(false)
            trip.addStop(p)
        }
    }

    fun choose(p: SavedPlace) = flow.update { it.choose(p) }

    fun backToBrowse() {
        flow.update { it.backToBrowse() }
        routeVm.clear()
        // Keep the "stepped away" flag while a trip is still running so arrival/auto-resume
        // logic still treats this screen as owning it (only reset once nothing is live).
        if (!navState.sessionState.isActiveNav()) trip.setMinimised(false)
        trip.acknowledgeFinish()
    }

    fun startTo(p: SavedPlace) {
        val sel = routePreview.selectedRoute
        // Carry the mode that produced the token so the SDK consumes it with the
        // same mode (else it rejects the token and silently picks its own route).
        val mode = sel?.travelMode ?: appSettings.vehicleMode
        val tripStops = stops.map { com.navigator.app.nav.model.Stop(it.lat, it.lng, it.label) }
        val dest = NavDestination(p.lat, p.lng, p.label, sel?.routeToken, mode, tripStops)
        // False for a double tap: a second start would supersede the first mid-compute.
        if (!trip.start(dest, origin, sel?.points?.map { it.latitude to it.longitude })) return
        if (p.label != rideBackLabel) {
            store.addRecent(p)
        }
        stage = NavStage.NAVIGATING
    }

    fun retryStart() {
        stage = NavStage.NAVIGATING
        trip.retry()
    }

    // Leave the active-navigation view but KEEP the trip running (foreground
    // service + dash guidance continue). A "Resume navigation" pill brings it back.
    fun moveAwayFromNav() {
        trip.setMinimised(true)
        // MO6: the map flattens back to a north-up 2D view as the trip bar becomes the strip.
        map.resetNorth()
        stage = NavStage.BROWSE
    }

    // "Ride back": plan the reverse of the finished trip (start point as destination, stops reversed).
    fun rideBack() {
        val plan = trip.rideBackPlan(finishState.points?.firstOrNull()?.let { it.lat to it.lng }) ?: return
        val tripStops = plan.tripStops.map { SavedPlace(it.lat, it.lng, it.label ?: stopFallback) }
        backToBrowse()
        flow.update { it.rideBack(SavedPlace(plan.origin.first, plan.origin.second, rideBackLabel), tripStops) }
    }

    /** DL3 "Add as a stop instead" and PL1 "Add as stop": the place becomes a stop of the running trip. */
    fun addAsStop(p: SavedPlace) {
        // A refusal is announced by the TripController's event.
        if (trip.addStop(p)) { stops = emptyList(); routeVm.clear(); searchTarget = SearchTarget.DESTINATION; trip.setMinimised(false); stage = NavStage.NAVIGATING }
    }

    fun resumeNav() {
        searchTarget = SearchTarget.DESTINATION
        trip.setMinimised(false)
        stage = NavStage.NAVIGATING
    }

    // The trip ended (arrival or the rider): show "Trip finished" instead of
    // dropping straight back to the map. Keeps `selected` so the screen can name the destination;
    // clears the transient search/preview state and resets the finished-ride state so the screen
    // loads this trip's result. Also adopts a trip that ended while this screen was away.
    fun adoptFinish(f: FinishedTrip) {
        if (stage == NavStage.TRIP_FINISHED) return
        routeVm.clear()
        // The card names the trip that ended, not whatever place the rider was looking at since.
        flow.update { it.copy(selected = f.destination).finishTrip() }
    }
    LaunchedEffect(tripState.finished) { tripState.finished?.let(::adoptFinish) }
    // Once the rider leaves the summary by any route (Done, a new place, another tab) it is dealt with.
    var previousStage by remember { mutableStateOf(stage) }
    // A summary the rider has seen is dealt with when they leave the screen too (a summary that
    // ended while they were away is still waiting for them).
    DisposableEffect(trip) {
        onDispose { if (flow.state.stage == NavStage.TRIP_FINISHED) trip.acknowledgeFinish() }
    }
    LaunchedEffect(stage) {
        if (previousStage == NavStage.TRIP_FINISHED && stage != NavStage.TRIP_FINISHED) trip.acknowledgeFinish()
        previousStage = stage
    }

    // Say why the trip refused or undid something (until these become snackbars / riding chips).
    TripEventFeedback(trip, { flow.stage }, feedback)

    // Keep the stage in sync with the real session:
    //  - resume the NAVIGATING view whenever a trip is live but we're not showing
    //    it (e.g. the activity was recreated, or the user tapped the notification
    //    after minimising) - the stage is local Compose state that would
    //    otherwise fall back to BROWSE;
    //  - show the "Trip finished" screen once the SDK reports arrival.
    // We "own" the trip on this screen while showing guidance OR while stepped
    // away on the map with a trip still running — arrival should finish it either way.
    LaunchedEffect(navState.sessionState) {
        // A trip the rider just replaced or closed (latched) is not resumed by a late frame of its session.
        if (!tripState.arrivalLatched && NavFlowRules.shouldAutoResume(navState.sessionState.isActiveNav(), stage, leftNavManually)) {
            stage = NavStage.NAVIGATING
        }
    }

    // Notification tap while stepped-away resumes the live guidance view.
    LaunchedEffect(openNavRequest) {
        if (openNavRequest && navState.sessionState.isActiveNav()) resumeNav()
    }

    LaunchedEffect(confirmEndRequest) {
        if (!confirmEndRequest) return@LaunchedEffect
        onConfirmEndConsumed()
        if (navState.sessionState.isActiveNav()) { resumeNav(); showEndConfirm = true }
    }

    LaunchedEffect(searchRequest) {
        if (!searchRequest) return@LaunchedEffect
        onSearchRequestConsumed()
        // A running trip keeps its screen: a shortcut must not pull the rider out of guidance.
        if (navState.sessionState.isActiveNav() || stage == NavStage.SEARCH) return@LaunchedEffect
        // From a place card, a preview or the finished card the shortcut still means "search": leave it first.
        if (stage != NavStage.BROWSE) backToBrowse()
        openSearch()
    }

    // The recorder's outcome is resolved while the finished screen is up (FinishResolution, with its 6 s wait).
    LaunchedEffect(stage) {
        if (stage == NavStage.TRIP_FINISHED) finished.begin() else { finished.reset(); finishInsets = null; recordingTurnedOn = false }
    }

    // A failed start while the rider stepped away to the map: bring the failure card up.
    LaunchedEffect(startFailure) {
        if (startFailure != null && stage == NavStage.BROWSE) resumeNav()
    }
    // A Google Maps link shared into the app: resolve it behind a card (the map stays usable), then CONFIRM.
    // App-scoped: a link still resolving when the rider leaves the tab is not lost.
    val linkController = remember { context.appContainer.sharedLink }
    val linkState by linkController.state.collectAsState()
    val linkFailure by linkController.failure.collectAsState()
    // Searching is the answer to a link card too ("Search instead"): leave no card behind it that Back would meet.
    LaunchedEffect(stage) { if (stage == NavStage.SEARCH) linkController.dismiss() }

    // Home / Work chip or launcher shortcut -> straight to the route preview for that place.
    fun rideTo(place: SavedPlace) {
        // Mid-guidance a shortcut must not tear the planning flow out from under the live trip: ask first (DL3).
        if (stage == NavStage.NAVIGATING) {
            replaceRequest = ReplaceRequest(place, cancelFirst = true) {
                backToBrowse()
                flow.update { it.quickRoute(place) }
            }
            return
        }
        backToBrowse()
        flow.update { it.quickRoute(place) }
    }
    // A saved place from the Places tab: its card (no network lookup, it has coordinates), or straight to the route preview.
    LaunchedEffect(pendingPlace) {
        val p = pendingPlace ?: return@LaunchedEffect
        onPendingPlaceConsumed()
        if (pendingDirections) rideTo(p) else choose(p)
    }
    LaunchedEffect(quickRoute) {
        val slot = quickRoute ?: return@LaunchedEffect
        onQuickRouteConsumed()
        val want = if (slot == "WORK") com.navigator.app.nav.destination.FavoriteSlot.WORK
        else com.navigator.app.nav.destination.FavoriteSlot.HOME
        val place = store.favorites().firstOrNull { it.slot == want }?.place
        if (place != null) rideTo(place) else {
            val which = if (want == com.navigator.app.nav.destination.FavoriteSlot.WORK) R.string.navigate_tile_work else R.string.navigate_tile_home
            feedback.snackbar(
                resources.getString(R.string.navigate_slot_unset, resources.getString(which)),
                actionLabel = resources.getString(R.string.navigate_slot_unset_action),
                onAction = onOpenPlaces,
            )
        }
    }
    // Long-press anywhere (except while the SDK owns the map during guidance) drops a pin and
    // opens the confirm card, like Google Maps.
    LaunchedEffect(map) {
        map.longClicks.collect { ll ->
            // Only where the map is the thing being touched; search, the finished card and riding ignore it.
            when (stage) {
                // On the route preview a long press proposes a stop, not a new destination (PL6).
                NavStage.PREVIEW -> if (!pickingStop) stopCandidate = SavedPlace(ll.lat, ll.lng, newStopLabel)
                NavStage.BROWSE, NavStage.CONFIRM -> choose(SavedPlace(ll.lat, ll.lng, droppedPinLabel))
                else -> {}
            }
        }
    }



    // The riding map button: Overview, or Recenter once the rider moved the map (NV8).
    fun applyMapButton(event: MapButtonEvent) {
        val (next, effect) = MapButtonMachine.reduce(mapButton, event)
        mapButton = next
        when (effect) {
            MapButtonEffect.Follow -> map.followMyLocation(FollowMode.TILTED)
            MapButtonEffect.ShowOverview -> map.showRouteOverview()
            MapButtonEffect.None -> {}
        }
    }
    // A question about the place being looked at is void once the stage moves on (the trip finished meanwhile).
    // (A Replace confirmed from CONFIRM calls trip.cancel() and goes to PREVIEW at once, before any stage change.)
    LaunchedEffect(stage) { replaceRequest = null }
    LaunchedEffect(stage) {
        map.setGestureListening(stage == NavStage.NAVIGATING)
        riding.onStageChanged(stage, openAddStop = addStopOnResume)
        addStopOnResume = false
    }
    DisposableEffect(map) { onDispose { map.setGestureListening(false) } }
    val reducedMotion = KtmTheme.motion.reduced
    LaunchedEffect(map, reducedMotion) { map.setReducedMotion(reducedMotion) }
    LaunchedEffect(map) { map.gesturePans.collect { if (stage == NavStage.NAVIGATING) applyMapButton(MapButtonEvent.Pan(System.currentTimeMillis())) } }
    // Checked every second while the map is panned: a steady speed emits no new value to react to.
    LaunchedEffect(mapButton.panned, stage) {
        while (mapButton.panned && stage == NavStage.NAVIGATING) {
            navSpeedKmh?.let { applyMapButton(MapButtonEvent.Speed(it, System.currentTimeMillis())) }
            delay(1_000)
        }
    }
    // Trip progress is measured against the longest the trip has been (a reroute or added stop can lengthen it).
    var progressTrip by remember { mutableStateOf(0L) }
    LaunchedEffect(tripState.startMs, navState.remainingDistanceMeters, navState.sessionState) {
        if (progressTrip != tripState.startMs) { progressTrip = tripState.startMs; initialRemainingM = null }
        val rem = navState.remainingDistanceMeters ?: return@LaunchedEffect
        if (navState.sessionState.isActiveNav() && rem > (initialRemainingM ?: 0)) initialRemainingM = rem
    }
    // The dash is the primary display, so losing it matters: a red chip once, then a green one when it is back.
    val bikeStatus by container.connection.status.collectAsState()
    val dashUp = bikeStatus is BikeStatus.Connected
    var dashWasUp by remember { mutableStateOf<Boolean?>(null) }
    var dashLostShown by remember { mutableStateOf(false) }
    LaunchedEffect(dashUp, stage) {
        if (stage != NavStage.NAVIGATING) { dashWasUp = null; dashLostShown = false; return@LaunchedEffect }
        val was = dashWasUp
        dashWasUp = dashUp
        // Red when it drops, or when a paired bike isn't there as the trip starts or resumes; green only after a red.
        if ((was == true && !dashUp) || (was == null && !dashUp && bikeStatus !is BikeStatus.NotPaired && bikeStatus !is BikeStatus.Connecting && bikeStatus !is BikeStatus.Reconnecting)) {
            dashLostShown = true
            feedback.chip(resources.getString(R.string.riding_chip_dash_lost), ChipTone.Bad, onTap = { showBike = true })
        }
        if (was == false && dashUp && dashLostShown) {
            dashLostShown = false
            feedback.chip(resources.getString(R.string.riding_chip_dash_back), ChipTone.Ok)
        }
    }

    // One back path for the system Back and the on-screen arrows (they must never disagree).
    fun performBack() {
        val outcome = flow.state.back(tripLive = navState.sessionState.isActiveNav())
        flow.update { outcome.state }
        when (outcome.effect) {
            BackEffect.NONE -> {}
            BackEffect.BACK_TO_BROWSE -> backToBrowse()
            BackEffect.MOVE_AWAY_FROM_NAV -> moveAwayFromNav()
            BackEffect.RESUME_NAV -> resumeNav()
            BackEffect.LEAVE -> {}
        }
    }
    // At the map with nothing running there is nothing to step back to: Back is the system's (it leaves the app).
    BackHandler(enabled = stage != NavStage.BROWSE || navState.sessionState.isActiveNav()) { performBack() }
    // Registered after the stage's: picking a stop (or a proposed one) is left first.
    BackHandler(enabled = pickingStop || stopCandidate != null) {
        if (pickingStop) pickingStop = false else stopCandidate = null
    }
    // Registered after the host's own Back handlers, so a card on screen is dismissed before the flow steps back
    // (the browse sheet's and overlays' own handlers are composed later and still go first).
    SharedLinkEffects(linkController, linkState, sharedLink, onSharedLinkConsumed, stage, { flow.stage }, feedback, onResolved = ::choose)
    // The tab bar and strip give way inside the planning flow as well as riding (SE3); the browse screen is the one with tabs.
    com.navigator.app.ui.shell.ImmersiveEffect(stage != NavStage.BROWSE && stage != NavStage.TRIP_FINISHED)

    // Built where the CONFIRM and PREVIEW stages compose, so its state reads (bearing, routes, favourites) subscribe
    // only those stages, not the whole host.
    fun planStage() = PlanStage(
        selected = selected, savedPlaces = savedPlaces, origin = origin, routePreview = routePreview,
        pickingStop = pickingStop, stopCandidate = stopCandidate,
        showResumePill = navState.sessionState.isActiveNav() && leftNavManually,
        mapReadyForControls = available && navReady, mapBearing = mapBearing, map = map, feedback = feedback,
        places = places, scope = scope, tripRunning = ::tripRunning,
        onResume = ::resumeNav, onBack = ::performBack, onClose = ::backToBrowse,
        onInsets = { left, bottom -> planInsets = left to bottom },
        onAskReplace = { replaceRequest = it }, onGoToPreview = { if (stage == NavStage.CONFIRM) stage = NavStage.PREVIEW }, onAddAsStop = { if (stage == NavStage.CONFIRM) addAsStop(it) },
        onPickingStop = { pickingStop = it }, onStopCandidate = { stopCandidate = it },
        onSelectRoute = routeVm::select, onAddStop = { if (stage == NavStage.PREVIEW) openSearchForStop() }, onStart = { if (stage == NavStage.PREVIEW) startTo(it) },
    )

    val motionSpecs = KtmTheme.motion
    Box(modifier = Modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        // --- Map surface -----------------------------------------------------
        when {
            available && navReady -> MapSurface(
                retained = retainedNav,
                controller = map,
                navUiEnabled = stage == NavStage.NAVIGATING,
                guidanceInsets = ridingInsets,
                dark = mapDark,
            )
            // Not ready: the Navigate screen says why and offers Try again; behind it is plain ground.
            else -> Box(Modifier.fillMaxSize().background(KtmTheme.colors.bg))
        }

        // Animated stage chrome (overlays the persistent map above). The map layer
        // stays OUTSIDE this AnimatedContent so it never animates or duplicates.
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = stage,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { stageTransition(initialState, targetState, motionSpecs) },
                label = "navStage",
            ) { s ->
              CompositionLocalProvider(LocalTripShared provides remember(this@SharedTransitionLayout, this) { SharedElements(this@SharedTransitionLayout, this) }) {
              Box(Modifier.fillMaxSize()) {
                when (s) {
                NavStage.BROWSE -> NavigateRoute(
                    map = map,
                    mapState = when {
                        !available -> MapState.Unavailable
                        navReady -> MapState.Ready
                        navError -> MapState.Failed
                        else -> MapState.Preparing
                    },
                    mapBearing = mapBearing,
                    location = LocationState.of(hasLocationPermission(context), locationReady),
                    padMap = stage == NavStage.BROWSE,
                    // (stage guards: the tiles stay tappable during the exit animation)
                    onSearch = { q, category -> if (stage == NavStage.BROWSE) openSearch(q.orEmpty(), category) },
                    onChoose = { if (stage == NavStage.BROWSE) choose(it) },
                    onRide = { if (stage == NavStage.BROWSE) rideTo(it) },
                    onResumeTrip = ::resumeNav,
                    // SE4 from the minimised trip's card: back to the riding screen with the add-a-stop sheet open.
                    onAddStop = { if (stage == NavStage.BROWSE) { addStopOnResume = true; resumeNav() } },
                    onOpenPairing = onOpenConnect,
                    onOpenPlaces = onOpenPlaces,
                    onRetryMap = { navReady = false; navError = false; prepareAttempt++ },
                    recordControls = { RecordRidePill() },
                )

                NavStage.SEARCH -> PlaceSearchRoute(
                    mode = when (searchTarget) {
                        SearchTarget.DESTINATION -> SearchMode.Destination
                        SearchTarget.STOP -> SearchMode.Stop
                        SearchTarget.TRIP_STOP -> SearchMode.TripStop
                    },
                    initialQuery = flow.query,
                    initialCategory = searchCategory,
                    onQueryChanged = { flow.query = it },
                    onPicked = ::applyPicked,
                    onBack = ::performBack,
                    routeAhead = {
                        // Along the route when adding a stop to a live trip or to the previewed trip; else around you.
                        when (searchTarget) {
                            SearchTarget.TRIP_STOP -> trip.state.value.let { it.route ?: it.routeHint }?.let { r ->
                                // Only what is still ahead of the rider.
                                (origin ?: lastKnownLocation(context))?.let { com.navigator.app.nav.destination.PolylineCodec.aheadOf(r, it.first, it.second) } ?: r
                            }
                            SearchTarget.STOP -> routePreview.selectedRoute?.points?.map { it.latitude to it.longitude }
                            SearchTarget.DESTINATION -> null
                        }
                    },
                    activity = if (navState.sessionState.isActiveNav() && leftNavManually && searchTarget != SearchTarget.TRIP_STOP) {
                        ActivityCard.build(tripState, navState, System.currentTimeMillis())
                    } else null,
                    onResumeTrip = ::resumeNav,
                    onPickOnMap = if (searchTarget == SearchTarget.STOP) {
                        { flow.update { it.copy(searchTarget = SearchTarget.DESTINATION, stage = NavStage.PREVIEW) }; pickingStop = true }
                    } else null,
                )

                NavStage.CONFIRM -> ConfirmStage(planStage())

                NavStage.PREVIEW -> PreviewStage(planStage(), flow)

                NavStage.NAVIGATING -> NavigatingStage(
                    riding = riding, ridingDark = ridingDark, settingsSnap = settingsSnap, startFailure = startFailure,
                    tripState = tripState, navState = navState, selected = selected, navSpeedKmh = navSpeedKmh,
                    initialRemainingM = initialRemainingM, mapBearing = mapBearing, isOnline = isOnline,
                    rerouteOfflinePaused = rerouteOfflinePaused, bikeStatus = bikeStatus, trip = trip,
                    settings = container.settings, map = map, feedback = feedback,
                    actions = NavigatingStageActions(
                        onRidingInsets = { ridingInsets = it }, onApplyMapButton = ::applyMapButton,
                        onRetryStart = ::retryStart, onBackToBrowse = ::backToBrowse, onBackToPreview = { stage = NavStage.PREVIEW },
                        onMoveAway = ::moveAwayFromNav, onOpenTripStopSearch = ::openTripStopSearch, onOpenConnect = onOpenConnect,
                    ),
                )

                NavStage.TRIP_FINISHED -> TripFinishedStage(
                    selected = selected, finishState = finishState, tripState = tripState, appSettings = appSettings,
                    recordingTurnedOn = recordingTurnedOn, store = store, rideStore = rideStore, scope = scope,
                    onFinishInsets = { left, bottom -> finishInsets = left to bottom }, onDone = ::backToBrowse,
                    onRideBack = ::rideBack, onRecordingTurnedOn = { recordingTurnedOn = true },
                    onOpenReplay = onOpenReplay, onOpenRecordingSettings = onOpenRecordingSettings,
                )
                }
              }
              }
            }
        }

        replaceRequest?.let { r ->
            val dialog: @Composable () -> Unit = {
                ReplaceTripDialog(
                    destination = r.place.label,
                    riding = stage == NavStage.NAVIGATING,
                    onReplace = {
                        replaceRequest = null
                        if (r.cancelFirst) { trip.cancel(); trip.resetStartDebounce() }
                        r.proceed()
                    },
                    onKeep = { replaceRequest = null },
                    onAddAsStop = { replaceRequest = null; addAsStop(r.place) },
                )
            }
            // Over the riding screen it follows the riding Day/Night, like the end-trip confirm.
            if (stage == NavStage.NAVIGATING) {
                RidingTheme(night = ridingDark, accent = accentFor(settingsSnap.accentColorId), controlSystemBars = false, content = dialog)
            } else dialog()
        }

        if (showEndConfirm) {
            RidingTheme(night = ridingDark, accent = accentFor(settingsSnap.accentColorId), controlSystemBars = false) {
                EndTripDialog(
                    onEnd = { showEndConfirm = false; trip.end() },
                    onKeep = { showEndConfirm = false },
                )
            }
        }

        if (stage != NavStage.NAVIGATING) SharedLinkCard(
            linkState,
            SharedLinkActions(
                onRetry = linkController::retry,
                onSearchInstead = { linkController.dismiss(); openSearch() },
                onDismiss = linkController::dismiss,
            ),
            Modifier.align(Alignment.BottomCenter),
            failure = linkFailure,
        )
    }
}
