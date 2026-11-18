package com.navigator.app.ride

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.SAXParserFactory

/** A track read from a GPX file. */
class GpxTrack(val name: String?, val points: List<RidePoint>)

/**
 * GPX 1.1 export and a tolerant track import. Pure JVM (SAX + java.time), unit-tested.
 * A user pause (`gapBefore`) becomes a new `<trkseg>`; importing starts a gap at every
 * segment boundary, so a round trip keeps pauses out of distance and duration.
 */
object Gpx {

    const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
    const val MAX_IMPORT_POINTS = 200_000
    /** Pace assumed for untimed points: 45 km/h. */
    private const val SYNTH_MPS = 12.5

    // ---- Export -----------------------------------------------------------

    fun write(name: String?, points: List<RidePoint>): String {
        val sb = StringBuilder(points.size * 110 + 400)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"KTM Navigator\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n")
        if (!name.isNullOrBlank()) sb.append("    <name>").append(escape(name)).append("</name>\n")
        var open = false
        points.forEachIndexed { i, p ->
            if (!open || (p.gapBefore && i > 0)) {
                if (open) sb.append("    </trkseg>\n")
                sb.append("    <trkseg>\n")
                open = true
            }
            sb.append("      <trkpt lat=\"").append(num(p.lat, 7)).append("\" lon=\"").append(num(p.lng, 7)).append("\">")
            p.altM?.let { sb.append("<ele>").append(num(it.toDouble(), 1)).append("</ele>") }
            sb.append("<time>").append(Instant.ofEpochMilli(p.timeMs)).append("</time>")
            p.speedKmh?.let { sb.append("<extensions><speed>").append(num((it / 3.6f).toDouble(), 3)).append("</speed></extensions>") }
            sb.append("</trkpt>\n")
        }
        if (open) sb.append("    </trkseg>\n")
        sb.append("  </trk>\n</gpx>\n")
        return sb.toString()
    }

    /** Plain decimal (never scientific notation, which xsd:decimal readers reject). */
    private fun num(v: Double, decimals: Int): String = String.format(java.util.Locale.ROOT, "%.${decimals}f", v)

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        // XML 1.0 can't carry control characters.
        .filter { it == '\n' || it == '\t' || it.code >= 0x20 }

    /** Filesystem-safe base name for an exported ride. */
    fun fileName(label: String?, startMs: Long): String {
        val date = java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(startMs), java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
        val slug = (label ?: "ride").lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { "ride" }
        return "$slug-$date.gpx"
    }

    // ---- Import -----------------------------------------------------------

    /**
     * Parse the first track(s) of a GPX stream. Points without a timestamp get synthetic ones
     * 1 s apart starting at [fallbackStartMs]. Out-of-range coordinates are skipped; more than
     * [MAX_IMPORT_POINTS] points are thinned evenly. Returns null for unreadable / non-GPX /
     * oversized input or fewer than 2 usable points.
     */
    fun parse(input: InputStream, fallbackStartMs: Long): GpxTrack? {
        val bytes = readCapped(input) ?: return null
        // A GPX file needs neither a DOCTYPE nor entities (the XXE / billion-laughs vectors), and
        // UTF-16 (NUL bytes) would slip past the byte scans below: reject all of them.
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1).lowercase()
        if ('\u0000' in head || "<!doctype" in head || indexOf(bytes, "<!ENTITY".toByteArray()) >= 0) return null
        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.errorHandler = handler
            // Never fetch an external DTD / entity, whatever the parser's feature support.
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(java.io.StringReader("")) }
            reader.parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (e: Exception) {
            // Keep whatever parsed before a late syntax error only if it is clearly a track.
            if (handler.raw.size < 2) return null
        }
        if (!handler.sawGpx) return null
        val pts = handler.build(fallbackStartMs)
        return if (pts.size >= 2) GpxTrack(handler.name, pts) else null
    }

    private fun readCapped(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    internal fun parseTime(s: String): Long? {
        val t = s.trim()
        return runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            // No zone given: GPX times are UTC by definition.
            ?: runCatching { java.time.LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private class Raw(val lat: Double, val lng: Double, var timeMs: Long?, var speedMs: Float?, val newSegment: Boolean, var altM: Float? = null)

    private class Handler : DefaultHandler() {
        val raw = ArrayList<Raw>()
        var name: String? = null
        var sawGpx = false
        private var inTrk = false
        private var inTrkpt = false
        private var pendingSegment = false
        private var cur: Raw? = null
        private var text = StringBuilder()
        private var capture = false

        override fun startElement(uri: String?, local: String?, q: String, a: Attributes) {
            when (q.substringAfter(':')) {
                "gpx" -> sawGpx = true
                "trk" -> inTrk = true
                "trkseg" -> pendingSegment = raw.isNotEmpty()
                "trkpt" -> {
                    inTrkpt = true
                    if (!inTrk) return
                    val lat = a.getValue("lat")?.toDoubleOrNull()
                    val lon = a.getValue("lon")?.toDoubleOrNull()
                    cur = if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                        lat in -90.0..90.0 && lon in -180.0..180.0
                    ) Raw(lat, lon, null, null, pendingSegment).also { pendingSegment = false } else null
                }
                "time", "speed", "name", "ele" -> { text = StringBuilder(); capture = true }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, local: String?, q: String) {
            when (q.substringAfter(':')) {
                "time" -> { capture = false; cur?.let { if (it.timeMs == null) it.timeMs = parseTime(text.toString()) } }
                "ele" -> {
                    capture = false
                    cur?.altM = text.toString().trim().toFloatOrNull()?.takeIf { it.isFinite() }
                }
                "speed" -> {
                    capture = false
                    cur?.speedMs = text.toString().trim().toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
                }
                "name" -> {
                    capture = false
                    if (inTrk && !inTrkpt && name == null) name = text.toString().trim().ifEmpty { null }
                }
                "trkpt" -> { cur?.let { raw += it }; cur = null; inTrkpt = false }
                "trk" -> inTrk = false
            }
        }

        fun build(fallbackStartMs: Long): List<RidePoint> {
            var src: List<Raw> = raw
            if (src.size > MAX_IMPORT_POINTS) {
                val step = (src.size + MAX_IMPORT_POINTS - 1) / MAX_IMPORT_POINTS
                // Keep each segment's first point so thinning can't erase a pause.
                src = src.filterIndexed { i, r -> i % step == 0 || i == src.lastIndex || r.newSegment }
            }
            // With a mix of timed and untimed points the real times anchor the synthetic ones.
            var lastMs = src.firstOrNull { it.timeMs != null }?.timeMs ?: fallbackStartMs
            var prev: Raw? = null
            var first = true
            return src.mapIndexed { i, r ->
                var t = r.timeMs ?: run {
                    // No time: pace the point from the previous one at ~45 km/h (>= 1 s), so a
                    // route-style file with widely spaced points doesn't imply absurd speeds.
                    val p = prev
                    if (first || p == null) lastMs
                    else lastMs + maxOf(
                        1000L,
                        (RideMetrics.haversineMeters(p.lat, p.lng, r.lat, r.lng) / SYNTH_MPS * 1000).toLong(),
                    )
                }
                // Time must not run backwards within a track: clamp (keeps order for the accumulator).
                if (t < lastMs) t = lastMs
                first = false
                lastMs = t
                prev = r
                RidePoint(
                    lat = r.lat, lng = r.lng, timeMs = t,
                    speedKmh = r.speedMs?.let { it * 3.6f },
                    gapBefore = r.newSegment && i > 0,
                    altM = r.altM,
                )
            }
        }
    }
}
