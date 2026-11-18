package com.navigator.app.nav.destination

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.navigator.app.R
import com.navigator.app.logging.AppLogger

/**
 * Launcher shortcuts ("Ride home" / "Ride work") for the Home and Work favorites: long-press the
 * app icon to jump straight to the route preview. Dynamic shortcuts, kept in step with the
 * favorites by [PlacesStore] and re-asserted at app start.
 */
object PlaceShortcuts {

    const val ACTION_RIDE_TO = "com.navigator.app.action.RIDE_TO"
    const val EXTRA_SLOT = "com.navigator.app.extra.SLOT"

    private fun idFor(slot: FavoriteSlot) = "ride_${slot.name.lowercase()}"

    /** Publish a shortcut per set slot and remove those of unset slots. Safe to call from any thread. */
    fun sync(context: Context, favorites: List<FavoritePlace>) {
        runCatching {
            val app = context.applicationContext
            val shortcuts = mutableListOf<ShortcutInfoCompat>()
            val removed = mutableListOf<String>()
            for (slot in listOf(FavoriteSlot.HOME, FavoriteSlot.WORK)) {
                val fav = favorites.firstOrNull { it.slot == slot }
                if (fav == null) { removed += idFor(slot); continue }
                val word = if (slot == FavoriteSlot.HOME) "Home" else "Work"
                shortcuts += ShortcutInfoCompat.Builder(app, idFor(slot))
                    .setShortLabel(word)
                    .setLongLabel("Ride $word")
                    .setIcon(IconCompat.createWithResource(app, R.mipmap.ic_launcher))
                    .setIntent(
                        Intent(app, com.navigator.app.ui.MainActivity::class.java)
                            .setAction(ACTION_RIDE_TO)
                            // Reach a running singleTask activity through onNewIntent instead of
                            // letting the launcher clear and recreate the task.
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(EXTRA_SLOT, slot.name),
                    )
                    .build()
            }
            if (removed.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(app, removed)
            // Returns false when rate-limited (background updates are); the next app start re-syncs.
            if (shortcuts.isNotEmpty() && !ShortcutManagerCompat.addDynamicShortcuts(app, shortcuts)) {
                AppLogger.log("Places", "shortcut update rate-limited; will retry at next start")
            }
        }.onFailure { AppLogger.log("Places", "shortcut sync failed: ${it.javaClass.simpleName}") }
    }

    /** Slot named by a shortcut intent, or null. */
    fun slotFrom(intent: Intent?): FavoriteSlot? {
        if (intent?.action != ACTION_RIDE_TO) return null
        return runCatching { FavoriteSlot.valueOf(intent.getStringExtra(EXTRA_SLOT).orEmpty()) }.getOrNull()
            ?.takeIf { it == FavoriteSlot.HOME || it == FavoriteSlot.WORK }
    }
}
