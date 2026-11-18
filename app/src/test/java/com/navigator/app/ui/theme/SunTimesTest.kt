package com.navigator.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SunTimesTest {
    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneOffset) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private val ist = ZoneOffset.ofHoursMinutes(5, 30)
    private val blr = 12.9716 to 77.5946

    @Test fun bangaloreJuneDayAndNight() {
        // Sunrise ~05:55, sunset ~18:50 IST in late June.
        assertTrue(SunTimes.isNight(ms(2026, 6, 21, 5, 20, ist), blr.first, blr.second))
        assertFalse(SunTimes.isNight(ms(2026, 6, 21, 6, 30, ist), blr.first, blr.second))
        assertFalse(SunTimes.isNight(ms(2026, 6, 21, 12, 0, ist), blr.first, blr.second))
        assertFalse(SunTimes.isNight(ms(2026, 6, 21, 18, 30, ist), blr.first, blr.second)) // the fixed 18:00 rule got this wrong
        assertTrue(SunTimes.isNight(ms(2026, 6, 21, 19, 30, ist), blr.first, blr.second))
        assertTrue(SunTimes.isNight(ms(2026, 6, 21, 23, 0, ist), blr.first, blr.second))
    }

    @Test fun bangaloreDecemberHasAnEarlierSunset() {
        // Sunset ~17:55 IST in December.
        assertFalse(SunTimes.isNight(ms(2026, 12, 21, 17, 30, ist), blr.first, blr.second))
        assertTrue(SunTimes.isNight(ms(2026, 12, 21, 18, 30, ist), blr.first, blr.second))
        assertTrue(SunTimes.isNight(ms(2026, 12, 21, 6, 0, ist), blr.first, blr.second))
        assertFalse(SunTimes.isNight(ms(2026, 12, 21, 7, 0, ist), blr.first, blr.second))
    }

    @Test fun dayStraddlingUtcMidnightIsHandled() {
        // Auckland (UTC+13 in summer): local noon is 23:00 UTC the day before.
        val nz = ZoneOffset.ofHours(13)
        assertFalse(SunTimes.isNight(ms(2026, 1, 10, 12, 0, nz), -36.85, 174.76))
        assertTrue(SunTimes.isNight(ms(2026, 1, 10, 2, 0, nz), -36.85, 174.76))
        assertTrue(SunTimes.isNight(ms(2026, 1, 10, 23, 30, nz), -36.85, 174.76))
    }

    @Test fun polarDayAndNight() {
        val utc = ZoneOffset.UTC
        // Tromso 69.65 N: midnight sun in June, polar night in December.
        assertFalse(SunTimes.isNight(ms(2026, 6, 21, 0, 0, utc), 69.65, 18.96))
        assertTrue(SunTimes.isNight(ms(2026, 12, 21, 11, 0, utc), 69.65, 18.96))
        // ...mirrored in the southern hemisphere.
        assertTrue(SunTimes.isNight(ms(2026, 6, 21, 12, 0, utc), -78.0, 166.0))
        assertFalse(SunTimes.isNight(ms(2026, 12, 21, 0, 0, utc), -78.0, 166.0))
    }

    @Test fun equatorIsRoughlyTwelveHoursEverywhere() {
        val utc = ZoneOffset.UTC
        assertFalse(SunTimes.isNight(ms(2026, 3, 20, 12, 0, utc), 0.0, 0.0))
        assertTrue(SunTimes.isNight(ms(2026, 3, 20, 0, 0, utc), 0.0, 0.0))
        assertTrue(SunTimes.isNight(ms(2026, 3, 20, 23, 0, utc), 0.0, 0.0))
    }
}
