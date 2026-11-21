package com.navigator.app.ui.feature.replay

import android.text.format.DateFormat
import com.navigator.app.nav.ktm.ClockFormat
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock times of a recorded fix, in the rider's locale and time zone. */
object ReplayFormat {
    /** Built once per locale: the marker label is formatted on every playback frame. */
    private class Formats(val locale: Locale, val h24: Boolean) {
        val clock: DateTimeFormatter = ClockFormat.formatter(locale, seconds = true)
        val clockShort: DateTimeFormatter = ClockFormat.formatter(locale)
        val dateTime: DateTimeFormatter = ClockFormat.formatter(locale, date = "EEEdMMM")
        val day: DateTimeFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
        val dayYear: DateTimeFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyy"), locale)
        val dateTimeYear: DateTimeFormatter = ClockFormat.formatter(locale, date = "EEEdMMMyyyy")
    }

    @Volatile private var formats = Formats(Locale.getDefault(), ClockFormat.use24h())

    private fun current(): Formats {
        val locale = Locale.getDefault()
        val h24 = ClockFormat.use24h()
        return formats.takeIf { it.locale == locale && it.h24 == h24 } ?: Formats(locale, h24).also { formats = it }
    }

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    /** The time of day at the marker, with seconds (14:32:08). */
    fun timeOfDay(ms: Long): String = current().clock.format(at(ms))

    /** The time of day for an axis label (14:32). */
    fun axisTime(ms: Long): String = current().clockShort.format(at(ms))

    /** "Thu 19 Nov". */
    fun rideDay(ms: Long): String = if (ms <= 0L) "" else at(ms).let { (if (thisYear(it)) current().day else current().dayYear).format(it) }

    /** "Thu 19 Nov · 5:51 pm" (the date and the time the ride began). */
    fun rideDate(ms: Long): String = if (ms <= 0L) "" else at(ms).let { (if (thisYear(it)) current().dateTime else current().dateTimeYear).format(it) }

    /** The year is left out for this year's rides, as on the list. */
    private fun thisYear(t: ZonedDateTime) = t.year == Year.now(t.zone).value
}
