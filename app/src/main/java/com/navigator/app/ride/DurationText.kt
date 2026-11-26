package com.navigator.app.ride

import android.content.res.Resources
import com.navigator.app.R

/** One wording and one rounding rule (up to the next minute) for every duration the app shows: "<1 min", "45 min", "1 h 05 min". */
object DurationText {
    /** The three templates; `%d` for the minutes, `%1$d h %2$02d` for hours and minutes. */
    data class Format(val lessThanMinute: String, val minutes: String, val hoursMinutes: String) {
        companion object {
            val English = Format("<1 min", "%d min", "%d h %02d min")

            fun of(res: Resources) = Format(
                res.getString(R.string.duration_less_than_min),
                res.getString(R.string.duration_min_template),
                res.getString(R.string.duration_hm_template),
            )
        }
    }

    fun format(seconds: Int, f: Format = Format.English): String {
        val minutes = (seconds.coerceAtLeast(0) + 59) / 60
        return when {
            minutes < 1 -> f.lessThanMinute
            minutes < 60 -> f.minutes.format(minutes)
            else -> f.hoursMinutes.format(minutes / 60, minutes % 60)
        }
    }

    fun of(res: Resources, seconds: Int): String = format(seconds, Format.of(res))
}
