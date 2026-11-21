package com.navigator.app.ui.feature.rides.merge

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.data.rides.RidesRepository
import com.navigator.app.ride.MergeRefusal
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideSource
import com.navigator.app.ride.RideStore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun pts(n: Int, start: Long, lng0: Double = 77.0) = (0 until n).map { RidePoint(12.0, lng0 + it * 0.0003, start + 1_000L * it, 40f) }

private fun ride(id: String, start: Long, sec: Int, name: String? = null, saved: Boolean = false) =
    RecordedRide(id, start, start + sec * 1000L, 1000, sec, destinationLabel = name, source = RideSource.NAV, saved = saved, maxSpeedKmh = 50f)

class MergePlannerTest {
    private val a = ride("a", 1_000_000L, 60, "First")
    private val b = ride("b", 1_000_000L + 60_000 + 18 * 60_000, 60, "Second", saved = true)

    @Test fun planJoinsTheRidesAndNamesTheStopBetween() {
        val plan = MergePlanner.plan(listOf(b, a), { false }) { id -> if (id == "a") pts(60, a.startMs) else pts(60, b.startMs, 77.1) }
        assertEquals(listOf("a", "b"), plan.parts.map { it.ride.id })
        assertEquals(listOf(MergeGap(18 * 60)), plan.gaps)
        assertEquals("First", plan.name)
        assertTrue(plan.anyStarred)
        assertTrue(plan.block == null)
        assertTrue(plan.route.any { it.gap })
        assertTrue("the stop adds no distance", plan.distanceMeters < 4_500)
        assertTrue(plan.ridingSeconds > 0)
    }

    @Test fun overlapNamesTheTwoRidesAndSkipsReadingTracks() {
        val c = ride("c", a.startMs + 30_000, 60)
        var read = 0
        val plan = MergePlanner.plan(listOf(a, c), { false }) { read++; emptyList() }
        val block = plan.block as MergeBlock.Overlap
        assertEquals("a", block.first.id); assertEquals("c", block.second.id)
        assertEquals(0, read)
        assertTrue(plan.route.isEmpty())
    }

    @Test fun activeAndTooFewAreBlocked() {
        assertTrue(MergePlanner.plan(listOf(a, b), { it == "b" }) { emptyList() }.block is MergeBlock.Active)
        assertTrue(MergePlanner.plan(listOf(a), { false }) { emptyList() }.block is MergeBlock.TooFew)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MergeViewModelTest {
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

    @Test fun loadsThePlanThenMergesAndReportsTheToken() = runTest(dispatcher) {
        val a = store.importRide("A", pts(60, 1_000_000L))!!; val b = store.importRide("B", pts(60, 2_000_000L, 77.1))!!
        val vm = MergeViewModel(listOf(a.id, b.id), repo, dispatcher)
        val ready = vm.ui.value as MergeUi.Ready
        assertEquals(2, ready.plan.parts.size)
        val events = mutableListOf<MergeEvent>()
        vm.events.onEach { events += it }.launchIn(backgroundScope)
        vm.merge()
        val merged = events.single() as MergeEvent.Merged
        assertEquals(listOf(a.id, b.id), merged.token.originalIds)
        assertEquals(listOf(merged.ride.id), store.list().map { it.id })
    }

    @Test fun anOverlapNeverReachesTheStore() = runTest(dispatcher) {
        val a = store.importRide("A", pts(60, 1_000_000L))!!; val b = store.importRide("B", pts(60, 1_030_000L))!!
        val vm = MergeViewModel(listOf(a.id, b.id), repo, dispatcher)
        assertTrue((vm.ui.value as MergeUi.Ready).plan.block is MergeBlock.Overlap)
        vm.merge()
        assertEquals(2, store.list().size)
    }

    @Test fun aRideDeletedMeanwhileIsGone() = runTest(dispatcher) {
        val a = store.importRide("A", pts(60, 1_000_000L))!!; val b = store.importRide("B", pts(60, 2_000_000L))!!
        store.softDelete(listOf(b.id))
        assertEquals(MergeUi.Gone, MergeViewModel(listOf(a.id, b.id), repo, dispatcher).ui.value)
    }

    @Test fun aRefusalAfterThePreviewIsReportedAndTheButtonComesBack() = runTest(dispatcher) {
        val a = store.importRide("A", pts(60, 1_000_000L))!!; val b = store.importRide("B", pts(60, 2_000_000L))!!
        val vm = MergeViewModel(listOf(a.id, b.id), repo, dispatcher)
        store.setActiveForTest(b.id)
        val events = mutableListOf<MergeEvent>()
        vm.events.onEach { events += it }.launchIn(backgroundScope)
        vm.merge()
        assertEquals(MergeRefusal.Active, (events.single() as MergeEvent.Failed).reason)
        assertTrue(!(vm.ui.value as MergeUi.Ready).merging)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class MergeContentTest {
    @get:Rule val rule = createComposeRule()
    private var merged = 0
    private val a = ride("a", 1_000_000L, 60, "Nandi Hills")
    private val b = ride("b", 1_000_000L + 60_000 + 18 * 60_000, 60, null, saved = true)

    private fun plan(block: MergeBlock? = null) = MergePlan(
        listOf(MergePart(a), MergePart(b)), listOf(MergeGap(18 * 60)), 2_000, 120, 120, true, "Nandi Hills", emptyList(), block,
    )

    private fun show(ui: MergeUi) = rule.setContent { KtmTheme(darkTheme = false) { MergeContent(ui, {}, { merged++ }) } }

    @Test fun showsThePartsTheStopAndTheCombinedStats() {
        show(MergeUi.Ready(plan()))
        rule.onNodeWithText("1 · Nandi Hills").assertIsDisplayed()
        rule.onNodeWithText("2 · Navigated ride").assertIsDisplayed()
        rule.onNodeWithText("18 min stop · not counted as riding").assertIsDisplayed()
        rule.onNodeWithText("Riding time").assertIsDisplayed()
        rule.onNodeWithText("Notes are joined · starred", substring = true).assertIsDisplayed()
        rule.onNode(hasText("Merge rides") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(1, merged)
    }

    @Test fun anUnnamedFirstRideMeansTheMergedRideIsCalledThat() {
        show(MergeUi.Ready(plan().copy(name = null)))
        rule.onNodeWithText("Merged ride").assertIsDisplayed()
    }

    @Test fun anOverlapDisablesMergeAndNamesBothRides() {
        show(MergeUi.Ready(plan(MergeBlock.Overlap(a, ride("c", 1_030_000L, 60, "Office")))))
        rule.onNodeWithText("These rides overlap").assertIsDisplayed()
        rule.onNodeWithText("Nandi Hills and Office", substring = true).assertIsDisplayed()
        rule.onNode(hasText("Merge rides") and hasClickAction()).assertIsNotEnabled()
        rule.onAllNodesWithText("Riding time").assertCountEquals(0)
    }

    @Test fun aRideThatIsGoneSaysSoWithNoMergeButton() {
        show(MergeUi.Gone)
        rule.onNodeWithText("A ride is no longer there").assertIsDisplayed()
        rule.onAllNodes(hasText("Merge rides") and hasClickAction()).assertCountEquals(0)
    }
}
