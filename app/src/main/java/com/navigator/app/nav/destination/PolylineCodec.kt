package com.navigator.app.nav.destination

import kotlin.math.roundToInt

/** Google encoded-polyline writer (the reader is [RoutesClient.decodePolyline]). Pure, JVM-testable. */
object PolylineCodec {

    /** Encode (lat, lng) pairs at 1e-5 precision. */
    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder(points.size * 8)
        var prevLat = 0
        var prevLng = 0
        for ((lat, lng) in points) {
            val la = (lat * 1e5).roundToInt()
            val ln = (lng * 1e5).roundToInt()
            writeValue(sb, la - prevLat)
            writeValue(sb, ln - prevLng)
            prevLat = la
            prevLng = ln
        }
        return sb.toString()
    }

    /** Evenly thin [points] to at most [max] (keeping first and last) so the request stays small. */
    fun <T> thin(points: List<T>, max: Int): List<T> {
        if (points.size <= max || max < 2) return points
        val step = (points.size + max - 1) / max
        val out = ArrayList<T>(max + 1)
        var i = 0
        while (i < points.size) { out += points[i]; i += step }
        if (out.last() !== points.last()) out += points.last()
        return out
    }

    /** Remaining route from the point nearest to ([lat], [lng]) onward (what is still ahead). */
    fun aheadOf(points: List<Pair<Double, Double>>, lat: Double, lng: Double): List<Pair<Double, Double>> {
        if (points.size < 2) return points
        var best = 0
        var bestD = Double.MAX_VALUE
        points.forEachIndexed { i, p ->
            val dLat = p.first - lat
            val dLng = (p.second - lng) * kotlin.math.cos(Math.toRadians(lat))
            val d = dLat * dLat + dLng * dLng
            if (d < bestD) { bestD = d; best = i }
        }
        // Keep at least two points so the polyline stays valid near the destination.
        return points.subList(minOf(best, points.size - 2), points.size)
    }

    private fun writeValue(sb: StringBuilder, value: Int) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append((((0x20 or (v and 0x1f)) + 63)).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
