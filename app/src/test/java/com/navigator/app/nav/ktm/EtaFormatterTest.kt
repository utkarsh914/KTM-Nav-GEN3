package com.navigator.app.nav.ktm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class EtaFormatterTest {

    private val utc = ZoneId.of("UTC")

    @Test fun format_zeroPaddedHHMM() {
        assertEquals("00:00", EtaFormatter.format(0, 0L, utc))
        assertEquals("00:05", EtaFormatter.format(5 * 60, 0L, utc))
        assertEquals("00:45", EtaFormatter.format(45 * 60, 0L, utc))
        assertEquals("01:00", EtaFormatter.format(3600, 0L, utc))
        assertEquals("01:01", EtaFormatter.format(3661, 0L, utc))
    }

    @Test fun format_dayRolloverKeepsTimeOnly() {
        // 25h ahead of 1970-01-01T00:00Z -> 01:00 (LocalTime, date dropped).
        assertEquals("01:00", EtaFormatter.format(25 * 3600, 0L, utc))
    }

    @Test fun format_addsRemainingToNow() {
        // now = 12:00:00Z, +30 min -> 12:30
        val noonUtc = 12L * 3600 * 1000
        assertEquals("12:30", EtaFormatter.format(30 * 60, noonUtc, utc))
    }

    @Test fun shouldUpdate_deadband() {
        assertTrue("no prior value -> update", EtaFormatter.shouldUpdate(null, 1_000L))
        assertFalse("within deadband -> hold", EtaFormatter.shouldUpdate(100_000L, 100_000L + 59_000L))
        assertTrue("at deadband -> update", EtaFormatter.shouldUpdate(100_000L, 100_000L + 60_000L))
        assertTrue("backwards past deadband -> update", EtaFormatter.shouldUpdate(100_000L, 100_000L - 60_000L))
    }
}
