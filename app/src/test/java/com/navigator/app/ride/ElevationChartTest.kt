package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationChartTest {

    private fun track(alts: List<Float?>) = alts.mapIndexed { i, a ->
        RidePoint(0.0, i * 0.0002, 1_000L * i, 60f, altM = a)
    }

    @Test fun noAltitudeMeansUnknown() {
        val s = RideMetrics.summarize(track(List(20) { null }))
        assertEquals(ELEV_UNKNOWN, s.elevGainM)
        assertEquals(ELEV_UNKNOWN, s.elevLossM)
    }

    @Test fun flatRoadWithWobbleCountsNothing() {
        val wobble = List(60) { 100f + if (it % 2 == 0) 1.5f else -1.5f }
        val s = RideMetrics.summarize(track(wobble))
        assertEquals(0, s.elevGainM)
        assertEquals(0, s.elevLossM)
    }

    @Test fun climbThenDescentIsMeasured() {
        val up = (0 until 30).map { 100f + it * 2f }          // +58 m
        val down = (0 until 30).map { 158f - it * 2f }        // -58 m
        val s = RideMetrics.summarize(track(up + down))
        assertTrue("gain=${s.elevGainM}", s.elevGainM in 50..60)
        assertTrue("loss=${s.elevLossM}", s.elevLossM in 50..60)
    }

    @Test fun singleSpikeIsIgnoredByTheMedian() {
        val alts = MutableList(40) { 100f }
        alts[20] = 400f
        val s = RideMetrics.summarize(track(alts))
        assertEquals(0, s.elevGainM)
    }

    @Test fun altitudeJumpAcrossAPauseIsNotClimb() {
        val before = (0 until 10).map { RidePoint(0.0, it * 0.0002, 1_000L * it, 60f, altM = 100f) }
        val after = (0 until 10).map {
            RidePoint(0.0, 0.01 + it * 0.0002, 700_000L + 1_000L * it, 60f, altM = 600f, gapBefore = it == 0)
        }
        val s = RideMetrics.summarize(before + after)
        assertEquals(0, s.elevGainM)
    }

    @Test fun altitudeSurvivesTheTrackFormat() {
        val p = RidePoint(1.0, 2.0, 5L, 3f, altM = 912.34f)
        assertEquals(912.3f, RideJson.parsePoint(RideJson.pointLine(p))!!.altM!!, 0.05f)
        assertNull(RideJson.parsePoint(RideJson.pointLine(p.copy(altM = null)))!!.altM)
    }

    @Test fun elevationSurvivesTheIndexAndOldEntriesStayUnknown() {
        val r = RecordedRide("1", 0, 1, 1000, 100, elevGainM = 40, elevLossM = 12)
        val back = RideJson.rideFromJson(RideJson.rideToJson(r))
        assertEquals(40, back.elevGainM)
        assertEquals(12, back.elevLossM)
        val legacy = RideJson.rideToJson(r).apply { remove("elevGainM"); remove("elevLossM") }
        assertEquals(ELEV_UNKNOWN, RideJson.rideFromJson(legacy).elevGainM)
    }

    @Test fun gpxCarriesElevation() {
        val pts = track(listOf(900f, 905f, null, 910f))
        val back = Gpx.parse(Gpx.write("x", pts).byteInputStream(), 0)!!.points
        assertEquals(listOf(900f, 905f, null, 910f), back.map { it.altM })
    }

    @Test fun chartResamplesToBucketsAndCarriesAltitude() {
        val pts = track(List(500) { 100f + it * 0.1f })
        val c = RideChart.build(pts, 100)!!
        assertEquals(100, c.speed.size)
        assertNotNull(c.alt)
        assertEquals(100, c.alt!!.size)
        assertTrue(c.minAlt < c.maxAlt)
        assertEquals(60f, c.speed[50], 0.01f)
    }

    @Test fun chartOmitsElevationWhenMostFixesLackIt() {
        val alts = List(100) { if (it < 10) 100f else null }
        val c = RideChart.build(track(alts), 50)!!
        assertNull(c.alt)
    }

    @Test fun chartNeedsEnoughPoints() {
        assertNull(RideChart.build(track(List(5) { 1f }), 50))
    }

    @Test fun slowSteadyClimbIsCounted() {
        val s = RideMetrics.summarize(track((0 until 100).map { 100f + it * 0.5f })) // +49.5 m at 0.5 m/fix
        assertTrue("gain=${s.elevGainM}", s.elevGainM in 44..52)
        assertEquals(0, s.elevLossM)
    }

    @Test fun pauseOnAFixWithoutAltitudeStillReanchors() {
        val before = (0 until 10).map { RidePoint(0.0, it * 0.0002, 1_000L * it, 60f, altM = 100f) }
        // first fix after the pause has NO altitude; the next ones are 500 m higher
        val resume = listOf(RidePoint(0.0, 0.01, 700_000L, 60f, altM = null, gapBefore = true)) +
            (1 until 10).map { RidePoint(0.0, 0.01 + it * 0.0002, 700_000L + 1_000L * it, 60f, altM = 600f) }
        assertEquals(0, RideMetrics.summarize(before + resume).elevGainM)
    }

    @Test fun chartWorksWithFewerPointsThanBuckets() {
        val c = RideChart.build(track(List(12) { 1f }), 120)!!
        assertEquals(12, c.speed.size)
    }
}
