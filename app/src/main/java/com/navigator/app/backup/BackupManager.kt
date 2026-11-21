package com.navigator.app.backup

import android.content.Context
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.ride.RideStore
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Back up / restore rides, saved places and app preferences (never the bike pairing). Blocking: call on IO. */
class BackupManager(context: Context) {

    private val app = context.applicationContext
    private val rides = RideStore(app)
    private val places = PlacesStore(app)
    private val prefs get() = app.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE)

    class Summary(val rides: Int, val ok: Boolean)
    class Restored(val ridesAdded: Int, val placesAdded: Int, val settingsApplied: Int, val ok: Boolean)

    fun backup(out: OutputStream): Summary = try {
        val all = rides.list()
        val (recents, favorites) = places.exportRaw()
        val placesJson = JSONObject().put("recents", recents).put("favorites", favorites).toString()
        BackupArchive.write(
            out, all, { id -> rides.trackFile(id) }, placesJson, PrefsBackup.encode(prefs.all),
            System.currentTimeMillis(),
        )
        AppLogger.log("Backup", "Backed up ${all.size} rides")
        Summary(all.size, true)
    } catch (e: Exception) {
        AppLogger.log("Backup", "!! backup failed: ${e.javaClass.simpleName}")
        Summary(0, false)
    }

    /** What a backup file holds, for the "what to restore" dialog. */
    class Preview(val ridesInFile: Int, val newRides: Int, val places: Int, val settings: Int)

    /** Look inside a backup without restoring anything (tracks are read through, not kept). Null if not a backup. */
    fun inspect(input: InputStream): Preview? = try {
        val c = BackupArchive.read(input, File(app.cacheDir, "unused"), withTracks = false)
        if (c == null) null else {
            val usable = c.rides.filter { it.id in c.trackIds }
            val have = rides.allIds() // a ride waiting in the undo window is not restored over
            var placeCount = 0
            c.placesJson?.let { json ->
                runCatching {
                    val o = JSONObject(json)
                    placeCount = com.navigator.app.nav.destination.PlacesJson.parseFavorites(o.optString("favorites")).items.size +
                        com.navigator.app.nav.destination.PlacesJson.parsePlaces(o.optString("recents")).items.size
                }
            }
            Preview(usable.size, usable.count { it.id !in have }, placeCount, PrefsBackup.decode(c.prefsJson).size)
        }
    } catch (e: Exception) {
        null
    }

    /** Restore only the chosen parts. Everything stays merge-only except settings, which the file replaces. */
    fun restore(input: InputStream, withRides: Boolean = true, withPlaces: Boolean = true, withSettings: Boolean = true): Restored {
        val tmp = File(app.cacheDir, "restore-${System.currentTimeMillis()}")
        return try {
            val c = BackupArchive.read(input, tmp, withTracks = withRides) ?: return Restored(0, 0, 0, false)
            val ridesAdded = if (withRides) rides.importBackup(c.rides, c.tracks) else 0
            var placesAdded = 0
            c.placesJson?.takeIf { withPlaces }?.let { json ->
                runCatching {
                    val o = JSONObject(json)
                    placesAdded = places.mergeFromBackup(o.optString("recents"), o.optString("favorites"))
                }
            }
            val entries = if (withSettings) PrefsBackup.decode(c.prefsJson) else emptyMap()
            if (entries.isNotEmpty()) {
                val e = prefs.edit()
                // The file replaces the settings: what it does not mention returns to its default, not the old value.
                PrefsBackup.absentFrom(entries).forEach { e.remove(it) }
                PrefsBackup.applyTo(e, entries)
                // A smaller history limit from the backup must not prune the rides just restored.
                val have = rides.list().size
                val limit = (entries["ride_history_limit"] as? Int) ?: prefs.getInt("ride_history_limit", 100)
                if (limit < have) e.putInt("ride_history_limit", have.coerceAtMost(1_000))
                e.commit()
            }
            AppLogger.log("Backup", "Restored: +$ridesAdded rides, +$placesAdded places, ${entries.size} settings")
            Restored(ridesAdded, placesAdded, entries.size, true)
        } catch (e: Exception) {
            AppLogger.log("Backup", "!! restore failed: ${e.javaClass.simpleName}")
            Restored(0, 0, 0, false)
        } finally {
            runCatching { tmp.deleteRecursively() }
        }
    }
}
