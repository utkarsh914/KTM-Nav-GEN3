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
    fun metaLine(destLabel: String?, destLat: Double?, destLng: Double?, source: String? = null): String? {
        if (destLabel == null && destLat == null && destLng == null && source == null) return null
        val o = JSONObject().put("meta", true)
        source?.let { o.put("src", it) }
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
        if (p.gapBefore) o.put("g", 1)
        p.altM?.let { o.put("a", Math.round(it * 10f) / 10.0) }
        return o.toString()
    }

    /** Parse one NDJSON line into a point, or null for meta/blank/invalid lines. */
    fun parsePoint(line: String): RidePoint? {
        if (line.isBlank()) return null
        return runCatching {
            val o = JSONObject(line)
            // A fix without a timestamp can't be placed in time: skip it rather than as t=0.
            if (o.optBoolean("meta", false) || !o.has("lat") || !o.has("t")) return null
            RidePoint(
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                timeMs = o.getLong("t"),
                speedKmh = if (o.has("s")) o.getDouble("s").toFloat() else null,
                gapBefore = o.optInt("g", 0) == 1,
                altM = if (o.has("a")) o.getDouble("a").toFloat().takeIf { it.isFinite() } else null,
            )
        }.getOrNull()
    }

    /** Parse a meta header line, or null when the line isn't a meta header. */
    fun parseMeta(line: String): JSONObject? = runCatching {
        val o = JSONObject(line)
        if (o.optBoolean("meta", false)) o else null
    }.getOrNull()

    // ---- Index entries (metadata) -----------------------------------------

    /** A merged ride cannot claim more parts than this, whatever a restored file says. */
    const val MAX_PARTS = 99

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
        put("movingSeconds", r.movingSeconds)
        put("avgOverallSpeedKmh", r.avgOverallSpeedKmh.toDouble())
        put("maxSpeedKmh", r.maxSpeedKmh.toDouble())
        put("pointCount", r.pointCount)
        put("saved", r.saved)
        put("elevGainM", r.elevGainM)
        put("elevLossM", r.elevLossM)
        r.notes?.let { put("notes", it) }
        r.source?.let { put("src", it) }
        r.deletedAtMs?.let { put("del", it) }
        if (r.parts > 1) put("parts", r.parts)
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
        movingSeconds = o.optInt("movingSeconds", MOVING_UNKNOWN),
        avgOverallSpeedKmh = o.optDouble("avgOverallSpeedKmh", 0.0).toFloat(),
        maxSpeedKmh = o.optDouble("maxSpeedKmh", 0.0).toFloat(),
        pointCount = o.optInt("pointCount", 0),
        saved = o.optBoolean("saved", false),
        elevGainM = o.optInt("elevGainM", ELEV_UNKNOWN),
        elevLossM = o.optInt("elevLossM", ELEV_UNKNOWN),
        notes = o.optString("notes").ifBlank { null },
        source = RideSource.clean(o.optString("src")),
        deletedAtMs = if (o.has("del")) o.optLong("del") else null,
        parts = o.optInt("parts", 1).coerceIn(1, MAX_PARTS),
    )
}
