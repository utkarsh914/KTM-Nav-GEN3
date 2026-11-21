package com.navigator.app.ui.feature.replay

import android.content.Context
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ride.MergeResult
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
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
@Config(sdk = [36])
class ReplayViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var store: RideStore
    private lateinit var repo: RidesRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val ctx: Context = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "rides").deleteRecursively()
        ctx.getSharedPreferences("ride_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = RideStore(ctx).also { it.resetStaticsForTest() }
        repo = RidesRepository(store, io = dispatcher)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun pts(n: Int, start: Long, lng0: Double = 77.0) = (0 until n).map { RidePoint(12.0, lng0 + it * 0.0003, start + 1_000L * it, 40f) }
    // An imported ride starts starred (so the history limit never prunes it); these tests want it plain.
    private fun plain(name: String, n: Int, start: Long, lng0: Double = 77.0) =
        store.importRide(name, pts(n, start, lng0))!!.also { store.setSaved(it.id, false) }
    private fun ride(start: Long, name: String = "A", n: Int = 60) = plain(name, n, start)
    private fun vm(id: String) = ReplayViewModel(id, repo, AvgSpeedMode.MOVING, RideMetrics.SpeedBands.DEFAULT, dispatcher) { dispatcher.scheduler.currentTime }
    private fun ReplayViewModel.ready() = ui.value as ReplayUi.Ready

    private fun TestScope.events(vm: ReplayViewModel): List<ReplayEvent> {
        val list = mutableListOf<ReplayEvent>()
        vm.events.onEach { list += it }.launchIn(backgroundScope)
        return list
    }

    @Test fun loadsTheRideAndItsTrack() = runTest(dispatcher) {
        val r = ride(1_000_000L, "Nandi")
        val v = vm(r.id)
        val ready = v.ready()
        assertEquals("Nandi", ready.ride.destinationLabel)
        assertEquals(60, ready.points.size)
        assertEquals(1, ready.parts)
        assertEquals(0, v.position.value)
        assertFalse(ready.playing)
    }

    @Test fun anUnknownRideHasNoTrack() = runTest(dispatcher) {
        assertTrue(vm("nope").ui.value.let { it is ReplayUi.NoTrack && it.ride == null })
    }

    @Test fun playbackAdvancesWithTheSpeedAndStopsAtTheEnd() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L).id)
        v.togglePlay()
        assertTrue(v.ready().playing)
        advanceTimeBy(1_000); runCurrent()
        val after1s = v.position.value
        assertTrue("4x for 1 s covers about 4 s of ride, was $after1s", after1s in 3..5)
        v.stepSpeed(1)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(59, v.position.value)
        assertFalse(v.ready().playing)
    }

    @Test fun playAtTheEndStartsOver() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L, n = 12).id)
        v.seek(1f)
        v.togglePlay()
        assertEquals(0, v.position.value)
        assertTrue(v.ready().playing)
    }

    @Test fun pausingHoldsThePosition() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L).id)
        v.togglePlay(); advanceTimeBy(500); runCurrent(); v.togglePlay()
        val held = v.position.value
        advanceTimeBy(5_000); runCurrent()
        assertEquals(held, v.position.value)
    }

    @Test fun scrubbingPausesAndSeeksAndRestartRewinds() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L).id)
        v.togglePlay()
        v.beginScrub()
        assertFalse(v.ready().playing)
        v.seek(0.5f)
        assertEquals(29, v.position.value)
        v.restart()
        assertEquals(0, v.position.value)
    }

    @Test fun aTapNearTheTrackSeeksAndOneFarFromItDoesNot() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L).id)
        v.seekToMap(12.0, 77.0 + 20 * 0.0003)
        assertEquals(20, v.position.value)
        v.seekToMap(13.0, 77.0)
        assertEquals(20, v.position.value)
    }

    @Test fun speedFollowElevationAndSmoothingAreInSessionToggles() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L).id)
        v.stepSpeed(1); assertEquals(8, v.ready().speed)
        v.toggleFollow(); assertTrue(v.ready().following)
        v.stopFollowing(); assertFalse(v.ready().following)
        v.toggleElevation(); assertFalse(v.ready().showElevation)
        assertEquals(6, v.ready().smoothingMeters)
        v.stepSmoothing(1); assertEquals(12, v.ready().smoothingMeters)
        repeat(5) { v.stepSmoothing(1) }; assertEquals(30, v.ready().smoothingMeters)
        repeat(9) { v.stepSmoothing(-1) }; assertEquals(0, v.ready().smoothingMeters)
    }

    @Test fun aMergedRideShowsItsPartsAndPlaybackSkipsTheStop() = runTest(dispatcher) {
        val a = plain("A", 30, 1_000_000L)
        val b = plain("B", 30, 9_000_000L, 77.1)
        val merged = (store.mergeRides(listOf(a.id, b.id)) as MergeResult.Merged).ride
        val v = vm(merged.id)
        assertEquals(2, v.ready().parts)
        assertEquals(1, v.ready().seams.size)
        v.seek(29f / 59)
        v.togglePlay()
        advanceTimeBy(100); runCurrent()
        assertTrue("playback crossed the stop at once", v.position.value >= 30)
    }

    @Test fun starringMarksTheRideAndEndsAMergeOffer() = runTest(dispatcher) {
        val a = plain("A", 30, 1_000_000L)
        val b = plain("B", 30, 9_000_000L)
        val merged = (store.mergeRides(listOf(a.id, b.id)) as MergeResult.Merged).ride
        val v = vm(merged.id)
        val seen = events(v)
        v.toggleStar()
        assertTrue(v.ready().ride.saved)
        assertEquals(listOf<ReplayEvent>(ReplayEvent.MergedRideChanged), seen)
    }

    @Test fun starringAPlainRideSaysNothingElse() = runTest(dispatcher) {
        val v = vm(ride(1_000_000L).id)
        val seen = events(v)
        v.toggleStar()
        assertTrue(v.ready().ride.saved)
        assertTrue(seen.isEmpty())
    }

    @Test fun deletingOffersUndoAndDoesNotAlsoReportGone() = runTest(dispatcher) {
        val r = ride(1_000_000L)
        val v = vm(r.id)
        val seen = events(v)
        v.delete()
        assertEquals(listOf<ReplayEvent>(ReplayEvent.Deleted(listOf(r.id))), seen)
        assertTrue(store.list().isEmpty())
    }

    @Test fun aRideDeletedElsewhereClosesTheReplay() = runTest(dispatcher) {
        val r = ride(1_000_000L)
        val v = vm(r.id)
        val seen = events(v)
        v.togglePlay()
        store.softDelete(listOf(r.id))
        assertEquals(listOf<ReplayEvent>(ReplayEvent.Gone), seen)
        assertEquals("no stale ride is left on show, and no error page either", ReplayUi.Loading, v.ui.value)
    }

    @Test fun aTrimReloadsTheTrackFromTheStartKeepingTheSettings() = runTest(dispatcher) {
        val r = ride(1_000_000L)
        val v = vm(r.id)
        v.stepSpeed(1); v.togglePlay(); advanceTimeBy(300); runCurrent()
        store.trimRide(r.id, 10, 40)
        assertEquals(31, v.ready().points.size)
        assertEquals(0, v.position.value)
        assertFalse(v.ready().playing)
        assertEquals(8, v.ready().speed)
    }

    @Test fun renamingFlowsIntoTheShownRide() = runTest(dispatcher) {
        val r = ride(1_000_000L, "Old")
        val v = vm(r.id)
        store.updateMeta(r.id, "New", null)
        assertEquals("New", v.ready().ride.destinationLabel)
    }

    @Test fun playbackFollowsTheRealClockNotTheFrameCount() = runTest(dispatcher) {
        val id = ride(1_000_000L).id
        var clock = 0L
        val v = ReplayViewModel(id, repo, AvgSpeedMode.MOVING, RideMetrics.SpeedBands.DEFAULT, dispatcher) { clock }
        runCurrent()
        v.togglePlay()
        // Three ticks each arrive 300 ms late: each counts MAX_FRAME_MS (100) at x4 = 1200 ms of ride, so one fix (1 s apart).
        // A fixed 16 ms step per tick would have gone 3 x 64 = 192 ms and stayed at 0.
        for (t in listOf(300L, 600L, 900L)) { clock = t; advanceTimeBy(16); runCurrent() }
        assertEquals(1, v.position.value)
        // A long stall counts only the cap: 400 ms more of ride, not 40 s.
        clock = 40_000; advanceTimeBy(16); runCurrent()
        assertEquals(1, v.position.value)
        v.togglePlay()
    }
}
