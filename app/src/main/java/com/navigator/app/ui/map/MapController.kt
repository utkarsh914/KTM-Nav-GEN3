package com.navigator.app.ui.map

import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A point on the map. Our own type, so screens and tests don't depend on the Maps SDK. */
data class MapPoint(val lat: Double, val lng: Double)

data class MapStop(val point: MapPoint, val title: String)

data class MapPoi(val point: MapPoint, val name: String, val placeId: String)

/** What the planning map shows: the destination, numbered stops and the route alternatives. */
data class TripPreviewScene(
    val destination: MapPoint,
    val destinationTitle: String?,
    val stops: List<MapStop>,
    val routes: List<List<MapPoint>>,
    val selectedRoute: Int,
    /** Extra points the camera must keep in view (where the trip starts, the stops). */
    val frameAlso: List<MapPoint>,
    /** The time label drawn on each route (same order as [routes]); empty for none. */
    val routeLabels: List<String> = emptyList(),
    /** Colours for numbered pins, the destination flag and the labels; null falls back to the SDK's default markers. */
    val style: PreviewPinStyle? = null,
    /** A spot the rider picked on the map and has not added yet. */
    val pending: MapPoint? = null,
    /** Move the camera to frame the route; false when only the markers changed. */
    val reframe: Boolean = true,
)

enum class FollowMode { TILTED, NORTH_UP, HEADING_UP }

/**
 * Everything a screen does to the map, and the few things it hears back. The Navigation SDK's map
 * is behind this, so screens and view models don't touch `GoogleMap`, and tests use a fake.
 */
interface MapController {
    /** True while a map is attached and drawable. */
    val ready: StateFlow<Boolean>

    /** Camera bearing in degrees (the compass shows when it is not north-up). */
    val bearing: StateFlow<Float>

    val longClicks: SharedFlow<MapPoint>
    val poiClicks: SharedFlow<MapPoi>

    /** Index of the route polyline that was tapped. */
    val polylineClicks: SharedFlow<Int>

    /** The rider moved the camera by hand (drag, pinch, tilt), not the app. */
    val gesturePans: SharedFlow<Unit>

    /** Where the camera stops after each move; only emits while [setCameraIdleListening] is on. */
    val cameraIdle: SharedFlow<MapPoint>

    /** The point under the middle of the map, or null before the map is ready. */
    fun cameraCenter(): MapPoint?

    /**
     * Start or stop [cameraIdle]. Off by default: the listener replaces any other on the shared map,
     * so only the screens that need it (Pick on map) switch it on, and off again when they leave.
     */
    fun setCameraIdleListening(enabled: Boolean)

    /**
     * Start or stop [poiClicks] (a tap on a place label). Off by default for the same reason: the
     * listener changes what a tap on the map does, so only the planning preview switches it on.
     */
    fun setPoiListening(enabled: Boolean)

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int)
    fun clearOverlays()

    /** A pin on the picked place, with the camera zoomed to it. */
    fun showPlace(place: MapPoint, title: String?)
    fun showTripPreview(scene: TripPreviewScene)

    /** The speed-coloured track of a ride, framed in the visible map. */
    fun showTrack(points: List<RidePoint>, bands: RideMetrics.SpeedBands)

    /** The compass-driven "you are here" marker used while not guiding. */
    fun showUserMarker(at: MapPoint, headingDegrees: Float?)
    fun hideUserMarker()

    fun recenter(at: MapPoint)
    fun resetNorth()
    fun followMyLocation(mode: FollowMode)
    fun showRouteOverview()

    /**
     * The SDK's own guidance chrome. [guidance] turns the engine and puck on; [stockChrome] shows
     * Google's header, ETA card, speedometer and incident cards (retired with the full-SDK UI);
     * [recenterButton] is Google's re-center button (the design replaces it with our own).
     */
    fun setGuidanceChrome(guidance: Boolean, stockChrome: Boolean, recenterButton: Boolean = true)
    fun setNightMode(dark: Boolean)
}
