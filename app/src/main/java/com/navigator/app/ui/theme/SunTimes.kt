package com.navigator.app.ui.theme

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Day / night from the real sun position (NOAA sunrise equation), computed on the phone with no
 * network: sunrise and sunset for a latitude / longitude and date. Accurate to a few minutes, which
 * is plenty for flipping a map theme. Pure, JVM-testable.
 */
object SunTimes {

    private const val DAY_MS = 86_400_000L

    /** Sunrise / sunset as minutes after 00:00 UTC of the UTC day containing the sun's noon; null in polar day / night. */
    private fun riseSetUtcMinutes(dayOfYear: Int, latDeg: Double, lngDeg: Double): Pair<Double, Double>? {
        val g = 2 * Math.PI / 365.0 * (dayOfYear - 1) // solar noon approximation (hour 12)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(latDeg)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        if (cosHa > 1.0 || cosHa < -1.0) return null
        val haDeg = Math.toDegrees(acos(cosHa))
        val rise = 720.0 - 4.0 * (lngDeg + haDeg) - eqTime
        val set = 720.0 - 4.0 * (lngDeg - haDeg) - eqTime
        return rise to set
    }

    /** True when the sun is below the horizon at [epochMs] at this place. */
    fun isNight(epochMs: Long, latDeg: Double, lngDeg: Double): Boolean {
        val utcDay = Math.floorDiv(epochMs, DAY_MS)
        // The sun's day may straddle a UTC midnight far from Greenwich: test the neighbours too.
        for (offset in -1L..1L) {
            val day = utcDay + offset
            val doy = LocalDate.ofEpochDay(day).dayOfYear
            val rs = riseSetUtcMinutes(doy, latDeg, lngDeg)
            if (rs == null) {
                // Polar: decide by the sign of the declination term (summer hemisphere = midnight sun).
                return !isPolarDay(doy, latDeg)
            }
            val rise = day * DAY_MS + (rs.first * 60_000).toLong()
            var set = day * DAY_MS + (rs.second * 60_000).toLong()
            if (set < rise) set += DAY_MS // sunset after UTC midnight
            if (epochMs in rise..set) return false
        }
        return true
    }

    private fun isPolarDay(dayOfYear: Int, latDeg: Double): Boolean {
        val g = 2 * Math.PI / 365.0 * (dayOfYear - 1)
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        return (latDeg > 0) == (decl > 0)
    }
}
