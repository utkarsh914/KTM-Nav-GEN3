package com.navigator.app.ride

/** Pure ride trimming (JVM-testable): cut a ride's track to the fixes [from]..[to] (inclusive indices). */
object RideTrim {

    const val MAX_NOTES = 500
    const val MAX_NAME = 80

    /** The kept fixes, or null when the range is invalid or leaves fewer than 2 points. */
    fun trim(points: List<RidePoint>, from: Int, to: Int): List<RidePoint>? {
        if (from < 0 || to > points.lastIndex || to - from < 1) return null
        val kept = points.subList(from, to + 1).toMutableList()
        // The new first fix has no predecessor: a pause flag on it would be meaningless.
        if (kept[0].gapBefore || kept[0].seamBefore) kept[0] = kept[0].copy(gapBefore = false, seamBefore = false)
        return kept
    }

    /** Clean a rider-entered name: trimmed, single-line, capped; null when blank. */
    fun cleanName(raw: String?): String? =
        raw?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_NAME)?.ifEmpty { null }

    /** Clean a note: trimmed and capped; line breaks are kept. */
    fun cleanNotes(raw: String?): String? = raw?.trim()?.take(MAX_NOTES)?.ifEmpty { null }
}
