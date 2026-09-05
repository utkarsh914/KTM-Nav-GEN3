package com.navigator.app.nav.destination

import java.net.HttpURLConnection

/**
 * Shared REST helpers for the Google Places / Routes clients (both call the same
 * `places`/`routes` v1 endpoints with the same Android-restricted-key auth).
 */

/** Attach the Android client identity so an Android-restricted key accepts the REST call. */
internal fun HttpURLConnection.applyAndroidAuth(auth: AndroidClientAuth?) {
    if (auth == null || auth.certSha1Hex.isBlank()) return
    setRequestProperty("X-Android-Package", auth.packageName)
    setRequestProperty("X-Android-Cert", auth.certSha1Hex)
}

/** Read the response (success) or error body as text, never throwing on an empty stream. */
internal fun readBody(conn: HttpURLConnection, code: Int): String {
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    return stream?.bufferedReader()?.use { it.readText() } ?: ""
}
