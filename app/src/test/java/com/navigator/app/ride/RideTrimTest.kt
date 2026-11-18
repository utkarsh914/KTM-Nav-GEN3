package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTrimTest {
    private fun pts(n: Int) = (0 until n).map { RidePoint(0.0, it * 0.0002, 1_000L * it, 80f) }

    @Test fun keepsTheInclusiveRange() {
        val t = RideTrim.trim(pts(20), 5, 14)!!
        assertEquals(10, t.size)
        assertEquals(5_000L, t.first().timeMs)
        assertEquals(14_000L, t.last().timeMs)
    }

    @Test fun invalidRangesAreRejected() {
        val p = pts(10)
        assertNull(RideTrim.trim(p, -1, 5))
        assertNull(RideTrim.trim(p, 0, 10))
        assertNull(RideTrim.trim(p, 4, 4))
        assertNull(RideTrim.trim(p, 6, 3))
    }

    @Test fun aPauseFlagOnTheNewFirstFixIsCleared() {
        val p = pts(10).toMutableList().also { it[4] = it[4].copy(gapBefore = true) }
        assertFalse(RideTrim.trim(p, 4, 9)!!.first().gapBefore)
        // ...but a pause INSIDE the kept range stays.
        assertTrue(RideTrim.trim(p, 2, 9)!![2].gapBefore)
    }

    @Test fun trimmedStatsShrink() {
        val all = RideMetrics.summarize(pts(60))
        val half = RideMetrics.summarize(RideTrim.trim(pts(60), 0, 29)!!)
        assertTrue(half.distanceMeters < all.distanceMeters)
        assertTrue(half.durationSeconds < all.durationSeconds)
    }

    @Test fun nameAndNotesCleaning() {
        assertEquals("Sunday ride", RideTrim.cleanName("  Sunday \n  ride  "))
        assertNull(RideTrim.cleanName("   "))
        assertEquals(RideTrim.MAX_NAME, RideTrim.cleanName("x".repeat(500))!!.length)
        assertEquals("a\nb", RideTrim.cleanNotes(" a\nb "))
        assertNull(RideTrim.cleanNotes(""))
        assertEquals(RideTrim.MAX_NOTES, RideTrim.cleanNotes("y".repeat(900))!!.length)
    }

    @Test fun notesSurviveTheIndex() {
        val r = RecordedRide("1", 0, 1, 10, 10, notes = "Great twisties")
        assertEquals("Great twisties", RideJson.rideFromJson(RideJson.rideToJson(r)).notes)
        assertNull(RideJson.rideFromJson(RideJson.rideToJson(r.copy(notes = null))).notes)
    }
}
