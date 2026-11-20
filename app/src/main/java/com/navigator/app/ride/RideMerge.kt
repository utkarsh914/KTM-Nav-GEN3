package com.navigator.app.ride

enum class MergeRefusal { TooFew, Active, Overlap, MissingTrack, Failed }

/** Undo handle for a merge: the merged ride to drop and the originals to bring back. */
data class MergeToken(val mergedId: String, val originalIds: List<String>)

sealed interface MergeResult {
    data class Merged(val ride: RecordedRide, val token: MergeToken) : MergeResult
    data class Refused(val reason: MergeRefusal) : MergeResult
}

/** The pure parts of merging rides. */
object RideMerge {
    /** Rides in time order, or why they can't be merged. [isActive] says a ride is still being recorded. */
    fun order(rides: List<RecordedRide>, isActive: (String) -> Boolean): Pair<List<RecordedRide>, MergeRefusal?> {
        val sorted = rides.sortedBy { it.startMs }
        val refusal = when {
            sorted.size < 2 -> MergeRefusal.TooFew
            sorted.any { isActive(it.id) } -> MergeRefusal.Active
            sorted.zipWithNext().any { (a, b) -> b.startMs < a.endMs } -> MergeRefusal.Overlap
            else -> null
        }
        return sorted to refusal
    }

    /** The parts joined in order; each later part starts a gap, so the hop between rides is not riding. */
    fun concat(parts: List<List<RidePoint>>): List<RidePoint> =
        parts.flatMapIndexed { i, pts -> if (i == 0) pts else pts.mapIndexed { j, p -> if (j == 0) p.copy(gapBefore = true, seamBefore = true) else p } }

    /**
     * How many parts a merged ride's track still has. Marked seams are exact (so a trim that drops a part lowers it);
     * a ride merged before seams were marked is inferred: [storedParts] if it was recorded, else every gap counts,
     * but never more parts than the track has segments.
     */
    fun partsIn(points: List<RidePoint>, storedParts: Int): Int {
        val marked = points.count { it.seamBefore }
        if (marked > 0) return (marked + 1).coerceAtMost(RideJson.MAX_PARTS)
        val gaps = points.withIndex().count { (i, p) -> i > 0 && p.gapBefore }
        return when {
            gaps == 0 || storedParts == 0 -> 1
            storedParts > 1 -> minOf(storedParts, gaps + 1)
            else -> (gaps + 1).coerceAtMost(RideJson.MAX_PARTS)
        }
    }

    /** Which gaps (indices into [points]) are merge seams rather than pauses: all of them for a ride merged before seams were marked. */
    fun seamIndices(points: List<RidePoint>, merged: Boolean, storedParts: Int = 1): Set<Int> {
        if (!merged) return emptySet()
        val marked = points.indices.filter { points[it].seamBefore }.toSet()
        if (marked.isNotEmpty() || storedParts == 0) return marked
        return points.indices.filter { it > 0 && points[it].gapBefore }.toSet()
    }

    /** [points] of a ride merged before seams were marked, with its inferred seams marked, so merging it again keeps its parts. */
    fun withSeamsMarked(points: List<RidePoint>, merged: Boolean, storedParts: Int): List<RidePoint> {
        val seams = seamIndices(points, merged, storedParts)
        if (seams.isEmpty() || points.any { it.seamBefore }) return points
        return points.mapIndexed { i, p -> if (i in seams) p.copy(seamBefore = true) else p }
    }

    fun notes(rides: List<RecordedRide>): String? =
        RideTrim.cleanNotes(rides.mapNotNull { it.notes?.takeIf(String::isNotBlank) }.joinToString("\n\n"))
}

/** How long the replay waits between fix [i] and the next: no time at all across a pause or a merge seam. */
object ReplayPacing {
    fun stepMs(points: List<RidePoint>, i: Int): Long {
        val next = points.getOrNull(i + 1) ?: return 0L
        if (next.gapBefore) return 0L
        return (next.timeMs - points[i].timeMs).coerceAtLeast(0L)
    }
}
