package com.navigator.app.data.rides

import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RidesRepositoryTest {
    private fun ride(id: String, name: String?, notes: String? = null) =
        RecordedRide(id, 0, 1, 1, 1, destinationLabel = name, notes = notes)

    @Test fun searchMatchesNameAndNotesAllWordsAnyCase() {
        val rides = listOf(ride("1", "Nandi Hills", "sunrise run"), ride("2", "Office", "rain all day"), ride("3", null, "Nandi coffee"))
        assertEquals(listOf("1", "3"), RideSearch.filter(rides, "nandi").map { it.id })
        assertEquals(listOf("1"), RideSearch.filter(rides, "HILLS sunrise").map { it.id })
        assertEquals(listOf("2"), RideSearch.filter(rides, " rain ").map { it.id })
        assertEquals(3, RideSearch.filter(rides, "  ").size)
        assertTrue(RideSearch.filter(rides, "nandi rain").isEmpty())
    }

    private fun pts(n: Int) = (0 until n).map { RidePoint(it.toDouble(), 0.0, it * 1000L) }

    @Test fun downsamplingKeepsSpeedAndTheSeams() {
        val p = (0 until 500).map { RidePoint(it.toDouble(), 0.0, it * 1000L, speedKmh = 10f, gapBefore = it == 251) }
        val out = RideThumbnail.downsample(p, 64)
        assertTrue(out.size <= 64)
        assertEquals(1, out.count { it.gap })
        assertEquals(251.0, out.first { it.gap }.lat, 0.0)
        assertEquals(10f, out.first().speedKmh)
    }

    @Test fun downsamplingKeepsEndsAndTheCap() {
        val out = RideThumbnail.downsample(pts(1000), 64)
        assertEquals(64, out.size)
        assertEquals(0.0, out.first().lat, 0.0)
        assertEquals(999.0, out.last().lat, 0.0)
        assertTrue(out.zipWithNext().all { (a, b) -> b.lat > a.lat })
        assertEquals(10, RideThumbnail.downsample(pts(10), 64).size)
        assertTrue(RideThumbnail.downsample(emptyList()).isEmpty())
    }
}
