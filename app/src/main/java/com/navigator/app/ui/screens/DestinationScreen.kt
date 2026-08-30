package com.navigator.app.ui.screens

import android.content.Context
import android.location.LocationManager
import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.navigator.app.nav.destination.PlaceSuggestion
import com.navigator.app.nav.destination.PlacesClient
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.JetBrainsMono
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private enum class DestMode { SEARCH, MAP, LINK }

/**
 * Destination entry - Search + Map (Phase 5a/5b). Search: type a place, see
 * matches with a straight-line distance, tap to resolve + navigate. Map: drop a
 * pin on the Nav SDK's bundled map and navigate to it. Shared-link entry (5c)
 * is added later.
 */
@Composable
fun DestinationScreen(
    onBack: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    initialLink: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // One session token per search session (groups autocomplete + details for billing).
    val sessionToken = remember { UUID.randomUUID().toString() }
    val origin = remember { lastLocation(context) }
    // Android client identity so the Android-restricted key accepts direct REST calls.
    val auth = remember { PlacesClient.androidAuth(context) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (initialLink.isNullOrBlank()) DestMode.SEARCH else DestMode.LINK) }
    var linkText by remember { mutableStateOf(initialLink.orEmpty()) }
    var linkError by remember { mutableStateOf<String?>(null) }

    // Make sure the Nav SDK has its key before the bundled map renders.
    LaunchedEffect(Unit) { GoogleNavSdkController.ensureApiKey() }

    // Debounced autocomplete.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(300)
        results = PlacesClient.autocomplete(
            input = q,
            sessionToken = sessionToken,
            auth = auth,
            originLat = origin?.first,
            originLng = origin?.second,
        )
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ktm.Screen)
            .systemBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                OpenDashIcons.ChevronLeft, contentDescription = "Back", tint = Ktm.TextSecondary,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "ENTER DESTINATION", color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!PlacesClient.apiKeyPresent) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Search needs a Google API key.\nSet NAV_SDK_API_KEY in local.properties.",
                    color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                )
            }
        } else {
            ModeToggle(mode) { mode = it }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.weight(1f).fillMaxWidth()) {
                when (mode) {
                    DestMode.SEARCH -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search a place or address", color = Ktm.Dim) },
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
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                resolving || loading -> CircularProgressIndicator(
                                    color = Ktm.Orange,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                                )
                                results.isEmpty() && query.trim().length >= 3 -> Text(
                                    "No matches", color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 18.sp,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                                )
                                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(results, key = { it.placeId }) { s ->
                                        SuggestionRow(s) {
                                            if (resolving) return@SuggestionRow
                                            resolving = true
                                            scope.launch {
                                                val loc = PlacesClient.details(s.placeId, sessionToken, auth)
                                                resolving = false
                                                if (loc != null) onNavigate(NavDestination(loc.lat, loc.lng, s.primary))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DestMode.LINK -> {
                        OutlinedTextField(
                            value = linkText,
                            onValueChange = { linkText = it; linkError = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste a Google Maps link", color = Ktm.Dim) },
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Best-effort: reads coordinates from the link. Share from " +
                                "Google Maps, or paste a link. Not all links contain a pin.",
                            color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 13.sp,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                resolving -> CircularProgressIndicator(
                                    color = Ktm.Orange,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                                )
                                linkError != null -> Text(
                                    linkError!!, color = Ktm.Danger, fontFamily = BarlowCondensed, fontSize = 16.sp,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                                )
                            }
                        }
                        NavigateHereButton(enabled = linkText.isNotBlank() && !resolving) {
                            resolving = true
                            linkError = null
                            scope.launch {
                                val dest = com.navigator.app.nav.destination.MapsUrlResolver.resolve(linkText, auth)
                                resolving = false
                                if (dest != null) onNavigate(dest)
                                else linkError = "Couldn't find a location in that link."
                            }
                        }
                    }

                    DestMode.MAP -> {
                        val mapView = rememberMapViewWithLifecycle()
                        var pin by remember { mutableStateOf<LatLng?>(null) }
                        AndroidView(
                            factory = {
                                mapView.getMapAsync { gm ->
                                    gm.uiSettings.isZoomControlsEnabled = true
                                    val start = origin?.let { LatLng(it.first, it.second) }
                                        ?: LatLng(12.9716, 77.5946) // Bengaluru fallback
                                    gm.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 13f))
                                    val drop = { ll: LatLng ->
                                        pin = ll
                                        gm.clear()
                                        gm.addMarker(MarkerOptions().position(ll))
                                        Unit
                                    }
                                    gm.setOnMapClickListener { drop(it) }
                                    gm.setOnMapLongClickListener { drop(it) }
                                }
                                mapView
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Ktm.RadiusRow)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap the map to place your destination",
                            color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        NavigateHereButton(enabled = pin != null) {
                            pin?.let { onNavigate(NavDestination(it.latitude, it.longitude, "Dropped pin")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: DestMode, onSelect: (DestMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ModeTab("SEARCH", mode == DestMode.SEARCH, Modifier.weight(1f)) { onSelect(DestMode.SEARCH) }
        ModeTab("MAP", mode == DestMode.MAP, Modifier.weight(1f)) { onSelect(DestMode.MAP) }
        ModeTab("LINK", mode == DestMode.LINK, Modifier.weight(1f)) { onSelect(DestMode.LINK) }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(if (selected) Ktm.Orange else Ktm.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) Ktm.OnAccent else Ktm.Muted2,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun NavigateHereButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(if (enabled) Ktm.Orange else Ktm.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OpenDashIcons.Navigation, contentDescription = null,
            tint = if (enabled) Ktm.OnAccent else Ktm.Dim, modifier = Modifier.size(19.dp))
        Spacer(Modifier.size(10.dp))
        Text("NAVIGATE HERE", color = if (enabled) Ktm.OnAccent else Ktm.Dim,
            fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp)
    }
}

/** A [MapView] wired to the composition's lifecycle (handles mid-lifecycle entry). */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // We enter while the host is already resumed, so kick these manually.
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

@Composable
private fun SuggestionRow(s: PlaceSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ktm.RadiusRow))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusRow))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.primary, color = Ktm.White, fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
            )
            s.secondary?.let {
                Text(it, color = Ktm.Muted2, fontFamily = BarlowCondensed, fontSize = 14.sp)
            }
        }
        val d = s.distanceMeters
        if (d != null && d > 0) {
            Spacer(Modifier.size(10.dp))
            Text(
                "~" + DistanceFormatter.format(d, DistanceUnits.METRIC),
                color = Ktm.Orange, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
        }
    }
}

/** Best-effort last-known location for the autocomplete origin (permission already granted). */
private fun lastLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        null
    } catch (e: SecurityException) {
        null
    }
}
