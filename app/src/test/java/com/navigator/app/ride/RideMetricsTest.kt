package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMetricsTest {

    @Test fun haversine_oneDegreeLat_isAbout111km() {
        val d = RideMetrics.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 200.0)
    }

    @Test fun haversine_sixMetres() {
        // ~0.00005 deg lat ≈ 5.56 m
        val d = RideMetrics.haversineMeters(12.9716, 77.5946, 12.97165, 77.5946)
        assertEquals(5.56, d, 0.6)
    }

    @Test fun summarize_distanceMaxAvgAndDuration() {
        val pts = listOf(
            RidePoint(0.0, 0.0, 1_000L, 10f),
            RidePoint(0.0, 0.001, 2_000L, 20f),
            RidePoint(0.0, 0.002, 3_000L, 30f),
        )
        val s = RideMetrics.summarize(pts)
        // ~111.2 m per 0.001 deg lng at the equator (R=6371 km), two hops ≈ 222 m.
        assertEquals(222.4, s.distanceMeters.toDouble(), 3.0)
        assertEquals(2, s.durationSeconds)
        assertEquals(20f, s.avgSpeedKmh, 0.001f)
        assertEquals(30f, s.maxSpeedKmh, 0.001f)
        assertEquals(3, s.pointCount)
        assertEquals(1_000L, s.startMs)
        assertEquals(3_000L, s.endMs)
    }

    @Test fun avgFallsBackToDistanceOverTimeWhenNoSpeeds() {
        // 0.01 deg lng ≈ 1113 m at equator over 100 s ≈ 40 km/h.
        val pts = listOf(
            RidePoint(0.0, 0.0, 0L, null),
            RidePoint(0.0, 0.01, 100_000L, null),
        )
        val s = RideMetrics.summarize(pts)
        assertEquals(40f, s.avgSpeedKmh, 2f)
        assertEquals(0f, s.maxSpeedKmh, 0.001f)
    }

    @Test fun incrementalMatchesWholeList() {
        val pts = (0 until 25).map {
            RidePoint(0.0, it * 0.0005, 1_000L * it, (it * 2).toFloat())
        }
        val whole = RideMetrics.summarize(pts)

        val acc = RideMetrics.Accumulator()
        pts.forEach(acc::add)
        val incr = acc.stats()

        assertEquals(whole, incr)
    }

    @Test fun meetsCutoff_requiresBothDistanceAndDuration() {
        assertTrue(RideMetrics.meetsCutoff(400, 60, 400, 60))
        assertFalse(RideMetrics.meetsCutoff(399, 60, 400, 60))
        assertFalse(RideMetrics.meetsCutoff(400, 59, 400, 60))
        assertFalse(RideMetrics.meetsCutoff(100, 30, 400, 60))
        assertTrue(RideMetrics.meetsCutoff(5000, 600, 400, 60))
    }

    @Test fun speedBands() {
        assertEquals(RideMetrics.SpeedBand.SLOW, RideMetrics.band(null))
        assertEquals(RideMetrics.SpeedBand.SLOW, RideMetrics.band(10f))
        assertEquals(RideMetrics.SpeedBand.MEDIUM, RideMetrics.band(30f))
        assertEquals(RideMetrics.SpeedBand.FAST, RideMetrics.band(60f))
        assertEquals(RideMetrics.SpeedBand.VERY_FAST, RideMetrics.band(100f))
    }
}
