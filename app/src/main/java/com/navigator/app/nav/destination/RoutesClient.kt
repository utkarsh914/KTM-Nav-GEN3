package com.navigator.app.nav.destination

import com.google.android.gms.maps.model.LatLng
import com.navigator.app.BuildConfig
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A single computed route for the pre-navigation preview. */
data class RoutePreview(
    val durationSeconds: Int,
    val distanceMeters: Int,
    val points: List<LatLng>,
    /** Routes API route token — lets the Nav SDK guide this exact route. */
    val routeToken: String? = null,
    /** Traffic delay in seconds: live (traffic-aware) `duration` minus the
     *  free-flow `staticDuration`. 0 when unknown or no delay. */
    val delaySeconds: Int = 0,
    /** The travel mode that produced this route/token. The Nav SDK must consume
     *  the token with the *same* mode or it rejects it (falling back to its own
     *  default route). Preview can fall back TWO_WHEELER -> DRIVE, so this is
     *  carried through to Start rather than assumed. */
    val travelMode: TravelMode = TravelMode.TWO_WHEELER,
    /** Per-leg breakdown (origin->stop1, stop1->stop2, ..., ->destination).
     *  Only populated for multi-stop routes; empty otherwise. */
    val legs: List<RouteLeg> = emptyList(),
)

/** One leg of a multi-stop route (between two consecutive waypoints). */
data class RouteLeg(
    val durationSeconds: Int,
    val distanceMeters: Int,
    val points: List<LatLng>,
)

/**
 * Routes API (New) over plain HTTPS — `directions/v2:computeRoutes` — used only to
 * draw the pre-navigation **preview** (blue route line + ETA/distance). The actual
 * turn-by-turn still runs through the Nav SDK ([com.navigator.app.nav.providers.GoogleNavSdkController]).
 *
 * Requests TWO_WHEELER and falls back to DRIVE (matching the Nav SDK path).
 * Alternate routes are a planned improvement (Routes API `computeAlternativeRoutes`).
 */
object RoutesClient {

    private const val URL_COMPUTE = "https://routes.googleapis.com/directions/v2:computeRoutes"

    /**
     * Returns routes best-first (index 0 = primary); empty on failure.
     *
     * Requests the [preferred] vehicle mode (from the user's setting) first and
     * falls back to the other mode only if that yields nothing, so the preview
     * never comes up empty. The chosen mode is recorded on each [RoutePreview] so
     * the Nav SDK consumes the route token with the same mode at Start.
     */
    suspend fun computeRoutes(
        origin: Pair<Double, Double>,
        dest: Pair<Double, Double>,
        auth: AndroidClientAuth?,
        preferred: TravelMode = TravelMode.TWO_WHEELER,
        intermediates: List<Pair<Double, Double>> = emptyList(),
    ): List<RoutePreview> {
        val primary = if (preferred == TravelMode.DRIVING) "DRIVE" else "TWO_WHEELER"
        val secondary = if (primary == "DRIVE") "TWO_WHEELER" else "DRIVE"
        // Try the other travel mode only when the first one was ANSWERED with nothing
        // (empty result / 4xx = "unsupported here"). A network failure or 5xx would just
        // fail again - retrying it doubled the wait offline.
        return when (val first = request(origin, dest, primary, auth, intermediates)) {
            is RequestResult.Ok -> if (first.routes.isNotEmpty()) first.routes else
                (request(origin, dest, secondary, auth, intermediates) as? RequestResult.Ok)?.routes.orEmpty()
            is RequestResult.Rejected ->
                if (!first.modeUnsupported) emptyList() else (request(origin, dest, secondary, auth, intermediates) as? RequestResult.Ok)?.routes.orEmpty()
            RequestResult.Unavailable -> emptyList()
        }
    }

    private sealed interface RequestResult {
        class Ok(val routes: List<RoutePreview>) : RequestResult
        /** HTTP 4xx. [modeUnsupported] only for 400/404/422 (travel mode not available here);
         *  401/403/429 (key, quota, rate limit) would fail the same way in the other mode. */
        class Rejected(val modeUnsupported: Boolean) : RequestResult
        /** Offline, timeout, 5xx, no key: retrying another mode won't help. */
        data object Unavailable : RequestResult
    }

    /** Map the Routes API travel-mode string to the app's [TravelMode]. */
    private fun modeOf(travelMode: String): TravelMode =
        if (travelMode == "TWO_WHEELER") TravelMode.TWO_WHEELER else TravelMode.DRIVING

    /**
     * Build the `computeRoutes` request body. Pure and JVM-testable.
     *
     * Note: the Routes API rejects `computeAlternativeRoutes: true` together with
     * `intermediates`, so alternates are requested only when there are no stops.
     */
    fun buildRequestBody(
        origin: Pair<Double, Double>,
        dest: Pair<Double, Double>,
        travelMode: String,
        intermediates: List<Pair<Double, Double>> = emptyList(),
    ): JSONObject = JSONObject().apply {
        put("origin", waypointJson(origin))
        put("destination", waypointJson(dest))
        if (intermediates.isNotEmpty()) {
            put("intermediates", org.json.JSONArray().apply { intermediates.forEach { put(waypointJson(it)) } })
        }
        put("travelMode", travelMode)
        // routingPreference is required for a routeToken to be returned.
        put("routingPreference", "TRAFFIC_AWARE")
        put("polylineEncoding", "ENCODED_POLYLINE")
        // Alternates are incompatible with intermediates; only ask when no stops.
        put("computeAlternativeRoutes", intermediates.isEmpty())
        put("regionCode", "IN")
    }

    private suspend fun request(
        origin: Pair<Double, Double>,
        dest: Pair<Double, Double>,
        travelMode: String,
        auth: AndroidClientAuth?,
        intermediates: List<Pair<Double, Double>> = emptyList(),
    ): RequestResult = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank()) return@withContext RequestResult.Unavailable

        val body = buildRequestBody(origin, dest, travelMode, intermediates)
        val conn = (URL(URL_COMPUTE).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty(
                "X-Goog-FieldMask",
                "routes.duration,routes.staticDuration,routes.distanceMeters," +
                    "routes.polyline.encodedPolyline,routes.routeToken," +
                    "routes.legs.duration,routes.legs.distanceMeters,routes.legs.polyline.encodedPolyline",
            )
            applyAndroidAuth(auth)
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = readBody(conn, code)
            if (code !in 200..299) {
                AppLogger.log("Routes", "computeRoutes($travelMode) HTTP $code: ${text.take(300)}")
                return@withContext if (code in 400..499) {
                    RequestResult.Rejected(modeUnsupported = code == 400 || code == 404 || code == 422)
                } else RequestResult.Unavailable
            }
            RequestResult.Ok(parseRoutes(text, modeOf(travelMode)))
        } catch (e: Exception) {
            AppLogger.log("Routes", "computeRoutes($travelMode) error: ${e.message}")
            RequestResult.Unavailable
        } finally {
            conn.disconnect()
        }
    }

    private fun waypointJson(p: Pair<Double, Double>): JSONObject =
        JSONObject().put(
            "location",
            JSONObject().put(
                "latLng",
                JSONObject().put("latitude", p.first).put("longitude", p.second),
            ),
        )

    /** Parse all routes' duration/distance/polyline (best-first). Visible for future tests. */
    fun parseRoutes(json: String, travelMode: TravelMode = TravelMode.TWO_WHEELER): List<RoutePreview> {
        val routes = JSONObject(json).optJSONArray("routes") ?: return emptyList()
        val out = ArrayList<RoutePreview>(routes.length())
        for (i in 0 until routes.length()) {
            val r = runCatching { routes.getJSONObject(i) }.getOrNull() ?: continue
            val distance = r.optInt("distanceMeters", 0)
            // duration / staticDuration come as protobuf duration strings, e.g. "3120s" or "3120.5s".
            val durationSec = parseDurationSeconds(r.optString("duration")) ?: 0
            val staticSec = parseDurationSeconds(r.optString("staticDuration")) ?: durationSec
            val delaySec = (durationSec - staticSec).coerceAtLeast(0)
            val encoded = r.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            val points = decodePolyline(encoded)
            val token = r.optString("routeToken").ifBlank { null }
            val legs = parseLegs(r.optJSONArray("legs"))
            if (points.isNotEmpty()) {
                out += RoutePreview(durationSec, distance, points, token, delaySec, travelMode, legs)
            }
        }
        return out
    }

    /** "3120s" / "3120.5s" -> whole seconds; null when unparsable. */
    internal fun parseDurationSeconds(s: String): Int? =
        s.removeSuffix("s").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.toInt()

    /** Parse a route's `legs[]` array into [RouteLeg]s (empty for single-leg). */
    private fun parseLegs(legs: org.json.JSONArray?): List<RouteLeg> {
        if (legs == null || legs.length() <= 1) return emptyList()
        val out = ArrayList<RouteLeg>(legs.length())
        for (i in 0 until legs.length()) {
            val l = legs.getJSONObject(i)
            val d = parseDurationSeconds(l.optString("duration")) ?: 0
            val dist = l.optInt("distanceMeters", 0)
            val pts = decodePolyline(l.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty())
            out += RouteLeg(d, dist, pts)
        }
        return out
    }

    /** Standard Google encoded-polyline algorithm → lat/lng points. */
    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) return poly // truncated polyline: keep what decoded cleanly
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) return poly // cut off between lat and lng: drop the half point
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return poly
    }

}
