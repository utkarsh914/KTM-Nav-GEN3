package com.navigator.app.ui.feature.places

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.location.lastKnownLocation
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.feature.navigate.PickOnMapHost
import com.navigator.app.ui.feature.navigate.pick.AndroidReverseGeocoder
import com.navigator.app.ui.feature.navigate.search.PlaceSearchRoute
import com.navigator.app.ui.feature.navigate.search.SearchMode
import com.navigator.app.ui.map.LocationMarkerStyle
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.map.MapSurface
import com.navigator.app.ui.map.RetainedNavigationView
import com.navigator.app.ui.map.rememberMapController
import com.navigator.app.ui.shell.LocalFeedback
import com.navigator.app.ui.findHostActivity
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PC3: choose the place for Home, Work or a new saved place with the shared search in pick mode, with two extra ways in: use
 * where the rider is, or pick on the map. Picking saves straight away, returns, and confirms with an Undo snackbar.
 */
@Composable
fun PlacePickScreen(slot: FavoriteSlot, retainedNav: RetainedNavigationView, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.appContainer
    val feedback = LocalFeedback.current
    val settings by container.settings.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var mapping by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun done(place: SavedPlace) {
        if (finished) return // a second tap on a result must not save twice
        finished = true
        val previous = container.places.assign(place, slot)
        feedback.snackbar(
            if (slot == FavoriteSlot.OTHER) resources.getString(R.string.places_saved_one)
            else resources.getString(R.string.places_slot_set, resources.getString(slotName(slot))),
            resources.getString(R.string.common_undo),
            onAction = { container.places.undoAssign(place, slot, previous) },
        )
        onBack()
    }

    fun useHere(unavailable: String, fallback: String) {
        val here = lastKnownLocation(context)
        if (here == null) feedback.snackbar(unavailable)
        else if (!busy) {
            busy = true
            scope.launch {
                // Never wait long on a slow geocoder: the point is what matters, the name is a nicety.
                val name = withTimeoutOrNull(NAME_TIMEOUT_MS) {
                    AndroidReverseGeocoder(context).streetName(MapPoint(here.first, here.second))
                }
                busy = false
                done(SavedPlace(here.first, here.second, name ?: fallback))
            }
        }
    }

    val unavailableText = resources.getString(R.string.search_current_unavailable)
    val fallbackName = resources.getString(R.string.places_current_label)
    // Asked at the moment it is needed; a refusal says so instead of doing nothing.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) useHere(unavailableText, fallbackName)
        else {
            // After "don't ask again" Android shows no dialog any more: the way out is the app's own settings.
            val activity = context.findHostActivity()
            val stuck = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            feedback.snackbar(
                resources.getString(R.string.search_current_denied),
                if (stuck) resources.getString(R.string.common_open_settings) else null,
                onAction = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                },
            )
        }
    }

    BackHandler(enabled = mapping) { mapping = false }
    Box(modifier.fillMaxSize()) {
        if (mapping) {
            val map = rememberMapController(retainedNav, LocationMarkerStyle.fromId(settings.locationMarkerStyle))
            val mapReady by map.ready.collectAsState()
            MapSurface(retained = retainedNav, controller = map, navUiEnabled = false, guidanceInsets = MapInsets(), dark = KtmTheme.colors.isDark)
            LaunchedEffect(mapReady) {
                if (mapReady) lastKnownLocation(context)?.let { map.recenter(MapPoint(it.first, it.second)) }
            }
            PickOnMapHost(map = map, onBack = { mapping = false }, onUse = ::done)
        } else {
            PlaceSearchRoute(
                mode = SearchMode.Pick(slot),
                onPicked = ::done,
                onBack = onBack,
                onPickOnMap = { mapping = true },
                onUseCurrent = {
                    if (hasLocationPermission(context)) useHere(unavailableText, fallbackName) else askLocation.launch(LOCATION_PERMISSIONS)
                },
            )
        }
    }
}

private const val NAME_TIMEOUT_MS = 3_000L

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun hasLocationPermission(context: Context) = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
