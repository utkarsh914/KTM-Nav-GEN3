package com.navigator.app.ui.feature.replay

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Clock times of a recorded fix, in the rider's locale and time zone. */
object ReplayFormat {
    private val clock: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
    private val clockShort: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dateTime: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    /** The time of day at the marker, with seconds (14:32:08). */
    fun timeOfDay(ms: Long): String = clock.format(at(ms))

    /** The time of day for an axis label (14:32). */
    fun axisTime(ms: Long): String = clockShort.format(at(ms))

    fun rideDate(ms: Long): String = if (ms <= 0L) "" else dateTime.format(at(ms))
}
