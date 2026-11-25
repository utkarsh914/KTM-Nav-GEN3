package com.navigator.app.ui.feature.replay.edit

import android.content.Context
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.MergeResult
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import com.navigator.app.ride.RideTrim
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditRideViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: RideStore
    private lateinit var repo: RidesRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val ctx: Context = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = RideStore(ctx).also { it.setActiveForTest(null) }
        repo = RidesRepository(store, io = dispatcher)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun pts(n: Int, start: Long, lng0: Double = 77.0) = (0 until n).map { RidePoint(12.0, lng0 + it * 0.0003, start + 1_000L * it, 40f) }
    // An imported ride starts starred (so the history limit never prunes it); these tests want it plain.
    private fun ride(name: String = "A", start: Long = 1_000_000L) =
        store.importRide(name, pts(60, start))!!.also { store.setSaved(it.id, false) }

    private var mergedOffers = 0
    private fun TestScope.edit(id: String) =
        EditRideViewModel(id, repo, backgroundScope, debounceMs = 500, onMergedRideChanged = { mergedOffers++ })

    private fun TestScope.events(vm: EditRideViewModel): List<EditEvent> {
        val list = mutableListOf<EditEvent>()
        vm.events.onEach { list += it }.launchIn(backgroundScope)
        return list
    }

    @Test fun theFormStartsWithTheRidesNameAndNotes() = runTest(dispatcher) {
        val r = ride("Nandi"); store.updateMeta(r.id, "Nandi", "windy")
        assertEquals(EditUi.Ready("Nandi", "windy", false), edit(r.id).ui.value)
    }

    @Test fun typingIsSavedAfterAPauseNotOnEveryKey() = runTest(dispatcher) {
        val r = ride("Old")
        val vm = edit(r.id)
        vm.onName("N"); vm.onName("Ne"); vm.onName("New")
        assertEquals("Old", store.get(r.id)!!.destinationLabel)
        advanceTimeBy(600); runCurrent()
        assertEquals("New", store.get(r.id)!!.destinationLabel)
    }

    @Test fun leavingSavesWhatIsPending() = runTest(dispatcher) {
        val r = ride("Old")
        val vm = edit(r.id)
        vm.onNotes("a note")
        vm.flush()
        assertEquals("a note", store.get(r.id)!!.notes)
    }

    @Test fun flushWithNothingChangedWritesNothing() = runTest(dispatcher) {
        val r = ride("Old")
        edit(r.id).flush()
        assertEquals("Old", store.get(r.id)!!.destinationLabel)
    }

    @Test fun fieldsAreCapped() = runTest(dispatcher) {
        val vm = edit(ride().id)
        vm.onName("x".repeat(500))
        assertEquals(RideTrim.MAX_NAME, (vm.ui.value as EditUi.Ready).name.length)
    }

    @Test fun theStarSavesAtOnce() = runTest(dispatcher) {
        val r = ride()
        val vm = edit(r.id)
        vm.toggleStar()
        assertTrue(store.get(r.id)!!.saved)
        assertTrue((vm.ui.value as EditUi.Ready).saved)
    }

    @Test fun editingAMergedRideEndsItsUndoOfferAtOnceAndOnlyOnce() = runTest(dispatcher) {
        val a = ride("A", 1_000_000L); val b = ride("B", 9_000_000L)
        val merged = (store.mergeRides(listOf(a.id, b.id)) as MergeResult.Merged).ride
        val vm = edit(merged.id)
        vm.onName("Day out")
        assertEquals("ended before anything is saved, so leaving at once cannot lose it", 1, mergedOffers)
        vm.flush(); vm.onName("Day out 2"); vm.toggleStar()
        assertEquals(1, mergedOffers)
    }

    @Test fun editingAnOrdinaryRideLeavesOffersAlone() = runTest(dispatcher) {
        val vm = edit(ride().id)
        vm.onName("x"); vm.toggleStar()
        assertEquals(0, mergedOffers)
    }

    @Test fun aRefusedDeleteKeepsTheTypedText() = runTest(dispatcher) {
        val r = ride()
        val vm = edit(r.id)
        vm.onName("typed")
        store.setActiveForTest(r.id) // being recorded: the store refuses
        vm.delete()
        store.setActiveForTest(null)
        advanceTimeBy(1_000); runCurrent()
        assertEquals("typed", store.get(r.id)!!.destinationLabel)
    }

    @Test fun deletingOffersUndoAndLeavesNothingPending() = runTest(dispatcher) {
        val r = ride()
        val vm = edit(r.id)
        val seen = events(vm)
        vm.onName("typed")
        vm.delete()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf<EditEvent>(EditEvent.Deleted(listOf(r.id))), seen)
        assertTrue("the page closes as state, not only by an event", vm.closed.value)
        assertTrue(store.list().isEmpty())
    }

    @Test fun undoAfterADeleteBringsBackTheTextTypedJustBefore() = runTest(dispatcher) {
        val r = ride()
        val vm = edit(r.id)
        vm.onName("typed")
        vm.delete()
        advanceTimeBy(1_000); runCurrent()
        store.restore(listOf(r.id))
        assertEquals("typed", store.get(r.id)!!.destinationLabel)
    }

    @Test fun aRideDeletedElsewhereClosesThePage() = runTest(dispatcher) {
        val r = ride()
        val vm = edit(r.id)
        val seen = events(vm)
        store.softDelete(listOf(r.id))
        assertEquals(listOf<EditEvent>(EditEvent.Gone), seen)
        assertEquals(EditUi.Loading, vm.ui.value)
    }

    @Test fun aStarFromTheReplayShowsInTheForm() = runTest(dispatcher) {
        val r = ride()
        val vm = edit(r.id)
        store.setSaved(r.id, true)
        assertTrue((vm.ui.value as EditUi.Ready).saved)
    }
}
