package com.navigator.app.nav.destination

import android.content.Context
import android.content.pm.ShortcutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The launcher shortcuts follow the favourites: the fixed two always, Ride home / Ride work only while that slot is set. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppShortcutsTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSystemService(ShortcutManager::class.java).removeAllDynamicShortcuts()
    }

    private fun ids(): Set<String> =
        ctx.getSystemService(ShortcutManager::class.java).dynamicShortcuts.map { it.id }.toSet()

    private fun fav(slot: FavoriteSlot) = FavoritePlace(SavedPlace(12.9, 77.6, slot.name), slot)

    @Test fun theFixedShortcutsAreAlwaysThereAndASlotOnlyWhileItIsSet() {
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.HOME)))
        val manager = ctx.getSystemService(ShortcutManager::class.java)
        assertEquals("max ${manager.maxShortcutCountPerActivity}, rate limited ${manager.isRateLimitingActive}", setOf("record_ride", "where_to", "ride_home"), ids())
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.HOME), fav(FavoriteSlot.WORK)))
        assertTrue("ride_work" in ids())
    }

    @Test fun clearingASlotRemovesItsShortcutAndKeepsTheOthers() {
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.HOME), fav(FavoriteSlot.WORK)))
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.WORK)))
        assertFalse("ride_home" in ids())
        assertEquals(setOf("record_ride", "where_to", "ride_work"), ids())
    }

    @Test fun anOtherFavouriteDoesNotMakeAShortcut() {
        AppShortcuts.sync(ctx, listOf(fav(FavoriteSlot.OTHER)))
        assertEquals(setOf("record_ride", "where_to"), ids())
    }
}
