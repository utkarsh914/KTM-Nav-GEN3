package com.navigator.app.ui.feature.navigate.plan

import com.navigator.app.nav.destination.RouteLeg
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopLegsTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_700_000_000_000L // 22:13:20 UTC

    @Test fun durationsRoundAndFormat() {
        assertEquals("1 min", StopLegs.duration(10))
        assertEquals("12 min", StopLegs.duration(700))
        assertEquals("59 min", StopLegs.duration(3_540))
        assertEquals("1 h 05 min", StopLegs.duration(3_900))
        assertEquals("2 h 00 min", StopLegs.duration(7_200))
    }

    @Test fun theSummaryHasTimeDistanceArrivalAndTrafficOnlyWhenThereIsSome() {
        val slow = RouteOptionUi("20 min", "6.8 km", RouteBadge.Only, 1, DelayTone.Minor, 2)
        val clear = slow.copy(delayTone = DelayTone.OnTime, delayMinutes = 0)
        val withTraffic = RouteSheetMapper.summary(slow, 1_200, now)
        assertTrue(withTraffic, withTraffic.startsWith("20 min \u00b7 6.8 km \u00b7 arrive "))
        assertTrue(withTraffic, withTraffic.endsWith(" \u00b7 +2 min traffic"))
        assertFalse(RouteSheetMapper.summary(clear, 1_200, now).contains("traffic"))
    }

    @Test fun legLinesAccumulateArrivalTimes() {
        val legs = listOf(RouteLeg(600, 5_000, emptyList()), RouteLeg(1_200, 12_400, emptyList()), RouteLeg(300, 800, emptyList()))
        val lines = StopLegs.lines(legs, 3, now, utc)
        assertEquals(3, lines.size)
        assertEquals("10 min \u00b7 5.0 km \u00b7 arrive 22:23", lines[0])
        assertEquals("20 min \u00b7 12 km \u00b7 arrive 22:43", lines[1])
        assertEquals("5 min \u00b7 800 m \u00b7 arrive 22:48", lines[2])
    }

    @Test fun noLinesWhenLegsDontMatchTheStops() {
        val one = listOf(RouteLeg(600, 5_000, emptyList()))
        assertEquals(emptyList<String>(), StopLegs.lines(one, 1, now, utc))      // plain trip
        assertEquals(emptyList<String>(), StopLegs.lines(emptyList(), 3, now, utc))
        assertEquals(emptyList<String>(), StopLegs.lines(one + one, 3, now, utc)) // stale route
    }

    @Test fun dragMovesWholeRowsAndStaysInTheList() {
        assertEquals(0, StopLegs.dragDelta(20f, 100f, 1, 3))
        assertEquals(1, StopLegs.dragDelta(60f, 100f, 0, 3))
        assertEquals(-1, StopLegs.dragDelta(-140f, 100f, 2, 3))
        assertEquals(2, StopLegs.dragDelta(900f, 100f, 0, 3))   // clamped to the last slot
        assertEquals(-1, StopLegs.dragDelta(-900f, 100f, 1, 3)) // clamped to the first
        assertEquals(0, StopLegs.dragDelta(500f, 0f, 0, 3))
        assertEquals(0, StopLegs.dragDelta(500f, 100f, 0, 1))
    }
}
