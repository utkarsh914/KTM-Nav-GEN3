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

    @Test fun speedBands_defaultScheme() {
        val b = RideMetrics.SpeedBands.DEFAULT // base 40, step 20, count 5
        assertEquals(6, b.bandCount)
        assertEquals(0, b.bandOf(null))
        assertEquals(0, b.bandOf(10f))
        assertEquals(0, b.bandOf(39.9f))
        assertEquals(1, b.bandOf(40f))
        assertEquals(1, b.bandOf(59f))
        assertEquals(2, b.bandOf(60f))
        assertEquals(3, b.bandOf(80f))
        assertEquals(4, b.bandOf(100f))
        assertEquals(5, b.bandOf(120f))
        // Above the top band saturates at the last index.
        assertEquals(5, b.bandOf(500f))
    }

    @Test fun speedBands_upperBounds() {
        val b = RideMetrics.SpeedBands(baseKmh = 40, stepKmh = 20, count = 5)
        assertEquals(40, b.upperBoundKmh(0))
        assertEquals(60, b.upperBoundKmh(1))
        assertEquals(null, b.upperBoundKmh(5)) // open-ended top band
    }

    private fun ride(id: String, startMs: Long, saved: Boolean = false) = RecordedRide(
        id = id, startMs = startMs, endMs = startMs + 1, distanceMeters = 1000,
        durationSeconds = 100, saved = saved,
    )

    @Test fun ridesToPrune_underLimit_keepsAll() {
        val rides = (1..5).map { ride("r$it", it * 1000L) }
        assertTrue(RideMetrics.ridesToPrune(rides, 10).isEmpty())
        assertTrue(RideMetrics.ridesToPrune(rides, 5).isEmpty())
    }

    @Test fun ridesToPrune_dropsOldestUnsavedBeyondLimit() {
        // startMs ascending; oldest are r1, r2 ...
        val rides = (1..5).map { ride("r$it", it * 1000L) }
        val dropped = RideMetrics.ridesToPrune(rides, 3).toSet()
        // Keep newest 3 (r5,r4,r3); drop oldest 2 (r1,r2).
        assertEquals(setOf("r1", "r2"), dropped)
    }

    @Test fun ridesToPrune_savedRidesExcludedFromCountAndNeverDropped() {
        val rides = listOf(
            ride("old-saved", 1_000L, saved = true),
            ride("u1", 2_000L),
            ride("u2", 3_000L),
            ride("u3", 4_000L),
        )
        // Limit 2 counts only unsaved (u1,u2,u3) -> keep newest 2 (u3,u2), drop u1.
        val dropped = RideMetrics.ridesToPrune(rides, 2).toSet()
        assertEquals(setOf("u1"), dropped)
    }

    @Test fun ridesToPrune_nonPositiveLimit_keepsAll() {
        val rides = (1..5).map { ride("r$it", it * 1000L) }
        assertTrue(RideMetrics.ridesToPrune(rides, 0).isEmpty())
        assertTrue(RideMetrics.ridesToPrune(rides, -1).isEmpty())
    }
}
