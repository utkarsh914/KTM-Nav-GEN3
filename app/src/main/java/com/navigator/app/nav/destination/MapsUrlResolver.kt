package com.navigator.app.nav.destination

import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID

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
/** Why a shared link could not be turned into a place. */
enum class LinkFailure { NOT_A_MAPS_LINK, NO_CONNECTION, UNREADABLE, PLACE_NOT_FOUND }

sealed interface LinkResolution {
    data class Found(val destination: NavDestination) : LinkResolution
    data class Failed(val reason: LinkFailure) : LinkResolution
}

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
    suspend fun resolve(text: String, auth: AndroidClientAuth? = null): NavDestination? =
        (resolveDetailed(text, auth) as? LinkResolution.Found)?.destination

    /** Like [resolve], but says why a link could not be read, so the rider (and the log) can tell. */
    suspend fun resolveDetailed(text: String, auth: AndroidClientAuth? = null): LinkResolution = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        parseCoordinates(trimmed)?.let { return@withContext LinkResolution.Found(it.toDest(placeName(trimmed))) }

        val url = URL_REGEX.find(trimmed)?.value?.let(::trimUrl)
            ?: return@withContext LinkResolution.Failed(LinkFailure.NOT_A_MAPS_LINK)
        parseCoordinates(url)?.let { return@withContext LinkResolution.Found(it.toDest(placeName(url))) }

        if (!isAllowedHost(url) || (!isShortLink(url) && !isGoogleMapsUrl(url))) {
            AppLogger.log("MapsUrl", "not a Google Maps link (host ${hostOf(url)})")
            return@withContext LinkResolution.Failed(LinkFailure.NOT_A_MAPS_LINK)
        }

        val resolvedUrl = try {
            fetchResolvedUrl(url)
        } catch (e: java.io.IOException) {
            // Timeout, no route, or a name that does not resolve (some networks block Google's short-link host).
            AppLogger.log("MapsUrl", "could not reach ${hostOf(url)}: ${e.javaClass.simpleName} ${e.message}")
            return@withContext LinkResolution.Failed(LinkFailure.NO_CONNECTION)
        } catch (e: Exception) {
            AppLogger.log("MapsUrl", "resolve error: ${e.javaClass.simpleName} ${e.message}")
            null
        } ?: return@withContext LinkResolution.Failed(LinkFailure.UNREADABLE)
        AppLogger.log("MapsUrl", "resolved to ${hostOf(resolvedUrl)} (${resolvedUrl.length} chars)")

        // Dropped-pin links carry coords in the resolved URL.
        parseCoordinates(resolvedUrl)?.let { return@withContext LinkResolution.Found(it.toDest(placeName(resolvedUrl))) }

        // Place links: turn the rich name/address into exact coords via Text Search.
        // (No fragile HTML/camera-centre scraping - it can be kilometres off.)
        // Google now sends listed places to `maps.google.com?q=<name, address>&ftid=...`, older links to
        // `/maps/place/<name>/...`, and some to `?q=place_id:<id>`: all three are handled.
        placeId(resolvedUrl)?.let { id ->
            val loc = runCatching { PlacesClient.details(id, UUID.randomUUID().toString(), auth) }.getOrNull()
            if (loc != null) return@withContext LinkResolution.Found(loc.toDest(placeName(resolvedUrl)))
        }
        (placeName(resolvedUrl) ?: queryText(resolvedUrl))?.let { name ->
            val loc = runCatching { PlacesClient.textSearch(name, auth) }.getOrNull()
            if (loc != null) return@withContext LinkResolution.Found(loc.toDest(shortName(name)))
            AppLogger.log("MapsUrl", "place search found nothing for the link's name")
            return@withContext LinkResolution.Failed(LinkFailure.PLACE_NOT_FOUND)
        }
        AppLogger.log("MapsUrl", "no place found in the resolved link (${resolvedUrl.take(60)}…)")
        LinkResolution.Failed(LinkFailure.UNREADABLE)
    }

    /** `intent://...;S.browser_fallback_url=<web url>;end` -> the web URL, or null when [location] is not one. */
    fun intentFallback(location: String): String? {
        if (!location.startsWith("intent:", ignoreCase = true)) return null
        val raw = Regex("S\\.browser_fallback_url=([^;]+)").find(location)?.groupValues?.get(1) ?: return null
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).takeIf { it.startsWith("http") }
    }

    /** The `q=` text of a Maps link when it is a name or address (not coordinates, not a place id), else null. */
    fun queryText(url: String): String? {
        val raw = Regex("[?&]q=([^&#]+)").find(url)?.groupValues?.get(1) ?: return null
        val text = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty() || text.startsWith("place_id:") || parseCoordinates("?q=$raw") != null) return null
        return text
    }

    /** The Places id of a `q=place_id:<id>` link, or null. */
    fun placeId(url: String): String? =
        Regex("[?&]q=place_id:([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)

    /** "Stillgood, Stillgood coffee truck, 100 Feet Rd..." -> "Stillgood": the name, without the address. */
    fun shortName(queryText: String): String = queryText.substringBefore(',').trim().ifBlank { queryText }

    /** The first link in shared text ("Place name\nhttps://maps.app.goo.gl/x" becomes the URL), or null. */
    fun extractUrl(text: String): String? = URL_REGEX.find(text.trim())?.value?.let(::trimUrl)

    /** Follow up to 6 redirects; return the final URL. */
    private fun fetchResolvedUrl(start: String): String? {
        var current = start
        repeat(6) {
            // Every hop (not just the first) must stay on a Google host: a short link must not
            // be able to bounce us to an arbitrary server.
            if (!isAllowedHost(current)) {
                AppLogger.log("MapsUrl", "redirect to a non-Google host refused")
                return null
            }
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
                    // Some links bounce to an `intent://` URI for the Maps app; its fallback is the web page.
                    current = intentFallback(loc)
                        ?: if (loc.startsWith("http")) loc
                        else runCatching { URL(URL(current), loc).toString() }.getOrNull() ?: return null
                } else if (code in 200..299 || parseCoordinates(current) != null) {
                    return current // (a bot-blocked final page still names the place in its URL)
                } else {
                    AppLogger.log("MapsUrl", "HTTP $code from ${hostOf(current)} and no coordinates in the link")
                    return null // 4xx/5xx with nothing to read from the URL
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
        isAllowedHost(url) && url.contains("/maps")

    /** Strip punctuation a chat message glues onto the end of a pasted link. */
    fun trimUrl(url: String): String = url.trimEnd('.', ',', ')', ']', '}', '>', ';', '!', '?', '"', '\'')

    private val GOOGLE_HOST_RE = Regex("""^(?:[a-z0-9-]+\.)*google\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")

    /** Hosts a Maps link may legitimately live on / redirect through. */
    fun isAllowedHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == "maps.app.goo.gl" || host == "goo.gl" || GOOGLE_HOST_RE.matches(host)
    }

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

    fun isShortLink(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == "maps.app.goo.gl" || host == "goo.gl"
    }

    // URL (not URI): lenient about characters like | { } ^ that chat apps leave in pasted links.
    private fun hostOf(url: String): String? =
        runCatching { URL(url).host }.getOrNull()?.lowercase()?.takeIf { it.isNotBlank() }

    /** Human label from `/maps/place/<name>/`, or a generic fallback. */
    fun labelFrom(url: String): String = placeName(url) ?: "Shared location"

    // A null label lets the caller use its own (translated) fallback.
    private fun PlaceLocation.toDest(label: String?) = NavDestination(lat, lng, label)
}
