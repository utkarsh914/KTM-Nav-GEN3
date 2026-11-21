package com.navigator.app.nav.ktm

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The time of day the rider reads (arrival times, leg lines, the Live Update, ride times). It follows the phone's
 * 12 / 24 hour setting ([use24h], installed once at startup) and the locale's own layout. The dash itself is always
 * 24 h and zero-padded: that is [EtaFormatter].
 *
 * [use24h] is process-wide state: a test that reads a rider-facing time through Robolectric pins it first.
 */
object ClockFormat {
    /** Reads the phone's setting; 24 h until the application installs the real one. */
    @Volatile var use24h: () -> Boolean = { true }

    /** The locale's pattern for a time, optionally with seconds, or with a short date in front. */
    fun pattern(locale: Locale = Locale.getDefault(), seconds: Boolean = false, date: String? = null): String {
        val h24 = use24h()
        val skeleton = (date.orEmpty()) + (if (h24) "H" else "h") + "mm" + (if (seconds) "ss" else "")
        return runCatching { DateFormat.getBestDateTimePattern(locale, skeleton) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: ((if (date != null) "yyyy-MM-dd " else "") + if (h24) "HH:mm" + (if (seconds) ":ss" else "") else "h:mm" + (if (seconds) ":ss" else "") + " a")
    }

    private data class Key(val locale: Locale, val h24: Boolean, val seconds: Boolean, val date: String?)
    private val formatters = ConcurrentHashMap<Key, DateTimeFormatter>()

    /** Cached: a list of rides asks for the same one on every row and every recomposition. */
    fun formatter(locale: Locale = Locale.getDefault(), seconds: Boolean = false, date: String? = null): DateTimeFormatter =
        formatters.getOrPut(Key(locale, use24h(), seconds, date)) { DateTimeFormatter.ofPattern(pattern(locale, seconds, date), locale) }

    fun local(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        if (use24h()) EtaFormatter.format(epochMs, zone)
        else formatter().format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
