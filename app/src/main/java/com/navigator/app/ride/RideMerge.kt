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
        parts.flatMapIndexed { i, pts -> if (i == 0) pts else pts.mapIndexed { j, p -> if (j == 0) p.copy(gapBefore = true) else p } }

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
