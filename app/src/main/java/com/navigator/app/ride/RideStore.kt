package com.navigator.app.ride

import android.content.Context
import com.navigator.app.logging.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Writer

/**
 * Local persistence for recorded rides — following the app's lightweight
 * conventions (no Room/DataStore): a small `org.json` **index** of ride
 * metadata in a private `SharedPreferences` file, and one **NDJSON track file**
 * per ride under `filesDir/rides/<id>.jsonl`.
 *
 * The track is appended one point per line as the ride happens (crash-safe): if
 * the app dies mid-ride the file survives with all points flushed so far, and
 * [recoverOrphans] promotes it to the index on next launch (or discards it if it
 * never cleared the keep cutoffs).
 *
 * The first line of a track file may be a `{"meta":true,...}` header carrying the
 * destination, so recovery can still label a ride whose index entry was lost.
 */
class RideStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("ride_store", Context.MODE_PRIVATE)
    private val ridesDir: File = File(appContext.filesDir, "rides").apply { mkdirs() }

    // ---- Index (metadata) -------------------------------------------------

    /** All rides, newest first. */
    fun list(): List<RecordedRide> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> RideJson.rideFromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
            .sortedByDescending { it.startMs }
    }

    fun get(id: String): RecordedRide? = list().firstOrNull { it.id == id }

    private fun writeIndex(rides: List<RecordedRide>) {
        val arr = JSONArray()
        rides.forEach { arr.put(RideJson.rideToJson(it)) }
        prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
    }

    /** Insert or replace the index entry for a finished ride. */
    fun finalizeRide(ride: RecordedRide) {
        writeIndex(list().filterNot { it.id == ride.id } + ride)
    }

    /** Remove a ride entirely (index entry + track file). */
    fun delete(id: String) {
        writeIndex(list().filterNot { it.id == id })
        runCatching { trackFile(id).delete() }
    }

    /** Delete a track file that will not be kept (no index entry created). */
    fun discardTrack(id: String) {
        runCatching { trackFile(id).delete() }
    }

    // ---- Track file (streaming NDJSON) ------------------------------------

    fun trackFile(id: String): File = File(ridesDir, "$id.jsonl")

    /**
     * Open (creating) a ride's track for appending. Writes a destination meta
     * header line first when any destination detail is known. Caller owns the
     * returned [Writer] and must [appendPoint] then close it.
     */
    fun openTrack(id: String, destLabel: String?, destLat: Double?, destLng: Double?): Writer {
        val w: Writer = BufferedWriter(FileWriter(trackFile(id), /* append = */ true))
        RideJson.metaLine(destLabel, destLat, destLng)?.let {
            w.write(it)
            w.write("\n")
            w.flush()
        }
        return w
    }

    /** Append one point as an NDJSON line and flush (crash-safe). */
    fun appendPoint(w: Writer, p: RidePoint) {
        w.write(RideJson.pointLine(p))
        w.write("\n")
        w.flush()
    }

    /** Read a ride's full point track (skips the meta header). */
    fun points(id: String): List<RidePoint> {
        val f = trackFile(id)
        if (!f.exists()) return emptyList()
        val out = ArrayList<RidePoint>()
        runCatching {
            BufferedReader(FileReader(f)).use { r ->
                r.forEachLine { line -> RideJson.parsePoint(line)?.let { out += it } }
            }
        }
        return out
    }

    private fun readMeta(id: String): JSONObject? {
        val f = trackFile(id)
        if (!f.exists()) return null
        return runCatching {
            BufferedReader(FileReader(f)).use { r ->
                val first = r.readLine() ?: return null
                RideJson.parseMeta(first)
            }
        }.getOrNull()
    }

    // ---- Crash recovery ---------------------------------------------------

    /**
     * Promote any orphan track files (present on disk but missing from the index
     * — i.e. the app died mid-ride) to real rides, or discard them if they never
     * cleared the keep cutoffs. Safe to call on every launch.
     */
    fun recoverOrphans(minDistanceMeters: Int, minDurationSeconds: Int) {
        val indexed = list().map { it.id }.toHashSet()
        val files = ridesDir.listFiles { f -> f.name.endsWith(".jsonl") } ?: return
        for (f in files) {
            val id = f.name.removeSuffix(".jsonl")
            if (id in indexed) continue
            val pts = points(id)
            val stats = RideMetrics.summarize(pts)
            if (pts.isEmpty() ||
                !RideMetrics.meetsCutoff(stats.distanceMeters, stats.durationSeconds, minDistanceMeters, minDurationSeconds)
            ) {
                runCatching { f.delete() }
                AppLogger.log("Ride", "Discarded orphan $id (${stats.distanceMeters}m / ${stats.durationSeconds}s)")
                continue
            }
            val meta = readMeta(id)
            finalizeRide(
                RecordedRide(
                    id = id,
                    startMs = stats.startMs,
                    endMs = stats.endMs,
                    distanceMeters = stats.distanceMeters,
                    durationSeconds = stats.durationSeconds,
                    destinationLabel = meta?.optString("label")?.ifBlank { null },
                    destLat = meta?.takeIf { it.has("dlat") }?.optDouble("dlat"),
                    destLng = meta?.takeIf { it.has("dlng") }?.optDouble("dlng"),
                    avgSpeedKmh = stats.avgSpeedKmh,
                    maxSpeedKmh = stats.maxSpeedKmh,
                    pointCount = stats.pointCount,
                ),
            )
            AppLogger.log("Ride", "Recovered orphan ride $id")
        }
    }

    private companion object {
        const val KEY_INDEX = "index"
    }
}
