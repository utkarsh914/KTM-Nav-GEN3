package com.navigator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.navigator.app.ble.BccuConnectionService
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
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.ui.components.CircleBackButton
import com.navigator.app.ui.components.ConnectPill
import com.navigator.app.ui.components.Eyebrow
import com.navigator.app.ui.components.IconPill
import com.navigator.app.ui.components.KtmPrimaryButton
import com.navigator.app.ui.components.TurnIconGlyph
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs

private enum class NavStage { BROWSE, SEARCH, CONFIRM, PREVIEW, NAVIGATING, TRIP_FINISHED }

/** Auto-finish the trip once we're within this many metres of the destination. */
private const val ARRIVAL_RADIUS_M = 10

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
    sharedLink: String? = null,
    onSharedLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

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
    var stage by remember { mutableStateOf(NavStage.BROWSE) }
    var selected by remember { mutableStateOf<SavedPlace?>(null) }
    var recents by remember { mutableStateOf(store.recents()) }
    var favorites by remember { mutableStateOf(store.favorites()) }

    // Live GPS speed (km/h) for the guidance speedometer; null when no recent fix.
    // Sourced independently of the BLE service so it works during standalone nav.
    val navSpeedKmh = rememberNavSpeedKmh(active = stage == NavStage.NAVIGATING)

    // ---- Preview state ----------------------------------------------------
    var previewRoutes by remember { mutableStateOf<List<RoutePreview>>(emptyList()) }
    var selectedRoute by remember { mutableIntStateOf(0) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }
    var resolvingLink by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Active navigation session state (drives the NAVIGATING stage / arrival).
    val navState by GoogleNavSdkProvider.state.collectAsState()

    // One-shot arrival latch for the current trip. Set the moment we finish (via
    // proximity or the SDK's arrival callback) and reset only when a new trip
    // starts, so continued movement past the destination can't re-run the
    // proximity auto-finish or otherwise re-trigger arrival handling.
    var arrivalLatched by remember { mutableStateOf(false) }

    // ---- Search state -----------------------------------------------------
    val auth = remember { PlacesClient.androidAuth(context) }
    val origin = remember { lastLocation(context) }
    var sessionToken by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3) { results = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        results = PlacesClient.autocomplete(
            input = q, sessionToken = sessionToken, auth = auth,
            originLat = origin?.first, originLng = origin?.second,
        )
        loading = false
    }

    // Fetch the preview routes (incl. alternates) when entering PREVIEW.
    LaunchedEffect(stage, selected) {
        if (stage != NavStage.PREVIEW) return@LaunchedEffect
        val p = selected ?: return@LaunchedEffect
        // Record the recent as soon as the rider opens the preview for a place -
        // reaching this screen is a strong signal of intent, even if they don't
        // hit Start. addRecent de-dupes by coords, so re-adding at Start is safe.
        store.addRecent(p)
        recents = store.recents()
        previewRoutes = emptyList(); selectedRoute = 0; previewFailed = false
        // Compute from a FRESH fix so route-token origins match the SDK's GPS at
        // Start (a stale origin makes the SDK snap to the nearest/fastest route).
        previewLoading = true
        val o = (withTimeoutOrNull(4000) { freshLocation(context) }) ?: origin ?: lastLocation(context)
        if (o == null) { previewLoading = false; previewFailed = true; return@LaunchedEffect }
        val routes = RoutesClient.computeRoutes(o, p.lat to p.lng, auth)
        previewLoading = false
        previewRoutes = routes
        previewFailed = routes.isEmpty()
    }

    // Reflect the current stage on the map (pin on CONFIRM; pin + routes on PREVIEW).
    LaunchedEffect(stage, selected, googleMap, previewRoutes, selectedRoute) {
        val gm = googleMap ?: return@LaunchedEffect
        gm.setOnPolylineClickListener(null)
        gm.clear()
        val p = selected ?: return@LaunchedEffect
        val ll = LatLng(p.lat, p.lng)
        when (stage) {
            NavStage.CONFIRM -> {
                gm.addMarker(MarkerOptions().position(ll).title(p.label))
                gm.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15f))
            }
            NavStage.PREVIEW -> {
                gm.addMarker(MarkerOptions().position(ll).title(p.label))
                if (previewRoutes.isNotEmpty()) {
                    // Alternates first (gray), then the selected route (blue, on top).
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
                        runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120)) }
                    }
                    gm.setOnPolylineClickListener { poly -> (poly.tag as? Int)?.let { selectedRoute = it } }
                } else {
                    gm.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 14f))
                }
            }
            else -> {}
        }
    }

    fun openSearch() {
        sessionToken = UUID.randomUUID().toString()
        query = ""; results = emptyList()
        recents = store.recents(); favorites = store.favorites()
        stage = NavStage.SEARCH
    }

    fun choose(p: SavedPlace) { selected = p; stage = NavStage.CONFIRM }

    fun backToBrowse() {
        stage = NavStage.BROWSE; selected = null; query = ""
        previewRoutes = emptyList(); selectedRoute = 0; previewFailed = false; previewLoading = false
    }

    fun startTo(p: SavedPlace) {
        store.addRecent(p)
        recents = store.recents()
        val token = previewRoutes.getOrNull(selectedRoute)?.routeToken
        onStartNavigation(NavDestination(p.lat, p.lng, p.label, token))
        arrivalLatched = false
        stage = NavStage.NAVIGATING
    }

    fun stopNav() {
        GoogleNavSdkController.stop()
        backToBrowse()
    }

    // End the trip and show the "Trip finished" confirmation instead of dropping
    // straight back to the map. Keeps `selected` so the screen can name the
    // destination; clears the transient search/preview state.
    fun finishTrip() {
        arrivalLatched = true
        GoogleNavSdkController.stop()
        query = ""
        previewRoutes = emptyList(); selectedRoute = 0; previewFailed = false; previewLoading = false
        stage = NavStage.TRIP_FINISHED
    }

    // Keep the stage in sync with the real session:
    //  - resume the NAVIGATING view whenever a trip is live but we're not showing
    //    it (e.g. the activity was recreated, or the user tapped the notification
    //    after minimising) - the stage is local Compose state that would
    //    otherwise fall back to BROWSE;
    //  - show the "Trip finished" screen once the SDK reports arrival.
    LaunchedEffect(navState.sessionState) {
        if (navState.sessionState.isActiveNav() &&
            stage != NavStage.NAVIGATING &&
            // Don't drag the user back into navigation from the completion screen:
            // late/continued ENROUTE frames after arrival must not resume the trip.
            stage != NavStage.TRIP_FINISHED
        ) {
            stage = NavStage.NAVIGATING
        } else if (stage == NavStage.NAVIGATING && navState.sessionState == NavSessionState.ARRIVED) {
            finishTrip()
        }
    }

    // Proximity auto-finish: end guidance and show "Trip finished" as soon as we
    // come within ARRIVAL_RADIUS_M of the destination, rather than waiting for the
    // SDK's own (sometimes later) arrival callback. remainingDistanceMeters is the
    // SDK's distance to the final destination, updated ~1 Hz while en route.
    LaunchedEffect(navState.remainingDistanceMeters, stage) {
        val remaining = navState.remainingDistanceMeters
        if (stage == NavStage.NAVIGATING &&
            !arrivalLatched &&
            navState.sessionState.isActiveNav() &&
            remaining != null && remaining <= ARRIVAL_RADIUS_M
        ) {
            finishTrip()
        }
    }

    // A Google Maps link shared into the app -> resolve it and jump to CONFIRM.
    LaunchedEffect(sharedLink) {
        val link = sharedLink ?: return@LaunchedEffect
        resolvingLink = true
        val dest = com.navigator.app.nav.destination.MapsUrlResolver.resolve(link, auth)
        resolvingLink = false
        onSharedLinkConsumed()
        if (dest != null) choose(SavedPlace(dest.lat, dest.lng, dest.label ?: "Shared location"))
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



    BackHandler {
        when (stage) {
            NavStage.PREVIEW -> stage = NavStage.CONFIRM
            NavStage.CONFIRM -> { stage = NavStage.SEARCH; selected = null }
            NavStage.NAVIGATING -> stopNav()
            NavStage.TRIP_FINISHED -> backToBrowse()
            NavStage.SEARCH -> backToBrowse()
            NavStage.BROWSE -> showExitConfirm = true // Back at home -> confirm full close
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        // --- Map surface -----------------------------------------------------
        when {
            available && navReady -> BrowseMap(
                retainedNav = retainedNav,
                navUiEnabled = stage == NavStage.NAVIGATING,
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

        when (stage) {
            NavStage.BROWSE -> {
                // Search bar (leaves room on the right for the control column).
                Box(
                    modifier = Modifier.fillMaxWidth().systemBarsPadding()
                        .padding(start = 16.dp, end = 74.dp, top = 10.dp),
                ) {
                    SearchBar(modifier = Modifier.fillMaxWidth(), onClick = ::openSearch)
                }
                // Top-right control column: Settings, then Compass (only when rotated).
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding()
                        .padding(end = 16.dp, top = 10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconPill(OpenDashIcons.Settings, "Settings", onClick = onOpenSettings)
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        CompassButton(bearing = mapBearing) { resetBearing(googleMap) }
                    }
                }
                // Bottom-left recenter.
                Box(Modifier.align(Alignment.BottomStart).systemBarsPadding().padding(16.dp)) {
                    if (available && navReady) {
                        IconPill(OpenDashIcons.LocateFixed, "Recenter") { recenter(context, googleMap) }
                    }
                }
                // Bottom-right connect pill.
                Box(Modifier.align(Alignment.BottomEnd).systemBarsPadding().padding(16.dp)) {
                    ConnectPill(onClick = onOpenConnect)
                }
            }

            NavStage.SEARCH -> SearchPanel(
                query = query,
                onQuery = { query = it },
                loading = loading || resolving,
                results = results,
                recents = recents,
                favorites = favorites,
                origin = origin,
                onBack = ::backToBrowse,
                onPickSuggestion = { s ->
                    if (!resolving) {
                        resolving = true
                        scope.launch {
                            val loc = PlacesClient.details(s.placeId, sessionToken, auth)
                            resolving = false
                            if (loc != null) choose(SavedPlace(loc.lat, loc.lng, s.primary, s.secondary))
                        }
                    }
                },
                onPickSaved = { choose(it) },
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
                    modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding()
                        .padding(end = 16.dp, top = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconPill(OpenDashIcons.Close, "Clear", onClick = ::backToBrowse)
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        CompassButton(bearing = mapBearing) { resetBearing(googleMap) }
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
                        onSelect = { selectedRoute = it },
                        onStart = { selected?.let(::startTo) },
                        onBack = { stage = NavStage.CONFIRM },
                    )
                }
                // Top-right control column: Clear, then Compass (only when rotated).
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding()
                        .padding(end = 16.dp, top = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconPill(OpenDashIcons.Close, "Clear", onClick = ::backToBrowse)
                    if (available && navReady && abs(mapBearing) > 0.5f) {
                        CompassButton(bearing = mapBearing) { resetBearing(googleMap) }
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
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NavGuidanceHeader(
                        nav = navState,
                        // Safeguard: while lanes show, drop the "Then" chip so the
                        // top cluster can't grow tall enough to crowd other elements.
                        hideNextHint = showLanes,
                    )
                    if (showLanes) {
                        Spacer(Modifier.height(8.dp))
                        LaneGuidance(lanes = navState.lanes)
                    }
                }

                // Mid-left: speedometer (hidden until we have a GPS fix).
                navSpeedKmh?.let { kmh ->
                    Speedometer(
                        kmh = kmh,
                        modifier = Modifier.align(Alignment.CenterStart).systemBarsPadding()
                            .padding(start = 16.dp),
                    )
                }

                // Bottom ETA bar (full width, anchored bottom-centre).
                NavGuidanceBottomBar(
                    nav = navState,
                    onEnd = ::stopNav,
                    modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
                // Compass at bottom-right (only when the map is rotated). The
                // re-center button is the SDK's own (kept in BrowseMap), which pops
                // up on pan at the bottom-left, above our ETA bar (via map padding).
                if (available && navReady && abs(mapBearing) > 0.5f) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).systemBarsPadding()
                            .padding(end = 16.dp, bottom = 104.dp),
                    ) {
                        CompassButton(bearing = mapBearing, cornerRadius = Ktm.RadiusCard) {
                            resetBearing(googleMap)
                        }
                    }
                }
            }

            NavStage.TRIP_FINISHED -> TripFinishedScreen(
                destinationLabel = selected?.label,
                onBack = ::backToBrowse,
            )
        }

        if (showExitConfirm) {
            ExitConfirmDialog(
                onConfirm = { showExitConfirm = false; onExit() },
                onDismiss = { showExitConfirm = false },
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
            gm.uiSettings.isMyLocationButtonEnabled = false // we draw our own recenter
            gm.uiSettings.isCompassEnabled = false          // custom controls instead
            if (hasLocationPermission(context)) {
                runCatching { gm.isMyLocationEnabled = true }
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
    // Enable the SDK's guidance engine while navigating (route line + follow
    // camera + turn-by-turn feed) and keep its built-in RE-CENTER button (it pops
    // up on pan and reliably re-engages follow), but suppress the rest of the
    // chrome — we render our own header, ETA bar and (no) report. Force the map's
    // day/night from the app theme (the SDK otherwise follows the system).
    LaunchedEffect(navUiEnabled, dark, mapRef) {
        runCatching {
            mapRef?.mapColorScheme = if (dark) MapColorScheme.DARK else MapColorScheme.LIGHT
        }
        runCatching {
            navView.setNavigationUiEnabled(navUiEnabled)
            navView.setForceNightMode(
                if (dark) ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY,
            )
        }
        // Hide the stock guidance chrome we replace, but KEEP the re-center button.
        // Wrapped individually so an API mismatch degrades gracefully.
        runCatching { navView.setHeaderEnabled(false) }
        runCatching { navView.setEtaCardEnabled(false) }
        runCatching { navView.setRecenterButtonEnabled(true) }
        runCatching { navView.setReportIncidentButtonEnabled(false) }
        runCatching { navView.setSpeedometerEnabled(false) }
        runCatching { navView.setTrafficIncidentCardsEnabled(false) }
        // Lift the SDK re-center button above our custom ETA bar while navigating.
        runCatching { mapRef?.setPadding(0, 0, 0, if (navUiEnabled) navBottomPadPx else 0) }
    }
    DisposableEffect(Unit) { onDispose { onMap(null) } }
}

/** Full-screen confirmation shown once a trip auto-finishes on arrival. A back
 *  button (top-left) returns to the browse map. */
@Composable
private fun TripFinishedScreen(destinationLabel: String?, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        Box(
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding()
                .padding(start = 16.dp, top = 10.dp),
        ) {
            CircleBackButton(onClick = onBack)
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Ktm.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    OpenDashIcons.Check, contentDescription = null, tint = Ktm.OnAccent,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Eyebrow("Trip finished", fontSize = 13, letterSpacing = 2.0)
            Spacer(Modifier.height(8.dp))
            Text(
                "Destination reached",
                color = Ktm.TextPrimary,
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (!destinationLabel.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    destinationLabel,
                    color = Ktm.Muted2,
                    fontFamily = Barlow,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(28.dp))
            KtmPrimaryButton(
                text = "Done",
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
            )
        }
    }
}

/** Google-style dark teal-green used for the custom maneuver header. */
private val NAV_GREEN = Color(0xFF0E6E5B)
private val NAV_GREEN_DIM = Color(0xFF0A5648)

/** Show lane guidance only within this distance of the maneuver (like Google). */
private const val LANE_HINT_DISTANCE_M = 400

/**
 * Custom top maneuver header replacing the SDK's green banner. Renders below the
 * status bar (caller applies systemBarsPadding). Shows the current turn icon,
 * distance-to-maneuver, the instruction/road, an optional exit-number chip, and a
 * compact "Then <icon>" hint for the following step. During REROUTING it shows a
 * "Rerouting…" label with the retained maneuver dimmed. Corner radius matches the
 * other nav controls (RadiusCard).
 *
 * @param hideNextHint suppress the "Then" chip (used while the lane row is shown,
 *   so the top cluster can't grow tall enough to crowd other elements).
 */
@Composable
private fun NavGuidanceHeader(
    nav: NormalizedNavigationState,
    hideNextHint: Boolean = false,
) {
    val rerouting = nav.sessionState == NavSessionState.REROUTING
    val contentAlpha = if (rerouting) 0.45f else 1f
    val icon = KtmManeuverMapping.toTurnIcon(nav.maneuver, nav.roundaboutRotation, nav.drivingSide)
    val distance = nav.distanceToManeuverMeters?.let { DistanceFormatter.format(it, nav.units) }
    // Prefer the SDK's full instruction; fall back to the plain road name.
    val instruction = nav.fullInstruction?.takeIf { it.isNotBlank() } ?: nav.roadName
    val exitLabel = when {
        !nav.exitNumber.isNullOrBlank() -> "Exit ${nav.exitNumber}"
        nav.roundaboutExit != null -> "Exit ${nav.roundaboutExit}"
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Ktm.RadiusCard))
                .background(NAV_GREEN)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TurnIconRef(icon, size = 44.dp, color = Color.White.copy(alpha = contentAlpha))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (rerouting) {
                    Text(
                        "Rerouting…", color = Color.White, fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (distance != null) {
                            Text(
                                distance, color = Color.White, fontFamily = BarlowCondensed,
                                fontWeight = FontWeight.Bold, fontSize = 24.sp,
                            )
                        }
                        if (exitLabel != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                exitLabel,
                                color = NAV_GREEN,
                                fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (!instruction.isNullOrBlank()) {
                    Text(
                        instruction,
                        color = Color.White.copy(alpha = 0.92f * contentAlpha), fontFamily = Barlow,
                        fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val next = nav.nextManeuver
        if (!hideNextHint && !rerouting && next != null && next != NormalizedManeuver.UNKNOWN) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(bottomStart = Ktm.RadiusCard, bottomEnd = Ktm.RadiusCard))
                    .background(NAV_GREEN_DIM)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Then", color = Color.White.copy(alpha = 0.9f), fontFamily = Barlow,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(10.dp))
                TurnIconRef(KtmManeuverMapping.toTurnIcon(next), size = 26.dp, color = Color.White)
            }
        }
    }
}

/**
 * Lane guidance strip shown under the header near a maneuver. Recommended lanes
 * (those that lead to the upcoming turn) are drawn bright; the rest are dimmed.
 */
@Composable
private fun LaneGuidance(lanes: List<LaneInfo>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lanes.forEach { lane ->
            val tint = if (lane.recommended) Ktm.White else Ktm.Dim
            // A lane may allow several directions; show each as a small arrow.
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                val shapes = lane.directions.ifEmpty { listOf(LaneShape.STRAIGHT) }
                shapes.forEach { shape ->
                    TurnIconGlyph(laneShapeIcon(shape), size = 22.dp, color = tint)
                }
            }
        }
    }
}

/** Map a [LaneShape] to the closest dash [TurnIcon] arrow glyph. */
private fun laneShapeIcon(shape: LaneShape): TurnIcon = when (shape) {
    LaneShape.STRAIGHT -> TurnIcon.GO_STRAIGHT
    LaneShape.SLIGHT_LEFT -> TurnIcon.LIGHT_LEFT
    LaneShape.LEFT -> TurnIcon.QUITE_LEFT
    LaneShape.SHARP_LEFT -> TurnIcon.HEAVY_LEFT
    LaneShape.UTURN_LEFT -> TurnIcon.UTURN_LEFT
    LaneShape.SLIGHT_RIGHT -> TurnIcon.LIGHT_RIGHT
    LaneShape.RIGHT -> TurnIcon.QUITE_RIGHT
    LaneShape.SHARP_RIGHT -> TurnIcon.HEAVY_RIGHT
    LaneShape.UTURN_RIGHT -> TurnIcon.UTURN_RIGHT
    LaneShape.UNKNOWN -> TurnIcon.GO_STRAIGHT
}

/**
 * Live GPS speed (km/h) while [active], from a self-contained GPS listener. Works
 * independently of the BLE overspeed service (which only runs when connected), so
 * the guidance speedometer is available during standalone navigation too. Returns
 * null until the first fix with a speed arrives.
 */
@Composable
private fun rememberNavSpeedKmh(active: Boolean): Float? {
    val context = LocalContext.current
    var speed by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(active) {
        if (!active || !hasLocationPermission(context)) {
            speed = null
            return@DisposableEffect onDispose { }
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        // Full LocationListener impl (not a SAM lambda): pre-API-30 the extra
        // callbacks are abstract, so a lambda would AbstractMethodError at runtime.
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (loc.hasSpeed()) speed = loc.speed * 3.6f
            }
            @Deprecated("Deprecated in API 29 but still abstract on older devices")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { speed = null }
        }
        runCatching {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }
        onDispose {
            runCatching { lm?.removeUpdates(listener) }
            speed = null
        }
    }
    return speed
}

/** Small speedometer pill: current speed in km/h. */
@Composable
private fun Speedometer(kmh: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            kmh.toInt().coerceAtLeast(0).toString(),
            color = Ktm.TextPrimary, fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold, fontSize = 28.sp,
        )
        Text(
            "km/h", color = Ktm.Muted2, fontFamily = Barlow, fontSize = 12.sp,
        )
    }
}

/**
 * Custom bottom guidance bar replacing the SDK's ETA card. The END (X) button
 * sits inside the bar on the left; remaining time / distance / arrival are
 * left-aligned beside it. Corner radius matches the header and controls.
 */
@Composable
private fun NavGuidanceBottomBar(
    nav: NormalizedNavigationState,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingTime = nav.remainingTimeSeconds
    val duration = remainingTime?.let { formatDuration(it) }
    val eta = remainingTime?.let { EtaFormatter.format(it, System.currentTimeMillis()) }
    val remDist = nav.remainingDistanceMeters?.let { DistanceFormatter.format(it, nav.units) }
    val sub = listOfNotNull(remDist, eta).joinToString("  ·  ")
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(OpenDashIcons.Close, "End navigation", onClick = onEnd)
        // Centered info: the END button on the left is balanced by an equal-width
        // spacer on the right so the text is centered within the whole bar.
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                duration ?: "On the way",
                color = Ktm.TextPrimary, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 27.sp,
            )
            if (sub.isNotBlank()) {
                Text(
                    sub, color = Ktm.TextSecondary, fontFamily = BarlowCondensed,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                )
            }
        }
        Spacer(Modifier.width(Ktm.ControlHeight)) // balances the 52dp END button
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

// ============================ Search ===================================

@Composable
private fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    loading: Boolean,
    results: List<PlaceSuggestion>,
    recents: List<SavedPlace>,
    favorites: List<FavoritePlace>,
    origin: Pair<Double, Double>?,
    onBack: () -> Unit,
    onPickSuggestion: (PlaceSuggestion) -> Unit,
    onPickSaved: (SavedPlace) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize().background(Ktm.Screen)
            .systemBarsPadding().padding(horizontal = 16.dp).padding(top = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OpenDashIcons.ChevronLeft, "Back", tint = Ktm.TextSecondary,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Enter Destination", color = Ktm.Dim) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Ktm.Surface,
                    unfocusedContainerColor = Ktm.Surface,
                    focusedTextColor = Ktm.White,
                    unfocusedTextColor = Ktm.White,
                    cursorColor = Ktm.Orange,
                    focusedIndicatorColor = Ktm.Orange,
                    unfocusedIndicatorColor = Ktm.Border,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))

        val showResults = query.trim().length >= 3
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(
                    color = Ktm.Orange,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                showResults && results.isEmpty() -> Text(
                    "No matches", color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                showResults -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.placeId }) { s ->
                        SuggestionRow(s) { onPickSuggestion(s) }
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val homes = favorites.filter { it.slot == FavoriteSlot.HOME }
                    val works = favorites.filter { it.slot == FavoriteSlot.WORK }
                    val saved = favorites.filter { it.slot == FavoriteSlot.OTHER }
                    val home = homes.firstOrNull()
                    val work = works.firstOrNull()
                    if (home != null || work != null) {
                        item { SectionLabel("FAVORITES") }
                        if (home != null && work != null) {
                            // Both set: Home and Work share one row, side by side.
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FavoriteHalfCard(
                                        icon = OpenDashIcons.House, title = "Home", subtitle = home.place.label,
                                        modifier = Modifier.weight(1f),
                                    ) { onPickSaved(home.place) }
                                    FavoriteHalfCard(
                                        icon = OpenDashIcons.Briefcase, title = "Work", subtitle = work.place.label,
                                        modifier = Modifier.weight(1f),
                                    ) { onPickSaved(work.place) }
                                }
                            }
                        } else {
                            // Only one set: keep the full-width row.
                            val only = home ?: work!!
                            item {
                                SavedRow(
                                    icon = if (only.slot == FavoriteSlot.HOME) OpenDashIcons.House else OpenDashIcons.Briefcase,
                                    title = if (only.slot == FavoriteSlot.HOME) "Home" else "Work",
                                    subtitle = only.place.label,
                                    origin = origin, lat = only.place.lat, lng = only.place.lng,
                                ) { onPickSaved(only.place) }
                            }
                        }
                    }
                    if (saved.isNotEmpty()) {
                        item { SectionLabel("SAVED") }
                        items(saved, key = { "s-${it.place.lat},${it.place.lng}" }) { f ->
                            SavedRow(
                                icon = OpenDashIcons.Bookmark, title = f.place.label,
                                subtitle = f.place.address, origin = origin,
                                lat = f.place.lat, lng = f.place.lng,
                            ) { onPickSaved(f.place) }
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item { SectionLabel("RECENT") }
                        items(recents, key = { "r-${it.lat},${it.lng}" }) { p ->
                            SavedRow(
                                icon = OpenDashIcons.Clock, title = p.label, subtitle = p.address,
                                origin = origin, lat = p.lat, lng = p.lng,
                            ) { onPickSaved(p) }
                        }
                    }
                    if (recents.isEmpty() && favorites.isEmpty()) {
                        item {
                            Text(
                                "Search for a place to get started.",
                                color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SuggestionRow(s: PlaceSuggestion, onClick: () -> Unit) {
    RowCard(onClick) {
        Column(Modifier.weight(1f)) {
            Text(s.primary, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            s.secondary?.let { Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp) }
        }
        s.distanceMeters?.takeIf { it > 0 }?.let {
            Spacer(Modifier.size(10.dp))
            Text(
                "~" + DistanceFormatter.format(it, DistanceUnits.METRIC),
                color = Ktm.Orange, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun SavedRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    origin: Pair<Double, Double>?,
    lat: Double,
    lng: Double,
    onClick: () -> Unit,
) {
    RowCard(onClick) {
        Icon(icon, null, tint = Ktm.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            subtitle?.let { Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp) }
        }
        distanceLabel(origin, lat, lng)?.let {
            Spacer(Modifier.size(10.dp))
            Text(it, color = Ktm.Orange, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Compact half-width favorite card used when both Home and Work are set. */
@Composable
private fun FavoriteHalfCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(icon, null, tint = Ktm.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        subtitle?.let {
            Text(
                it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowCard(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ============================ Preview ==================================

@Composable
private fun PreviewCard(
    place: SavedPlace?,
    loading: Boolean,
    routes: List<RoutePreview>,
    selectedIndex: Int,
    failed: Boolean,
    onSelect: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    place ?: return
    Column(
        modifier = Modifier.fillMaxWidth().systemBarsPadding()
            .padding(16.dp)
            .clip(RoundedCornerShape(Ktm.RadiusCard))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusCard))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OpenDashIcons.ChevronLeft, "Back", tint = Ktm.TextSecondary,
                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
            )
            Spacer(Modifier.size(6.dp))
            Text(place.label, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    routes.forEachIndexed { i, r ->
                        RouteChip(
                            durationSeconds = r.durationSeconds,
                            distanceMeters = r.distanceMeters,
                            label = if (i == 0) "Fastest" else "Alt ${i}",
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

/** A selectable route option (duration + distance), highlighted when selected. */
@Composable
private fun RouteChip(
    durationSeconds: Int,
    distanceMeters: Int,
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
            .clickable(onClick = onClick)
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

/** A neutral dark circular icon button (Google-style controls). */
@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape)
            .background(Ktm.Surface)
            .border(2.dp, Ktm.BorderSoft, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Ktm.White, modifier = Modifier.size(22.dp))
    }
}

/** Compass button: the north needle rotates with the map bearing; tap resets.
 *  [cornerRadius] lets the guidance chrome match its 16dp bars. */
@Composable
private fun CompassButton(
    bearing: Float,
    cornerRadius: androidx.compose.ui.unit.Dp = Ktm.RadiusButton,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(Ktm.ControlHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            OpenDashIcons.Compass, "Reset orientation to north", tint = Ktm.Danger,
            modifier = Modifier.size(22.dp).rotate(-bearing),
        )
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
        modifier = Modifier.fillMaxWidth().systemBarsPadding()
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
private fun formatDuration(seconds: Int): String {
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
private fun distanceLabel(origin: Pair<Double, Double>?, lat: Double, lng: Double): String? {
    origin ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(origin.first, origin.second, lat, lng, out)
    val meters = out[0].toInt()
    if (meters <= 0) return null
    return "~" + DistanceFormatter.format(meters, DistanceUnits.METRIC)
}

private fun hasLocationPermission(context: Context): Boolean =
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastLocation(context)
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
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        null
    } catch (e: SecurityException) {
        null
    }
}
