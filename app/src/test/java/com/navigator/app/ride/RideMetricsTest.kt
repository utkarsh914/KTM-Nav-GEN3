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

    // 0.0002 deg lng at the equator ~ 22.2 m; one hop per second ~ 80 km/h.
    private fun cruise(n: Int, startMs: Long = 0L, speed: Float = 80f) =
        (0 until n).map { RidePoint(0.0, it * 0.0002, startMs + 1_000L * it, speed) }

    @Test fun summarize_distanceMaxAvgAndDuration() {
        val s = RideMetrics.summarize(cruise(11))
        assertEquals(222.4, s.distanceMeters.toDouble(), 3.0)   // 10 hops ~ 22.2 m
        assertEquals(10, s.durationSeconds)
        assertEquals(10, s.movingSeconds)
        assertEquals(80f, s.avgSpeedKmh, 1.5f)                 // distance / moving time
        assertEquals(80f, s.maxSpeedKmh, 0.001f)
        assertEquals(11, s.pointCount)
        assertEquals(0L, s.startMs)
        assertEquals(10_000L, s.endMs)
    }

    @Test fun avgUsesImpliedSpeedWhenFixesCarryNoSpeed() {
        // 0.01 deg lng ~ 1113 m over 100 s ~ 40 km/h.
        val pts = listOf(RidePoint(0.0, 0.0, 0L, null), RidePoint(0.0, 0.01, 100_000L, null))
        val s = RideMetrics.summarize(pts)
        assertEquals(40f, s.avgSpeedKmh, 2f)
        assertEquals(0f, s.maxSpeedKmh, 0.001f)
    }

    @Test fun stationaryJitterAddsNoDistanceAndNoMovingTime() {
        // Parked: 1 m wobble each second at 0 km/h.
        val pts = (0 until 60).map { RidePoint(0.0, (it % 2) * 0.00001, 1_000L * it, 0f) }
        val s = RideMetrics.summarize(pts)
        assertEquals(0, s.distanceMeters)
        assertEquals(0, s.movingSeconds)
        assertEquals(0f, s.avgSpeedKmh, 0.001f)
        assertEquals(59, s.durationSeconds)
    }

    @Test fun slowRidingIsNotMistakenForJitter() {
        // 2.8 m per second = ~10 km/h: hops are under 3 m but speed says moving.
        val pts = (0 until 11).map { RidePoint(0.0, it * 0.000025, 1_000L * it, 10f) }
        val s = RideMetrics.summarize(pts)
        assertEquals(27.8, s.distanceMeters.toDouble(), 2.0)
        assertEquals(10, s.movingSeconds)
    }

    @Test fun teleportSpikeIsIgnored() {
        val pts = cruise(5) + RidePoint(10.0, 10.0, 5_000L, 80f) + cruise(5, startMs = 6_000L).map { it.copy(lng = it.lng + 0.0010) }
        val clean = RideMetrics.summarize(cruise(5)).distanceMeters
        val s = RideMetrics.summarize(pts)
        // The 1500 km jump must not be counted.
        assertTrue("distance=${s.distanceMeters}", s.distanceMeters < clean + 400)
    }

    @Test fun backwardsTimeFixIsSkipped() {
        val pts = cruise(5) + RidePoint(0.0, 5 * 0.0002, 2_000L, 80f) + cruise(1, startMs = 5_000L).map { it.copy(lng = 5 * 0.0002) }
        val s = RideMetrics.summarize(pts)
        assertEquals(5_000L, s.endMs)
        assertEquals(6, s.pointCount)
    }

    @Test fun maxSpeedIgnoresASingleSpike() {
        val pts = listOf(60f, 62f, 250f, 64f, 66f).mapIndexed { i, v ->
            RidePoint(0.0, i * 0.0002, 1_000L * i, v)
        }
        assertEquals(66f, RideMetrics.summarize(pts).maxSpeedKmh, 0.001f) // never the 250 spike
    }

    @Test fun badFirstFixDoesNotKillTheWholeRide() {
        // First fix is wildly wrong and far in the future; every real fix after it must still count.
        val bad = RidePoint(50.0, 50.0, 9_000_000_000L, 80f)
        val pts = listOf(bad) + cruise(30, startMs = 1_000L)
        val s = RideMetrics.summarize(pts)
        assertTrue("points=${s.pointCount}", s.pointCount >= 25)
        assertTrue("distance=${s.distanceMeters}", s.distanceMeters in 400..700)
    }

    @Test fun jitterWithoutSpeedIsNotMovingTime() {
        val pts = (0 until 30).map { RidePoint(0.0, (it % 2) * 0.00002, 1_000L * it, null) }
        assertEquals(0, RideMetrics.summarize(pts).movingSeconds)
    }

    @Test fun haversineHandlesAntipodalPointsWithoutNaN() {
        val d = RideMetrics.haversineMeters(0.0, 0.0, 0.0, 180.0)
        assertEquals(20_015_000.0, d, 20_000.0)
    }

    @Test fun pruneUsesCreationIdNotGpsStartTime() {
        // Newest ride (id by creation) has a bogus ancient GPS startMs; it must NOT be pruned.
        val rides = listOf(
            ride("1000", 9_000_000L), ride("2000", 8_000_000L), ride("3000", 1L),
        )
        assertEquals(setOf("1000"), RideMetrics.ridesToPrune(rides, 2).toSet())
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
