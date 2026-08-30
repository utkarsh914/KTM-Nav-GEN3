package com.navigator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.navigation.NavigationView
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.ui.components.KtmPrimaryButton
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private enum class NavStage { BROWSE, SEARCH, CONFIRM }

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
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val available = remember { GoogleNavSdkController.isAvailable(context) }
    var navReady by remember { mutableStateOf(false) }
    var navError by remember { mutableStateOf(false) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    LaunchedEffect(Unit) {
        if (available && activity != null) {
            GoogleNavSdkController.prepare(
                activity,
                onReady = { navReady = true },
                onError = { navError = true },
            )
        }
    }

    // ---- Flow state -------------------------------------------------------
    val store = remember { PlacesStore(context) }
    var stage by remember { mutableStateOf(NavStage.BROWSE) }
    var selected by remember { mutableStateOf<SavedPlace?>(null) }
    var recents by remember { mutableStateOf(store.recents()) }
    var favorites by remember { mutableStateOf(store.favorites()) }

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

    // Reflect the current stage on the map (red pin + camera on CONFIRM).
    LaunchedEffect(stage, selected, googleMap) {
        val gm = googleMap ?: return@LaunchedEffect
        gm.clear()
        if (stage == NavStage.CONFIRM) {
            selected?.let { p ->
                val ll = LatLng(p.lat, p.lng)
                gm.addMarker(MarkerOptions().position(ll).title(p.label))
                gm.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15f))
            }
        }
    }

    fun openSearch() {
        sessionToken = UUID.randomUUID().toString()
        query = ""; results = emptyList()
        recents = store.recents(); favorites = store.favorites()
        stage = NavStage.SEARCH
    }

    fun choose(p: SavedPlace) { selected = p; stage = NavStage.CONFIRM }

    fun backToBrowse() { stage = NavStage.BROWSE; selected = null; query = "" }

    fun startTo(p: SavedPlace) {
        store.addRecent(p)
        recents = store.recents()
        onStartNavigation(NavDestination(p.lat, p.lng, p.label))
        backToBrowse()
    }

    BackHandler(enabled = stage != NavStage.BROWSE) {
        if (stage == NavStage.CONFIRM) { stage = NavStage.SEARCH; selected = null } else backToBrowse()
    }

    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        // --- Map surface -----------------------------------------------------
        when {
            available && navReady -> BrowseMap(onMap = { googleMap = it })
            available && !navError -> MapPlaceholder("Preparing map…")
            available && navError -> MapPlaceholder("Couldn't start the map.\nCheck connection and Google Play services.")
            else -> MapPlaceholder("Maps need a Google API key.\nSet NAV_SDK_API_KEY in local.properties.")
        }

        when (stage) {
            NavStage.BROWSE -> {
                // Top: search bar + settings.
                Column(
                    modifier = Modifier.fillMaxWidth().systemBarsPadding()
                        .padding(horizontal = 16.dp).padding(top = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SearchBar(modifier = Modifier.weight(1f), onClick = ::openSearch)
                        Spacer(Modifier.size(10.dp))
                        IconPill(OpenDashIcons.Settings, "Settings", onClick = onOpenSettings)
                    }
                }
                // Bottom-left recenter, bottom-right connect.
                Box(Modifier.align(Alignment.BottomStart).systemBarsPadding().padding(16.dp)) {
                    if (available && navReady) {
                        IconPill(OpenDashIcons.LocateFixed, "Recenter") { recenter(context, googleMap) }
                    }
                }
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
                        onGetDirections = { sel?.let(::startTo) },
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
                // Clear/close affordance top-right.
                Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(16.dp)) {
                    IconPill(OpenDashIcons.Close, "Clear", onClick = ::backToBrowse)
                }
            }
        }
    }
}

// ============================ Map ======================================

@Composable
private fun BrowseMap(onMap: (GoogleMap?) -> Unit) {
    val context = LocalContext.current
    val navView = rememberNavigationViewWithLifecycle()
    AndroidView(
        factory = {
            runCatching { navView.setNavigationUiEnabled(false) }
            navView.getMapAsync { gm ->
                onMap(gm)
                gm.uiSettings.isMyLocationButtonEnabled = false // we draw our own recenter
                gm.uiSettings.isCompassEnabled = true
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
    DisposableEffect(Unit) { onDispose { onMap(null) } }
}

@Composable
private fun MapPlaceholder(message: String) {
    Box(Modifier.fillMaxSize().background(Ktm.Screen), contentAlignment = Alignment.Center) {
        Text(message, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp)
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

private fun recenter(context: Context, gm: GoogleMap?) {
    val map = gm ?: return
    val loc = lastLocation(context) ?: return
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 16f))
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
