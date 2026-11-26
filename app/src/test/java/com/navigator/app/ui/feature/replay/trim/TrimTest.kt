package com.navigator.app.ui.feature.replay.trim

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.MergeResult
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideStore
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.theme.KtmTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class TrimRangeTest {
    private val r = TrimRange(0, 9, 9)

    @Test fun startsWholeAndUnchanged() {
        assertFalse(TrimRange.whole(10).changed)
        assertEquals(TrimRange(0, 9, 9), TrimRange.whole(10))
    }

    @Test fun handlesNeverCrossAndAlwaysKeepTwoFixes() {
        assertEquals(TrimRange(8, 9, 9), r.moveStart(99))
        assertEquals(TrimRange(0, 1, 9), r.moveEnd(-5))
        assertEquals(TrimRange(0, 9, 9), r.moveStart(-3))
    }

    @Test fun aTouchMovesTheNearerHandle() {
        assertEquals(TrimRange(2, 9, 9), r.moveNearest(2))
        assertEquals(TrimRange(0, 7, 9), r.moveNearest(7))
        assertEquals(TrimRange(5, 10, 10), TrimRange(0, 10, 10).moveNearest(5)) // a tie goes to the start
    }

    @Test fun changedOnceEitherEndMoves() {
        assertTrue(r.moveStart(1).changed)
        assertTrue(r.moveEnd(8).changed)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrimViewModelTest {
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
    private fun ride(start: Long = 1_000_000L) = store.importRide("A", pts(60, start))!!.also { store.setSaved(it.id, false) }
    private fun vm(id: String) = TrimViewModel(id, repo, dispatcher)
    private fun TrimViewModel.ready() = ui.value as TrimUi.Ready

    @Test fun startsWholeWithItsStatsAndNothingToApply() = runTest(dispatcher) {
        val v = vm(ride().id)
        assertEquals(TrimRange(0, 59, 59), v.ready().range)
        assertNotNull(v.ready().kept)
        assertFalse(v.ready().canApply)
    }

    @Test fun movingAHandleUpdatesTheRangeAndWhatItKeeps() = runTest(dispatcher) {
        val v = vm(ride().id)
        val whole = v.ready().kept!!
        v.moveStart(0.25f); v.moveEnd(0.75f)
        assertEquals(TrimRange(14, 44, 59), v.ready().range)
        assertTrue(v.ready().kept!!.ridingSeconds < whole.ridingSeconds)
        assertTrue(v.ready().canApply)
    }

    @Test fun aTapOnTheRouteMovesTheNearestHandleAndAFarTapDoesNothing() = runTest(dispatcher) {
        val v = vm(ride().id)
        v.tapRoute(12.0, 77.0 + 50 * 0.0003)
        assertEquals(TrimRange(0, 50, 59), v.ready().range)
        v.tapRoute(13.0, 77.0)
        assertEquals(TrimRange(0, 50, 59), v.ready().range)
    }

    @Test fun nudgingStepsAHandleByFixes() = runTest(dispatcher) {
        val v = vm(ride().id)
        v.nudgeStart(3); v.nudgeEnd(-2)
        assertEquals(TrimRange(3, 57, 59), v.ready().range)
    }

    @Test fun applyCutsTheRideAndReportsIt() = runTest(dispatcher) {
        val r = ride()
        val v = vm(r.id)
        val seen = mutableListOf<TrimEvent>()
        v.events.onEach { seen += it }.launchIn(backgroundScope)
        v.moveStart(0.2f); v.moveEnd(0.8f)
        val range = v.ready().range
        v.apply()
        assertEquals(listOf<TrimEvent>(TrimEvent.Trimmed(merged = false)), seen)
        assertTrue("closes as state and stops spinning", v.ready().closed && !v.ready().applying)
        assertEquals(range.to - range.from + 1, store.get(r.id)!!.pointCount)
    }

    @Test fun nothingToApplyMeansNothingHappens() = runTest(dispatcher) {
        val r = ride()
        val v = vm(r.id)
        val seen = mutableListOf<TrimEvent>()
        v.events.onEach { seen += it }.launchIn(backgroundScope)
        v.apply()
        assertTrue(seen.isEmpty())
        assertEquals(60, store.get(r.id)!!.pointCount)
    }

    @Test fun aRefusedTrimShowsInlineAndClearsWhenAHandleMoves() = runTest(dispatcher) {
        val r = ride()
        val v = vm(r.id)
        v.moveStart(0.5f)
        store.setActiveForTest(r.id) // being recorded: the store refuses
        v.apply()
        store.setActiveForTest(null)
        assertTrue(v.ready().failed)
        assertFalse(v.ready().applying)
        v.moveStart(0.4f)
        assertFalse(v.ready().failed)
    }

    @Test fun trimmingAMergedRideSaysSoThatItsUndoOfferEnds() = runTest(dispatcher) {
        val a = ride(1_000_000L); val b = ride(9_000_000L)
        val merged = (store.mergeRides(listOf(a.id, b.id)) as MergeResult.Merged).ride
        val v = vm(merged.id)
        val seen = mutableListOf<TrimEvent>()
        v.events.onEach { seen += it }.launchIn(backgroundScope)
        v.moveStart(0.1f)
        v.apply()
        assertEquals(listOf<TrimEvent>(TrimEvent.Trimmed(merged = true)), seen)
    }

    @Test fun aRideThatGoesAwayUnderThePageLeavesInsteadOfShowingAnError() = runTest(dispatcher) {
        val r = ride(1_000_000L)
        val v = vm(r.id)
        assertTrue(v.ui.value is TrimUi.Ready)
        store.softDelete(listOf(r.id))
        assertEquals(TrimUi.Loading, v.ui.value)
        assertTrue("and the page leaves by itself", v.vanished.value)
    }

    @Test fun aMissingRideIsGone() = runTest(dispatcher) {
        assertEquals(TrimUi.Gone, vm("nope").ui.value)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class TrimContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val points = (0 until 20).map { RidePoint(12.0, 77.0 + it * 0.0003, 1_700_000_000_000 + 60_000L * it, 40f) }
    private val ride = RecordedRide("r", 1_700_000_000_000, 1_700_001_140_000, 5_000, 1_140)

    private fun ready(from: Int = 0, to: Int = 19, kept: Kept? = Kept(5_000, 1_140), failed: Boolean = false, applying: Boolean = false, settled: Boolean = true) =
        TrimUi.Ready(ride, points, TrimRange(from, to, 19), kept, settled, applying, failed)

    private fun show(ui: TrimUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            TrimContent(
                ui,
                TrimActions({ log += "back" }, { log += "start" }, { log += "end" }, { log += "nearest" }, { log += "ns$it" }, { log += "ne$it" }, { log += "apply" }),
                onInsets = { log += "insets${it.bottom > 0}" },
            )
        }
    }

    @Test fun showsTheCutTheTimesAndWhatItKeeps() {
        show(ready(from = 5, to = 15))
        rule.onNodeWithText("Keeps 5.0 km · 19 min").assertIsDisplayed()
        rule.onNodeWithText("This permanently removes the rest of the track.").assertIsDisplayed()
    }

    @Test fun trimStaysDisabledUntilSomethingChanges() {
        show(ready())
        rule.onNode(hasText("Trim ride") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun trimIsEnabledOnceTheRangeChangedAndItsStatsAreKnown() {
        show(ready(from = 5))
        rule.onNode(hasText("Trim ride") and hasClickAction()).assertIsEnabled().performClick()
        assertTrue("apply" in log)
    }

    @Test fun noTrimWhileTheStatsAreStillBeingWorkedOut() {
        show(ready(from = 5, settled = false))
        rule.onNode(hasText("Trim ride") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun aRefusalShowsInlineNotAsAToast() {
        show(ready(from = 5, failed = true))
        rule.onNodeWithText("Couldn't trim this ride", substring = true).assertIsDisplayed()
    }

    @Test fun cancelAndBackLeaveWithoutTrimming() {
        show(ready(from = 5))
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back", "back"), log.filter { it == "back" })
        assertFalse("apply" in log)
    }

    @Test fun screenReadersCanMoveEachHandle() {
        show(ready(from = 5))
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep")
        val actions = bar.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(4, actions.size)
        actions.first { it.label == "Move the start later" }.action()
        actions.first { it.label == "Move the end earlier" }.action()
        assertTrue(log.any { it.startsWith("ns") && !it.startsWith("ns-") })
        assertTrue(log.any { it.startsWith("ne-") })
    }

    @Test fun theBarAnnouncesTheCut() {
        show(ready(from = 5, to = 15))
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep").fetchSemanticsNode()
        assertTrue(bar.config[SemanticsProperties.StateDescription].startsWith("From "))
    }

    @Test fun nothingMovesOrCancelsWhileTheWriteRuns() {
        show(ready(from = 5, applying = true))
        rule.onNodeWithText("Cancel").assertIsNotEnabled()
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep").fetchSemanticsNode()
        assertTrue(bar.config[SemanticsActions.CustomActions].isEmpty())
    }

    @Test fun aMissingRideSaysSo() {
        show(TrimUi.Gone)
        rule.onNodeWithText("Ride not found").assertIsDisplayed()
    }
}
