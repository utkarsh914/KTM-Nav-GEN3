package com.navigator.app.ui.feature.rides.merge

import com.navigator.app.data.rides.RideThumbnail
import com.navigator.app.data.rides.ThumbPoint
import com.navigator.app.ride.MergeRefusal
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideMerge
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint

/** One ride in the merge list. */
data class MergePart(val ride: RecordedRide)

/** The stop between two parts: not riding, so it adds nothing to the distance or the riding time. */
data class MergeGap(val seconds: Int)

/** Why the merge can't go ahead, naming the two rides when they overlap. */
sealed interface MergeBlock {
    data class Overlap(val first: RecordedRide, val second: RecordedRide) : MergeBlock
    data object Active : MergeBlock
    data object TooFew : MergeBlock
}

/** What the preview shows, computed from the rides and their points before anything is written. */
data class MergePlan(
    val parts: List<MergePart>,
    /** One fewer than [parts]: the stop after part i. */
    val gaps: List<MergeGap>,
    val distanceMeters: Int,
    val ridingSeconds: Int,
    val totalSeconds: Int,
    val anyStarred: Boolean,
    val name: String?,
    /** The joined track, thinned for drawing, with the seams marked. */
    val route: List<ThumbPoint>,
    val block: MergeBlock?,
)

object MergePlanner {
    /** [pointsOf] gives a ride's track; it is only read when the rides can be merged. */
    fun plan(rides: List<RecordedRide>, isActive: (String) -> Boolean, pointsOf: (String) -> List<RidePoint>): MergePlan {
        val (ordered, refusal) = RideMerge.order(rides, isActive)
        val block = when (refusal) {
            null -> null
            MergeRefusal.Overlap -> ordered.zipWithNext().first { (a, b) -> b.startMs < a.endMs }.let { MergeBlock.Overlap(it.first, it.second) }
            MergeRefusal.Active -> MergeBlock.Active
            else -> MergeBlock.TooFew
        }
        val gaps = ordered.zipWithNext().map { (a, b) -> MergeGap(((b.startMs - a.endMs) / 1000).toInt().coerceAtLeast(0)) }
        if (block != null || ordered.size < 2) {
            return MergePlan(
                ordered.map(::MergePart), gaps, ordered.sumOf { it.distanceMeters }, 0, ordered.sumOf { it.durationSeconds },
                ordered.any { it.saved }, ordered.firstOrNull()?.destinationLabel,
                emptyList(), block ?: MergeBlock.TooFew,
            )
        }
        val joined = RideMerge.concat(ordered.map { pointsOf(it.id) })
        val stats = RideMetrics.summarize(joined)
        return MergePlan(
            parts = ordered.map(::MergePart), gaps = gaps, distanceMeters = stats.distanceMeters,
            ridingSeconds = stats.movingSeconds.coerceAtLeast(0), totalSeconds = stats.durationSeconds,
            anyStarred = ordered.any { it.saved }, name = ordered.first().destinationLabel,
            route = RideThumbnail.downsample(joined, ROUTE_POINTS), block = null,
        )
    }

    /** More points than a list thumbnail, since the preview is larger. */
    const val ROUTE_POINTS = 300
}
