package com.navigator.app.ui.map

import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure track-reduction passes that fix ride-replay zoom lag: a real
 * (jittery, multi-thousand-point) ride must collapse to a handful of polylines
 * with few vertices, while the track's shape and colour boundaries stay intact.
 */
class RideTrackReduceTest {

    private val bands = RideMetrics.SpeedBands.DEFAULT // base 40, step 20, count 5
    private fun p(lat: Double, lng: Double, spd: Float? = null) = RidePoint(lat, lng, 0L, spd)

    @Test fun simplifyCollapsesCollinearRun() {
        val straight = (0..100).map { p(12.9700 + it * 0.00001, 77.5900) }
        assertEquals(2, simplifyRun(straight, SIMPLIFY_EPSILON_M).size)
    }

    @Test fun simplifyKeepsCornersAndEndpoints() {
        val corner = listOf(p(12.9700, 77.5900)) +
            (1..50).map { p(12.9700, 77.5900 + it * 0.00001) } +
            (1..50).map { p(12.9700 + it * 0.00001, 77.5900 + 50 * 0.00001) }
        val s = simplifyRun(corner, SIMPLIFY_EPSILON_M)
        assertTrue("corner kept", s.size >= 3)
        assertSame(corner.first(), s.first())
        assertSame(corner.last(), s.last())
    }

    @Test fun simplifyLeavesTinyRunsAlone() {
        val two = listOf(p(0.0, 0.0), p(1.0, 1.0))
        assertEquals(2, simplifyRun(two, 6.0).size)
    }

    @Test fun epsilonZeroKeepsAllNonCollinearPoints() {
        // "Off" (epsilon 0) must keep every point of a zig-zag run - real GPS
        // fixes are never perfectly collinear, so nothing is dropped.
        val zigzag = (0..40).map { p(12.9700 + it * 0.00002, 77.5900 + (it % 2) * 0.00002) }
        assertEquals(zigzag.size, simplifyRun(zigzag, 0.0).size)
    }

    @Test fun largerEpsilonReducesMore() {
        // A gently curving run: coarser tolerance drops at least as many points.
        val curve = (0..200).map {
            val t = it / 200.0
            p(12.9700 + t * 0.01, 77.5900 + 0.002 * Math.sin(t * Math.PI))
        }
        val fine = simplifyRun(curve, 3.0).size
        val coarse = simplifyRun(curve, 25.0).size
        assertTrue("coarse ($coarse) should keep <= fine ($fine)", coarse <= fine)
        assertTrue("coarse should still keep endpoints", coarse >= 2)
    }

    @Test fun hysteresisSuppressesBandFlapping() {
        // Speed oscillating 39/41 around the 40 km/h base boundary -> one run.
        val jitter = (0..60).map { p(12.97 + it * 0.00001, 77.59, if (it % 2 == 0) 39f else 41f) }
        assertEquals(1, buildBandRuns(jitter, bands).size)
    }

    @Test fun clearBandChangeSplitsAndSharesBoundary() {
        val climb = (0..20).map { p(12.97 + it * 0.00001, 77.59, 20f) } +
            (0..20).map { p(12.97 + (21 + it) * 0.00001, 77.59, 90f) }
        val runs = buildBandRuns(climb, bands)
        assertEquals(2, runs.size)
        // Consecutive runs share the boundary point so the polylines join.
        assertSame(runs[0].points.last(), runs[1].points.first())
    }

    @Test fun microRunBlipIsMerged() {
        val blip = (0..20).map { p(12.97 + it * 0.00001, 77.59, 20f) } +
            listOf(p(12.97 + 0.00021, 77.59, 90f), p(12.97 + 0.00022, 77.59, 90f)) +
            (0..20).map { p(12.97 + (23 + it) * 0.00001, 77.59, 20f) }
        assertEquals(1, buildBandRuns(blip, bands).size)
    }

    @Test fun strideCapsHugeTracks() {
        val big = (0 until 10000).map { p(12.97 + it * 0.000001, 77.59) }
        val strided = strideToBudget(big, 1500)
        assertTrue(strided.size <= 1501)
        assertSame(big.last(), strided.last())
        // Small lists are returned unchanged.
        assertEquals(100, strideToBudget(big.take(100), 1500).size)
    }

    @Test fun realisticRideCollapsesDramatically() {
        // ~6300-point winding ride with speed jitter across band edges.
        val pts = ArrayList<RidePoint>(6300)
        var lat = 12.9700; var lng = 77.5900
        for (i in 0 until 6300) {
            lat += 0.00003 * Math.sin(i / 40.0) + 0.000012
            lng += 0.00003 + 0.00001 * Math.sin(i / 55.0)
            val base = 40 + 40 * (0.5 + 0.5 * Math.sin(i / 120.0))
            val spd = (base + ((i * 7) % 17) - 8).toFloat().coerceAtLeast(0f)
            pts.add(RidePoint(lat, lng, i * 1000L, spd))
        }
        val runs = buildBandRuns(strideToBudget(pts, 4500), bands)
        val vertices = runs.sumOf { simplifyRun(it.points, SIMPLIFY_EPSILON_M).size }
        // Expect a huge reduction: far fewer than a polyline/vertex per raw point.
        assertTrue("polylines should be modest, was ${runs.size}", runs.size < 300)
        assertTrue("vertices should be modest, was $vertices", vertices < 1000)
    }

    @Test fun capTotalVerticesEnforcesTheBudgetAndKeepsRunEnds() {
        fun run(n: Int, off: Int) = (0 until n).map { RidePoint(0.0, off + it * 0.0001, 1000L * (off + it), 10f) }
        val runs = listOf(run(1000, 0), run(1000, 5000), run(500, 9000))
        val capped = capTotalVertices(runs, 1500)
        assertTrue("total=${capped.sumOf { it.size }}", capped.sumOf { it.size } <= 1500 + runs.size)
        runs.forEachIndexed { i, r ->
            assertEquals(r.first(), capped[i].first())
            assertEquals(r.last(), capped[i].last())
        }
    }

    @Test fun capTotalVerticesLeavesSmallTracksAlone() {
        val runs = listOf((0 until 10).map { RidePoint(0.0, it * 0.0001, 1000L * it, 10f) })
        assertEquals(runs, capTotalVertices(runs, 1500))
    }

    @Test fun aPauseBreaksTheLineInsteadOfBridgingTheGap() {
        fun seg(startMs: Long, lng0: Double, gap: Boolean) = (0 until 12).map {
            RidePoint(0.0, lng0 + it * 0.0002, startMs + 1_000L * it, 50f, gapBefore = gap && it == 0)
        }
        val pts = seg(0, 0.0, false) + seg(600_000, 0.05, true)
        val runs = buildBandRuns(pts, bands)
        assertEquals(2, runs.size)
        assertEquals(12, runs[0].points.size)
        assertEquals(12, runs[1].points.size)
        assertTrue(runs[1].points.first().gapBefore)
    }

    @Test fun stridingNeverDropsAPauseMarker() {
        val pts = (0 until 100).map { RidePoint(0.0, it * 0.0001, 1_000L * it, 50f, gapBefore = it == 37) }
        val s = strideToBudget(pts, 10)
        assertTrue(s.any { it.gapBefore })
        assertEquals(2, buildBandRuns(s, bands).size)
    }
}
