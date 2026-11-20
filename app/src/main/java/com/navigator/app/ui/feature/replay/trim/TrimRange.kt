package com.navigator.app.ui.feature.replay.trim

import kotlin.math.abs

/** The part of a ride to keep: fixes [from]..[to] of a track whose last fix is [last]. At least two fixes stay. */
data class TrimRange(val from: Int, val to: Int, val last: Int) {
    val changed: Boolean get() = from != 0 || to != last

    fun moveStart(index: Int) = copy(from = index.coerceIn(0, to - 1))
    fun moveEnd(index: Int) = copy(to = index.coerceIn(from + 1, last))

    /** A tap or drag at [index] moves whichever handle is nearer (the start on a tie). */
    fun moveNearest(index: Int): TrimRange = if (abs(index - from) <= abs(index - to)) moveStart(index) else moveEnd(index)

    companion object {
        fun whole(size: Int) = TrimRange(0, (size - 1).coerceAtLeast(1), (size - 1).coerceAtLeast(1))
    }
}
