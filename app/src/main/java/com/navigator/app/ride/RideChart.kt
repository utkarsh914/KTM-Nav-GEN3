package com.navigator.app.ride

/** Speed (and elevation, when recorded) resampled into fixed buckets for the replay chart. */
class ChartSeries(
    val speed: FloatArray,
    /** null when too few fixes carried an altitude to draw a profile. */
    val alt: FloatArray?,
    val maxSpeed: Float,
    val minAlt: Float,
    val maxAlt: Float,
)

object RideChart {

    /** Buckets follow point INDEX, like the replay cursor, so the cursor lines up with the chart. */
    fun build(points: List<RidePoint>, buckets: Int = 120): ChartSeries? {
        if (points.size < 10 || buckets < 2) return null
        val n = minOf(buckets, points.size)
        val speed = FloatArray(n)
        val altRaw = FloatArray(n) { Float.NaN }
        var altCount = 0
        for (b in 0 until n) {
            val from = (b.toLong() * points.size / n).toInt()
            val to = ((b + 1).toLong() * points.size / n).toInt().coerceAtLeast(from + 1)
            var sSum = 0f; var sN = 0; var aSum = 0f; var aN = 0
            for (i in from until minOf(to, points.size)) {
                points[i].speedKmh?.let { sSum += it; sN++ }
                points[i].altM?.let { aSum += it; aN++ }
            }
            speed[b] = if (sN > 0) sSum / sN else 0f
            if (aN > 0) { altRaw[b] = aSum / aN; altCount++ }
        }
        val alt: FloatArray? = if (altCount >= n * 0.3) {
            // Carry the last known altitude over gaps (and back-fill a leading gap).
            var last = altRaw.firstOrNull { !it.isNaN() } ?: 0f
            FloatArray(n) { i -> if (altRaw[i].isNaN()) last else altRaw[i].also { last = it } }
        } else null
        return ChartSeries(
            speed = speed,
            alt = alt,
            maxSpeed = speed.max().coerceAtLeast(10f),
            minAlt = alt?.min() ?: 0f,
            maxAlt = alt?.max() ?: 0f,
        )
    }
}
