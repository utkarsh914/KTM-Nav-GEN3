package com.navigator.app.nav.destination

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    private const val DETAILS_BASE = "https://places.googleapis.com/v1/places/"

    val apiKeyPresent: Boolean get() = BuildConfig.NAV_SDK_API_KEY.isNotBlank()

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
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION", "PackageManagerGetSignatures")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
        }
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
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val key = BuildConfig.NAV_SDK_API_KEY
        if (key.isBlank() || input.isBlank()) return@withContext emptyList()

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
                return@withContext emptyList()
            }
            parseSuggestions(text)
        } catch (e: Exception) {
            AppLogger.log("Places", "autocomplete error: ${e.message}")
            emptyList()
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

    /** Attach the Android client identity so an Android-restricted key accepts the REST call. */
    private fun HttpURLConnection.applyAndroidAuth(auth: AndroidClientAuth?) {
        if (auth == null || auth.certSha1Hex.isBlank()) return
        setRequestProperty("X-Android-Package", auth.packageName)
        setRequestProperty("X-Android-Cert", auth.certSha1Hex)
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
