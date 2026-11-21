package com.navigator.app.nav.ktm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class ClockFormatTest {
    private val utc = ZoneOffset.UTC
    private val at2223 = 1_700_000_000_000L + 10 * 60_000 // 22:23 UTC

    @After fun reset() { ClockFormat.use24h = { true } }

    @Test fun twentyFourHourIsTheDashStyle() {
        assertEquals("22:23", ClockFormat.local(at2223, utc))
        assertEquals(EtaFormatter.format(at2223, utc), ClockFormat.local(at2223, utc))
    }

    @Test fun twelveHourShowsTheMarkerAndNoLeadingZero() {
        ClockFormat.use24h = { false }
        val text = ClockFormat.local(at2223, utc).replace('\u202F', ' ').uppercase()
        assertEquals("10:23 PM", text)
        assertEquals("12:05 AM", ClockFormat.local(5 * 60_000L, utc).replace('\u202F', ' ').uppercase())
        assertEquals("12:05 PM", ClockFormat.local(12 * 3_600_000L + 5 * 60_000L, utc).replace('\u202F', ' ').uppercase())
    }

    @Test fun theDashPathIgnoresThePhoneSetting() {
        ClockFormat.use24h = { false }
        assertEquals("22:23", EtaFormatter.format(at2223, utc))
    }

    @Test fun patternsCarrySecondsAndDateWhenAsked() {
        assertEquals(true, ClockFormat.pattern(Locale.US, seconds = true).contains("ss"))
        assertEquals(true, ClockFormat.pattern(Locale.US, date = "yMd").length > ClockFormat.pattern(Locale.US).length)
    }
}
