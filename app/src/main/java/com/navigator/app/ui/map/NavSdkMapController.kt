package com.navigator.app.ui.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.navigation.NavigationView
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ui.feature.navigate.plan.RouteSelection
import com.navigator.app.ui.theme.KtmMotionSpecs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ROUTE_SELECTED = 0xFF1A73E8.toInt() // Google route blue
private const val ROUTE_ALT = 0xFF8FA4BF.toInt()      // desaturated blue alternate

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
    private val appContext = context.applicationContext
    private val framePaddingPx = (64 * context.resources.displayMetrics.density).toInt()
    private val userMarker = UserLocationMarker(context, markerStyle)
    private var map: GoogleMap? = null
    @Volatile private var reducedMotion = false

    override fun setReducedMotion(reduced: Boolean) { reducedMotion = reduced }

    /** Camera moves take their length from the motion tokens, and jump under reduced motion. */
    private fun GoogleMap.glide(update: com.google.android.gms.maps.CameraUpdate, ms: Int) {
        if (reducedMotion) moveCamera(update) else animateCamera(update, ms, null)
    }
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
    private val _cameraIdle = MutableSharedFlow<MapPoint>(extraBufferCapacity = 4)
    override val cameraIdle: SharedFlow<MapPoint> = _cameraIdle.asSharedFlow()
    private val _gesturePans = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val gesturePans: SharedFlow<Unit> = _gesturePans.asSharedFlow()
    private val _mapClicks = MutableSharedFlow<MapPoint>(extraBufferCapacity = 4)
    override val mapClicks: SharedFlow<MapPoint> = _mapClicks.asSharedFlow()
    private var replayMarker: Marker? = null
    private val endpointPins by lazy { RideEndpointPins(appContext) }
    private val trimOverlay by lazy { TrimPreviewOverlay(endpointPins) }

    /**
     * Whether this screen set the shared map up last. Each screen owns its own controller, so a screen that leaves
     * (Replay → Trim and back) must not clear the listeners the arriving one has just set.
     */
    private val owns: Boolean get() = retained.mapOwner === this

    /** The screen is (again) on show: a map delivered from now on is taken. Pairs with [detach], which refuses late deliveries. */
    internal fun arm() { detached = false }

    /** The retained view delivered its map: take it over and start listening. */
    internal fun attach(map: GoogleMap, view: NavigationView) {
        if (detached) return // the map delivered after the screen was gone
        this.map = map
        navView = view
        // Whatever the previous screen left on the shared map goes first: only what this screen asks for stays.
        clearListeners(map)
        retained.mapOwner = this
        map.uiSettings.isMyLocationButtonEnabled = false // we draw our own recenter
        map.uiSettings.isCompassEnabled = false          // custom controls instead
        _bearing.value = map.cameraPosition.bearing
        map.setOnCameraMoveListener { _bearing.value = map.cameraPosition.bearing }
        map.setOnMapLongClickListener { _longClicks.tryEmit(MapPoint(it.latitude, it.longitude)) }
        // A screen that asked for camera-idle before the map arrived gets it now.
        if (idleWanted) setCameraIdleListening(true)
        if (poiWanted) setPoiListening(true)
        if (gesturesWanted) setGestureListening(true)
        if (clicksWanted) setMapClickListening(true)
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
        replayMarker = null
        val gm = map
        if (gm != null && owns) {
            clearListeners(gm)
            retained.mapOwner = null
        }
        map = null
        navView = null
        _ready.value = false
    }

    private fun clearListeners(gm: GoogleMap) {
        runCatching {
            gm.setOnCameraMoveListener(null)
            gm.setOnCameraMoveStartedListener(null)
            gm.setOnMapLongClickListener(null)
            gm.setOnPolylineClickListener(null)
            gm.setOnMarkerClickListener(null)
            gm.setOnPoiClickListener(null)
            gm.setOnCameraIdleListener(null)
            gm.setOnMapClickListener(null)
        }
    }

    private var clicksWanted = false

    override fun setMapClickListening(enabled: Boolean) {
        clicksWanted = enabled
        val gm = map ?: return
        if (enabled) gm.setOnMapClickListener { _mapClicks.tryEmit(MapPoint(it.latitude, it.longitude)) }
        else if (owns) gm.setOnMapClickListener(null)
    }

    private var gesturesWanted = false

    override fun setGestureListening(enabled: Boolean) {
        gesturesWanted = enabled
        val gm = map ?: return
        if (enabled) {
            gm.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) _gesturePans.tryEmit(Unit)
            }
        } else if (owns) gm.setOnCameraMoveStartedListener(null)
    }

    private var poiWanted = false

    override fun setPoiListening(enabled: Boolean) {
        poiWanted = enabled
        val gm = map ?: return
        if (enabled) {
            gm.setOnPoiClickListener { _poiClicks.tryEmit(MapPoi(MapPoint(it.latLng.latitude, it.latLng.longitude), it.name, it.placeId)) }
        } else if (owns) gm.setOnPoiClickListener(null)
    }

    override fun cameraCenter(): MapPoint? = map?.cameraPosition?.target?.let { MapPoint(it.latitude, it.longitude) }

    override fun cameraZoom(): Float? = map?.cameraPosition?.zoom

    private var idleWanted = false

    override fun setCameraIdleListening(enabled: Boolean) {
        idleWanted = enabled
        val gm = map ?: return
        if (enabled) gm.setOnCameraIdleListener { cameraCenter()?.let { _cameraIdle.tryEmit(it) } }
        else if (owns) gm.setOnCameraIdleListener(null)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        runCatching { map?.setPadding(left, top, right, bottom) }
    }

    override fun clearOverlays() {
        val gm = map ?: return
        gm.setOnPolylineClickListener(null)
        gm.setOnMarkerClickListener(null)
        gm.clear()
        trimOverlay.forget()
        replayMarker = null
        // gm.clear() dropped the user marker: forget the stale handle so showUserMarker re-adds it.
        userMarker.remove()
    }

    override fun showPlace(place: MapPoint, title: String?, moveCamera: Boolean) {
        val gm = map ?: return
        val ll = LatLng(place.lat, place.lng)
        gm.addMarker(MarkerOptions().position(ll).title(title))
        if (moveCamera) gm.glide(CameraUpdateFactory.newLatLngZoom(ll, 15f), KtmMotionSpecs.CAMERA_FIT_MS)
    }

    override fun showTripPreview(scene: TripPreviewScene) {
        val gm = map ?: return
        val dest = LatLng(scene.destination.lat, scene.destination.lng)
        val pins = scene.style?.let { MapPins(appContext, it) }
        // Numbered stop markers (1..n), then the destination on top.
        scene.stops.forEachIndexed { i, stop ->
            val options = MarkerOptions().position(LatLng(stop.point.lat, stop.point.lng)).title(stop.title)
            if (pins != null) options.icon(pins.numbered(i + 1)).anchor(0.5f, 0.5f)
            gm.addMarker(options.zIndex(5f))
        }
        scene.pending?.let { gm.addMarker(MarkerOptions().position(LatLng(it.lat, it.lng)).zIndex(3f)) }
        val destination = MarkerOptions().position(dest).title(scene.destinationTitle)
        if (pins != null) destination.icon(pins.flag()).anchor(8f / 36f, 1f)
        gm.addMarker(destination.zIndex(6f))
        if (scene.routes.isEmpty()) {
            if (scene.reframe) gm.glide(CameraUpdateFactory.newLatLngZoom(dest, 14f), KtmMotionSpecs.CAMERA_FIT_MS)
            return
        }
        scene.routes.forEachIndexed { i, points ->
            if (i != scene.selectedRoute) gm.addRoute(points, scene.style?.routeAlt ?: ROUTE_ALT, 9f, i)
        }
        scene.routes.getOrNull(scene.selectedRoute)?.let { selected ->
            gm.addRoute(selected, scene.style?.route ?: ROUTE_SELECTED, 14f, scene.selectedRoute)
            val b = LatLngBounds.builder()
            selected.forEach { b.include(LatLng(it.lat, it.lng)) }
            scene.frameAlso.forEach { b.include(LatLng(it.lat, it.lng)) }
            if (scene.reframe) {
                // Padding that leaves no room (several stops on a short screen) can't be fitted: centre on the destination instead.
                runCatching { gm.glide(CameraUpdateFactory.newLatLngBounds(b.build(), framePaddingPx), KtmMotionSpecs.CAMERA_FIT_MS) }
                    .onFailure { runCatching { gm.glide(CameraUpdateFactory.newLatLngZoom(dest, 13f), KtmMotionSpecs.CAMERA_FIT_MS) } }
            }
        }
        // A time label on each route; tapping it selects the route, like tapping the line.
        if (pins != null) {
            val avoid = scene.frameAlso + scene.stops.map { it.point } + scene.destination
            scene.routes.forEachIndexed { i, points ->
                val label = scene.routeLabels.getOrNull(i) ?: return@forEachIndexed
                val at = RouteSelection.calloutPoint(points, i, scene.selectedRoute, avoid)
                    ?: return@forEachIndexed
                gm.addMarker(
                    MarkerOptions().position(LatLng(at.lat, at.lng)).icon(pins.callout(label, i == scene.selectedRoute))
                        .anchor(0.5f, 0.5f).zIndex(if (i == scene.selectedRoute) 2f else 1f),
                )?.tag = i
            }
            gm.setOnMarkerClickListener { m ->
                val route = m.tag as? Int
                if (route != null) _polylineClicks.tryEmit(route)
                route != null // other markers keep the default (info window)
            }
        }
        gm.setOnPolylineClickListener { poly -> (poly.tag as? Int)?.let { _polylineClicks.tryEmit(it) } }
    }

    private fun GoogleMap.addRoute(points: List<MapPoint>, color: Int, width: Float, index: Int): Polyline =
        addPolyline(
            PolylineOptions().addAll(points.map { LatLng(it.lat, it.lng) }).color(color).width(width),
        ).apply { tag = index; isClickable = true }

    override fun showTrack(points: List<RidePoint>, bands: RideMetrics.SpeedBands) {
        val gm = map ?: return
        trimOverlay.forget()
        drawRideTrack(gm, points, bands, endpoints = endpointPins)
        frameRideTrack(gm, points)
    }

    override fun showReplay(points: List<RidePoint>, bands: RideMetrics.SpeedBands, smoothingMeters: Double, markerAt: MapPoint, markerColor: Int) {
        val gm = map ?: return
        // drawRideTrack clears the map, which also drops the user marker: forget its stale handle.
        userMarker.remove()
        trimOverlay.forget()
        drawRideTrack(gm, points, bands, endpoints = endpointPins, simplifyEpsilonM = smoothingMeters)
        replayMarker = gm.addMarker(
            MarkerOptions().position(LatLng(markerAt.lat, markerAt.lng)).icon(replayMarkerIcon(appContext, markerColor))
                .anchor(0.5f, 0.5f).zIndex(2f),
        )
    }

    override fun showTrimPreview(points: List<RidePoint>, from: Int, to: Int, keptColor: Int, removedColor: Int) {
        val gm = map ?: return
        userMarker.remove()
        replayMarker = null
        trimOverlay.update(gm, points, from, to, keptColor, removedColor)
    }

    override fun moveReplayMarker(at: MapPoint, follow: Boolean) {
        val ll = LatLng(at.lat, at.lng)
        replayMarker?.position = ll
        if (follow) runCatching { map?.moveCamera(CameraUpdateFactory.newLatLng(ll)) }
    }

    override fun frameTrack(points: List<RidePoint>) {
        map?.let { frameRideTrack(it, points) }
    }

    override fun showUserMarker(at: MapPoint, headingDegrees: Float?) {
        val gm = map ?: return
        userMarker.update(gm, LatLng(at.lat, at.lng), headingDegrees)
    }

    override fun hideUserMarker() = userMarker.remove()

    override fun recenter(at: MapPoint) {
        map?.glide(CameraUpdateFactory.newLatLngZoom(LatLng(at.lat, at.lng), 16f), KtmMotionSpecs.CAMERA_RECENTER_MS)
    }

    /** Reset the map to north-up (bearing 0, tilt 0), keeping centre and zoom. */
    override fun resetNorth() {
        val gm = map ?: return
        val north = CameraPosition.Builder(gm.cameraPosition).bearing(0f).tilt(0f).build()
        gm.glide(CameraUpdateFactory.newCameraPosition(north), KtmMotionSpecs.CAMERA_RECENTER_MS)
    }

    @SuppressLint("MissingPermission") // only called while guiding, which needs location
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
    override fun setGuidanceChrome(guidance: Boolean) {
        val view = navView ?: return
        runCatching { view.setNavigationUiEnabled(guidance) }
        runCatching { view.setHeaderEnabled(false) }
        runCatching { view.setEtaCardEnabled(false) }
        runCatching { view.setSpeedometerEnabled(false) }
        runCatching { view.setRecenterButtonEnabled(false) }
        runCatching { view.setReportIncidentButtonEnabled(false) }
        runCatching { view.setTrafficIncidentCardsEnabled(false) }
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
    @SuppressLint("MissingPermission") // the caller checks the permission
    internal fun setPuck(enabled: Boolean) {
        runCatching { map?.isMyLocationEnabled = enabled }
    }
}

@Composable
fun rememberMapController(retained: RetainedNavigationView, markerStyle: LocationMarkerStyle): NavSdkMapController {
    val context = LocalContext.current
    return remember(retained) { NavSdkMapController(retained, markerStyle, context) }
}
