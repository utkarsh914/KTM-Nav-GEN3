package com.navigator.app.ride

import android.content.Context
import com.navigator.app.logging.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.OutputStreamWriter
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
    fun list(): List<RecordedRide> = synchronized(LOCK) { readIndex() }

    /**
     * Parse the index entry by entry: one bad entry is skipped (not the whole list). If
     * the stored JSON as a whole is unreadable it is moved aside to [KEY_INDEX_CORRUPT]
     * (kept for recovery) and treated as empty, so the next write can't silently destroy it.
     */
    private fun readIndex(): List<RecordedRide> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            AppLogger.log("Ride", "!! ride index unreadable - moved aside (${e.javaClass.simpleName})")
            prefs.edit().putString(KEY_INDEX_CORRUPT, raw).remove(KEY_INDEX).apply()
            return emptyList()
        }
        val out = ArrayList<RecordedRide>(arr.length())
        for (i in 0 until arr.length()) {
            val ride = runCatching { RideJson.rideFromJson(arr.getJSONObject(i)) }.getOrNull()
            if (ride == null || ride.id.isBlank()) continue // blank ids would also crash LazyColumn keys
            out += ride
        }
        return out.distinctBy { it.id }.sortedByDescending { it.startMs }
    }

    fun get(id: String): RecordedRide? = list().firstOrNull { it.id == id }

    private fun writeIndex(rides: List<RecordedRide>) {
        val arr = JSONArray()
        rides.forEach { arr.put(RideJson.rideToJson(it)) }
        prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
    }

    // Saved (pinned) ids are mirrored outside the index so crash/corruption recovery
    // (which rebuilds rides from track files) keeps the pin.
    private fun savedIds(): Set<String> = prefs.getStringSet(KEY_SAVED_IDS, emptySet()) ?: emptySet()

    private fun writeSavedIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SAVED_IDS, HashSet(ids)).apply()
    }

    /** Insert or replace the index entry for a finished ride. */
    fun finalizeRide(ride: RecordedRide) = synchronized(LOCK) {
        writeIndex(readIndex().filterNot { it.id == ride.id } + ride)
        if (ride.saved) writeSavedIds(savedIds() + ride.id)
    }

    /** Remove a ride entirely (index entry + track file). */
    fun delete(id: String) = deleteAll(listOf(id))

    /** Remove several rides at once (index entries + track files). */
    fun deleteAll(ids: Collection<String>) {
        val set = ids.toHashSet()
        synchronized(LOCK) {
            writeIndex(readIndex().filterNot { it.id in set })
            writeSavedIds(savedIds() - set)
            set.forEach { id -> runCatching { trackFile(id).delete() } }
        }
    }

    /** Pin/unpin a ride to keep it permanently (excluded from the history prune). */
    fun setSaved(id: String, saved: Boolean) = synchronized(LOCK) {
        writeIndex(readIndex().map { if (it.id == id) it.copy(saved = saved) else it })
        writeSavedIds(if (saved) savedIds() + id else savedIds() - id)
    }

    /**
     * Enforce the history limit: keep every saved ride (they don't count) plus the
     * newest [limit] unsaved rides, deleting the rest (index entry + track file).
     * Returns the ids that were pruned.
     */
    fun pruneToLimit(limit: Int): List<String> {
        val toDrop = RideMetrics.ridesToPrune(list(), limit)
        if (toDrop.isNotEmpty()) {
            deleteAll(toDrop)
            AppLogger.log("Ride", "Pruned ${toDrop.size} ride(s) over history limit $limit")
        }
        return toDrop
    }

    /** Delete a track file that will not be kept (no index entry created). */
    fun discardTrack(id: String) {
        ACTIVE_TRACKS.remove(id)
        runCatching { trackFile(id).delete() }
    }

    /** The recorder is done with [id]'s track file (finalised or discarded). */
    fun markTrackClosed(id: String) {
        ACTIVE_TRACKS.remove(id)
    }

    // ---- Track file (streaming NDJSON) ------------------------------------

    fun trackFile(id: String): File = File(ridesDir, "$id.jsonl")

    /**
     * Open (creating) a ride's track for appending. Writes a destination meta
     * header line first when any destination detail is known. Caller owns the
     * returned [Writer] and must [appendPoint] then close it.
     */
    fun openTrack(id: String, destLabel: String?, destLat: Double?, destLng: Double?): Writer {
        // Mark active BEFORE the file exists so orphan recovery can never see (and delete) it empty.
        ACTIVE_TRACKS.add(id)
        val w: Writer = try {
            SyncingWriter(FileOutputStream(trackFile(id), /* append = */ true))
        } catch (e: Exception) {
            ACTIVE_TRACKS.remove(id)
            throw e
        }
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
            if (id in ACTIVE_TRACKS) continue // a ride is being recorded into this file right now
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
                    saved = id in savedIds(),
                ),
            )
            AppLogger.log("Ride", "Recovered orphan ride $id")
        }
    }

    /**
     * Buffered NDJSON writer that also fsyncs: `flush()` only hands bytes to the OS, so a
     * power loss could still lose the tail of the track. Sync at most every
     * [SYNC_EVERY_MS] while recording, and always on close.
     */
    private class SyncingWriter(private val fos: FileOutputStream) :
        Writer() {
        private val inner = BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8))
        private var lastSyncMs = System.currentTimeMillis()

        override fun write(cbuf: CharArray, off: Int, len: Int) = inner.write(cbuf, off, len)

        override fun flush() {
            inner.flush()
            val now = System.currentTimeMillis()
            if (now - lastSyncMs >= SYNC_EVERY_MS) {
                runCatching { fos.fd.sync() }
                lastSyncMs = now
            }
        }

        override fun close() {
            runCatching { inner.flush(); fos.fd.sync() }
            inner.close()
        }
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_INDEX_CORRUPT = "index_corrupt"
        const val KEY_SAVED_IDS = "saved_ids"
        const val SYNC_EVERY_MS = 15_000L
        /** Process-wide: one lock for every RideStore instance (history, recorder, replay all create their own). */
        val LOCK = Any()
        /** Track files currently being written by a live recorder; recovery must not touch them. */
        val ACTIVE_TRACKS: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }
}
