package com.navigator.app.nav.destination

import android.content.Context
import android.content.pm.PackageManager
import com.navigator.app.BuildConfig
import com.navigator.app.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** One autocomplete prediction. [distanceMeters] is straight-line (may be null). */
data class PlaceSuggestion(
    val placeId: String,
    val primary: String,
    val secondary: String?,
    val distanceMeters: Int?,
)

/** Resolved coordinates for a place. */
data class PlaceLocation(val lat: Double, val lng: Double)

/**
 * The app's identity for an Android-restricted API key on a direct REST call.
 * The Nav SDK sends this automatically; our HTTPS calls must send it as the
 * `X-Android-Package` / `X-Android-Cert` headers or Google rejects the request
 * ("Requests from this Android client application <empty> are blocked").
 */
data class AndroidClientAuth(val packageName: String, val certSha1Hex: String)

/**
 * Places API (New) over plain HTTPS - no Places SDK dependency (which would drag
 * in the excluded play-services-maps). Autocomplete returns predictions + a
 * straight-line distance from [originLat]/[originLng]; details resolves the
 * chosen place to coordinates.
 *
 * Uses one session token per search session (passed to both calls) so billing
 * groups them; terminate on the details call. Field mask on details is kept to
 * `location` (Essentials tier) - the display label reuses the autocomplete text.
 */
object PlacesClient {

    private const val AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete"
    private const val TEXT_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
    private const val DETAILS_BASE = "https://places.googleapis.com/v1/places/"

    @Volatile private var cachedAuth: AndroidClientAuth? = null

    /**
     * Build (and cache) the Android client identity for this build's signing
     * certificate, so an Android-restricted key authenticates direct REST calls.
     * Computed at runtime -> matches the debug cert now and any release cert later.
     */
    fun androidAuth(context: Context): AndroidClientAuth {
        cachedAuth?.let { return it }
        val pkg = context.packageName
        val sha1 = runCatching { signingCertSha1Hex(context, pkg) }.getOrElse {
            AppLogger.log("Places", "cert SHA-1 error: ${it.message}")
            ""
        }
        return AndroidClientAuth(pkg, sha1).also { cachedAuth = it }
    }

    private fun signingCertSha1Hex(context: Context, pkg: String): String {
        val pm = context.packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = info.signingInfo?.apkContentsSigners ?: emptyArray()
        val cert = signatures.firstOrNull() ?: return ""
        val digest = MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
        // Base16, uppercase, no delimiters - the X-Android-Cert header format.
        return digest.joinToString("") { "%02X".format(it) }
    }

    suspend fun autocomplete(
        input: String,
        sessionToken: String,
        auth: AndroidClientAuth?,
        originLat: Double? = null,
        originLng: Double? = null,
        regionCode: String = "in",
    ): List<PlaceSuggestion> = autocompleteResult(input, sessionToken, auth, originLat, originLng, regionCode).items

    /** Autocomplete outcome: [failed] distinguishes "offline / error" from "no matches". */
    class AutocompleteResult(val items: List<PlaceSuggestion>, val failed: Boolean)

    suspend fun autocompleteResult(
        input: String,
        sessionToken: String,
        auth: AndroidClientAuth?,
        originLat: Double? = null,
        originLng: Double? = null,
        regionCode: String = "in",
    ): AutocompleteResult = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank() || input.isBlank()) return@withContext AutocompleteResult(emptyList(), failed = false)

        val body = JSONObject().apply {
            put("input", input)
            put("sessionToken", sessionToken)
            put("regionCode", regionCode)
            if (originLat != null && originLng != null) {
                put("origin", JSONObject().put("latitude", originLat).put("longitude", originLng))
            }
        }
        val conn = (URL(AUTOCOMPLETE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            applyAndroidAuth(auth)
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = readBody(conn, code)
            if (code !in 200..299) {
                AppLogger.log("Places", "autocomplete HTTP $code: ${text.take(200)}")
                return@withContext AutocompleteResult(emptyList(), failed = true)
            }
            AutocompleteResult(parseSuggestions(text), failed = false)
        } catch (e: Exception) {
            AppLogger.log("Places", "autocomplete error: ${e.message}")
            AutocompleteResult(emptyList(), failed = true)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun details(placeId: String, sessionToken: String, auth: AndroidClientAuth?): PlaceLocation? = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank() || placeId.isBlank()) return@withContext null

        val url = DETAILS_BASE + URLEncoder.encode(placeId, "UTF-8") +
            "?sessionToken=" + URLEncoder.encode(sessionToken, "UTF-8")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty("X-Goog-FieldMask", "location")
            applyAndroidAuth(auth)
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            val code = conn.responseCode
            val text = readBody(conn, code)
            if (code !in 200..299) {
                AppLogger.log("Places", "details HTTP $code: ${text.take(200)}")
                return@withContext null
            }
            parseLocation(text)
        } catch (e: Exception) {
            AppLogger.log("Places", "details error: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Text Search (New): resolve a free-text place/address string to coordinates.
     * Used to turn a Maps place name (from a resolved short link) into an exact
     * location - far more reliable than scraping the page.
     */
    suspend fun textSearch(query: String, auth: AndroidClientAuth?): PlaceLocation? = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank() || query.isBlank()) return@withContext null

        val body = JSONObject().apply {
            put("textQuery", query)
            put("regionCode", "in")
            put("maxResultCount", 1)
        }
        val conn = (URL(TEXT_SEARCH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty("X-Goog-FieldMask", "places.location")
            applyAndroidAuth(auth)
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = readBody(conn, code)
            if (code !in 200..299) {
                AppLogger.log("Places", "textSearch HTTP $code: ${text.take(200)}")
                return@withContext null
            }
            parseFirstPlaceLocation(text)
        } catch (e: Exception) {
            AppLogger.log("Places", "textSearch error: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Category search (Text Search New): places matching [category] near [origin], or - when
     * [routePoints] is given - along that route (searchAlongRouteParameters). Sorted by straight-line
     * distance from [origin]. [failed] separates "offline / error" from "nothing found".
     */
    suspend fun searchCategory(
        category: PlaceCategory,
        auth: AndroidClientAuth?,
        origin: Pair<Double, Double>?,
        routePoints: List<Pair<Double, Double>>? = null,
    ): CategorySearch = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank()) return@withContext CategorySearch(emptyList(), failed = true)

        val body = buildCategoryBody(category, origin, routePoints)
        val conn = (URL(TEXT_SEARCH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
            applyAndroidAuth(auth)
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = readBody(conn, code)
            if (code !in 200..299) {
                AppLogger.log("Places", "categorySearch HTTP $code: ${text.take(200)}")
                return@withContext CategorySearch(emptyList(), failed = true)
            }
            CategorySearch(parseCategoryResults(text, origin), failed = false)
        } catch (e: Exception) {
            AppLogger.log("Places", "categorySearch error: ${e.message}")
            CategorySearch(emptyList(), failed = true)
        } finally {
            conn.disconnect()
        }
    }

    /** Request body: along the route when given (exclusive), else biased to a 5 km circle around [origin]. */
    internal fun buildCategoryBody(
        category: PlaceCategory,
        origin: Pair<Double, Double>?,
        routePoints: List<Pair<Double, Double>>?,
    ): JSONObject = JSONObject().apply {
        put("textQuery", category.query)
        put("regionCode", "in")
        put("maxResultCount", 15)
        if (routePoints != null && routePoints.size >= 2) {
            put(
                "searchAlongRouteParameters",
                JSONObject().put(
                    "polyline",
                    JSONObject().put(
                        "encodedPolyline",
                        PolylineCodec.encode(PolylineCodec.thin(routePoints, MAX_ROUTE_POINTS)),
                    ),
                ),
            )
        } else if (origin != null) {
            put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject()
                        .put("center", JSONObject().put("latitude", origin.first).put("longitude", origin.second))
                        .put("radius", 5000.0),
                ),
            )
        }
    }

    class CategorySearch(val items: List<CategoryResult>, val failed: Boolean)

    private const val MAX_ROUTE_POINTS = 1000

    /** Parse a Text Search response into results, nearest first when [origin] is known. */
    fun parseCategoryResults(json: String, origin: Pair<Double, Double>?): List<CategoryResult> {
        val arr = runCatching { JSONObject(json).optJSONArray("places") }.getOrNull() ?: return emptyList()
        val out = ArrayList<CategoryResult>(arr.length())
        for (i in 0 until arr.length()) {
            val p = runCatching { arr.getJSONObject(i) }.getOrNull() ?: continue
            val loc = p.optJSONObject("location") ?: continue
            if (!loc.has("latitude") || !loc.has("longitude")) continue
            val lat = loc.optDouble("latitude", Double.NaN)
            val lng = loc.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lng.isNaN() || lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
            val name = p.optJSONObject("displayName")?.optString("text").orEmpty().ifBlank { continue }
            out += CategoryResult(
                name = name,
                address = p.optString("formattedAddress").ifBlank { null },
                lat = lat, lng = lng,
                distanceMeters = origin?.let {
                    com.navigator.app.ride.RideMetrics.haversineMeters(it.first, it.second, lat, lng).toInt()
                },
            )
        }
        return if (origin != null) out.sortedBy { it.distanceMeters } else out
    }

    /** First place location from a Text Search response. */
    fun parseFirstPlaceLocation(json: String): PlaceLocation? {
        val places = JSONObject(json).optJSONArray("places") ?: return null
        if (places.length() == 0) return null
        val loc = places.getJSONObject(0).optJSONObject("location") ?: return null
        if (!loc.has("latitude") || !loc.has("longitude")) return null
        return PlaceLocation(loc.getDouble("latitude"), loc.getDouble("longitude"))
    }

    /** Parse the autocomplete response. Package-visible for future JVM tests. */
    fun parseSuggestions(json: String): List<PlaceSuggestion> {
        val out = mutableListOf<PlaceSuggestion>()
        val arr = JSONObject(json).optJSONArray("suggestions") ?: return out
        for (i in 0 until arr.length()) {
            val pred = arr.getJSONObject(i).optJSONObject("placePrediction") ?: continue
            val placeId = pred.optString("placeId")
            if (placeId.isBlank()) continue
            val structured = pred.optJSONObject("structuredFormat")
            val primary = structured?.optJSONObject("mainText")?.optString("text")
                ?: pred.optJSONObject("text")?.optString("text")
                ?: continue
            val secondary = structured?.optJSONObject("secondaryText")?.optString("text")
            // distanceMeters is omitted for 0/route predictions - treat missing as null.
            val distance = if (pred.has("distanceMeters")) pred.optInt("distanceMeters") else null
            out += PlaceSuggestion(
                placeId = placeId,
                primary = primary,
                secondary = secondary?.ifBlank { null },
                distanceMeters = distance,
            )
        }
        return out
    }

    /** Parse the details response for the `location` field. */
    fun parseLocation(json: String): PlaceLocation? {
        val loc = JSONObject(json).optJSONObject("location") ?: return null
        if (!loc.has("latitude") || !loc.has("longitude")) return null
        return PlaceLocation(loc.getDouble("latitude"), loc.getDouble("longitude"))
    }

}
