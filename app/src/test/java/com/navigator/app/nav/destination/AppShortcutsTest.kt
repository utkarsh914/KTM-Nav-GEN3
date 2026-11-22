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

    private fun fav(slot: FavoriteSlot) = FavoritePlace(SavedPlace(12.9, 77.6, slot.name), slot)

    @Test fun theFixedShortcutsAreAlwaysThereAndASlotOnlyWhileItIsSet() {
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.HOME)))
        val manager = ctx.getSystemService(ShortcutManager::class.java)
        assertEquals("max ${manager.maxShortcutCountPerActivity}, rate limited ${manager.isRateLimitingActive}", setOf("record_ride", "where_to", "ride_home"), ids(setOf("record_ride", "where_to", "ride_home")))
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.HOME), fav(FavoriteSlot.WORK)))
        assertTrue("ride_work" in ids(setOf("record_ride", "where_to", "ride_home", "ride_work")))
    }

    @Test fun clearingASlotRemovesItsShortcutAndKeepsTheOthers() {
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.HOME), fav(FavoriteSlot.WORK)))
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.WORK)))
        assertEquals(setOf("record_ride", "where_to", "ride_work"), ids(setOf("record_ride", "where_to", "ride_work")))
    }

    @Test fun anOtherFavouriteDoesNotMakeAShortcut() {
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.OTHER)))
        assertEquals(setOf("record_ride", "where_to"), ids(setOf("record_ride", "where_to")))
    }

    private companion object {
        const val MAX_READS = 20
        const val READ_RETRY_MS = 25L
    }
}
