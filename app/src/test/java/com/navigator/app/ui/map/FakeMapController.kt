package com.navigator.app.ui.map

import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [MapController] that records what it was asked to draw and lets a test fire the map's events. */
class FakeMapController : MapController {
    override val ready = MutableStateFlow(true)
    override val bearing = MutableStateFlow(0f)
    override val longClicks = MutableSharedFlow<MapPoint>(extraBufferCapacity = 8)
    override val poiClicks = MutableSharedFlow<MapPoi>(extraBufferCapacity = 8)
    override val polylineClicks = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    override val gesturePans = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val cameraIdle = MutableSharedFlow<MapPoint>(extraBufferCapacity = 8)
    override val mapClicks = MutableSharedFlow<MapPoint>(extraBufferCapacity = 8)
    var center: MapPoint? = null
    var idleListening = false

    /** Everything asked of the map, in order, as short strings (`place`, `preview`, `clear`, ...). */
    val calls = mutableListOf<String>()
    var padding: List<Int> = listOf(0, 0, 0, 0)
    var place: MapPoint? = null
    var preview: TripPreviewScene? = null
    var track: List<RidePoint>? = null
    var userMarker: Pair<MapPoint, Float?>? = null
    var followMode: FollowMode? = null
    var overviewShown = 0
    var dark: Boolean? = null
    var guiding: Boolean? = null

    override fun cameraCenter() = center
    var zoom: Float? = null
    override fun cameraZoom() = zoom
    override fun setCameraIdleListening(enabled: Boolean) { idleListening = enabled }
    var poisOn = false
    var gesturesOn = false
    override fun setGestureListening(enabled: Boolean) { gesturesOn = enabled }
    override fun setPoiListening(enabled: Boolean) { poisOn = enabled }
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) { padding = listOf(left, top, right, bottom) }

    override fun clearOverlays() {
        calls += "clear"; place = null; preview = null; track = null; userMarker = null
    }

    override fun showPlace(place: MapPoint, title: String?, moveCamera: Boolean) { calls += "place"; this.place = place }
    override fun showTripPreview(scene: TripPreviewScene) { calls += "preview"; preview = scene }
    override fun showTrack(points: List<RidePoint>, bands: RideMetrics.SpeedBands) { calls += "track"; track = points }
    var clicksOn = false
    var replay: List<RidePoint>? = null
    var replaySmoothing: Double? = null
    var replayMarker: MapPoint? = null
    var replayFollow = false
    override fun setMapClickListening(enabled: Boolean) { clicksOn = enabled }
    override fun showReplay(points: List<RidePoint>, bands: RideMetrics.SpeedBands, smoothingMeters: Double, markerAt: MapPoint, markerColor: Int) {
        calls += "replay"; replay = points; replaySmoothing = smoothingMeters; replayMarker = markerAt
    }
    override fun moveReplayMarker(at: MapPoint, follow: Boolean) { replayMarker = at; replayFollow = follow }
    var trim: Triple<Int, Int, Int>? = null
    override fun showTrimPreview(points: List<RidePoint>, from: Int, to: Int, keptColor: Int, removedColor: Int) { calls += "trim"; trim = Triple(points.size, from, to) }
    override fun frameTrack(points: List<RidePoint>, animate: Boolean) { calls += if (animate) "frame" else "frameJump" }
    override fun showUserMarker(at: MapPoint, headingDegrees: Float?) { userMarker = at to headingDegrees }
    override fun hideUserMarker() { userMarker = null }
    override fun recenter(at: MapPoint) { calls += "recenter" }
    override fun resetNorth() { calls += "north"; bearing.value = 0f }
    override fun followMyLocation(mode: FollowMode) { calls += "follow"; followMode = mode }
    override fun showRouteOverview() { overviewShown++ }
    override fun setGuidanceChrome(guidance: Boolean) { guiding = guidance }
    override fun setNightMode(dark: Boolean) { this.dark = dark }
}
