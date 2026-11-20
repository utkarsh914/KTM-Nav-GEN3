package com.navigator.app.ui.feature.replay

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Clock times of a recorded fix, in the rider's locale and time zone. */
object ReplayFormat {
    /** Built once per locale: the marker label is formatted on every playback frame. */
    private class Formats(val locale: Locale) {
        val clock: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
        val clockShort: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val dateTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }

    @Volatile private var formats = Formats(Locale.getDefault())

    private fun current(): Formats {
        val locale = Locale.getDefault()
        return formats.takeIf { it.locale == locale } ?: Formats(locale).also { formats = it }
    }

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    /** The time of day at the marker, with seconds (14:32:08). */
    fun timeOfDay(ms: Long): String = current().clock.format(at(ms))

    /** The time of day for an axis label (14:32). */
    fun axisTime(ms: Long): String = current().clockShort.format(at(ms))

    fun rideDate(ms: Long): String = if (ms <= 0L) "" else current().dateTime.format(at(ms))
}
