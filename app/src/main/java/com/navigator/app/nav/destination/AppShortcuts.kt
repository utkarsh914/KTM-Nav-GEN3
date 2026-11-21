package com.navigator.app.nav.destination

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.navigator.app.R
import com.navigator.app.app.AppIntents
import com.navigator.app.ride.RecordRideShortcutActivity
import com.navigator.app.logging.AppLogger

/**
 * Launcher shortcuts (long-press the app icon, SY6): Record a ride (starts recording without opening the app, the
 * pocket-first start), Where to? (opens search), and Ride home / Ride work for the Home and Work favorites (the route
 * preview, or Google Maps in mirror mode). Dynamic shortcuts, kept in step with the favorites by [PlacesStore] and
 * re-asserted at app start.
 */
object AppShortcuts {

    const val ACTION_RIDE_TO = "com.navigator.app.action.RIDE_TO"
    const val ACTION_SEARCH = "com.navigator.app.action.SEARCH"
    const val EXTRA_SLOT = "com.navigator.app.extra.SLOT"

    private const val ID_RECORD = "record_ride"
    private const val ID_SEARCH = "where_to"

    private fun idFor(slot: FavoriteSlot) = "ride_${slot.name.lowercase()}"

    /** Publish the fixed shortcuts and one per set slot, and remove those of unset slots. Safe to call from any thread. */
    fun sync(context: Context, favorites: List<FavoritePlace>) {
        runCatching {
            val app = context.applicationContext
            val shortcuts = mutableListOf<ShortcutInfoCompat>()
            val enabled = mutableListOf<String>()
            val removed = mutableListOf<String>()
            fun icon(res: Int) = IconCompat.createWithResource(app, res)
            shortcuts += ShortcutInfoCompat.Builder(app, ID_RECORD)
                .setShortLabel(app.getString(R.string.shortcut_record_short))
                .setLongLabel(app.getString(R.string.shortcut_record_long))
                .setIcon(icon(R.drawable.ic_shortcut_record))
                .setRank(0)
                .setIntent(Intent(app, RecordRideShortcutActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            shortcuts += ShortcutInfoCompat.Builder(app, ID_SEARCH)
                .setShortLabel(app.getString(R.string.shortcut_search_short))
                .setLongLabel(app.getString(R.string.shortcut_search_long))
                .setIcon(icon(R.drawable.ic_shortcut_search))
                .setRank(1)
                .setIntent(mainIntent(app).setAction(ACTION_SEARCH).putExtra(AppIntents.EXTRA_REQUEST, AppIntents.REQUEST_SEARCH))
                .build()
            for (slot in listOf(FavoriteSlot.HOME, FavoriteSlot.WORK)) {
                val fav = favorites.firstOrNull { it.slot == slot }
                if (fav == null) { removed += idFor(slot); continue }
                val word = app.getString(if (slot == FavoriteSlot.HOME) R.string.shortcut_home_word else R.string.shortcut_work_word)
                enabled += idFor(slot)
                shortcuts += ShortcutInfoCompat.Builder(app, idFor(slot))
                    .setShortLabel(word)
                    .setLongLabel(app.getString(R.string.shortcut_ride_label, word))
                    .setIcon(icon(if (slot == FavoriteSlot.HOME) R.drawable.ic_shortcut_home else R.drawable.ic_shortcut_work))
                    .setRank(if (slot == FavoriteSlot.HOME) 2 else 3)
                    .setIntent(mainIntent(app).setAction(ACTION_RIDE_TO).putExtra(EXTRA_SLOT, slot.name))
                    .build()
            }
            if (enabled.isNotEmpty()) ShortcutManagerCompat.enableShortcuts(app, shortcuts.filter { it.id in enabled })
            if (removed.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(app, removed)
                // A copy the rider pinned to the home screen outlives the dynamic one: grey it out instead of leaving a dead tile.
                removed.forEach { id ->
                    val word = app.getString(if (id == idFor(FavoriteSlot.HOME)) R.string.shortcut_home_word else R.string.shortcut_work_word)
                    ShortcutManagerCompat.disableShortcuts(app, listOf(id), app.getString(R.string.shortcut_unset_message, word))
                }
            }
            // Returns false when rate-limited (background updates are); the next app start re-syncs.
            if (!ShortcutManagerCompat.addDynamicShortcuts(app, shortcuts)) {
                AppLogger.log("Places", "shortcut update rate-limited; will retry at next start")
            }
        }.onFailure { AppLogger.log("Places", "shortcut sync failed: ${it.javaClass.simpleName}") }
    }

    /** Reach a running singleTask activity through onNewIntent instead of letting the launcher clear and recreate the task. */
    private fun mainIntent(app: Context) = Intent(app, com.navigator.app.ui.MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Tell the launcher a shortcut was used, so it ranks it (and keeps it) properly. */
    fun reportUsed(context: Context, intent: Intent?) {
        val id = when (intent?.action) {
            ACTION_SEARCH -> ID_SEARCH
            ACTION_RIDE_TO -> slotFrom(intent)?.let(::idFor)
            else -> null
        } ?: return
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context.applicationContext, id) }
    }

    /** Slot named by a shortcut intent, or null. */
    fun slotFrom(intent: Intent?): FavoriteSlot? {
        if (intent?.action != ACTION_RIDE_TO) return null
        return runCatching { FavoriteSlot.valueOf(intent.getStringExtra(EXTRA_SLOT).orEmpty()) }.getOrNull()
            ?.takeIf { it == FavoriteSlot.HOME || it == FavoriteSlot.WORK }
    }
}
