package com.navigator.app.nav.ktm

import com.navigator.app.nav.model.DistanceUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceFormatterTest {

    private fun m(meters: Int) = DistanceFormatter.format(meters, DistanceUnits.METRIC)
    private fun i(meters: Int) = DistanceFormatter.format(meters, DistanceUnits.IMPERIAL)

    @Test fun metric_metres_roundToNearest10() {
        assertEquals("0 m", m(0))
        assertEquals("0 m", m(4))
        assertEquals("10 m", m(5))
        assertEquals("110 m", m(114))
        assertEquals("120 m", m(116))
        assertEquals("990 m", m(990))
    }

    @Test fun metric_kilometres_bucketing() {
        assertEquals("1.0 km", m(1000))
        assertEquals("1.2 km", m(1240))
        assertEquals("9.5 km", m(9500))
        assertEquals("10 km", m(10000))
        assertEquals("42 km", m(42000))
        assertEquals("123 km", m(123456))
    }

    @Test fun metric_negativeClampsToZero() {
        assertEquals("0 m", m(-50))
    }

    @Test fun imperial_feetAndMiles() {
        assertEquals("0 ft", i(0))
        assertEquals("160 ft", i(50))
        assertEquals("330 ft", i(100))
        assertEquals("1.0 mi", i(1609))
        assertEquals("20 mi", i(32187))
    }

    @Test fun allOutputsFitEightChars() {
        // The KTM distance/remaining characteristics cap at 8 chars.
        val samples = listOf(0, 5, 114, 990, 1000, 1240, 9500, 42000, 123456, 999999)
        for (s in samples) {
            assertTrue("metric '${m(s)}' <= 8", m(s).length <= 8)
            assertTrue("imperial '${i(s)}' <= 8", i(s).length <= 8)
        }
    }
}
