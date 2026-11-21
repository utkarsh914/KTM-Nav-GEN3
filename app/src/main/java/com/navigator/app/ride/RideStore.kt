package com.navigator.app.ride

import android.content.Context
import com.navigator.app.logging.AppLogger
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    /** Bumps on every index write, in any [RideStore] instance, so screens can reload when rides change. */
    val changes: StateFlow<Int> get() = CHANGES

    /** All rides the rider can see, newest first (deleted ones waiting for an undo are hidden). */
    fun list(): List<RecordedRide> = synchronized(LOCK) { readIndex().filterNot { it.isDeleted } }

    /** Ids of every ride in the index, deleted ones included. */
    fun allIds(): Set<String> = synchronized(LOCK) { readIndex().map { it.id }.toHashSet() }

    /** Rides deleted but not yet purged, newest first. */
    fun listDeleted(): List<RecordedRide> = synchronized(LOCK) { readIndex().filter { it.isDeleted } }

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
            out += if (ride.source == null) ride.copy(source = RideSource.infer(ride)) else ride
        }
        return out.distinctBy { it.id }.sortedByDescending { it.startMs }
    }

    fun get(id: String): RecordedRide? = list().firstOrNull { it.id == id }

    private fun writeIndex(rides: List<RecordedRide>) {
        val arr = JSONArray()
        rides.forEach { arr.put(RideJson.rideToJson(it)) }
        prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
        CHANGES.update { it + 1 }
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

    /** Hide rides for an undo window; the tracks stay until [purgeDeleted]. Active recordings are left alone. */
    fun softDelete(ids: Collection<String>, nowMs: Long = System.currentTimeMillis()): List<String> = synchronized(LOCK) {
        val hit = ArrayList<String>()
        writeIndex(readIndex().map { r ->
            if (r.id in ids && !r.isDeleted && r.id !in ACTIVE_TRACKS) { hit += r.id; r.copy(deletedAtMs = nowMs) } else r
        })
        hit
    }

    /** Bring soft-deleted rides back. Returns the ids restored (a purged ride is gone for good). */
    fun restore(ids: Collection<String>): List<String> = synchronized(LOCK) {
        val hit = ArrayList<String>()
        writeIndex(readIndex().map { r ->
            if (r.id in ids && r.isDeleted && trackFile(r.id).exists()) { hit += r.id; r.copy(deletedAtMs = null) } else r
        })
        hit
    }

    /** Remove for good the rides deleted before [olderThanMs]. Returns their ids. */
    fun purgeDeleted(olderThanMs: Long): List<String> {
        return synchronized(LOCK) {
            val ids = readIndex().filter { (it.deletedAtMs ?: Long.MAX_VALUE) < olderThanMs }.map { it.id }
            if (ids.isNotEmpty()) {
                deleteAll(ids)
                AppLogger.log("Ride", "Purged ${ids.size} deleted ride(s)")
            }
            ids
        }
    }

    /** Remove for good these rides if (and only if) they are deleted: after the undo snackbar goes away. */
    fun purge(ids: Collection<String>): List<String> = synchronized(LOCK) {
        val gone = readIndex().filter { it.isDeleted && it.id in ids }.map { it.id }
        if (gone.isNotEmpty()) deleteAll(gone)
        gone
    }

    /**
     * Join rides into one new ride (name of the first, notes joined, saved if any was) and soft-delete the originals.
     * Refused while any is being recorded or when their time ranges overlap. The result carries the undo token.
     */
    fun mergeRides(ids: Collection<String>): MergeResult {
        val all = synchronized(LOCK) { readIndex() }.filterNot { it.isDeleted }
        val picked = all.filter { it.id in ids }
        val (ordered, refusal) = RideMerge.order(picked) { it in ACTIVE_TRACKS }
        if (refusal != null) return MergeResult.Refused(refusal)
        val parts = ordered.map { RideMerge.withSeamsMarked(points(it.id), it.source == RideSource.MERGED, it.parts) }
        // A read that lost lines would silently shorten the merged ride.
        if (ordered.indices.any { parts[it].size < 2 || parts[it].size < ordered[it].pointCount }) {
            return MergeResult.Refused(MergeRefusal.MissingTrack)
        }
        val merged = RideMerge.concat(parts)
        val stats = RideMetrics.summarize(merged)
        // Picked and reserved under the lock: another writer taking the same millisecond must not share the file.
        val newId = synchronized(LOCK) {
            var n = System.currentTimeMillis()
            while (trackFile(n.toString()).exists() || n.toString() in ACTIVE_TRACKS) n++
            n.toString().also { ACTIVE_TRACKS.add(it) } // keeps orphan recovery away until the index entry exists
        }
        val name = RideTrim.cleanName(ordered.first().destinationLabel)
        val dest = ordered.lastOrNull { it.destLat != null && it.destLng != null }
        val tmp = File(ridesDir, "$newId.jsonl.tmp")
        var moved = false
        var indexed = false
        try {
            FileOutputStream(tmp).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).let { w ->
                    RideJson.metaLine(name, dest?.destLat, dest?.destLng, RideSource.MERGED)?.let { w.write(it); w.write("\n") }
                    merged.forEach { w.write(RideJson.pointLine(it)); w.write("\n") }
                    w.flush()
                }
                fos.fd.sync()
            }
            val ride = RecordedRide(
                id = newId, startMs = stats.startMs, endMs = stats.endMs, distanceMeters = stats.distanceMeters,
                durationSeconds = stats.durationSeconds, destinationLabel = name, destLat = dest?.destLat, destLng = dest?.destLng,
                avgSpeedKmh = stats.avgSpeedKmh, movingSeconds = stats.movingSeconds,
                avgOverallSpeedKmh = stats.avgOverallSpeedKmh, maxSpeedKmh = stats.maxSpeedKmh, pointCount = stats.pointCount,
                saved = ordered.any { it.saved }, elevGainM = stats.elevGainM, elevLossM = stats.elevLossM,
                notes = RideMerge.notes(ordered), source = RideSource.MERGED,
                parts = ordered.sumOf { it.parts.coerceAtLeast(1) }.coerceAtMost(RideJson.MAX_PARTS),
            )
            synchronized(LOCK) {
                val now = readIndex()
                // Anything changed meanwhile (deleted, trimmed, re-recorded): give up rather than merge stale points.
                val same = ordered.all { o -> now.any { it.id == o.id && !it.isDeleted && it.pointCount == o.pointCount } }
                if (!same || ordered.any { it.id in ACTIVE_TRACKS }) {
                    tmp.delete()
                    return MergeResult.Refused(MergeRefusal.Failed)
                }
                Files.move(
                    tmp.toPath(), trackFile(newId).toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
                moved = true
                val at = System.currentTimeMillis()
                val originals = ordered.map { it.id }.toSet()
                writeIndex(now.map { if (it.id in originals) it.copy(deletedAtMs = at) else it } + ride)
                indexed = true
                if (ride.saved) writeSavedIds(savedIds() + newId)
                AppLogger.log("Ride", "Merged ${ordered.size} rides into $newId")
                return MergeResult.Merged(ride, MergeToken(newId, ordered.map { it.id }))
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            // Only a file this merge itself put in place and did not index is ours to remove; once indexed it must stay.
            if (moved && !indexed) runCatching { trackFile(newId).delete() }
            AppLogger.log("Ride", "!! merge failed: ${e.javaClass.simpleName}")
            return MergeResult.Refused(MergeRefusal.Failed)
        } finally {
            ACTIVE_TRACKS.remove(newId)
        }
    }

    /** Undo a merge: the originals come back and the merged ride goes. False when an original is already purged. */
    fun undoMerge(token: MergeToken): Boolean = synchronized(LOCK) {
        val now = readIndex()
        val back = now.filter { it.id in token.originalIds && it.isDeleted && trackFile(it.id).exists() }
        if (back.size != token.originalIds.size) return false
        deleteAll(listOf(token.mergedId))
        restore(token.originalIds)
        true
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

    /**
     * One-time (idempotent) upgrade: rides saved before both averages were stored get their
     * moving time and moving/overall averages recomputed from the track file. Reads the tracks
     * outside the lock and applies the results under it. Rides whose track is gone are left
     * alone (the UI falls back to distance / duration for them).
     */
    fun backfillMovingStats() {
        val pending = list().filter { it.movingSeconds < 0 }
        if (pending.isEmpty()) return
        val computed = HashMap<String, RideStats>()
        for (r in pending) {
            val pts = points(r.id)
            if (pts.size >= 2) computed[r.id] = RideMetrics.summarize(pts)
        }
        if (computed.isEmpty()) return
        synchronized(LOCK) {
            writeIndex(readIndex().map { r ->
                val st = computed[r.id]
                // Only touch rides that still lack the data (they may have been re-saved meanwhile).
                if (st == null || r.movingSeconds >= 0) r else r.copy(
                    avgSpeedKmh = st.avgSpeedKmh,
                    movingSeconds = st.movingSeconds,
                    avgOverallSpeedKmh = st.avgOverallSpeedKmh,
                )
            })
        }
        AppLogger.log("Ride", "Backfilled moving stats for ${computed.size} ride(s)")
    }

    /** Delete a track file that will not be kept (no index entry created). */
    fun discardTrack(id: String) {
        ACTIVE_TRACKS.remove(id)
        runCatching { trackFile(id).delete() }
    }

    /** Test hook: forget the process-wide state a previous test left behind (marked tracks, the change counter). */
    internal fun resetStaticsForTest() {
        ACTIVE_TRACKS.clear()
        CHANGES.value = 0
    }

    /** Test hook: mark a track as being recorded (or clear every mark with a null id). */
    internal fun setActiveForTest(id: String?, active: Boolean = true) {
        if (id == null) ACTIVE_TRACKS.clear() else if (active) ACTIVE_TRACKS.add(id) else ACTIVE_TRACKS.remove(id)
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
    fun openTrack(id: String, destLabel: String?, destLat: Double?, destLng: Double?, source: String? = null): Writer {
        // Mark active BEFORE the file exists so orphan recovery can never see (and delete) it empty.
        ACTIVE_TRACKS.add(id)
        val w: Writer = try {
            SyncingWriter(FileOutputStream(trackFile(id), /* append = */ true))
        } catch (e: Exception) {
            ACTIVE_TRACKS.remove(id)
            throw e
        }
        RideJson.metaLine(destLabel, destLat, destLng, source)?.let {
            w.write(it)
            w.write("\n")
            w.flush()
        }
        return w
    }

    /** An existing ride that looks like [points] (same start time, near-identical distance): an earlier import of the same file. */
    fun findDuplicate(points: List<RidePoint>): RecordedRide? {
        if (points.size < 2) return null
        val st = RideMetrics.summarize(points)
        return list().firstOrNull { r ->
            kotlin.math.abs(r.startMs - st.startMs) <= 2_000L &&
                kotlin.math.abs(r.distanceMeters - st.distanceMeters) <= maxOf(25, st.distanceMeters / 100)
        }
    }

    /**
     * Add rides from a restored backup: only ids not already here, and only those whose track was
     * extracted. Existing rides are never touched. Returns how many were added.
     */
    fun importBackup(rides: List<RecordedRide>, tracks: Map<String, File>): Int = synchronized(LOCK) {
        val have = readIndex().map { it.id }.toHashSet()
        val added = ArrayList<RecordedRide>()
        val claimed = ArrayList<String>()
        try {
            for (r in rides) {
                if (r.id in have) continue
                val src = tracks[r.id] ?: continue
                val dst = trackFile(r.id)
                if (dst.exists()) continue
                // The file's index metadata is not trusted: the stats are recomputed from the track
                // itself (same math as recovery) and text fields are cleaned and capped.
                val pts = ArrayList<RidePoint>()
                runCatching {
                    BufferedReader(FileReader(src)).use { rd -> rd.forEachLine { l -> RideJson.parsePoint(l)?.let { pts += it } } }
                }
                if (pts.size < 2) continue
                ACTIVE_TRACKS.add(r.id) // keep orphan recovery away until the index entry exists
                claimed += r.id
                try {
                    src.copyTo(dst)
                } catch (e: Exception) {
                    runCatching { dst.delete() }
                    continue
                }
                val st = RideMetrics.summarize(pts)
                added += r.copy(
                    startMs = st.startMs, endMs = st.endMs, distanceMeters = st.distanceMeters,
                    durationSeconds = st.durationSeconds, avgSpeedKmh = st.avgSpeedKmh,
                    movingSeconds = st.movingSeconds, avgOverallSpeedKmh = st.avgOverallSpeedKmh,
                    maxSpeedKmh = st.maxSpeedKmh, pointCount = st.pointCount,
                    elevGainM = st.elevGainM, elevLossM = st.elevLossM,
                    destinationLabel = RideTrim.cleanName(r.destinationLabel),
                    notes = RideTrim.cleanNotes(r.notes),
                    source = r.source ?: RideSource.infer(r.copy(startMs = st.startMs, endMs = st.endMs)),
                    deletedAtMs = null,
                )
            }
            if (added.isNotEmpty()) writeIndex(readIndex() + added)
            if (added.any { it.saved }) writeSavedIds(savedIds() + added.filter { it.saved }.map { it.id })
        } finally {
            claimed.forEach { ACTIVE_TRACKS.remove(it) }
        }
        added.size
    }

    /** Rename a ride and/or change its note. Returns the updated ride, or null when it no longer exists. */
    fun updateMeta(id: String, label: String?, notes: String?): RecordedRide? = synchronized(LOCK) {
        var updated: RecordedRide? = null
        writeIndex(readIndex().map { r ->
            if (r.id != id || r.isDeleted) r else r.copy(
                destinationLabel = RideTrim.cleanName(label),
                notes = RideTrim.cleanNotes(notes),
            ).also { updated = it }
        })
        updated
    }

    /**
     * Cut a ride to the fixes [from]..[to] (inclusive) and recompute its stats. Destructive: the
     * track is rewritten (temp file, then an atomic replace) and the old points are gone. [expectedCount] is how many
     * fixes the caller chose the indices on.
     */
    fun trimRide(id: String, from: Int, to: Int, expectedCount: Int? = null): RecordedRide? {
        if (id in ACTIVE_TRACKS) return null // still being recorded
        val file = trackFile(id)
        val sizeBefore = file.length()
        val modifiedBefore = file.lastModified()
        val pts = points(id)
        // A read that lost lines would permanently truncate the ride: refuse unless it matches the index.
        val expected = get(id)?.pointCount ?: return null
        if (expected > 0 && pts.size < expected) return null
        // The indices are the page's: refuse when the track is no longer the one they were chosen on.
        if (expectedCount != null && pts.size != expectedCount) return null
        val kept = RideTrim.trim(pts, from, to) ?: return null
        val stats = RideMetrics.summarize(kept)
        val tmp = File(ridesDir, "$id.jsonl.tmp")
        try {
            val meta = readMetaLine(id)
            FileOutputStream(tmp).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).let { w ->
                    meta?.let { w.write(it); w.write("\n") }
                    kept.forEach { w.write(RideJson.pointLine(it)); w.write("\n") }
                    w.flush()
                }
                fos.fd.sync() // data on disk BEFORE the rename can make it the track
            }
            synchronized(LOCK) {
                // Re-validate under the lock: deleted / pruned / re-recorded / rewritten meanwhile -> abort.
                if (id in ACTIVE_TRACKS || readIndex().none { it.id == id } ||
                    file.length() != sizeBefore || file.lastModified() != modifiedBefore
                ) {
                    tmp.delete()
                    return null
                }
                Files.move(
                    tmp.toPath(), trackFile(id).toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
                var updated: RecordedRide? = null
                writeIndex(readIndex().map { r ->
                    if (r.id != id) r else r.copy(
                        startMs = stats.startMs, endMs = stats.endMs,
                        distanceMeters = stats.distanceMeters, durationSeconds = stats.durationSeconds,
                        avgSpeedKmh = stats.avgSpeedKmh, movingSeconds = stats.movingSeconds,
                        avgOverallSpeedKmh = stats.avgOverallSpeedKmh, maxSpeedKmh = stats.maxSpeedKmh,
                        pointCount = stats.pointCount, elevGainM = stats.elevGainM, elevLossM = stats.elevLossM,
                        parts = if (pts.any { p -> p.seamBefore }) (kept.count { p -> p.seamBefore } + 1).let { n -> if (n == 1) 0 else n } else r.parts,
                    ).also { updated = it }
                })
                AppLogger.log("Ride", "Trimmed ride $id to ${kept.size} of ${pts.size} points")
                return updated
            }
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            AppLogger.log("Ride", "!! trim failed: ${e.javaClass.simpleName}")
            return null
        }
    }

    private fun readMetaLine(id: String): String? = runCatching {
        BufferedReader(FileReader(trackFile(id))).use { r -> r.readLine()?.takeIf { RideJson.parseMeta(it) != null } }
    }.getOrNull()

    /**
     * Store an imported track (GPX) as a ride. Written in one pass (no per-point flush: the file is
     * complete before the index entry exists). Marked saved so the history limit never prunes it.
     * Returns null when the track is too short to be a ride.
     */
    fun importRide(label: String?, points: List<RidePoint>): RecordedRide? {
        if (points.size < 2) return null
        var id = System.currentTimeMillis()
        while (trackFile(id.toString()).exists()) id++
        val rideId = id.toString()
        val stats = RideMetrics.summarize(points)
        val last = points.last()
        ACTIVE_TRACKS.add(rideId) // keep orphan recovery away until the index entry exists
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(trackFile(rideId)), Charsets.UTF_8)).use { w ->
                RideJson.metaLine(label, last.lat, last.lng, RideSource.GPX)?.let { w.write(it); w.write("\n") }
                points.forEach { w.write(RideJson.pointLine(it)); w.write("\n") }
            }
        } catch (e: Exception) {
            runCatching { trackFile(rideId).delete() }
            ACTIVE_TRACKS.remove(rideId)
            AppLogger.log("Ride", "!! GPX import write failed: ${e.javaClass.simpleName}")
            return null
        }
        val ride = RecordedRide(
            id = rideId,
            startMs = stats.startMs,
            endMs = stats.endMs,
            distanceMeters = stats.distanceMeters,
            durationSeconds = stats.durationSeconds,
            destinationLabel = label,
            destLat = last.lat,
            destLng = last.lng,
            avgSpeedKmh = stats.avgSpeedKmh,
            movingSeconds = stats.movingSeconds,
            avgOverallSpeedKmh = stats.avgOverallSpeedKmh,
            elevGainM = stats.elevGainM,
            elevLossM = stats.elevLossM,
            maxSpeedKmh = stats.maxSpeedKmh,
            pointCount = stats.pointCount,
            saved = true,
            source = RideSource.GPX,
        )
        try {
            finalizeRide(ride)
        } catch (e: Exception) {
            runCatching { trackFile(rideId).delete() }
            AppLogger.log("Ride", "!! GPX import index write failed: ${e.javaClass.simpleName}")
            return null
        } finally {
            ACTIVE_TRACKS.remove(rideId)
        }
        AppLogger.log("Ride", "Imported GPX ride $rideId: ${stats.distanceMeters}m / ${stats.durationSeconds}s")
        return ride
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
        // Deleted rides still own their track until purged: they are not orphans.
        val indexed = synchronized(LOCK) { readIndex() }.map { it.id }.toHashSet()
        // Leftovers of an interrupted trim.
        ridesDir.listFiles { f -> f.name.endsWith(".jsonl.tmp") }?.forEach { runCatching { it.delete() } }
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
            // A merge that died between moving its track and writing the index: the originals are still live, so
            // this track is a duplicate of them. Roll the merge back.
            if (meta?.optString("src") == RideSource.MERGED &&
                synchronized(LOCK) { readIndex() }.any { !it.isDeleted && it.startMs < stats.endMs && it.endMs > stats.startMs }
            ) {
                runCatching { f.delete() }
                AppLogger.log("Ride", "Rolled back interrupted merge $id")
                continue
            }
            val recovered =
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
                    movingSeconds = stats.movingSeconds,
                    avgOverallSpeedKmh = stats.avgOverallSpeedKmh,
                    elevGainM = stats.elevGainM,
                    elevLossM = stats.elevLossM,
                    maxSpeedKmh = stats.maxSpeedKmh,
                    pointCount = stats.pointCount,
                    saved = id in savedIds(),
                )
            finalizeRide(
                recovered.copy(source = RideSource.clean(meta?.optString("src")) ?: RideSource.infer(recovered)),
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

    companion object {
        /** A deleted ride can be restored for this long before the launch purge removes it for good. */
        const val UNDO_WINDOW_MS = 24 * 60 * 60_000L
        private const val KEY_INDEX = "index"
        private const val KEY_INDEX_CORRUPT = "index_corrupt"
        private const val KEY_SAVED_IDS = "saved_ids"
        private const val SYNC_EVERY_MS = 15_000L
        private val CHANGES = MutableStateFlow(0)
        /** Process-wide: one lock for every RideStore instance (history, recorder, replay all create their own). */
        private val LOCK = Any()
        /** Track files currently being written by a live recorder; recovery must not touch them. */
        private val ACTIVE_TRACKS: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
