package com.navigator.app.notify

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Route lengths for the progress bar's stop points. Pure, so it is JVM-testable. */
object GeoLength {
    private const val EARTH_M = 6_371_000.0

    fun between(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_M * asin(sqrt(a))
    }

    fun polyline(points: List<Pair<Double, Double>>): Double =
        points.zipWithNext().sumOf { (a, b) -> between(a.first, a.second, b.first, b.second) }

    /**
     * Where each intermediate stop sits along the route, in metres from the start, and the route's whole length,
     * from the length of each leg (the last leg ends at the destination, so it has no mark).
     */
    fun marks(legMeters: List<Double>): RouteMarks {
        var sum = 0.0
        val stops = legMeters.dropLast(1).map { sum += it; sum.toInt() }
        return RouteMarks(total = (sum + (legMeters.lastOrNull() ?: 0.0)).toInt(), stops = stops)
    }
}

data class RouteMarks(val total: Int = 0, val stops: List<Int> = emptyList())
