package com.navigator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.android.libraries.navigation.NavigationView
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.ui.theme.Barlow
import com.navigator.app.ui.theme.BarlowCondensed
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.OpenDashIcons

/**
 * Phone-first map home (UX revamp P1 — see docs/NAVIGATION_UX_REVAMP.md).
 *
 * A single persistent Nav SDK [NavigationView] is the map surface for every
 * later stage (browse → confirm → preview → active guidance). P1 renders the
 * browse map with a location dot, a top search bar (bridges to the existing
 * destination flow for now), a Connect/Connected pill bound to the BLE service,
 * a recenter control, and a settings entry.
 *
 * The Nav SDK requires its Terms accepted and a [com.google.android.libraries.navigation.Navigator]
 * obtained before a [NavigationView] can render, so we warm it up via
 * [GoogleNavSdkController.prepare] on entry and only mount the map once ready.
 */
@Composable
fun NavigationHomeScreen(
    onOpenSearch: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

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

    Box(modifier = Modifier.fillMaxSize().background(Ktm.Screen)) {
        // --- Map surface -----------------------------------------------------
        when {
            available && navReady -> BrowseMap(onMap = { googleMap = it })
            available && !navError -> MapPlaceholder("Preparing map…")
            available && navError -> MapPlaceholder("Couldn't start the map.\nCheck connection and Google Play services.")
            else -> MapPlaceholder("Maps need a Google API key.\nSet NAV_SDK_API_KEY in local.properties.")
        }

        // --- Top overlay: search bar + settings ------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(modifier = Modifier.weight(1f), onClick = onOpenSearch)
                Spacer(Modifier.size(10.dp))
                IconPill(icon = OpenDashIcons.Settings, contentDescription = "Settings", onClick = onOpenSettings)
            }
        }

        // --- Bottom-left: recenter ; Bottom-right: connect pill --------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .systemBarsPadding()
                .padding(16.dp),
        ) {
            if (available && navReady) {
                IconPill(icon = OpenDashIcons.LocateFixed, contentDescription = "Recenter") {
                    recenter(context, googleMap)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(16.dp),
        ) {
            ConnectPill(onClick = onOpenConnect)
        }
    }
}

@Composable
private fun BrowseMap(onMap: (GoogleMap?) -> Unit) {
    val context = LocalContext.current
    val navView = rememberNavigationViewWithLifecycle()
    AndroidView(
        factory = {
            // Hide the built-in guidance chrome during browse; re-enabled when
            // active navigation lands (P4).
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
        Text(
            message,
            color = Ktm.Muted2,
            fontFamily = BarlowCondensed,
            fontSize = 18.sp,
        )
    }
}

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
        Icon(OpenDashIcons.Search, contentDescription = null, tint = Ktm.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            "Enter Destination",
            color = Ktm.Dim,
            fontFamily = Barlow,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Ktm.RadiusButton))
            .background(Ktm.Surface)
            .border(1.dp, Ktm.Border, RoundedCornerShape(Ktm.RadiusButton))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Ktm.White, modifier = Modifier.size(22.dp))
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
        Icon(OpenDashIcons.Bike, contentDescription = null, tint = Ktm.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = Ktm.White,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * A [NavigationView] wired to the host lifecycle.
 *
 * Unlike `MapView`, the Nav SDK's [NavigationView] strictly validates lifecycle
 * ordering (e.g. `onStart` may not follow `onResume`). So we drive it *only*
 * from lifecycle events — `Lifecycle.addObserver` synchronously syncs a fresh
 * observer up to the host's current state (dispatching ON_CREATE → ON_START →
 * ON_RESUME when we enter while resumed). We never call the lifecycle methods
 * manually, and on dispose we walk the view down from wherever it is to
 * destroyed exactly once (the observer never calls `onDestroy`, so there is no
 * double-destroy).
 */
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
            // Track the last *lifecycle* state; onDestroy is handled in onDispose.
            if (event != Lifecycle.Event.ON_DESTROY) last = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Bring the view down to destroyed from its current state, in order.
            when (last) {
                Lifecycle.Event.ON_RESUME -> {
                    navView.onPause(); navView.onStop(); navView.onDestroy()
                }
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_PAUSE -> {
                    navView.onStop(); navView.onDestroy()
                }
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_CREATE -> {
                    navView.onDestroy()
                }
                else -> {}
            }
        }
    }
    return navView
}

private fun recenter(context: Context, gm: GoogleMap?) {
    val map = gm ?: return
    val loc = lastLocation(context) ?: return
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 16f))
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Best-effort last-known location for the initial camera / recenter. */
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
