package com.navigator.app.nav.destination

import android.content.Context
import android.content.pm.ShortcutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager

/** The launcher shortcuts follow the favourites: the fixed two always, Ride home / Ride work only while that slot is set. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppShortcutsTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        ShadowShortcutManager.reset()
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSystemService(ShortcutManager::class.java).removeAllDynamicShortcuts()
    }

    /**
     * androidx's ShortcutManagerCompat also touches the launcher state from a background thread, and Robolectric's
     * shadow keeps it in a plain HashMap: a read that overlaps a write throws (an NPE inside ImmutableList.copyOf) or
     * sees the state before the write lands. So read until it is [expected] (or the time is up) and return what was read.
     */
    private fun ids(expected: Set<String>? = null): Set<String> {
        var last: Set<String> = emptySet()
        repeat(MAX_READS) {
            try {
                last = ctx.getSystemService(ShortcutManager::class.java).dynamicShortcuts.map { it.id }.toSet()
                if (expected == null || last == expected) return last
            } catch (racing: NullPointerException) {
            } catch (racing: ConcurrentModificationException) {
            }
            Thread.sleep(READ_RETRY_MS)
        }
        return last
    }

    /** A sync that lost the race above is swallowed by design (logged, retried at the next start): do the same here. */
    private fun syncedIds(favorites: List<FavoritePlace>, expected: Set<String>): Set<String> {
        var got: Set<String> = emptySet()
        repeat(SYNC_ATTEMPTS) {
            AppShortcuts.sync(ctx, favorites)
            got = ids(expected)
            if (got == expected) return got
        }
        return got
    }

    private fun fav(slot: FavoriteSlot) = FavoritePlace(SavedPlace(12.9, 77.6, slot.name), slot)

    @Test fun theFixedShortcutsAreAlwaysThereAndASlotOnlyWhileItIsSet() {
        val manager = ctx.getSystemService(ShortcutManager::class.java)
        assertEquals("max ${manager.maxShortcutCountPerActivity}, rate limited ${manager.isRateLimitingActive}", setOf("record_ride", "where_to", "ride_home"), syncedIds(listOf(fav(FavoriteSlot.HOME)), setOf("record_ride", "where_to", "ride_home")))
        assertTrue("ride_work" in syncedIds(listOf(fav(FavoriteSlot.HOME), fav(FavoriteSlot.WORK)), setOf("record_ride", "where_to", "ride_home", "ride_work")))
    }

    @Test fun clearingASlotRemovesItsShortcutAndKeepsTheOthers() {
        syncedIds(listOf(fav(FavoriteSlot.HOME), fav(FavoriteSlot.WORK)), setOf("record_ride", "where_to", "ride_home", "ride_work"))
        assertEquals(setOf("record_ride", "where_to", "ride_work"), syncedIds(listOf(fav(FavoriteSlot.WORK)), setOf("record_ride", "where_to", "ride_work")))
    }

    @Test fun anOtherFavouriteDoesNotMakeAShortcut() {
        assertEquals(setOf("record_ride", "where_to"), syncedIds(listOf(fav(FavoriteSlot.OTHER)), setOf("record_ride", "where_to")))
    }

    @Test fun theShortcutsThatOpenTheAppGoThroughItsOwnEntry() {
        syncedIds(listOf(fav(FavoriteSlot.HOME)), setOf("record_ride", "where_to", "ride_home"))
        val manager = ctx.getSystemService(ShortcutManager::class.java)
        val byId = manager.dynamicShortcuts.associateBy { it.id }
        listOf("where_to", "ride_home").forEach { id ->
            assertEquals(id, com.navigator.app.app.AppIntents.INTERNAL_ENTRY, byId.getValue(id).intent!!.component!!.className)
        }
    }

    private companion object {
        const val MAX_READS = 40
        const val SYNC_ATTEMPTS = 4
        const val READ_RETRY_MS = 25L
    }
}
