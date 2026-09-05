package com.navigator.app.nav.destination

import com.google.android.gms.maps.model.LatLng
import com.navigator.app.BuildConfig
import com.navigator.app.logging.AppLogger
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

    /** Returns routes best-first (index 0 = primary); empty on failure. */
    suspend fun computeRoutes(
        origin: Pair<Double, Double>,
        dest: Pair<Double, Double>,
        auth: AndroidClientAuth?,
    ): List<RoutePreview> {
        return request(origin, dest, "TWO_WHEELER", auth)
            .ifEmpty { request(origin, dest, "DRIVE", auth) }
    }

    private suspend fun request(
        origin: Pair<Double, Double>,
        dest: Pair<Double, Double>,
        travelMode: String,
        auth: AndroidClientAuth?,
    ): List<RoutePreview> = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank()) return@withContext emptyList()

        val body = JSONObject().apply {
            put("origin", waypointJson(origin))
            put("destination", waypointJson(dest))
            put("travelMode", travelMode)
            // routingPreference is required for a routeToken to be returned.
            put("routingPreference", "TRAFFIC_AWARE")
            put("polylineEncoding", "ENCODED_POLYLINE")
            put("computeAlternativeRoutes", true)
            put("regionCode", "IN")
        }
        val conn = (URL(URL_COMPUTE).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty(
                "X-Goog-FieldMask",
                "routes.duration,routes.staticDuration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.routeToken",
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
                return@withContext emptyList()
            }
            parseRoutes(text)
        } catch (e: Exception) {
            AppLogger.log("Routes", "computeRoutes($travelMode) error: ${e.message}")
            emptyList()
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
    fun parseRoutes(json: String): List<RoutePreview> {
        val routes = JSONObject(json).optJSONArray("routes") ?: return emptyList()
        val out = ArrayList<RoutePreview>(routes.length())
        for (i in 0 until routes.length()) {
            val r = routes.getJSONObject(i)
            val distance = r.optInt("distanceMeters", 0)
            // duration / staticDuration come as protobuf duration strings, e.g. "3120s".
            val durationSec = r.optString("duration").removeSuffix("s").toIntOrNull() ?: 0
            val staticSec = r.optString("staticDuration").removeSuffix("s").toIntOrNull() ?: durationSec
            val delaySec = (durationSec - staticSec).coerceAtLeast(0)
            val encoded = r.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            val points = decodePolyline(encoded)
            val token = r.optString("routeToken").ifBlank { null }
            if (points.isNotEmpty()) out += RoutePreview(durationSec, distance, points, token, delaySec)
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
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
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
