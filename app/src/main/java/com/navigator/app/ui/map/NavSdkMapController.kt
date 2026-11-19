package com.navigator.app.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.libraries.navigation.NavigationView
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ui.screens.LocationMarkerStyle
import com.navigator.app.ui.screens.UserLocationMarker
import com.navigator.app.ui.screens.drawRideTrack
import com.navigator.app.ui.screens.frameRideTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owner token for the shared map while the map home is the active screen. */
internal object HomeMapOwner

private const val ROUTE_SELECTED = 0xFF1A73E8.toInt() // Google route blue
private const val ROUTE_ALT = 0xFF7C93B0.toInt()      // desaturated blue alternate

/**
 * [MapController] on the Navigation SDK's `NavigationView`/`GoogleMap`. One per map screen; it is
 * attached by [MapSurface] when the retained view delivers its map and detached when the screen
 * leaves (only if another screen has not taken the shared map over meanwhile). Main thread.
 *
 * Verified against navigation-8.1.0: `GoogleMap.followMyLocation(int)` with `CameraPerspective`
 * TILTED/TOP_DOWN_NORTH_UP/TOP_DOWN_HEADING_UP, `setOnPoiClickListener`,
 * `setOnCameraMoveStartedListener` (REASON_GESTURE), `NavigationView.showRouteOverview()`,
 * `setRecenterButtonEnabled`, `addOnRecenterButtonClickedListener`.
 */
class NavSdkMapController(
    private val retained: RetainedNavigationView,
    markerStyle: LocationMarkerStyle,
    context: Context,
) : MapController {
    private val userMarker = UserLocationMarker(context, markerStyle)
    private var map: GoogleMap? = null
    private var navView: NavigationView? = null
    private var detached = false

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _bearing = MutableStateFlow(0f)
    override val bearing: StateFlow<Float> = _bearing.asStateFlow()
    private val _longClicks = MutableSharedFlow<MapPoint>(extraBufferCapacity = 4)
    override val longClicks: SharedFlow<MapPoint> = _longClicks.asSharedFlow()
    private val _poiClicks = MutableSharedFlow<MapPoi>(extraBufferCapacity = 4)
    override val poiClicks: SharedFlow<MapPoi> = _poiClicks.asSharedFlow()
    private val _polylineClicks = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    override val polylineClicks: SharedFlow<Int> = _polylineClicks.asSharedFlow()
    private val _gesturePans = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val gesturePans: SharedFlow<Unit> = _gesturePans.asSharedFlow()

    /** The retained view delivered its map: take it over and start listening. */
    internal fun attach(map: GoogleMap, view: NavigationView) {
        if (detached) return // the map delivered after the screen was gone
        this.map = map
        navView = view
        retained.mapOwner = HomeMapOwner
        map.uiSettings.isMyLocationButtonEnabled = false // we draw our own recenter
        map.uiSettings.isCompassEnabled = false          // custom controls instead
        _bearing.value = map.cameraPosition.bearing
        map.setOnCameraMoveListener { _bearing.value = map.cameraPosition.bearing }
        map.setOnMapLongClickListener { _longClicks.tryEmit(MapPoint(it.latitude, it.longitude)) }
        _ready.value = true
    }

    /**
     * The screen left composition. Our marker is always ours to remove; the shared listeners only
     * if no other screen has taken the map over (the NEW screen sets up before the OLD one is
     * disposed during animated route changes).
     */
    internal fun detach() {
        detached = true
        runCatching { userMarker.remove() }
        val gm = map
        if (gm != null && retained.mapOwner === HomeMapOwner) {
            runCatching {
                gm.setOnCameraMoveListener(null)
                gm.setOnCameraMoveStartedListener(null)
                gm.setOnMapLongClickListener(null)
                gm.setOnPolylineClickListener(null)
                gm.setOnPoiClickListener(null)
            }
        }
        map = null
        navView = null
        _ready.value = false
    }

    /**
     * Start emitting [gesturePans]. Not on by default: the listener replaces any other on the shared
     * map, and until a screen uses it (B7/B9) the SDK's own handling should stay untouched. Check on
     * a device that Google's re-center button still appears after a pan once this is enabled.
     */
    fun listenForGestures() {
        map?.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) _gesturePans.tryEmit(Unit)
        }
    }

    /** Start emitting [poiClicks]. Off by default for the same reason as [listenForGestures]: a POI listener changes tap behaviour. */
    fun listenForPois() {
        map?.setOnPoiClickListener { _poiClicks.tryEmit(MapPoi(MapPoint(it.latLng.latitude, it.latLng.longitude), it.name, it.placeId)) }
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        runCatching { map?.setPadding(left, top, right, bottom) }
    }

    override fun clearOverlays() {
        val gm = map ?: return
        gm.setOnPolylineClickListener(null)
        gm.clear()
        // gm.clear() dropped the user marker: forget the stale handle so showUserMarker re-adds it.
        userMarker.remove()
    }

    override fun showPlace(place: MapPoint, title: String?) {
        val gm = map ?: return
        val ll = LatLng(place.lat, place.lng)
        gm.addMarker(MarkerOptions().position(ll).title(title))
        gm.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 15f))
    }

    override fun showTripPreview(scene: TripPreviewScene) {
        val gm = map ?: return
        val dest = LatLng(scene.destination.lat, scene.destination.lng)
        // Numbered stop markers (1..n), then the destination pin on top.
        scene.stops.forEach { gm.addMarker(MarkerOptions().position(LatLng(it.point.lat, it.point.lng)).title(it.title)) }
        gm.addMarker(MarkerOptions().position(dest).title(scene.destinationTitle))
        if (scene.routes.isEmpty()) {
            gm.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 14f))
            return
        }
        scene.routes.forEachIndexed { i, points ->
            if (i != scene.selectedRoute) gm.addRoute(points, ROUTE_ALT, 9f, i)
        }
        scene.routes.getOrNull(scene.selectedRoute)?.let { selected ->
            gm.addRoute(selected, ROUTE_SELECTED, 14f, scene.selectedRoute)
            val b = LatLngBounds.builder()
            selected.forEach { b.include(LatLng(it.lat, it.lng)) }
            scene.frameAlso.forEach { b.include(LatLng(it.lat, it.lng)) }
            runCatching { gm.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120)) }
        }
        gm.setOnPolylineClickListener { poly -> (poly.tag as? Int)?.let { _polylineClicks.tryEmit(it) } }
    }

    private fun GoogleMap.addRoute(points: List<MapPoint>, color: Int, width: Float, index: Int): Polyline =
        addPolyline(
            PolylineOptions().addAll(points.map { LatLng(it.lat, it.lng) }).color(color).width(width),
        ).apply { tag = index; isClickable = true }

    override fun showTrack(points: List<RidePoint>, bands: RideMetrics.SpeedBands) {
        val gm = map ?: return
        drawRideTrack(gm, points, bands)
        frameRideTrack(gm, points)
    }

    override fun showUserMarker(at: MapPoint, headingDegrees: Float?) {
        val gm = map ?: return
        userMarker.update(gm, LatLng(at.lat, at.lng), headingDegrees)
    }

    override fun hideUserMarker() = userMarker.remove()

    override fun recenter(at: MapPoint) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(at.lat, at.lng), 16f))
    }

    /** Reset the map to north-up (bearing 0, tilt 0), keeping centre and zoom. */
    override fun resetNorth() {
        val gm = map ?: return
        val north = CameraPosition.Builder(gm.cameraPosition).bearing(0f).tilt(0f).build()
        gm.animateCamera(CameraUpdateFactory.newCameraPosition(north))
    }

    @android.annotation.SuppressLint("MissingPermission") // only called while guiding, which needs location
    override fun followMyLocation(mode: FollowMode) {
        val perspective = when (mode) {
            FollowMode.TILTED -> GoogleMap.CameraPerspective.TILTED
            FollowMode.NORTH_UP -> GoogleMap.CameraPerspective.TOP_DOWN_NORTH_UP
            FollowMode.HEADING_UP -> GoogleMap.CameraPerspective.TOP_DOWN_HEADING_UP
        }
        runCatching { map?.followMyLocation(perspective) }
    }

    override fun showRouteOverview() {
        runCatching { navView?.showRouteOverview() }
    }

    // Each call individually guarded so an API mismatch degrades gracefully.
    override fun setGuidanceChrome(guidance: Boolean, stockChrome: Boolean, recenterButton: Boolean) {
        val view = navView ?: return
        runCatching { view.setNavigationUiEnabled(guidance) }
        runCatching { view.setHeaderEnabled(stockChrome) }
        runCatching { view.setEtaCardEnabled(stockChrome) }
        runCatching { view.setSpeedometerEnabled(stockChrome) }
        runCatching { view.setRecenterButtonEnabled(recenterButton) }
        runCatching { view.setReportIncidentButtonEnabled(false) }
        runCatching { view.setTrafficIncidentCardsEnabled(stockChrome) }
    }

    override fun setNightMode(dark: Boolean) {
        runCatching {
            map?.mapColorScheme = if (dark) com.google.android.gms.maps.model.MapColorScheme.DARK
            else com.google.android.gms.maps.model.MapColorScheme.LIGHT
        }
        runCatching {
            navView?.setForceNightMode(
                if (dark) com.google.android.libraries.navigation.ForceNightMode.FORCE_NIGHT
                else com.google.android.libraries.navigation.ForceNightMode.FORCE_DAY,
            )
        }
    }

    /** The SDK puck is only wanted while guiding; the compass-driven marker owns browse and preview. */
    @android.annotation.SuppressLint("MissingPermission") // the caller checks the permission
    internal fun setPuck(enabled: Boolean) {
        runCatching { map?.isMyLocationEnabled = enabled }
    }
}

@Composable
fun rememberMapController(retained: RetainedNavigationView, markerStyle: LocationMarkerStyle): NavSdkMapController {
    val context = LocalContext.current
    return remember(retained) { NavSdkMapController(retained, markerStyle, context) }
}
