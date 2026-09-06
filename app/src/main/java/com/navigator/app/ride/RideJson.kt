package com.navigator.app.ride

import org.json.JSONObject

/**
 * Pure (de)serialisation for rides — no Android dependencies, so the wire format
 * is JVM unit-testable. [RideStore] handles the file/prefs I/O and delegates the
 * actual encoding here.
 *
 * Track files are NDJSON: an optional `{"meta":true,...}` first line carrying the
 * destination, then one `{"lat","lng","t","s"}` object per fix.
 */
object RideJson {

    // ---- Track file lines (NDJSON) ----------------------------------------

    /** Destination header line, or null when no destination detail is known. */
    fun metaLine(destLabel: String?, destLat: Double?, destLng: Double?): String? {
        if (destLabel == null && destLat == null && destLng == null) return null
        val o = JSONObject().put("meta", true)
        destLabel?.let { o.put("label", it) }
        destLat?.let { o.put("dlat", it) }
        destLng?.let { o.put("dlng", it) }
        return o.toString()
    }

    fun pointLine(p: RidePoint): String {
        val o = JSONObject()
            .put("lat", p.lat)
            .put("lng", p.lng)
            .put("t", p.timeMs)
        p.speedKmh?.let { o.put("s", it.toDouble()) }
        return o.toString()
    }

    /** Parse one NDJSON line into a point, or null for meta/blank/invalid lines. */
    fun parsePoint(line: String): RidePoint? {
        if (line.isBlank()) return null
        return runCatching {
            val o = JSONObject(line)
            if (o.optBoolean("meta", false) || !o.has("lat")) return null
            RidePoint(
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                timeMs = o.optLong("t", 0L),
                speedKmh = if (o.has("s")) o.getDouble("s").toFloat() else null,
            )
        }.getOrNull()
    }

    /** Parse a meta header line, or null when the line isn't a meta header. */
    fun parseMeta(line: String): JSONObject? = runCatching {
        val o = JSONObject(line)
        if (o.optBoolean("meta", false)) o else null
    }.getOrNull()

    // ---- Index entries (metadata) -----------------------------------------

    fun rideToJson(r: RecordedRide): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("startMs", r.startMs)
        put("endMs", r.endMs)
        put("distanceMeters", r.distanceMeters)
        put("durationSeconds", r.durationSeconds)
        r.destinationLabel?.let { put("destinationLabel", it) }
        r.destLat?.let { put("destLat", it) }
        r.destLng?.let { put("destLng", it) }
        put("avgSpeedKmh", r.avgSpeedKmh.toDouble())
        put("maxSpeedKmh", r.maxSpeedKmh.toDouble())
        put("pointCount", r.pointCount)
        put("saved", r.saved)
    }

    fun rideFromJson(o: JSONObject): RecordedRide = RecordedRide(
        id = o.optString("id"),
        startMs = o.optLong("startMs", 0L),
        endMs = o.optLong("endMs", 0L),
        distanceMeters = o.optInt("distanceMeters", 0),
        durationSeconds = o.optInt("durationSeconds", 0),
        destinationLabel = o.optString("destinationLabel").ifBlank { null },
        destLat = if (o.has("destLat")) o.getDouble("destLat") else null,
        destLng = if (o.has("destLng")) o.getDouble("destLng") else null,
        avgSpeedKmh = o.optDouble("avgSpeedKmh", 0.0).toFloat(),
        maxSpeedKmh = o.optDouble("maxSpeedKmh", 0.0).toFloat(),
        pointCount = o.optInt("pointCount", 0),
        saved = o.optBoolean("saved", false),
    )
}
