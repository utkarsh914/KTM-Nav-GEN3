package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTextTest {
    @Test fun oneRoundingRuleUpToTheNextMinute() {
        assertEquals("<1 min", DurationText.format(0))
        assertEquals("<1 min", DurationText.format(-5))
        assertEquals("1 min", DurationText.format(1))
        assertEquals("1 min", DurationText.format(60))
        assertEquals("2 min", DurationText.format(61))
        assertEquals("45 min", DurationText.format(45 * 60))
        assertEquals("59 min", DurationText.format(59 * 60 - 1))
        assertEquals("1 h 00 min", DurationText.format(3_600))
        assertEquals("1 h 05 min", DurationText.format(3_900))
        assertEquals("2 h 00 min", DurationText.format(7_200))
    }
}
