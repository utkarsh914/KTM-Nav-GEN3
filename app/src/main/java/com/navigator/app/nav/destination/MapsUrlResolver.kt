package com.navigator.app.nav.destination

import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Best-effort resolution of a Google Maps link (or shared text containing one)
 * to coordinates. There is NO official API for this, so it is unofficial and
 * deliberately conservative:
 *
 * - short links (`maps.app.goo.gl`, `goo.gl/maps`) are expanded by following
 *   redirects (app-generated goo.gl links survive the 2025 shutdown),
 * - coordinates are read from, in order of trust: `!3d<lat>!4d<lng>` (the real
 *   pin), then `q=`/`query=`/`destination=`/`ll=`, then `geo:`/`google.navigation:`,
 * - the `/@lat,lng,zoom` segment is **never** used (it is the viewport centre,
 *   not the pin).
 *
 * The coordinate parsing is pure and unit-tested; only redirect-following needs
 * the network.
 */
object MapsUrlResolver {

    private val LAT = "(-?\\d{1,2}\\.\\d+)"
    private val LNG = "(-?\\d{1,3}\\.\\d+)"
    private val SEP = "(?:,|%2C)"

    // Ordered by trust. The @-viewport pattern is intentionally absent.
    private val COORD_PATTERNS = listOf(
        Regex("!3d$LAT!4d$LNG"),
        Regex("[?&]q=$LAT$SEP$LNG"),
        Regex("[?&]query=$LAT$SEP$LNG"),
        Regex("[?&]destination=$LAT$SEP$LNG"),
        Regex("[?&]ll=$LAT$SEP$LNG"),
        Regex("[?&]daddr=$LAT$SEP$LNG"),
        Regex("geo:$LAT$SEP$LNG"),
        Regex("google\\.navigation:q=$LAT$SEP$LNG"),
    )

    private val URL_REGEX = Regex("""https?://\S+""")

    // Look like a real browser so Google returns the full page (not a bot/consent variant).
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /**
     * Resolve shared text / a URL to a destination, or null if it can't be found.
     *
     * Strategy, most-accurate first:
     * 1. inline coordinates in the text/URL (dropped-pin links: `!3d!4d`, `?q=`…),
     * 2. for a short/Maps URL, follow redirects; if the resolved URL carries
     *    inline coords, use them,
     * 3. otherwise extract the place name/address from the resolved URL and run a
     *    Places **Text Search** for exact coordinates.
     *
     * (No HTML/camera-centre scraping - it can be kilometres off.)
     * [auth] is the Android client identity needed for the Text Search REST call.
     */
    suspend fun resolve(text: String, auth: AndroidClientAuth? = null): NavDestination? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        parseCoordinates(trimmed)?.let { return@withContext it.toDest(labelFrom(trimmed)) }

        val url = URL_REGEX.find(trimmed)?.value ?: return@withContext null
        parseCoordinates(url)?.let { return@withContext it.toDest(labelFrom(url)) }

        if (!isShortLink(url) && !isGoogleMapsUrl(url)) return@withContext null

        val resolvedUrl = runCatching { fetchResolvedUrl(url) }.getOrElse {
            AppLogger.log("MapsUrl", "resolve error: ${it.message}")
            null
        } ?: return@withContext null

        // Dropped-pin links carry coords in the resolved URL.
        parseCoordinates(resolvedUrl)?.let { return@withContext it.toDest(labelFrom(resolvedUrl)) }

        // Place links: turn the rich name/address into exact coords via Text Search.
        // (No fragile HTML/camera-centre scraping - it can be kilometres off.)
        placeName(resolvedUrl)?.let { name ->
            val loc = runCatching { PlacesClient.textSearch(name, auth) }.getOrNull()
            if (loc != null) return@withContext loc.toDest(name)
        }
        null
    }

    /** Follow up to 6 redirects; return the final URL. */
    private fun fetchResolvedUrl(start: String): String? {
        var current = start
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", BROWSER_UA)
                setRequestProperty("Accept-Language", "en")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                } else {
                    return current
                }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    /** The `/maps/place/<name>` segment, decoded, or null if absent. */
    fun placeName(url: String): String? {
        val m = Regex("/maps/place/([^/@?]+)").find(url) ?: return null
        return runCatching {
            URLDecoder.decode(m.groupValues[1].replace('+', ' '), "UTF-8").trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun isGoogleMapsUrl(url: String): Boolean =
        url.contains("google.") && url.contains("/maps")

    /** Pure coordinate extraction from a URL/text string. */
    fun parseCoordinates(s: String): PlaceLocation? {
        for (re in COORD_PATTERNS) {
            val m = re.find(s) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lng = m.groupValues[2].toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lng in -180.0..180.0) return PlaceLocation(lat, lng)
        }
        return null
    }

    fun isShortLink(url: String): Boolean =
        url.contains("maps.app.goo.gl") || url.contains("goo.gl/maps") || url.contains("//goo.gl/")

    /** Human label from `/maps/place/<name>/`, or a generic fallback. */
    fun labelFrom(url: String): String = placeName(url) ?: "Shared location"

    private fun PlaceLocation.toDest(label: String) = NavDestination(lat, lng, label)
}
