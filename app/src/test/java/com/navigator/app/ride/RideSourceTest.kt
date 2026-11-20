package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSourceTest {
    private fun ride(id: String, endMs: Long, destLat: Double? = null, source: String? = null) =
        RecordedRide(id, startMs = endMs - 60_000, endMs = endMs, distanceMeters = 1000, durationSeconds = 60, destLat = destLat, source = source)

    @Test fun inferenceTable() {
        assertEquals(RideSource.GPX, RideSource.infer(ride("2000000000", endMs = 1_000_000_000)))
        assertEquals(RideSource.NAV, RideSource.infer(ride("1000000", endMs = 1_060_000, destLat = 12.0)))
        assertEquals(RideSource.MANUAL, RideSource.infer(ride("1000000", endMs = 1_060_000)))
        assertEquals("an id slightly after the last fix is still a recording", RideSource.MANUAL, RideSource.infer(ride("1000000", endMs = 900_000)))
        assertEquals("an imported ride with a destination is still an import", RideSource.GPX, RideSource.infer(ride("2000000000", endMs = 1_000_000_000, destLat = 1.0)))
        assertEquals(RideSource.MANUAL, RideSource.infer(ride("not-a-number", endMs = 5)))
    }

    @Test fun unknownSourcesAreDropped() {
        assertEquals(RideSource.MIRROR, RideSource.clean("mirror"))
        assertNull(RideSource.clean("bogus"))
        assertNull(RideSource.clean(null))
    }

    @Test fun sourceAndDeletionRoundTripThroughTheIndexJson() {
        val r = ride("1", 100, source = RideSource.MERGED).copy(deletedAtMs = 55L)
        val back = RideJson.rideFromJson(RideJson.rideToJson(r))
        assertEquals(RideSource.MERGED, back.source)
        assertEquals(55L, back.deletedAtMs)
        val old = RideJson.rideFromJson(RideJson.rideToJson(ride("1", 100)))
        assertNull(old.source); assertNull(old.deletedAtMs)
    }

    @Test fun theMetaLineCarriesTheSource() {
        val line = RideJson.metaLine(null, null, null, RideSource.MIRROR)!!
        assertEquals(RideSource.MIRROR, RideJson.parseMeta(line)!!.getString("src"))
        assertNull(RideJson.metaLine(null, null, null))
        assertTrue(RideJson.parseMeta(RideJson.metaLine("Home", 1.0, 2.0)!!)!!.optString("src").isEmpty())
    }
}

class RideMergeTest {
    private fun ride(id: String, start: Long, end: Long, notes: String? = null) =
        RecordedRide(id, start, end, 100, ((end - start) / 1000).toInt(), notes = notes)

    @Test fun ordersByTimeAndRefusesOverlapActiveAndTooFew() {
        val a = ride("a", 0, 100); val b = ride("b", 200, 300)
        assertEquals(listOf("a", "b"), RideMerge.order(listOf(b, a)) { false }.first.map { it.id })
        assertEquals(MergeRefusal.TooFew, RideMerge.order(listOf(a)) { false }.second)
        assertEquals(MergeRefusal.Active, RideMerge.order(listOf(a, b)) { it == "b" }.second)
        assertEquals(MergeRefusal.Overlap, RideMerge.order(listOf(a, ride("c", 50, 150))) { false }.second)
        assertEquals("touching ranges are fine", null, RideMerge.order(listOf(a, ride("c", 100, 150))) { false }.second)
    }

    @Test fun laterPartsStartAGap() {
        val p = { t: Long -> RidePoint(1.0, 1.0, t) }
        val out = RideMerge.concat(listOf(listOf(p(1), p(2)), listOf(p(3), p(4)), listOf(p(5))))
        assertEquals(listOf(false, false, true, false, true), out.map { it.gapBefore })
    }

    @Test fun notesAreJoinedAndBlanksDropped() {
        assertEquals("one\n\ntwo", RideMerge.notes(listOf(ride("a", 0, 1, "one"), ride("b", 0, 1, " "), ride("c", 0, 1, "two"))))
        assertNull(RideMerge.notes(listOf(ride("a", 0, 1))))
    }

    @Test fun replayDoesNotWaitAcrossAGap() {
        val pts = listOf(RidePoint(0.0, 0.0, 1_000), RidePoint(0.0, 0.0, 3_000), RidePoint(0.0, 0.0, 90_000, gapBefore = true), RidePoint(0.0, 0.0, 91_000))
        assertEquals(2_000L, ReplayPacing.stepMs(pts, 0))
        assertEquals(0L, ReplayPacing.stepMs(pts, 1))
        assertEquals(1_000L, ReplayPacing.stepMs(pts, 2))
        assertEquals(0L, ReplayPacing.stepMs(pts, 3))
    }
}
