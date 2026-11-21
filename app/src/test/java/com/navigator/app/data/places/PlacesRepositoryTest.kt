package com.navigator.app.data.places

import android.content.Context
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun assigningHomeReturnsTheOldOneAndUndoBringsItBack() {
        val repo = PlacesRepository(ctx)
        assertNull(repo.assign(toit, FavoriteSlot.HOME))
        val previous = repo.assign(park, FavoriteSlot.HOME)
        assertEquals(FavoritePlace(toit, FavoriteSlot.HOME), previous)
        assertEquals(park, repo.favorites.value.single { it.slot == FavoriteSlot.HOME }.place)
        repo.undoAssign(park, FavoriteSlot.HOME, previous)
        assertEquals(toit, repo.favorites.value.single { it.slot == FavoriteSlot.HOME }.place)
    }

    @Test fun undoDoesNotOverwriteANewerChangeToTheSameSlot() {
        val repo = PlacesRepository(ctx)
        val first = repo.assign(toit, FavoriteSlot.HOME)
        repo.assign(park, FavoriteSlot.HOME)
        repo.undoAssign(toit, FavoriteSlot.HOME, first)
        assertEquals(park, repo.favorites.value.single { it.slot == FavoriteSlot.HOME }.place)
    }

    @Test fun undoingTheFirstHomeLeavesNone() {
        val repo = PlacesRepository(ctx)
        val previous = repo.assign(toit, FavoriteSlot.HOME)
        repo.undoAssign(toit, FavoriteSlot.HOME, previous)
        assertTrue(repo.favorites.value.isEmpty())
    }

    @Test fun savingAPlaceTwiceKeepsOneAndUndoRestoresTheFirstVersion() {
        val repo = PlacesRepository(ctx)
        repo.assign(toit, FavoriteSlot.OTHER)
        val renamed = toit.copy(label = "Toit Indiranagar")
        val previous = repo.assign(renamed, FavoriteSlot.OTHER)
        assertEquals(listOf(renamed), repo.favorites.value.map { it.place })
        repo.undoAssign(renamed, FavoriteSlot.OTHER, previous)
        assertEquals(listOf(toit), repo.favorites.value.map { it.place })
    }

    @Test fun removingASlotLeavesSavedPlacesAndReturnsWhatWasThere() {
        val repo = PlacesRepository(ctx)
        repo.assign(toit, FavoriteSlot.HOME); repo.assign(toit, FavoriteSlot.OTHER)
        assertEquals(toit, repo.removeSlot(FavoriteSlot.HOME)?.place)
        assertNull(repo.removeSlot(FavoriteSlot.WORK))
        assertEquals(listOf(FavoriteSlot.OTHER), repo.favorites.value.map { it.slot })
    }

    @Test fun removedSavedPlacesComeBackWithRestore() {
        val repo = PlacesRepository(ctx)
        repo.assign(toit, FavoriteSlot.OTHER); repo.assign(park, FavoriteSlot.OTHER); repo.assign(toit, FavoriteSlot.HOME)
        val gone = repo.removeSaved(setOf(PlacesRepository.key(toit)))
        assertEquals(listOf(toit), gone.map { it.place })
        assertEquals(setOf(park), repo.favorites.value.filter { it.slot == FavoriteSlot.OTHER }.map { it.place }.toSet())
        assertEquals("Home survives", toit, repo.favorites.value.single { it.slot == FavoriteSlot.HOME }.place)
        repo.restore(gone)
        assertEquals(setOf(toit, park), repo.favorites.value.filter { it.slot == FavoriteSlot.OTHER }.map { it.place }.toSet())
    }

    @Test fun undoingARemovedHomeDoesNotOverwriteANewerOne() {
        val repo = PlacesRepository(ctx)
        repo.assign(toit, FavoriteSlot.HOME)
        val gone = listOfNotNull(repo.removeSlot(FavoriteSlot.HOME))
        repo.assign(park, FavoriteSlot.HOME)
        repo.restore(gone)
        assertEquals("the newer Home stays", park, repo.favorites.value.single { it.slot == FavoriteSlot.HOME }.place)
    }

    @Test fun undoingARemovedHomeRestoresItWhenTheSlotIsStillEmpty() {
        val repo = PlacesRepository(ctx)
        repo.assign(toit, FavoriteSlot.HOME)
        repo.restore(listOfNotNull(repo.removeSlot(FavoriteSlot.HOME)))
        assertEquals(toit, repo.favorites.value.single { it.slot == FavoriteSlot.HOME }.place)
    }
}
