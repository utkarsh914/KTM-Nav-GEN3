package com.navigator.app.ui.feature.rides

import com.navigator.app.R
import com.navigator.app.data.rides.ThumbPoint
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSource
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RidesListTest {
    private val utc = ZoneOffset.UTC
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atTime(h, 0).toInstant(utc).toEpochMilli()

    private fun ride(id: String, start: Long, name: String? = null, notes: String? = null, km: Int = 10, sec: Int = 600, top: Float = 50f,
                     source: String? = RideSource.NAV, saved: Boolean = false) =
        RecordedRide(id, start, start + sec * 1000L, km * 1000, sec, destinationLabel = name, notes = notes, maxSpeedKmh = top, source = source, saved = saved)

    private val a = ride("a", ms(2026, 11, 20, 18), "Toit Brewpub", km = 6, sec = 1260, top = 61f)
    private val b = ride("b", ms(2026, 11, 20, 8), null, "morning coffee", km = 14, sec = 2280, top = 78f, source = RideSource.MANUAL)
    private val c = ride("c", ms(2026, 11, 19, 5), "Nandi Hills", km = 62, sec = 6120, top = 104f)
    private val d = ride("d", ms(2026, 11, 14, 7), "Coorg loop", km = 212, sec = 18600, top = 0f, source = RideSource.GPX)
    private val all = listOf(c, a, d, b)

    private fun ids(l: List<RideListItem>) = l.map { if (it is RideListItem.Ride) it.ride.id else "H" }

    @Test fun byDateNewestFirstGroupsUnderDayHeaders() {
        val l = RidesListBuilder.build(all, "", RideSort(), utc)
        assertEquals(listOf("H", "a", "b", "H", "c", "H", "d"), ids(l))
        assertEquals(LocalDate.of(2026, 11, 20), (l[0] as RideListItem.Header).date)
    }

    @Test fun oldestFirstReversesAndStillGroups() {
        val l = RidesListBuilder.build(all, "", RideSort(RideSortField.Date, desc = false), utc)
        assertEquals(listOf("H", "d", "H", "c", "H", "b", "a"), ids(l))
    }

    @Test fun durationSortsByTimeMovingNotByElapsedTime() {
        // "long" took longer on the clock but rode for less than "short": the card shows moving time, so the sort follows it.
        val long = ride("long", ms(2026, 11, 20), sec = 7200).copy(movingSeconds = 600)
        val short = ride("short", ms(2026, 11, 19), sec = 3000).copy(movingSeconds = 2400)
        assertEquals(listOf("short", "long"), ids(RidesListBuilder.build(listOf(long, short), "", RideSort(RideSortField.Duration), utc)))
    }

    @Test fun otherFieldsSortFlatWithNoHeaders() {
        assertEquals(listOf("d", "c", "b", "a"), ids(RidesListBuilder.build(all, "", RideSort(RideSortField.Distance), utc)))
        assertEquals(listOf("a", "b", "c", "d"), ids(RidesListBuilder.build(all, "", RideSort(RideSortField.Distance, false), utc)))
        assertEquals(listOf("d", "c", "b", "a"), ids(RidesListBuilder.build(all, "", RideSort(RideSortField.Duration), utc)))
        assertEquals(listOf("c", "b", "a", "d"), ids(RidesListBuilder.build(all, "", RideSort(RideSortField.TopSpeed), utc)))
    }

    @Test fun searchMatchesNameAndNotesAndDropsHeaders() {
        assertEquals(listOf("c"), ids(RidesListBuilder.build(all, "nandi", RideSort(), utc)))
        assertEquals(listOf("b"), ids(RidesListBuilder.build(all, "COFFEE", RideSort(), utc)))
        assertTrue("the default title is not searchable text", RidesListBuilder.build(all, "manual", RideSort(), utc).isEmpty())
        assertTrue(RidesListBuilder.build(all, "nandi coffee", RideSort(), utc).isEmpty())
        assertEquals(4, RidesListBuilder.build(all, "   ", RideSort(), utc).count { it is RideListItem.Ride })
    }

    @Test fun theListGoesToTheTopOnlyWhenTheOrderOrSearchChanges() {
        val t = TopOnChange<Pair<RideSort, Boolean>>()
        val date = RideSort() to false
        assertFalse("nothing loaded yet", t.shouldReset(null))
        assertFalse("first sight, e.g. coming back from a replay", t.shouldReset(date))
        assertFalse("the same again, e.g. a recomposition", t.shouldReset(date))
        assertTrue(t.shouldReset(RideSort(RideSortField.Distance) to false))
        assertTrue("a search starts", t.shouldReset(RideSort(RideSortField.Distance) to true))
    }

    @Test fun cardsNameTheirDayWhenThereAreNoDayHeaders() {
        fun dated(l: List<RideListItem>) = l.filterIsInstance<RideListItem.Ride>().map { it.showDate }
        assertTrue(dated(RidesListBuilder.build(all, "", RideSort(), utc)).none { it })
        assertTrue(dated(RidesListBuilder.build(all, "", RideSort(RideSortField.Distance), utc)).all { it })
        assertTrue(dated(RidesListBuilder.build(all, "nandi", RideSort(), utc)).all { it })
    }

    @Test fun matchesAreReportedForHighlighting() {
        val hit = RidesListBuilder.build(all, "hills", RideSort(), utc).single() as RideListItem.Ride
        assertEquals(6..10, hit.titleMatch)
        assertNull(hit.notesMatch)
        val note = RidesListBuilder.build(all, "coffee", RideSort(), utc).single() as RideListItem.Ride
        assertEquals(8..13, note.notesMatch)
    }

    @Test fun dayKinds() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals(DayKind.Today, RidesListBuilder.dayKind(today, today))
        assertEquals(DayKind.Yesterday, RidesListBuilder.dayKind(today.minusDays(1), today))
        assertEquals(DayKind.ThisYear, RidesListBuilder.dayKind(LocalDate.of(2026, 1, 3), today))
        assertEquals(DayKind.Older, RidesListBuilder.dayKind(LocalDate.of(2025, 12, 31), today))
    }

    @Test fun sortLabelsAdaptToTheField() {
        assertEquals(R.string.rides_sort_newest, RideSort(RideSortField.Date, true).label)
        assertEquals(R.string.rides_sort_oldest, RideSort(RideSortField.Date, false).label)
        assertEquals(R.string.rides_sort_longest, RideSort(RideSortField.Distance, true).label)
        assertEquals(R.string.rides_sort_shortest_time, RideSort(RideSortField.Duration, false).label)
        assertEquals(R.string.rides_sort_fastest, RideSort(RideSortField.TopSpeed, true).label)
        assertEquals(8, RideSort.options.size)
    }

    @Test fun titlesSayHowTheRideStarted() {
        assertEquals(R.string.ride_title_navigated, RideSourceText.title(RideSource.NAV))
        assertEquals(R.string.ride_title_imported, RideSourceText.title(RideSource.GPX))
        assertEquals(R.string.ride_title_recorded, RideSourceText.title(RideSource.MANUAL))
        assertEquals(R.string.ride_how_merged, RideSourceText.how(RideSource.MERGED))
    }

    @Test fun selectionKeepsOnlyRidesStillShownAndStarFlipsWhenAllStarred() {
        val items = RidesListBuilder.build(all, "", RideSort(), utc)
        assertEquals(setOf("a"), RideSelection.visible(setOf("a", "gone"), items))
        assertTrue(RideSelection.starTarget(listOf(a, a.copy(saved = true))))
        assertTrue(!RideSelection.starTarget(listOf(a.copy(saved = true), b.copy(saved = true))))
    }

    @Test fun thumbnailKeepsTheShapeAndCentresIt() {
        val line = listOf(ThumbPoint(12.0, 77.0), ThumbPoint(12.0, 77.02, speedKmh = 30f), ThumbPoint(12.0, 77.04, gap = true))
        val px = ThumbnailGeometry.project(line, 100f, 100f, pad = 10f)
        assertEquals(3, px.size)
        assertEquals(10f, px.first().x, 0.5f)
        assertEquals(90f, px.last().x, 0.5f)
        assertEquals("a straight line is centred vertically", 50f, px[1].y, 0.5f)
        assertEquals(30f, px[1].speedKmh)
        assertTrue(px.last().gap)
        assertTrue(ThumbnailGeometry.project(emptyList(), 10f, 10f, 1f).isEmpty())
        val one = ThumbnailGeometry.project(listOf(ThumbPoint(1.0, 1.0)), 100f, 100f, 10f)
        assertTrue(one.single().x in 10f..90f)
    }
}
