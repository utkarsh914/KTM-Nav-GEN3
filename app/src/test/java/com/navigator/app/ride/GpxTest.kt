package com.navigator.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {

    private fun parse(s: String, fallback: Long = 1_000_000L) =
        Gpx.parse(s.byteInputStream(), fallback)

    private val pts = listOf(
        RidePoint(12.9716, 77.5946, 1_700_000_000_000L, 36f),
        RidePoint(12.9720, 77.5950, 1_700_000_001_000L, 40f),
        RidePoint(12.9800, 77.6000, 1_700_000_600_000L, null, gapBefore = true),
        RidePoint(12.9805, 77.6004, 1_700_000_601_000L, 20f),
    )

    @Test fun roundTripKeepsPointsSpeedsAndPauses() {
        val xml = Gpx.write("Cafe & <Co>", pts)
        val back = parse(xml)!!
        assertEquals("Cafe & <Co>", back.name)
        assertEquals(pts.size, back.points.size)
        pts.zip(back.points).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-9); assertEquals(a.lng, b.lng, 1e-9)
            assertEquals(a.timeMs, b.timeMs); assertEquals(a.gapBefore, b.gapBefore)
            assertEquals(a.speedKmh?.toDouble() ?: -1.0, b.speedKmh?.toDouble() ?: -1.0, 0.01)
        }
        // The pause is its own trkseg and stays out of the stats.
        assertEquals(2, Regex("<trkseg>").findAll(xml).count())
        assertEquals(RideMetrics.summarize(pts).durationSeconds, RideMetrics.summarize(back.points).durationSeconds)
    }

    @Test fun readsTypicalThirdPartyFile() {
        val xml = """<?xml version="1.0"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1" xmlns:x="u">
              <metadata><name>ignored meta</name></metadata>
              <wpt lat="1" lon="1"><name>wp</name></wpt>
              <trk><name>Morning ride</name><trkseg>
                <trkpt lat="12.0" lon="77.0"><ele>900</ele><time>2024-05-01T05:00:00+05:30</time></trkpt>
                <trkpt lat="12.001" lon="77.001"><time>2024-05-01T05:00:10Z</time><extensions><x:speed>5</x:speed></extensions></trkpt>
              </trkseg></trk></gpx>"""
        val t = parse(xml)!!
        assertEquals("Morning ride", t.name)
        assertEquals(2, t.points.size)
        // +05:30 offset honoured: 05:00+05:30 = 23:30Z previous day, before 05:00:10Z -> clamped monotonic
        assertTrue(t.points[1].timeMs >= t.points[0].timeMs)
        assertEquals(18f, t.points[1].speedKmh!!, 0.01f) // 5 m/s
    }

    @Test fun pointsWithoutTimeGetSyntheticSpacing() {
        val t = parse("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="1.001" lon="1"/><trkpt lat="1.002" lon="1"/></trkseg></trk></gpx>""", 5_000)!!
        // First point at the fallback start, then paced at ~45 km/h (111 m ~ 8.9 s), never less than 1 s.
        assertEquals(5_000L, t.points[0].timeMs)
        assertEquals(8_900.0, (t.points[1].timeMs - t.points[0].timeMs).toDouble(), 100.0)
        assertEquals(8_900.0, (t.points[2].timeMs - t.points[1].timeMs).toDouble(), 100.0)
    }

    @Test fun badPointsAreSkipped() {
        val t = parse("""<gpx><trk><trkseg>
            <trkpt lat="95" lon="1"><time>2024-01-01T00:00:00Z</time></trkpt>
            <trkpt lat="x" lon="1"/>
            <trkpt lat="1" lon="1"><time>2024-01-01T00:00:01Z</time></trkpt>
            <trkpt lat="1.1" lon="1"><time>2024-01-01T00:00:02Z</time></trkpt></trkseg></trk></gpx>""")!!
        assertEquals(2, t.points.size)
    }

    @Test fun rejectsNonGpxTooFewPointsGarbageAndEntities() {
        assertNull(parse("not xml at all"))
        assertNull(parse("<html><body/></html>"))
        assertNull(parse("""<gpx><trk><trkseg><trkpt lat="1" lon="1"/></trkseg></trk></gpx>"""))
        assertNull(parse(""))
        val xxe = """<?xml version="1.0"?><!DOCTYPE g [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="2" lon="2"/></trkseg></trk></gpx>"""
        assertNull(parse(xxe))
    }

    @Test fun routePointsAndWaypointsAreNotTrackPoints() {
        val t = parse("""<gpx><rte><rtept lat="1" lon="1"/><rtept lat="2" lon="2"/></rte></gpx>""")
        assertNull(t)
    }

    @Test fun segmentsBecomeGaps() {
        val t = parse("""<gpx><trk>
            <trkseg><trkpt lat="1" lon="1"><time>2024-01-01T00:00:00Z</time></trkpt><trkpt lat="1.0001" lon="1"><time>2024-01-01T00:00:01Z</time></trkpt></trkseg>
            <trkseg><trkpt lat="2" lon="2"><time>2024-01-01T01:00:00Z</time></trkpt><trkpt lat="2.0001" lon="2"><time>2024-01-01T01:00:01Z</time></trkpt></trkseg>
            </trk></gpx>""")!!
        assertEquals(listOf(false, false, true, false), t.points.map { it.gapBefore })
    }

    @Test fun hugeTracksAreThinned() {
        val sb = StringBuilder("<gpx><trk><trkseg>")
        repeat(Gpx.MAX_IMPORT_POINTS + 50_000) { sb.append("<trkpt lat=\"1\" lon=\"${1 + it * 1e-6}\"/>") }
        sb.append("</trkseg></trk></gpx>")
        val t = parse(sb.toString())!!
        assertTrue(t.points.size <= Gpx.MAX_IMPORT_POINTS + 1)
        assertTrue(t.points.size > Gpx.MAX_IMPORT_POINTS / 2)
    }

    @Test fun fileNameIsSafe() {
        val n = Gpx.fileName("Café / Bar: Ünï*", 1_700_000_000_000L)
        assertTrue(n, n.matches(Regex("[a-z0-9-]+-\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.gpx")))
        assertTrue(Gpx.fileName(null, 0L).startsWith("ride-"))
        assertFalse(Gpx.fileName("***", 0L).startsWith("-"))
    }

    @Test fun controlCharactersAreStrippedFromNames() {
        val xml = Gpx.write("a\u0001b", pts)
        assertNotNull(parse(xml))
        assertEquals("ab", parse(xml)!!.name)
    }

    @Test fun untimedRouteWithWidePointSpacingKeepsSaneStats() {
        // Points ~550 m apart, no times: 1 s synthetic spacing would imply ~2000 km/h.
        val sb = StringBuilder("<gpx><trk><trkseg>")
        repeat(20) { sb.append("<trkpt lat=\"${12.0 + it * 0.005}\" lon=\"77.0\"/>") }
        sb.append("</trkseg></trk></gpx>")
        val t = parse(sb.toString())!!
        val s = RideMetrics.summarize(t.points)
        assertEquals(20, s.pointCount) // nothing rejected as a glitch
        assertTrue("avg=${s.avgOverallSpeedKmh}", s.avgOverallSpeedKmh in 30f..60f)
    }

    @Test fun thinningKeepsSegmentStarts() {
        val sb = StringBuilder("<gpx><trk>")
        repeat(3) { seg ->
            sb.append("<trkseg>")
            repeat(Gpx.MAX_IMPORT_POINTS / 2 + 10_000) { sb.append("<trkpt lat=\"1\" lon=\"${seg + it * 1e-6}\"/>") }
            sb.append("</trkseg>")
        }
        sb.append("</trk></gpx>")
        val t = parse(sb.toString())!!
        assertEquals(2, t.points.count { it.gapBefore })
    }

    @Test fun doctypeAndUtf16AreRejected() {
        assertNull(parse("""<?xml version="1.0"?><!DOCTYPE gpx><gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="2" lon="2"/></trkseg></trk></gpx>"""))
        val utf16 = """<?xml version="1.0" encoding="UTF-16"?><gpx><trk><trkseg><trkpt lat="1" lon="1"/><trkpt lat="2" lon="2"/></trkseg></trk></gpx>""".toByteArray(Charsets.UTF_16)
        assertNull(Gpx.parse(utf16.inputStream(), 0))
    }

    @Test fun coordinatesNearZeroAreWrittenAsPlainDecimals() {
        val xml = Gpx.write("x", listOf(RidePoint(0.00001, -0.00002, 1_700_000_000_000L, 0.5f), RidePoint(0.0001, 0.0001, 1_700_000_001_000L, null)))
        assertFalse(xml, xml.contains("E-"))
        assertEquals(2, parse(xml)!!.points.size)
    }

    @Test fun zonelessTimesAreTakenAsUtcAndMixedFilesAreAnchoredToRealTimes() {
        val t = parse("""<gpx><trk><trkseg>
            <trkpt lat="1" lon="1"/>
            <trkpt lat="1.0001" lon="1"><time>2024-05-01T05:00:00</time></trkpt>
            <trkpt lat="1.0002" lon="1"><time>2024-05-01T05:00:10Z</time></trkpt></trkseg></trk></gpx>""")!!
        val expected = java.time.Instant.parse("2024-05-01T05:00:00Z").toEpochMilli()
        assertEquals(expected, t.points[0].timeMs) // untimed first point anchored to the first real time
        assertEquals(expected, t.points[1].timeMs)
        assertEquals(expected + 10_000, t.points[2].timeMs)
    }

    @Test fun nameInsideAnInvalidPointIsNotTheTrackName() {
        val t = parse("""<gpx><trk><trkseg><trkpt lat="999" lon="1"><name>wrong</name></trkpt>
            <trkpt lat="1" lon="1"/><trkpt lat="1.1" lon="1"/></trkseg></trk></gpx>""")!!
        assertNull(t.name)
    }
}
