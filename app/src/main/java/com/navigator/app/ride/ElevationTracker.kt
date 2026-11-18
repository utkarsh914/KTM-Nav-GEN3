package com.navigator.app.ride

/**
 * Climb / descent from noisy GPS altitude: a rolling median of 3 removes single-fix spikes and a
 * hysteresis threshold ignores wobble, so a flat road doesn't accumulate hundreds of "metres".
 * A fix after a pause re-anchors without counting the jump. Pure, JVM-testable.
 */
class ElevationTracker(private val thresholdM: Float = THRESHOLD_M) {
    private val recent = ArrayDeque<Float>()
    private var anchor: Float? = null
    private var gain = 0f
    private var loss = 0f
    private var samples = 0
    // A pause that arrived on a fix without altitude still applies to the next fix that has one.
    private var pendingGap = false

    fun add(altM: Float?, afterGap: Boolean) {
        if (afterGap) pendingGap = true
        if (altM == null || !altM.isFinite()) return
        samples++
        val afterGap = pendingGap
        pendingGap = false
        if (afterGap) recent.clear()
        recent.addLast(altM)
        if (recent.size > 3) recent.removeFirst()
        val smoothed = if (recent.size == 3) recent.sorted()[1] else recent.average().toFloat()
        val a = anchor
        if (a == null || afterGap) { anchor = smoothed; return }
        val d = smoothed - a
        if (d >= thresholdM) { gain += d; anchor = smoothed }
        else if (d <= -thresholdM) { loss += -d; anchor = smoothed }
    }

    /** -1 ([ELEV_UNKNOWN]) when no fix carried an altitude. */
    fun gainM(): Int = if (samples == 0) ELEV_UNKNOWN else Math.round(gain)
    fun lossM(): Int = if (samples == 0) ELEV_UNKNOWN else Math.round(loss)

    companion object {
        const val THRESHOLD_M = 4f
    }
}
