package com.navigator.app.backup

import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file: a zip with `manifest.json`, `rides.json` (index), `tracks/<id>.jsonl`,
 * `places.json` and `prefs.json`. Reading is defensive (it is a user-supplied file): entry names are
 * whitelisted (never used as paths), and sizes / counts are capped.
 */
object BackupArchive {

    const val FORMAT = 1
    const val MAX_ENTRIES = 5_000
    const val MAX_TOTAL_BYTES = 300L * 1024 * 1024
    const val MAX_TRACK_BYTES = 50L * 1024 * 1024
    private const val MAX_SMALL_BYTES = 8L * 1024 * 1024

    private val TRACK_NAME = Regex("tracks/(\\d{1,20})\\.jsonl")

    class Contents(
        val rides: List<RecordedRide>,
        val placesJson: String?,
        val prefsJson: String?,
        /** Extracted track files by ride id (in the temp dir passed to [read]). */
        val tracks: Map<String, File>,
        /** Ids that have a track entry (also filled when tracks were not extracted). */
        val trackIds: Set<String> = tracks.keys,
    )

    fun write(
        out: OutputStream,
        rides: List<RecordedRide>,
        trackFile: (String) -> File?,
        placesJson: String,
        prefsJson: String,
        createdAtMs: Long,
    ): Int {
        var tracksWritten = 0
        ZipOutputStream(out).use { zip ->
            fun text(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            text("manifest.json", JSONObject().put("format", FORMAT).put("createdAt", createdAtMs).put("rides", rides.size).toString())
            val arr = JSONArray()
            rides.forEach { arr.put(RideJson.rideToJson(it)) }
            text("rides.json", arr.toString())
            text("places.json", placesJson)
            text("prefs.json", prefsJson)
            var total = 0L
            for (r in rides) {
                val f = trackFile(r.id)?.takeIf { it.isFile } ?: continue
                // Stay inside what restore accepts: an over-limit track is left out (the rest still restores).
                if (f.length() > MAX_TRACK_BYTES || total + f.length() > MAX_TOTAL_BYTES - MAX_SMALL_BYTES) continue
                total += f.length()
                zip.putNextEntry(ZipEntry("tracks/${r.id}.jsonl"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                tracksWritten++
            }
        }
        return tracksWritten
    }

    /** Parse a backup, extracting tracks into [tempDir]. Null when it isn't a valid backup of this format. */
    fun read(input: InputStream, tempDir: File, withTracks: Boolean = true): Contents? {
        if (withTracks) tempDir.mkdirs()
        val seenTrackIds = HashSet<String>()
        var manifestOk = false
        var rides: List<RecordedRide> = emptyList()
        var places: String? = null
        var prefs: String? = null
        val tracks = HashMap<String, File>()
        var entries = 0
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (++entries > MAX_ENTRIES) return null
                    val name = e.name
                    val trackId = TRACK_NAME.matchEntire(name)?.groupValues?.get(1)
                    when {
                        trackId != null && !withTracks -> {
                            // Only counting: read through (capped), keep nothing.
                            val n = copyCapped(zip, OutputStream.nullOutputStream(), MAX_TRACK_BYTES, MAX_TOTAL_BYTES - total) ?: return null
                            total += n
                            seenTrackIds += trackId
                        }
                        trackId != null -> {
                            val f = File(tempDir, "$trackId.jsonl")
                            val n = copyCapped(zip, f.outputStream(), MAX_TRACK_BYTES, MAX_TOTAL_BYTES - total) ?: run { f.delete(); return null }
                            total += n
                            tracks[trackId] = f
                        }
                        name == "manifest.json" || name == "rides.json" || name == "places.json" || name == "prefs.json" -> {
                            val bos = ByteArrayOutputStream()
                            val n = copyCapped(zip, bos, MAX_SMALL_BYTES, MAX_TOTAL_BYTES - total) ?: return null
                            total += n
                            val body = bos.toString(Charsets.UTF_8.name())
                            when (name) {
                                "manifest.json" -> manifestOk = runCatching { JSONObject(body).getInt("format") == FORMAT }.getOrDefault(false)
                                "rides.json" -> rides = parseRides(body)
                                "places.json" -> places = body
                                "prefs.json" -> prefs = body
                            }
                        }
                        // anything else: ignored, never written anywhere
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        if (!manifestOk) return null
        return Contents(rides, places, prefs, tracks, if (withTracks) tracks.keys.toSet() else seenTrackIds)
    }

    private fun parseRides(body: String): List<RecordedRide> {
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<RecordedRide>(arr.length())
        for (i in 0 until arr.length()) {
            val r = runCatching { RideJson.rideFromJson(arr.getJSONObject(i)) }.getOrNull() ?: continue
            if (r.id.matches(Regex("\\d{1,20}"))) out += r
        }
        return out.distinctBy { it.id }
    }

    /** Copy at most [maxEntry] bytes (and [maxRemaining] overall); null when exceeded. Closes [out]. */
    private fun copyCapped(input: InputStream, out: OutputStream, maxEntry: Long, maxRemaining: Long): Long? =
        out.use { o ->
            val buf = ByteArray(16 * 1024)
            var n = 0L
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                n += r
                if (n > maxEntry || n > maxRemaining) return null
                o.write(buf, 0, r)
            }
            n
        }
}
