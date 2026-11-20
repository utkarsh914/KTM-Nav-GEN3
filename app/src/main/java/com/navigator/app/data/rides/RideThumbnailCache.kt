package com.navigator.app.data.rides

import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore

/** A fix reduced to what a list thumbnail draws. */
/** [gap]: the line breaks before this point (a pause or a merge seam). [speedKmh] colours the line; null = neutral. */
data class ThumbPoint(val lat: Double, val lng: Double, val speedKmh: Float? = null, val gap: Boolean = false)

/** Few points are enough for a 64 dp route preview; the full track is only read for the rides on screen. */
object RideThumbnail {
    const val MAX_POINTS = 64

    /** At most [max] points, evenly spread, always keeping the first and the last. Points that start a gap are kept first. */
    fun downsample(points: List<RidePoint>, max: Int = MAX_POINTS): List<ThumbPoint> {
        if (points.isEmpty() || max < 2) return emptyList()
        fun thumb(p: RidePoint) = ThumbPoint(p.lat, p.lng, p.speedKmh, p.gapBefore)
        if (points.size <= max) return points.map(::thumb)
        val last = points.lastIndex
        val gaps = points.indices.filter { points[it].gapBefore }.take(max / 2)
        val keep = (0 until max - gaps.size).map { i -> (i.toLong() * last / (max - gaps.size - 1)).toInt() }
        return (keep + gaps).distinct().sorted().map { thumb(points[it]) }
    }
}

/**
 * Downsampled tracks for the list, kept in memory only (never in the index). Least recently used goes first.
 * An entry is keyed by ride and point count, so a trimmed or merged ride is read again. Blocking: call on IO.
 */
class RideThumbnailCache(
    private val store: RideStore,
    private val capacity: Int = 64,
    private val load: (String) -> List<RidePoint> = store::points,
) {
    private val lru = object : LinkedHashMap<String, List<ThumbPoint>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ThumbPoint>>?) = size > capacity
    }

    fun get(id: String, pointCount: Int): List<ThumbPoint> {
        val key = "$id:$pointCount"
        synchronized(lru) { lru[key]?.let { return it } }
        val thumb = RideThumbnail.downsample(load(id))
        if (thumb.isNotEmpty()) synchronized(lru) { lru[key] = thumb } // an unreadable track is tried again
        return thumb
    }

    internal fun size(): Int = synchronized(lru) { lru.size }
}
