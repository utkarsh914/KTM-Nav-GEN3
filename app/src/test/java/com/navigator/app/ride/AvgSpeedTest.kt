package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class AvgSpeedTest {

    // 10 min cruising at ~80 km/h with a 5 min stop in the middle.
    private fun rideWithStop(): List<RidePoint> {
        val pts = ArrayList<RidePoint>()
        var t = 0L
        var lng = 0.0
        repeat(300) { pts += RidePoint(0.0, lng, t, 80f); lng += 0.0002; t += 1000 }  // 5 min moving
        repeat(300) { pts += RidePoint(0.0, lng, t, 0f); t += 1000 }                   // 5 min parked
        repeat(300) { pts += RidePoint(0.0, lng, t, 80f); lng += 0.0002; t += 1000 }  // 5 min moving
        return pts
    }

    @Test fun movingAndOverallDifferWhenThereAreStops() {
        val s = RideMetrics.summarize(rideWithStop())
        assertEquals(80f, s.avgSpeedKmh, 3f)           // moving
        assertEquals(53f, s.avgOverallSpeedKmh, 3f)    // 2/3 of the time moving
        assertEquals(600f, s.movingSeconds.toFloat(), 5f)
    }

    private fun ride(avg: Float, overall: Float, moving: Int) = RecordedRide(
        id = "1", startMs = 0, endMs = 1, distanceMeters = 12_000, durationSeconds = 1800,
        avgSpeedKmh = avg, movingSeconds = moving, avgOverallSpeedKmh = overall,
    )

    @Test fun displayPicksTheSelectedAverage() {
        val r = ride(avg = 48f, overall = 24f, moving = 900)
        assertEquals(48f, r.displayAvgKmh(AvgSpeedMode.MOVING), 0.001f)
        assertEquals(24f, r.displayAvgKmh(AvgSpeedMode.OVERALL), 0.001f)
    }

    @Test fun ridesWithoutStoredAveragesFallBackToDistanceOverDuration() {
        // Old ride, not backfilled yet: movingSeconds unknown, no overall stored.
        val old = RecordedRide(id = "1", startMs = 0, endMs = 1, distanceMeters = 12_000, durationSeconds = 1800, avgSpeedKmh = 31f)
        assertEquals(24f, old.displayAvgKmh(AvgSpeedMode.OVERALL), 0.1f) // 12 km / 30 min
        assertEquals(24f, old.displayAvgKmh(AvgSpeedMode.MOVING), 0.1f)  // unknown moving -> overall, not the stale mean
    }

    @Test fun modeIdsRoundTripAndDefaultToMoving() {
        AvgSpeedMode.entries.forEach { assertEquals(it, AvgSpeedMode.fromId(it.id)) }
        assertEquals(AvgSpeedMode.MOVING, AvgSpeedMode.fromId(null))
        assertEquals(AvgSpeedMode.MOVING, AvgSpeedMode.fromId("bogus"))
    }

    @Test fun jsonRoundTripKeepsBothAveragesAndOldEntriesStayUnknown() {
        val r = ride(48f, 24f, 900)
        val back = RideJson.rideFromJson(RideJson.rideToJson(r))
        assertEquals(900, back.movingSeconds)
        assertEquals(24f, back.avgOverallSpeedKmh, 0.001f)
        val legacy = org.json.JSONObject(RideJson.rideToJson(r).toString()).apply {
            remove("movingSeconds"); remove("avgOverallSpeedKmh")
        }
        assertEquals(MOVING_UNKNOWN, RideJson.rideFromJson(legacy).movingSeconds)
    }

    @Test fun zeroMovingTimeAndZeroDurationAreSafe() {
        val parked = ride(avg = 0f, overall = 3f, moving = 0)
        assertEquals(3f, parked.displayAvgKmh(AvgSpeedMode.MOVING), 0.001f)   // no moving data: show overall
        val empty = RecordedRide(id = "1", startMs = 0, endMs = 0, distanceMeters = 0, durationSeconds = 0)
        assertEquals(0f, empty.displayAvgKmh(AvgSpeedMode.OVERALL), 0.001f)
        assertEquals(0f, empty.displayAvgKmh(AvgSpeedMode.MOVING), 0.001f)
    }
}
