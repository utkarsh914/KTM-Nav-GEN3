package com.navigator.app.ui.map

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
import kotlin.math.cos

/**
 * Shared ride-track rendering used by both the full ride-replay screen and the
 * trip-finished summary: a speed-coloured polyline (green slow → red fast) with
 * start/end markers, plus a helper to frame the whole track. Kept in one place
 * so the track looks identical wherever it appears.
 *
 * The number of bands and their speed thresholds are configurable
 * ([RideMetrics.SpeedBands]); colours are interpolated on an HSV hue ramp from
 * green (slow) to red (fast) so any band count still reads slow→fast.
 *
 * ## Zoom performance
 * Rides record at ~1 fix/sec, so a 1-2 h ride is thousands of points. Naively
 * drawing one polyline per speed-band run gave **hundreds of polylines with
 * thousands of total vertices** (speed constantly jitters across band
 * thresholds), and the map re-tessellates every polyline on every zoom frame -
 * the cause of ride-replay zoom lag. Three cheap passes fix it without changing
 * how the track looks at normal zoom:
 *  1. band classification with **hysteresis** so speed jitter near a threshold
 *     doesn't flip bands every point,
 *  2. **micro-run merging** so a stray point or two can't spawn its own polyline,
 *  3. **per-run Douglas-Peucker simplification** (~[SIMPLIFY_EPSILON_M] m), plus
 *     a global **vertex budget** ([MAX_TRACK_VERTICES]) as a hard upper bound.
 */

/** Line width of a drawn ride track. */
private const val TRACK_WIDTH = 12f

/** Hue (HSV degrees) for the slowest band (green) and fastest band (red). */
private const val HUE_SLOW = 140f // green
private const val HUE_FAST = 0f // red

/** Douglas-Peucker tolerance: points within this perpendicular distance of the
 *  simplified segment are dropped. ~6 m is imperceptible at normal zoom but
 *  removes the dense near-collinear GPS samples along straight roads.
 *  (internal for tests.) */
internal const val SIMPLIFY_EPSILON_M = 6.0

/** Runs shorter than this (after hysteresis) are merged into the previous run so
 *  a brief speed blip doesn't create its own tiny polyline. */
private const val MIN_RUN_POINTS = 4

/** Hard cap on total vertices drawn across all polylines; if simplification still
 *  leaves more, the track is uniformly strided down to this budget. Keeps the
 *  per-zoom re-tessellation cost bounded no matter how long the ride. */
private const val MAX_TRACK_VERTICES = 1500

/** Speed margin (km/h) a point must exceed a band boundary by before it's allowed
 *  to switch bands - prevents flapping when speed hovers on a threshold. */
private const val BAND_HYSTERESIS_KMH = 3f

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
 *  When [withEndpoints] is false only the track line is drawn (summary view).
 *  [simplifyEpsilonM] is the Douglas-Peucker tolerance; 0 disables that pass (the
 *  replay screen exposes this as an in-session "Track detail" control). */
internal fun drawRideTrack(
    map: GoogleMap,
    pts: List<RidePoint>,
    bands: RideMetrics.SpeedBands = RideMetrics.SpeedBands.DEFAULT,
    withEndpoints: Boolean = true,
    endpoints: RideEndpointPins? = null,
    simplifyEpsilonM: Double = SIMPLIFY_EPSILON_M,
) {
    map.clear()
    if (pts.isEmpty()) return

    // Optional global down-sample so even a huge ride stays under the vertex
    // budget before we do any per-run work.
    val source = strideToBudget(pts, MAX_TRACK_VERTICES * 3)

    // 1+2. Split into band runs with hysteresis, then merge micro-runs.
    val runs = buildBandRuns(source, bands)

    // 3. Draw each run as one simplified polyline, with the documented total-vertex cap
    //    enforced AFTER simplification (it used to be applied only before it).
    val simplified = capTotalVertices(runs.map { simplifyRun(it.points, simplifyEpsilonM) }, MAX_TRACK_VERTICES)
    runs.forEachIndexed { i, run -> addRideRun(map, simplified[i], run.band, bands.bandCount) }

    if (withEndpoints) {
        map.addMarker(
            MarkerOptions().position(LatLng(pts.first().lat, pts.first().lng)).anchor(0.5f, 0.5f)
                .icon(endpoints?.start ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)),
        )
        map.addMarker(
            MarkerOptions().position(LatLng(pts.last().lat, pts.last().lng)).anchor(0.5f, 0.5f)
                .icon(endpoints?.end ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)),
        )
    }
}

/** The camera update that frames the whole track in the (padded) viewport, or null for no points. */
internal fun trackFrame(pts: List<RidePoint>, padding: Int): com.google.android.gms.maps.CameraUpdate? {
    if (pts.isEmpty()) return null
    val bounds = LatLngBounds.builder()
    pts.forEach { bounds.include(LatLng(it.lat, it.lng)) }
    return runCatching { CameraUpdateFactory.newLatLngBounds(bounds.build(), padding) }.getOrNull()
}

// ---- Track reduction (pure, unit-tested) -----------------------------------

/** A consecutive run of points sharing one speed band. */
internal data class BandRun(val band: Int, val points: List<RidePoint>)

/**
 * Split [pts] into consecutive same-band runs, using hysteresis so a point only
 * switches band when its speed crosses the next boundary by [BAND_HYSTERESIS_KMH],
 * then merge any run shorter than [MIN_RUN_POINTS] into the previous run. Both
 * passes cut the polyline count dramatically on real (jittery) speed traces.
 */
internal fun buildBandRuns(
    pts: List<RidePoint>,
    bands: RideMetrics.SpeedBands,
): List<BandRun> {
    if (pts.none { it.gapBefore }) return buildSegmentRuns(pts, bands)
    // A user pause: the line must not be drawn across the unrecorded gap. Each segment is
    // reduced on its own (the merge steps assume runs that share boundary points).
    val out = ArrayList<BandRun>()
    var start = 0
    for (i in 1..pts.lastIndex) {
        if (pts[i].gapBefore) {
            out += buildSegmentRuns(pts.subList(start, i), bands)
            start = i
        }
    }
    out += buildSegmentRuns(pts.subList(start, pts.size), bands)
    return out
}

private fun buildSegmentRuns(
    pts: List<RidePoint>,
    bands: RideMetrics.SpeedBands,
): List<BandRun> {
    if (pts.isEmpty()) return emptyList()

    // Hysteresis band assignment: keep the current band unless the raw band is
    // different AND the speed is clear of the boundary by the margin.
    val assigned = IntArray(pts.size)
    var current = bands.bandOf(pts[0].speedKmh)
    assigned[0] = current
    for (i in 1..pts.lastIndex) {
        val raw = bands.bandOf(pts[i].speedKmh)
        current = if (raw != current && clearsBoundary(pts[i].speedKmh, current, bands)) raw else current
        assigned[i] = current
    }

    // Group into runs. Consecutive runs SHARE the boundary point (subList end is
    // i+1) so the coloured polylines join with no visual gap, matching the
    // original renderer.
    val runs = ArrayList<BandRun>()
    var start = 0
    for (i in 1..pts.lastIndex) {
        if (assigned[i] != assigned[i - 1]) {
            runs.add(BandRun(assigned[start], pts.subList(start, i + 1)))
            start = i
        }
    }
    runs.add(BandRun(assigned[start], pts.subList(start, pts.size)))

    return mergeMicroRuns(runs)
}

/**
 * True when [speedKmh] is far enough past the boundary between the current band
 * and its neighbour to justify a switch (by [BAND_HYSTERESIS_KMH]).
 */
private fun clearsBoundary(
    speedKmh: Float?,
    currentBand: Int,
    bands: RideMetrics.SpeedBands,
): Boolean {
    val s = speedKmh ?: return true // no speed -> band 0; let it switch freely
    // The boundary above the current band (or, when slowing, below it).
    val upper = bands.upperBoundKmh(currentBand)?.toFloat()
    val lower = if (currentBand > 0) bands.upperBoundKmh(currentBand - 1)?.toFloat() else null
    return when {
        upper != null && s >= upper + BAND_HYSTERESIS_KMH -> true // speeding up
        lower != null && s < lower - BAND_HYSTERESIS_KMH -> true // slowing down
        else -> false
    }
}

/** Fold runs shorter than [MIN_RUN_POINTS] into the previous run (or the next,
 *  for a short leading run), so brief blips don't each become a polyline. */
private fun mergeMicroRuns(runs: List<BandRun>): List<BandRun> {
    if (runs.size <= 1) return runs
    // Pass 1: fold short runs into the previous run's band. Runs share their
    // boundary point, so skip the appended run's first point (no duplicate vertex).
    val folded = ArrayList<BandRun>(runs.size)
    for (run in runs) {
        val last = folded.lastOrNull()
        if (last != null && run.points.size < MIN_RUN_POINTS) {
            folded[folded.lastIndex] = BandRun(last.band, last.points + run.points.drop(1))
        } else {
            folded.add(run)
        }
    }
    // A short leading run may remain at index 0; fold it into its neighbour.
    if (folded.size > 1 && folded[0].points.size < MIN_RUN_POINTS) {
        folded[1] = BandRun(folded[1].band, folded[0].points + folded[1].points.drop(1))
        folded.removeAt(0)
    }
    // Pass 2: folding can leave adjacent runs of the SAME band (e.g. band0, blip,
    // band0 -> band0, band0); coalesce them into one polyline.
    val out = ArrayList<BandRun>(folded.size)
    for (run in folded) {
        val last = out.lastOrNull()
        if (last != null && last.band == run.band) {
            out[out.lastIndex] = BandRun(last.band, last.points + run.points.drop(1))
        } else {
            out.add(run)
        }
    }
    return out
}

/**
 * Douglas-Peucker line simplification keyed on geographic perpendicular distance
 * in metres ([epsilonM]). Returns the reduced point list (endpoints always kept).
 * Iterative (explicit stack) so a long run can't overflow the call stack.
 */
internal fun simplifyRun(points: List<RidePoint>, epsilonM: Double): List<RidePoint> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, points.lastIndex))
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        var maxDist = 0.0
        var index = -1
        for (i in first + 1 until last) {
            val d = perpDistanceM(points[i], points[first], points[last])
            if (d > maxDist) { maxDist = d; index = i }
        }
        if (index != -1 && maxDist > epsilonM) {
            keep[index] = true
            stack.addLast(intArrayOf(first, index))
            stack.addLast(intArrayOf(index, last))
        }
    }
    val out = ArrayList<RidePoint>()
    for (i in points.indices) if (keep[i]) out.add(points[i])
    return out
}

/**
 * Perpendicular distance (metres) from point [p] to the segment [a]-[b], using a
 * local equirectangular projection (lat/lng → metres) around [a]. Accurate for
 * the short spans between consecutive fixes.
 */
private fun perpDistanceM(p: RidePoint, a: RidePoint, b: RidePoint): Double {
    val mPerDegLat = 111_320.0
    val mPerDegLng = 111_320.0 * cos(Math.toRadians(a.lat))
    val ax = 0.0
    val ay = 0.0
    val bx = (b.lng - a.lng) * mPerDegLng
    val by = (b.lat - a.lat) * mPerDegLat
    val px = (p.lng - a.lng) * mPerDegLng
    val py = (p.lat - a.lat) * mPerDegLat
    val dx = bx - ax
    val dy = by - ay
    val segLen2 = dx * dx + dy * dy
    if (segLen2 == 0.0) return kotlin.math.hypot(px, py) // a == b
    // Projection factor of p onto the segment, clamped to the segment.
    val t = ((px - ax) * dx + (py - ay) * dy) / segLen2
    val tc = t.coerceIn(0.0, 1.0)
    val cx = ax + tc * dx
    val cy = ay + tc * dy
    return kotlin.math.hypot(px - cx, py - cy)
}

/**
 * Enforce a total vertex budget across all polylines: when the sum exceeds [max], every run
 * is strided by the same factor (keeping each run's first and last point so runs still join).
 */
internal fun capTotalVertices(runs: List<List<RidePoint>>, max: Int): List<List<RidePoint>> {
    val total = runs.sumOf { it.size }
    if (total <= max || max < 2) return runs
    val step = (total + max - 1) / max // ceil
    return runs.map { run ->
        if (run.size <= 2) run else {
            val out = ArrayList<RidePoint>(run.size / step + 2)
            var i = 0
            while (i < run.size) { out.add(run[i]); i += step }
            if (out.last() !== run.last()) out.add(run.last())
            out
        }
    }
}

/** Uniformly stride [pts] down to at most [budget] points (endpoints kept). A
 *  coarse pre-pass so per-run simplification never starts from an enormous list. */
internal fun strideToBudget(pts: List<RidePoint>, budget: Int): List<RidePoint> {
    if (pts.size <= budget || budget < 2) return pts
    val step = (pts.size + budget - 1) / budget // ceil
    val out = ArrayList<RidePoint>(budget + 1)
    // Pause markers are kept even when striding skips them, so a thinned track still breaks there.
    for (j in pts.indices) {
        if (j % step == 0 || pts[j].gapBefore) out.add(pts[j])
    }
    if (out.last() !== pts.last()) out.add(pts.last())
    return out
}

private fun addRideRun(map: GoogleMap, run: List<RidePoint>, bandIndex: Int, bandCount: Int) {
    if (run.size < 2) return
    map.addPolyline(
        PolylineOptions()
            .addAll(run.map { LatLng(it.lat, it.lng) })
            .color(bandColor(bandIndex, bandCount))
            .width(TRACK_WIDTH),
    )
}

/**
 * The trim page's map: the part the cut keeps over the parts it removes, with a marker at each cut. Handle moves update
 * the lines and markers in place (the map is only cleared when another ride is shown), so dragging does not flicker.
 */
internal class TrimPreviewOverlay(private val endpoints: RideEndpointPins) {
    private val lines = Array(3) { ArrayList<com.google.android.gms.maps.model.Polyline>() }
    private var startMarker: com.google.android.gms.maps.model.Marker? = null
    private var endMarker: com.google.android.gms.maps.model.Marker? = null
    private var shownFor: List<RidePoint>? = null

    fun forget() { lines.forEach { it.clear() }; startMarker = null; endMarker = null; shownFor = null }

    fun update(map: GoogleMap, pts: List<RidePoint>, from: Int, to: Int, keptColor: Int, removedColor: Int) {
        if (shownFor !== pts) { map.clear(); forget(); shownFor = pts }
        if (pts.size < 2) return
        draw(map, 0, pts, 0..from, removedColor, 0f)
        draw(map, 1, pts, to..pts.lastIndex, removedColor, 0f)
        draw(map, 2, pts, from..to, keptColor, 1f)
        startMarker = place(map, startMarker, pts[from], endpoints.start)
        endMarker = place(map, endMarker, pts[to], endpoints.end)
    }

    private fun place(map: GoogleMap, marker: com.google.android.gms.maps.model.Marker?, at: RidePoint, icon: com.google.android.gms.maps.model.BitmapDescriptor): com.google.android.gms.maps.model.Marker? {
        val ll = LatLng(at.lat, at.lng)
        if (marker != null) { marker.position = ll; return marker }
        return map.addMarker(MarkerOptions().position(ll).anchor(0.5f, 0.5f).icon(icon).zIndex(2f))
    }

    private fun draw(map: GoogleMap, slot: Int, pts: List<RidePoint>, range: IntRange, color: Int, z: Float) {
        val segments = ArrayList<List<LatLng>>()
        if (range.first < range.last) {
            val part = strideToBudget(pts.subList(range.first, range.last + 1), MAX_TRACK_VERTICES / 3)
            // A pause or a merge seam inside the part is a break, not a line across the gap.
            var start = 0
            for (i in 1..part.size) {
                if (i == part.size || part[i].gapBefore) {
                    val seg = part.subList(start, i)
                    if (seg.size >= 2) segments += seg.map { LatLng(it.lat, it.lng) }
                    start = i
                }
            }
        }
        val have = lines[slot]
        segments.forEachIndexed { i, seg ->
            if (i < have.size) { have[i].points = seg; have[i].color = color }
            else have += map.addPolyline(PolylineOptions().addAll(seg).color(color).width(TRACK_WIDTH).zIndex(z))
        }
        while (have.size > segments.size) have.removeAt(have.lastIndex).remove()
    }
}
