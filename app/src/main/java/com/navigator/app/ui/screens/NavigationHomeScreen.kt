package com.navigator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.ui.components.KtmPrimaryButton
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

private enum class NavStage { BROWSE, SEARCH, CONFIRM, PREVIEW, NAVIGATING }

/** Route preview polyline colors (ARGB). */
private val ROUTE_SELECTED = 0xFF1A73E8.toInt() // Google route blue
private val ROUTE_ALT = 0xFF7C93B0.toInt()      // desaturated blue alternate

/** Top inset so the END button clears the SDK's maneuver header during guidance. */
private val NAV_HEADER_CLEARANCE = 108.dp

/**
 * Phone-first map home (UX revamp P1–P2 — see docs/NAVIGATION_UX_REVAMP.md).
 *
 * A single persistent Nav SDK [NavigationView] is the map surface across every
 * stage. [NavStage.BROWSE] shows the map + search bar + Connect pill; tapping
 * search opens [NavStage.SEARCH] (recents, favorites, live autocomplete);
 * picking a place goes to [NavStage.CONFIRM] (red pin + place card → Get
 * Directions / Save). Route preview + on-phone active guidance land in P3/P4.
 */
@Composable
fun NavigationHomeScreen(
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

    // ---- Preview state ----------------------------------------------------
    var previewRoutes by remember { mutableStateOf<List<RoutePreview>>(emptyList()) }
    var selectedRoute by remember { mutableIntStateOf(0) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }
    var resolvingLink by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Active navigation session state (drives the NAVIGATING stage / arrival).
    val navState by GoogleNavSdkProvider.state.collectAsState()

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
        stage = NavStage.NAVIGATING
    }

    fun stopNav() {
        GoogleNavSdkController.stop()
        backToBrowse()
    }

    // Auto-return to the map once the trip ends by arrival.
    LaunchedEffect(navState.sessionState) {
        if (stage == NavStage.NAVIGATING && navState.sessionState == NavSessionState.ARRIVED) {
            backToBrowse()
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

    // Track map bearing for the compass (shown only when rotated).
    LaunchedEffect(googleMap) {
        val gm = googleMap ?: return@LaunchedEffect
        mapBearing = gm.cameraPosition.bearing
        gm.setOnCameraMoveListener { mapBearing = gm.cameraPosition.bearing }
    }



    BackHandler {
        when (stage) {
            NavStage.PREVIEW -> stage = NavStage.CONFIRM
            NavStage.CONFIRM -> { stage = NavStage.SEARCH; selected = null }
            NavStage.NAVIGATING -> stopNav()
            NavStage.SEARCH -> backToBrowse()
            NavStage.BROWSE -> showExitConfirm = true // Back at home -> confirm full close
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        // --- Map surface -----------------------------------------------------
        when {
            available && navReady -> BrowseMap(
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
                // The SDK's NavigationView renders the full guidance UI (top
                // maneuver header + bottom ETA card + re-center + report). We only
                // overlay a small END, placed below the header so it doesn't
                // overlap the SDK chrome. Hardware Back also ends navigation.
                Box(
                    modifier = Modifier.align(Alignment.TopStart).systemBarsPadding()
                        .padding(start = 16.dp, top = NAV_HEADER_CLEARANCE),
                ) {
                    CircleIconButton(OpenDashIcons.Close, "End navigation", onClick = ::stopNav)
                }
                // Compass reset (only when the map is rotated off north), placed
                // below the SDK's maneuver header so it clears the stock chrome.
                if (available && navReady && abs(mapBearing) > 0.5f) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding()
                            .padding(end = 16.dp, top = NAV_HEADER_CLEARANCE),
                    ) {
                        CompassButton(bearing = mapBearing) { resetBearing(googleMap) }
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
private fun BrowseMap(navUiEnabled: Boolean, onMap: (GoogleMap?) -> Unit) {
    val context = LocalContext.current
    val navView = rememberNavigationViewWithLifecycle()
    AndroidView(
        factory = {
            navView.getMapAsync { gm ->
                onMap(gm)
                gm.uiSettings.isMyLocationButtonEnabled = false // we draw our own recenter
                gm.uiSettings.isCompassEnabled = false          // custom controls instead
                // Browse/preview map follows the phone's light/dark setting.
                runCatching { gm.mapColorScheme = MapColorScheme.FOLLOW_SYSTEM }
                if (hasLocationPermission(context)) {
                    runCatching { gm.isMyLocationEnabled = true }
                }
                val start = lastLocation(context)?.let { LatLng(it.first, it.second) }
                    ?: LatLng(12.9716, 77.5946) // Bengaluru fallback
                gm.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 15f))
            }
            navView
        },
        modifier = Modifier.fillMaxSize(),
    )
    // Show the SDK's full built-in guidance UI (header + ETA card + re-center +
    // report) only while navigating; make the guidance map follow the phone's
    // light/dark mode too (the SDK otherwise defaults to time-of-day night mode).
    LaunchedEffect(navUiEnabled) {
        runCatching {
            navView.setNavigationUiEnabled(navUiEnabled)
            navView.setForceNightMode(
                if (isSystemDark(context)) ForceNightMode.FORCE_NIGHT else ForceNightMode.FORCE_DAY,
            )
        }
    }
    DisposableEffect(Unit) { onDispose { onMap(null) } }
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
                    if (homes.isNotEmpty() || works.isNotEmpty()) {
                        item { SectionLabel("FAVORITES") }
                        items(homes + works, key = { "fav-${it.slot}-${it.place.lat},${it.place.lng}" }) { f ->
                            SavedRow(
                                icon = if (f.slot == FavoriteSlot.HOME) OpenDashIcons.House else OpenDashIcons.Briefcase,
                                title = if (f.slot == FavoriteSlot.HOME) "Home" else "Work",
                                subtitle = f.place.label,
                                origin = origin, lat = f.place.lat, lng = f.place.lng,
                            ) { onPickSaved(f.place) }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Ktm.Dim) } },
    )
}

/** A neutral dark circular icon button (Google-style controls). */
@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape)
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Ktm.White, modifier = Modifier.size(22.dp))
    }
}

/** Compass button: the north needle rotates with the map bearing; tap resets. */
@Composable
private fun CompassButton(bearing: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
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
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Search, null, tint = Ktm.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text("Enter Destination", color = Ktm.Dim, fontFamily = Barlow, fontSize = 16.sp)
    }
}

@Composable
private fun IconPill(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Ktm.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ConnectPill(onClick: () -> Unit) {
    val state by BccuConnectionService.connectionState.collectAsState()
    val deviceName by BccuConnectionService.deviceName.collectAsState()

    val connected = state == BccuConnectionService.ConnectionState.AUTHENTICATED
    val connecting = state == BccuConnectionService.ConnectionState.CONNECTING
    val dotColor = when {
        connected -> Ktm.Green
        connecting -> Ktm.Orange
        else -> Ktm.Danger
    }
    val label = when {
        connected -> deviceName ?: "Connected"
        connecting -> "Connecting…"
        else -> "Connect"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, if (connected) Ktm.ConnBorder else Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.size(9.dp))
        Icon(OpenDashIcons.Bike, null, tint = Ktm.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = Ktm.White, fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}

/** A [NavigationView] wired to the host lifecycle (see P1 crash note). */
@Composable
private fun rememberNavigationViewWithLifecycle(): NavigationView {
    val context = LocalContext.current
    val navView = remember { NavigationView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navView) {
        var last: Lifecycle.Event? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> navView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> navView.onStart()
                Lifecycle.Event.ON_RESUME -> navView.onResume()
                Lifecycle.Event.ON_PAUSE -> navView.onPause()
                Lifecycle.Event.ON_STOP -> navView.onStop()
                else -> {}
            }
            if (event != Lifecycle.Event.ON_DESTROY) last = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            when (last) {
                Lifecycle.Event.ON_RESUME -> { navView.onPause(); navView.onStop(); navView.onDestroy() }
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_PAUSE -> { navView.onStop(); navView.onDestroy() }
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_CREATE -> { navView.onDestroy() }
                else -> {}
            }
        }
    }
    return navView
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

/** True when the phone's system UI is in dark mode. */
private fun isSystemDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

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
