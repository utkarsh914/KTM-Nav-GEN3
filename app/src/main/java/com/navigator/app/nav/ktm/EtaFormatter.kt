package com.navigator.app.nav.ktm

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Derives the wall-clock arrival time from "seconds remaining" + "now".
 *
 * The Google Nav SDK exposes only remaining seconds, not an ETA epoch, so we
 * compute `now + remaining` and format the local time as bare 24h "HH:MM"
 * (the firmware wants plain numeric time, ASCII digits, zero-padded).
 *
 * Pure and deterministic: `now` and the zone are injected, so tests don't
 * depend on the wall clock.
 */
object EtaFormatter {

    /** Absolute epoch-ms of arrival for a snapshot produced at [nowEpochMs]. */
    fun etaEpochMs(remainingTimeSeconds: Int, nowEpochMs: Long): Long =
        nowEpochMs + remainingTimeSeconds.coerceAtLeast(0) * 1000L

    /** Format an arrival epoch as local "HH:MM" (24h, zero-padded, ASCII digits). */
    fun format(etaEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(etaEpochMs).atZone(zone).toLocalTime()
        return "${pad2(t.hour)}:${pad2(t.minute)}"
    }

    /** Convenience: format directly from remaining seconds + now. */
    fun format(remainingTimeSeconds: Int, nowEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        format(etaEpochMs(remainingTimeSeconds, nowEpochMs), zone)

    /**
     * Anti-flap deadband. Traffic re-estimates nudge the ETA a few seconds back
     * and forth across a minute boundary, which would otherwise flip the shown
     * "HH:MM" repeatedly. Accept a new ETA only when it moves by at least
     * [deadbandMs] from the last accepted one (or when there is none yet).
     */
    fun shouldUpdate(lastEtaEpochMs: Long?, candidateEtaEpochMs: Long, deadbandMs: Long = 60_000L): Boolean =
        lastEtaEpochMs == null || abs(candidateEtaEpochMs - lastEtaEpochMs) >= deadbandMs

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()
}
