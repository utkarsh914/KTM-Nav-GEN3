package com.navigator.app.ui.feature.rides

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.navigator.app.data.rides.ImportResult
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.Gpx
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// The plain Application: OpenDashApplication's launch work (orphan recovery, pruning) would race with these tests' files.
@Config(sdk = [36], application = android.app.Application::class)
class RidesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: RideStore
    private lateinit var vm: RidesViewModel
    private val viewModels = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val ctx: Context = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = RideStore(ctx).also { it.resetStaticsForTest() }
        vm = RidesViewModel(RidesRepository(store, io = dispatcher), CoroutineScope(SupervisorJob() + dispatcher))
        // Held in a store so a test can clear the view model the way leaving the page does.
        vm = ViewModelProvider(viewModels, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })[RidesViewModel::class.java]
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun pts(n: Int, start: Long) = (0 until n).map { RidePoint(12.0, 77.0 + it * 0.0003, start + 1_000L * it, 40f) }
    private fun ride(start: Long, name: String) = store.importRide(name, pts(60, start))!!


    private fun TestScope.watch(): () -> RidesUi? {
        val job = vm.ui.launchIn(backgroundScope)
        return { vm.ui.value.also { job.hashCode() } }
    }

    @Test fun nothingShownUntilLoadedThenEmptyOrItems() = runTest(dispatcher) {
        val ui = watch()
        assertTrue(ui()!!.body is RidesBody.Empty)
        ride(1_000_000L, "A")
        assertTrue(ui()!!.body is RidesBody.Items)
    }

    @Test fun selectionRulesAndStarringTheSelection() = runTest(dispatcher) {
        val ui = watch()
        val a = ride(1_000_000L, "A"); val b = ride(2_000_000L, "B")
        store.setSaved(a.id, false); store.setSaved(b.id, false)
        vm.startSelecting(a.id)
        assertTrue(ui()!!.selecting)
        vm.toggle(b.id)
        assertEquals(setOf(a.id, b.id), ui()!!.selected)
        assertTrue(ui()!!.starWould)
        vm.starSelected()
        assertTrue(store.list().all { it.saved })
        assertFalse(ui()!!.starWould)
        vm.starSelected()
        assertTrue(store.list().none { it.saved })
        vm.selectAll(false)
        assertTrue(ui()!!.selected.isEmpty())
        vm.selectAll(true)
        assertTrue(ui()!!.allSelected)
        vm.stopSelecting()
        assertFalse(ui()!!.selecting)
        assertTrue(ui()!!.selected.isEmpty())
    }

    @Test fun deleteOffersUndoAndUndoBringsTheRidesBack() = runTest(dispatcher) {
        val ui = watch()
        val a = ride(1_000_000L, "A"); val b = ride(2_000_000L, "B")
        val events = mutableListOf<RidesEvent>()
        vm.events.onEach { events += it }.launchIn(backgroundScope)
        vm.startSelecting(a.id)
        vm.deleteSelected()
        val deleted = events.single() as RidesEvent.Deleted
        assertEquals(listOf(a.id), deleted.ids)
        assertFalse("selection mode ends", ui()!!.selecting)
        assertEquals(listOf(b.id), store.list().map { it.id })
        vm.undoDelete(deleted.ids)
        assertEquals(setOf(a.id, b.id), store.list().map { it.id }.toSet())
    }

    @Test fun undoAndPurgeStillWorkAfterThePageIsGone() = runTest(dispatcher) {
        val a = ride(1_000_000L, "A"); val b = ride(2_000_000L, "B")
        store.softDelete(listOf(a.id, b.id))
        viewModels.clear() // Back popped Rides: the snackbar and its Undo live on in the shell
        vm.undoDelete(listOf(a.id))
        assertEquals(listOf(a.id), store.list().map { it.id })
        vm.purge(listOf(b.id))
        assertFalse(store.trackFile(b.id).exists())
    }

    @Test fun starringAMergedRideEndsTheMergeOffer() = runTest(dispatcher) {
        watch()
        val a = ride(1_000_000L, "A"); val b = ride(2_000_000L, "B")
        val merged = (store.mergeRides(listOf(a.id, b.id)) as com.navigator.app.ride.MergeResult.Merged).ride
        val plain = ride(5_000_000L, "C")
        val events = mutableListOf<RidesEvent>()
        vm.events.onEach { events += it }.launchIn(backgroundScope)
        vm.toggleStar(plain.id)
        assertTrue(events.isEmpty())
        vm.toggleStar(merged.id)
        assertEquals(listOf<RidesEvent>(RidesEvent.MergedRideChanged(merged.id)), events)
    }

    @Test fun purgeAfterTheOfferRemovesThemForGood() = runTest(dispatcher) {
        watch()
        val a = ride(1_000_000L, "A"); ride(2_000_000L, "B")
        store.softDelete(listOf(a.id))
        vm.purge(listOf(a.id))
        assertFalse(store.trackFile(a.id).exists())
        vm.undoDelete(listOf(a.id))
        assertEquals(1, store.list().size)
    }

    @Test fun deletingNothingSelectedDoesNothing() = runTest(dispatcher) {
        watch()
        ride(1_000_000L, "A")
        vm.deleteSelected()
        assertEquals(1, store.list().size)
    }

    @Test fun searchAndSortReachTheList() = runTest(dispatcher) {
        val ui = watch()
        ride(1_000_000L, "Nandi"); ride(2_000_000L, "Office")
        vm.openSearch(); vm.setQuery("nandi")
        assertEquals(1, ((ui()!!.body as RidesBody.Items).items.size))
        vm.setQuery("zzz")
        assertTrue(ui()!!.body is RidesBody.NoMatch)
        vm.closeSearch()
        assertEquals("", ui()!!.query)
        assertFalse(ui()!!.searchOpen)
        vm.setSort(RideSort(RideSortField.Distance, false))
        assertEquals(RideSortField.Distance, ui()!!.sort.field)
    }

    @Test fun aSelectionDoesNotOutliveItsRide() = runTest(dispatcher) {
        val ui = watch()
        val a = ride(1_000_000L, "A"); ride(2_000_000L, "B")
        vm.startSelecting(a.id)
        store.softDelete(listOf(a.id))
        assertTrue(ui()!!.selected.isEmpty())
    }

    @Test fun selectionModeEndsWhenTheSelectedRidesAreMerged() = runTest(dispatcher) {
        val ui = watch()
        val a = ride(1_000_000L, "A"); val b = ride(2_000_000L, "B"); ride(9_000_000L, "C")
        vm.startSelecting(a.id); vm.toggle(b.id)
        store.mergeRides(listOf(a.id, b.id))
        assertFalse(ui()!!.selecting)
        assertTrue(ui()!!.selected.isEmpty())
    }

    @Test fun aSearchThatHidesTheSelectedRidesKeepsSelectionMode() = runTest(dispatcher) {
        val ui = watch()
        val a = ride(1_000_000L, "Nandi"); ride(2_000_000L, "Office")
        vm.startSelecting(a.id)
        vm.openSearch(); vm.setQuery("office")
        assertTrue(ui()!!.selecting)
        vm.setQuery("")
        assertEquals(setOf(a.id), ui()!!.selected)
    }

    @Test fun importReportsItsOutcome() = runTest(dispatcher) {
        watch()
        val events = mutableListOf<RidesEvent>()
        vm.events.onEach { events += it }.launchIn(backgroundScope)
        vm.importGpx({ null }, null)
        assertEquals(ImportResult.Unreadable, (events.last() as RidesEvent.Imported).result)
        val gpx = Gpx.write("Loop", pts(60, 5_000_000L))
        vm.importGpx({ gpx.byteInputStream() }, "loop.gpx")
        val first = (events.last() as RidesEvent.Imported).result as ImportResult.Imported
        assertEquals("Loop", first.ride.destinationLabel)
        vm.importGpx({ gpx.byteInputStream() }, "loop.gpx")
        assertEquals(ImportResult.Duplicate, (events.last() as RidesEvent.Imported).result)
        val tiny = Gpx.write("x", pts(1, 9_000_000L))
        vm.importGpx({ tiny.byteInputStream() }, "x.gpx")
        assertEquals("a one-fix track is not a track", ImportResult.Unreadable, (events.last() as RidesEvent.Imported).result)
    }
}
