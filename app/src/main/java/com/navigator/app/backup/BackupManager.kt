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

    fun restore(input: InputStream): Restored {
        val tmp = File(app.cacheDir, "restore-${System.currentTimeMillis()}")
        return try {
            val c = BackupArchive.read(input, tmp) ?: return Restored(0, 0, 0, false)
            val ridesAdded = rides.importBackup(c.rides, c.tracks)
            var placesAdded = 0
            c.placesJson?.let { json ->
                runCatching {
                    val o = JSONObject(json)
                    placesAdded = places.mergeFromBackup(o.optString("recents"), o.optString("favorites"))
                }
            }
            val entries = PrefsBackup.decode(c.prefsJson)
            if (entries.isNotEmpty()) {
                val e = prefs.edit()
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
