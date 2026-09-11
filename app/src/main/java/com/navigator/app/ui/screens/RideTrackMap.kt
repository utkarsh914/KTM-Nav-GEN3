package com.navigator.app.ui.screens

import android.graphics.Color
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
 *
 * The number of bands and their speed thresholds are configurable
 * ([RideMetrics.SpeedBands]); colours are interpolated on an HSV hue ramp from
 * green (slow) to red (fast) so any band count still reads slow→fast.
 */

/** Hue (HSV degrees) for the slowest band (green) and fastest band (red). */
private const val HUE_SLOW = 140f // green
private const val HUE_FAST = 0f // red

/**
 * ARGB colour for band [index] of a [count]-band scheme, interpolated along the
 * green→red hue ramp. index 0 = green, index [count]-1 = red.
 */
internal fun bandColor(index: Int, count: Int): Int {
    if (count <= 1) return Color.HSVToColor(floatArrayOf(HUE_SLOW, 0.72f, 0.86f))
    val t = (index.coerceIn(0, count - 1)).toFloat() / (count - 1)
    val hue = HUE_SLOW + (HUE_FAST - HUE_SLOW) * t
    // Slightly punchier saturation/value toward the fast end for legibility.
    val sat = 0.70f + 0.15f * t
    val value = 0.88f - 0.06f * t
    return Color.HSVToColor(floatArrayOf(hue, sat, value))
}

/** Clear the map and draw the speed-coloured track + start/end markers (no camera).
 *  When [withEndpoints] is false only the track line is drawn (summary view). */
internal fun drawRideTrack(
    map: GoogleMap,
    pts: List<RidePoint>,
    bands: RideMetrics.SpeedBands = RideMetrics.SpeedBands.DEFAULT,
    withEndpoints: Boolean = true,
) {
    map.clear()
    if (pts.isEmpty()) return
    // Split into consecutive same-band runs so each is one coloured polyline.
    var runStart = 0
    var runBand = bands.bandOf(pts[0].speedKmh)
    for (i in 1..pts.lastIndex) {
        val b = bands.bandOf(pts[i].speedKmh)
        if (b != runBand) {
            addRideRun(map, pts.subList(runStart, i + 1), runBand, bands.bandCount)
            runStart = i
            runBand = b
        }
    }
    addRideRun(map, pts.subList(runStart, pts.size), runBand, bands.bandCount)

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

private fun addRideRun(map: GoogleMap, run: List<RidePoint>, bandIndex: Int, bandCount: Int) {
    if (run.size < 2) return
    map.addPolyline(
        PolylineOptions()
            .addAll(run.map { LatLng(it.lat, it.lng) })
            .color(bandColor(bandIndex, bandCount))
            .width(12f),
    )
}
