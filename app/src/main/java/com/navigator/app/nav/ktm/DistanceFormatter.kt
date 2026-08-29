package com.navigator.app.nav.ktm

import com.navigator.app.nav.model.DistanceUnits
import kotlin.math.roundToInt

/**
 * Formats a distance in metres into a short, jitter-stable label that fits the
 * KTM `<=8`-char distance/remaining characteristics.
 *
 * Rounding buckets kill GPS jitter (a value wobbling by a few metres never
 * changes the shown text):
 * - metric   `< 1 km` -> nearest 10 m ("110 m"); `< 10 km` -> 0.1 km ("1.2 km");
 *            `>= 10 km` -> integer km ("42 km")
 * - imperial `>= 0.1 mi` -> 0.1 mi ("0.2 mi") / integer mi at `>= 10 mi`;
 *            below that -> nearest 10 ft ("500 ft")
 *
 * Pure and locale-independent (always a '.' decimal, as the firmware expects).
 */
object DistanceFormatter {

    private const val FEET_PER_METER = 1.0 / 0.3048
    private const val METERS_PER_MILE = 1609.344

    fun format(meters: Int, units: DistanceUnits): String =
        when (units) {
            DistanceUnits.METRIC -> metric(meters.coerceAtLeast(0))
            DistanceUnits.IMPERIAL -> imperial(meters.coerceAtLeast(0))
        }

    private fun metric(m: Int): String {
        val rounded10 = roundToStep(m, 10)
        if (rounded10 < 1000) return "$rounded10 m"
        val km = m / 1000.0
        return if (km >= 10) "${km.roundToInt()} km" else "${oneDecimal(km)} km"
    }

    private fun imperial(m: Int): String {
        val miles = m / METERS_PER_MILE
        return when {
            miles >= 10 -> "${miles.roundToInt()} mi"
            miles >= 0.1 -> "${oneDecimal(miles)} mi"
            else -> "${roundToStep((m * FEET_PER_METER).roundToInt(), 10)} ft"
        }
    }

    /** Round a value to the nearest [step] (half-up). */
    private fun roundToStep(value: Int, step: Int): Int = ((value + step / 2) / step) * step

    /** One decimal place, manual (no locale separator surprises): 1.24 -> "1.2". */
    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return "${tenths / 10}.${tenths % 10}"
    }
}
