package com.navigator.app.nav.ktm

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The time of day the rider reads: arrival times, leg lines, the Live Update. It follows the phone's 12 / 24 hour
 * setting ([use24h], set once at startup). The dash itself is always 24 h and zero-padded: that is [EtaFormatter].
 */
object ClockFormat {
    /** Reads the phone's setting; 24 h until the application installs the real one. */
    @Volatile var use24h: () -> Boolean = { true }

    fun local(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        return if (use24h()) EtaFormatter.format(epochMs, zone) else DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(t)
    }
}
