package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideJsonTest {

    @Test fun pointLine_roundTrip_withSpeed() {
        val p = RidePoint(12.9716, 77.5946, 1_725_000_000_000L, 42.5f)
        val parsed = RideJson.parsePoint(RideJson.pointLine(p))!!
        assertEquals(p.lat, parsed.lat, 1e-9)
        assertEquals(p.lng, parsed.lng, 1e-9)
        assertEquals(p.timeMs, parsed.timeMs)
        assertEquals(42.5f, parsed.speedKmh!!, 1e-4f)
    }

    @Test fun pointLine_roundTrip_noSpeed() {
        val p = RidePoint(1.0, 2.0, 5L, null)
        val parsed = RideJson.parsePoint(RideJson.pointLine(p))!!
        assertNull(parsed.speedKmh)
    }

    @Test fun parsePoint_rejectsMetaAndBlank() {
        assertNull(RideJson.parsePoint(""))
        assertNull(RideJson.parsePoint("   "))
        val meta = RideJson.metaLine("Home", 1.0, 2.0)!!
        assertNull(RideJson.parsePoint(meta))
        assertNull(RideJson.parsePoint("{not json"))
    }

    @Test fun metaLine_nullWhenNoDestination() {
        assertNull(RideJson.metaLine(null, null, null))
    }

    @Test fun metaLine_roundTrip() {
        val line = RideJson.metaLine("Office", 12.34, 56.78)!!
        val o = RideJson.parseMeta(line)!!
        assertTrue(o.optBoolean("meta"))
        assertEquals("Office", o.optString("label"))
        assertEquals(12.34, o.optDouble("dlat"), 1e-9)
        assertEquals(56.78, o.optDouble("dlng"), 1e-9)
    }

    @Test fun parseMeta_nullForPointLine() {
        val line = RideJson.pointLine(RidePoint(1.0, 2.0, 3L, 4f))
        assertNull(RideJson.parseMeta(line))
    }

    @Test fun ride_roundTrip() {
        val r = RecordedRide(
            id = "1725000000000",
            startMs = 1_725_000_000_000L,
            endMs = 1_725_000_600_000L,
            distanceMeters = 12_345,
            durationSeconds = 600,
            destinationLabel = "Nandi Hills",
            destLat = 13.37,
            destLng = 77.68,
            avgSpeedKmh = 34.2f,
            maxSpeedKmh = 88.0f,
            pointCount = 601,
            saved = true,
        )
        val back = RideJson.rideFromJson(RideJson.rideToJson(r))
        assertEquals(r.id, back.id)
        assertEquals(r.startMs, back.startMs)
        assertEquals(r.endMs, back.endMs)
        assertEquals(r.distanceMeters, back.distanceMeters)
        assertEquals(r.durationSeconds, back.durationSeconds)
        assertEquals(r.destinationLabel, back.destinationLabel)
        assertEquals(r.destLat!!, back.destLat!!, 1e-9)
        assertEquals(r.destLng!!, back.destLng!!, 1e-9)
        assertEquals(r.avgSpeedKmh, back.avgSpeedKmh, 1e-4f)
        assertEquals(r.maxSpeedKmh, back.maxSpeedKmh, 1e-4f)
        assertEquals(r.pointCount, back.pointCount)
        assertTrue(back.saved)
    }

    @Test fun ride_roundTrip_noDestination() {
        val r = RecordedRide(
            id = "x", startMs = 1L, endMs = 2L, distanceMeters = 500,
            durationSeconds = 120, avgSpeedKmh = 15f, maxSpeedKmh = 20f, pointCount = 10,
        )
        val back = RideJson.rideFromJson(RideJson.rideToJson(r))
        assertNull(back.destinationLabel)
        assertNull(back.destLat)
        assertNull(back.destLng)
        assertEquals(false, back.saved)
    }
}
