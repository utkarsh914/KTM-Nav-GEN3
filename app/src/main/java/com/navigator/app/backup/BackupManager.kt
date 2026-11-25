package com.navigator.app.backup

import android.content.Context
import androidx.core.content.edit
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.destination.PlacesJson
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.ride.RideLimitPolicy
import com.navigator.app.ride.RideStore
import com.navigator.app.settings.AppSettings
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject

/** Back up / restore rides, saved places and app preferences (never the bike pairing). Blocking: call on IO. */
class BackupManager(context: Context) {

    private val app = context.applicationContext
    private val rides = RideStore(app)
    private val places = PlacesStore(app)
    private val prefs get() = app.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE)

    /** [rides] were written; [skipped] were left out because their track is too large. */
    class Summary(val rides: Int, val ok: Boolean, val skipped: Int = 0)
    class Restored(
        val ridesAdded: Int,
        val placesAdded: Int,
        val settingsApplied: Int,
        val ok: Boolean,
        /** The "Rides to keep" value written so the restored rides survive the next prune, or null. */
        val limitRaisedTo: Int? = null,
    )

    fun backup(out: OutputStream): Summary = try {
        val all = rides.list()
        val (recents, favorites) = places.exportRaw()
        val placesJson = JSONObject().put("recents", recents).put("favorites", favorites).toString()
        val written = BackupArchive.write(
            out, all, { id -> rides.trackFile(id) }, placesJson, PrefsBackup.encode(prefs.all),
            System.currentTimeMillis(),
        )
        val skipped = (all.size - written).coerceAtLeast(0)
        AppLogger.log("Backup", "Backed up $written rides" + if (skipped > 0) ", skipped $skipped (too large or no track)" else "")
        Summary(written, true, skipped)
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
                    placeCount = PlacesJson.parseFavorites(o.optString("favorites")).items.size +
                        PlacesJson.parsePlaces(o.optString("recents")).items.size
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
                e.commit()
            }
            // Whatever was ticked, the next prune must not delete the rides just restored.
            val limitRaisedTo = if (ridesAdded > 0) {
                val limit = prefs.getInt("ride_history_limit", AppSettings.DEFAULT_RIDE_HISTORY_LIMIT)
                RideLimitPolicy.raisedFor(rides.list().count { !it.saved }, limit)
                    ?.also { limit -> prefs.edit { putInt("ride_history_limit", limit) } }
            } else null
            AppLogger.log("Backup", "Restored: +$ridesAdded rides, +$placesAdded places, ${entries.size} settings")
            Restored(ridesAdded, placesAdded, entries.size, true, limitRaisedTo)
        } catch (e: Exception) {
            AppLogger.log("Backup", "!! restore failed: ${e.javaClass.simpleName}")
            Restored(0, 0, 0, false)
        } finally {
            runCatching { tmp.deleteRecursively() }
        }
    }
}
