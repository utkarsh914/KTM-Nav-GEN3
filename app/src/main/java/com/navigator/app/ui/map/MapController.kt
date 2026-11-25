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
    /** Returns once the shell's coverage of the map has stopped moving, so a camera framed next uses the final padding. */
    suspend fun awaitPaddingSettled() {}

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

    /** The camera's zoom level, or null before the map is ready. */
    fun cameraZoom(): Float?

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

    /** With reduced motion the camera jumps instead of gliding (docs/design-system.md). */
    fun setReducedMotion(reduced: Boolean) {}
    fun clearOverlays()

    /** A pin on the picked place, with the camera zoomed to it. */
    fun showPlace(place: MapPoint, title: String?, moveCamera: Boolean = true)
    fun showTripPreview(scene: TripPreviewScene)

    /** The speed-coloured track of a ride, framed in the visible map. */
    fun showTrack(points: List<RidePoint>, bands: RideMetrics.SpeedBands)

    /** A tap on the map itself (not a place label or a route). Only emits while [setMapClickListening] is on. */
    val mapClicks: SharedFlow<MapPoint>
    fun setMapClickListening(enabled: Boolean)

    /**
     * The replay of a ride: the track (simplified to [smoothingMeters], 0 = as recorded) with its start and end, and the
     * playback marker at [markerAt] ringed in [markerColor] (ARGB). The camera stays where it is: see [frameTrack].
     */
    fun showReplay(points: List<RidePoint>, bands: RideMetrics.SpeedBands, smoothingMeters: Double, markerAt: MapPoint, markerColor: Int)

    /** Move the playback marker; with [follow] the camera keeps it in the middle of the visible map. */
    fun moveReplayMarker(at: MapPoint, follow: Boolean)

    /**
     * The trim preview: the fixes [from]..[to] that would be kept in [keptColor], the rest faded in [removedColor] (ARGB),
     * with a start and an end marker at the cut. The camera stays where it is: see [frameTrack].
     */
    fun showTrimPreview(points: List<RidePoint>, from: Int, to: Int, keptColor: Int, removedColor: Int)

    /** Fit the whole track in the visible map; [animate] false jumps (the first frame, behind a cover). */
    fun frameTrack(points: List<RidePoint>, animate: Boolean = true): Boolean

    /** The compass-driven "you are here" marker used while not guiding. */
    fun showUserMarker(at: MapPoint, headingDegrees: Float?)
    fun hideUserMarker()

    fun recenter(at: MapPoint)
    fun resetNorth()
    fun followMyLocation(mode: FollowMode)
    fun showRouteOverview()

    /**
     * Start or stop [gesturePans]. Off by default: the listener replaces any other on the shared map, so only
     * riding switches it on (to turn the map button into Recenter).
     */
    fun setGestureListening(enabled: Boolean)

    /**
     * The guidance engine and puck on or off. Google's own header, ETA card, speedometer, incident cards and
     * Re-center button are always off: the riding screen draws all of that itself.
     */
    fun setGuidanceChrome(guidance: Boolean)
    fun setNightMode(dark: Boolean)
}
