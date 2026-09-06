package com.navigator.app.ui.screens

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint

/**
 * Shared ride-track rendering used by both the full ride-replay screen and the
 * trip-finished summary: a speed-coloured polyline (green slow → red fast) with
 * start/end markers, plus a helper to frame the whole track. Kept in one place
 * so the track looks identical wherever it appears.
 */

/** Speed-band → ARGB colour for the ride track. */
internal val RIDE_BAND_COLORS = mapOf(
    RideMetrics.SpeedBand.SLOW to 0xFF2ECC71.toInt(),      // green
    RideMetrics.SpeedBand.MEDIUM to 0xFFF1C40F.toInt(),    // yellow
    RideMetrics.SpeedBand.FAST to 0xFFE67E22.toInt(),      // orange
    RideMetrics.SpeedBand.VERY_FAST to 0xFFE74C3C.toInt(), // red
)

/** Clear the map and draw the speed-coloured track + start/end markers (no camera).
 *  When [withEndpoints] is false only the track line is drawn (summary view). */
internal fun drawRideTrack(map: GoogleMap, pts: List<RidePoint>, withEndpoints: Boolean = true) {
    map.clear()
    if (pts.isEmpty()) return
    // Split into consecutive same-band runs so each is one coloured polyline.
    var runStart = 0
    var runBand = RideMetrics.band(pts[0].speedKmh)
    for (i in 1..pts.lastIndex) {
        val b = RideMetrics.band(pts[i].speedKmh)
        if (b != runBand) {
            addRideRun(map, pts.subList(runStart, i + 1), runBand)
            runStart = i
            runBand = b
        }
    }
    addRideRun(map, pts.subList(runStart, pts.size), runBand)

    if (withEndpoints) {
        map.addMarker(
            MarkerOptions().position(LatLng(pts.first().lat, pts.first().lng)).title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
        )
        map.addMarker(
            MarkerOptions().position(LatLng(pts.last().lat, pts.last().lng)).title("End")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
        )
    }
}

/** Frame the whole track in the (padded) viewport. */
internal fun frameRideTrack(map: GoogleMap, pts: List<RidePoint>, padding: Int = 140) {
    if (pts.isEmpty()) return
    val bounds = LatLngBounds.builder()
    pts.forEach { bounds.include(LatLng(it.lat, it.lng)) }
    runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding)) }
}

private fun addRideRun(map: GoogleMap, run: List<RidePoint>, band: RideMetrics.SpeedBand) {
    if (run.size < 2) return
    val color = RIDE_BAND_COLORS[band] ?: 0xFF2ECC71.toInt()
    map.addPolyline(
        PolylineOptions()
            .addAll(run.map { LatLng(it.lat, it.lng) })
            .color(color)
            .width(12f),
    )
}
