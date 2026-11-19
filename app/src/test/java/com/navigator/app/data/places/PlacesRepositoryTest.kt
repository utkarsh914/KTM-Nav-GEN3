package com.navigator.app.data.places

import android.content.Context
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlacesRepositoryTest {
    private lateinit var ctx: Context
    private val toit = SavedPlace(13.0, 77.5, "Toit")
    private val park = SavedPlace(12.2, 77.2, "Cubbon Park")

    @Before fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences(PlacesStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun removingARecentLeavesTheOthersAndUndoPutsItBackFirst() {
        val repo = PlacesRepository(ctx)
        repo.addRecent(park); repo.addRecent(toit)
        assertEquals(listOf(toit, park), repo.recents.value)
        repo.removeRecent(toit)
        assertEquals(listOf(park), repo.recents.value)
        repo.addRecent(toit)
        assertEquals(listOf(toit, park), repo.recents.value)
    }

    @Test fun removingMatchesByCoordinatesNotLabel() {
        val repo = PlacesRepository(ctx)
        repo.addRecent(toit)
        repo.removeRecent(toit.copy(label = "Renamed"))
        assertTrue(repo.recents.value.isEmpty())
    }

    @Test fun writesFromAnywhereShowUp() {
        val repo = PlacesRepository(ctx)
        PlacesStore(ctx).addRecent(park)
        assertEquals(listOf(park), repo.recents.value)
    }
}
