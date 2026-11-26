package com.navigator.app.ui.feature.navigate.finished

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RidePoint
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val ride = RecordedRide("r1", 0, 3_600_000, 42_000, 3_600, avgSpeedKmh = 50f, avgOverallSpeedKmh = 42f, maxSpeedKmh = 88f)

class FinishResolutionTest {
    @Test fun aSavedRideIsTheRide() {
        assertEquals(FinishUi.Recorded(ride), FinishResolution.resolve(RideFinish.Saved(ride), timedOut = false, recordingEnabled = true))
    }

    @Test fun aDiscardedRideWasTooShort() {
        assertEquals(FinishUi.NoRecording(NoRecordingReason.TooShort), FinishResolution.resolve(RideFinish.Discarded, false, true))
    }

    @Test fun withRecordingOffThereIsNothingToWaitFor() {
        assertEquals(FinishUi.NoRecording(NoRecordingReason.RecordingOff), FinishResolution.resolve(null, timedOut = false, recordingEnabled = false))
    }

    @Test fun nothingYetIsSavingUntilTheWaitIsOver() {
        assertEquals(FinishUi.Saving, FinishResolution.resolve(null, timedOut = false, recordingEnabled = true))
        assertEquals(FinishUi.NoRecording(NoRecordingReason.TooShort), FinishResolution.resolve(null, timedOut = true, recordingEnabled = true))
        assertEquals(6_000L, FinishResolution.TIMEOUT_MS)
    }

    @Test fun aRideThatArrivesAfterTheWaitStillWins() {
        assertEquals(FinishUi.Recorded(ride), FinishResolution.resolve(RideFinish.Saved(ride), timedOut = true, recordingEnabled = false))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripFinishedViewModelTest {
    private val points = listOf(RidePoint(12.0, 77.0, 0, 10f))

    @Test fun savingThenTheRideWithItsTrack() = runTest(UnconfinedTestDispatcher()) {
        val finish = MutableStateFlow<RideFinish?>(null)
        val vm = TripFinishedViewModel(finish, { points }, { true }, scope = backgroundScope)
        vm.begin()
        assertEquals(FinishUi.Saving, vm.state.value.ui)
        finish.value = RideFinish.Saved(ride)
        assertEquals(FinishUi.Recorded(ride), vm.state.value.ui)
        assertEquals(points, vm.state.value.points)
    }

    @Test fun noOutcomeInSixSecondsMeansTooShort() = runTest(UnconfinedTestDispatcher()) {
        val vm = TripFinishedViewModel(MutableStateFlow(null), { points }, { true }, scope = backgroundScope)
        vm.begin()
        advanceTimeBy(5_900)
        assertEquals(FinishUi.Saving, vm.state.value.ui)
        advanceTimeBy(200)
        assertEquals(FinishUi.NoRecording(NoRecordingReason.TooShort), vm.state.value.ui)
        assertNull(vm.state.value.points)
    }

    @Test fun recordingOffIsSaidAtOnce() = runTest(UnconfinedTestDispatcher()) {
        val vm = TripFinishedViewModel(MutableStateFlow(null), { points }, { false }, scope = backgroundScope)
        vm.begin()
        assertEquals(FinishUi.NoRecording(NoRecordingReason.RecordingOff), vm.state.value.ui)
    }

    @Test fun theRecordingSettingIsReadOnceSoTurningItOnFromTheSummaryDoesNotChangeThisBanner() = runTest(UnconfinedTestDispatcher()) {
        var enabled = false
        val finish = MutableStateFlow<RideFinish?>(null)
        val vm = TripFinishedViewModel(finish, { points }, { enabled }, scope = backgroundScope)
        vm.begin()
        assertEquals(FinishUi.NoRecording(NoRecordingReason.RecordingOff), vm.state.value.ui)
        enabled = true
        finish.value = null // anything that re-runs the resolution
        advanceTimeBy(7_000)
        assertEquals("still the banner of the trip that was not recorded", FinishUi.NoRecording(NoRecordingReason.RecordingOff), vm.state.value.ui)
    }

    @Test fun beginAgainWhileRunningKeepsWhatIsShown() = runTest(UnconfinedTestDispatcher()) {
        val finish = MutableStateFlow<RideFinish?>(RideFinish.Saved(ride))
        val vm = TripFinishedViewModel(finish, { points }, { true }, scope = backgroundScope)
        vm.begin()
        vm.begin() // a rotation re-enters the screen
        assertEquals(FinishUi.Recorded(ride), vm.state.value.ui)
        assertEquals(points, vm.state.value.points)
    }

    @Test fun anUnreadableTrackStillShowsTheStats() = runTest(UnconfinedTestDispatcher()) {
        val vm = TripFinishedViewModel(MutableStateFlow(RideFinish.Saved(ride)), { error("corrupt") }, { true }, scope = backgroundScope)
        vm.begin()
        assertEquals(FinishUi.Recorded(ride), vm.state.value.ui)
        assertNull(vm.state.value.points)
    }

    @Test fun resetForgetsTheRideAndStopsWaiting() = runTest(UnconfinedTestDispatcher()) {
        val finish = MutableStateFlow<RideFinish?>(RideFinish.Saved(ride))
        val vm = TripFinishedViewModel(finish, { points }, { true }, scope = backgroundScope)
        vm.begin()
        vm.reset()
        assertEquals(FinishState(), vm.state.value)
        finish.value = RideFinish.Discarded
        assertEquals("a reset screen does not react", FinishUi.Saving, vm.state.value.ui)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class TripFinishedContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val actions = TripFinishedActions(
        { log += "done" }, { log += "replay" }, { log += "save" }, { log += "back" }, { log += "share" }, { log += "on" }, { log += "change" },
    )

    private fun model(ui: FinishUi, placeSaved: Boolean = false, avg: AvgSpeedMode = AvgSpeedMode.MOVING) =
        TripFinishedModel("Toit Brewpub", ui, 600, avg, placeSaved, canSavePlace = true, canRideBack = true, minDistanceM = 400, minDurationS = 60)

    private fun show(m: TripFinishedModel, landscape: Boolean = false) =
        rule.setContent { KtmTheme(darkTheme = false) { TripFinishedContent(m, actions, landscape) } }

    @Test fun aRecordedRideShowsFourStatsAndEveryFollowUp() {
        show(model(FinishUi.Recorded(ride)))
        rule.onNodeWithText("Toit Brewpub").assertIsDisplayed()
        rule.onNodeWithText("Distance").assertIsDisplayed()
        rule.onNodeWithText("Average (moving)").assertIsDisplayed()
        rule.onNodeWithText("Top speed").assertIsDisplayed()
        rule.onNodeWithText("Save place").performClick()
        rule.onNodeWithText("Ride back").performClick()
        rule.onNodeWithText("Share GPX").performClick()
        rule.onNodeWithText("Replay").performClick()
        rule.onNodeWithText("Done").performClick()
        assertEquals(listOf("save", "back", "share", "replay", "done"), log)
    }

    @Test fun theAverageSaysWhichOneItIs() {
        show(model(FinishUi.Recorded(ride), avg = AvgSpeedMode.OVERALL))
        rule.onNodeWithText("Average (overall)").assertIsDisplayed()
    }

    @Test fun aSavedPlaceReadsSavedAndCannotBeSavedTwice() {
        show(model(FinishUi.Recorded(ride), placeSaved = true))
        rule.onNodeWithText("Saved").assertIsNotEnabled()
    }

    @Test fun whileSavingTimeIsKnownAndDoneWorks() {
        show(model(FinishUi.Saving))
        rule.onNodeWithText("10").assertIsDisplayed()
        rule.onNodeWithText("min").assertIsDisplayed()
        rule.onNodeWithText("Saving ride…").assertIsDisplayed()
        rule.onNodeWithText("Done").assertIsEnabled()
        rule.onAllNodesWithText("Distance").assertCountEquals(0)
        rule.onNodeWithText("Done").performClick()
        assertEquals(listOf("done"), log)
    }

    @Test fun recordingOffOffersToTurnItOnAndLeavesOutShareAndReplay() {
        show(model(FinishUi.NoRecording(NoRecordingReason.RecordingOff)))
        rule.onNodeWithText("Turn on").performClick()
        assertEquals(listOf("on"), log)
        rule.onAllNodesWithText("Share GPX").assertCountEquals(0)
        rule.onAllNodesWithText("Replay").assertCountEquals(0)
        rule.onNodeWithText("Ride back").assertIsDisplayed()
    }

    @Test fun afterTurningRecordingOnTheButtonGivesWayToAPromise() {
        show(model(FinishUi.NoRecording(NoRecordingReason.RecordingOff)).copy(recordingNowOn = true))
        rule.onNodeWithText("Recording is on now. Your next trip will be kept.").assertIsDisplayed()
        rule.onAllNodesWithText("Turn on").assertCountEquals(0)
    }

    @Test fun tooShortNamesTheLimitsAndOffersToChangeThem() {
        show(model(FinishUi.NoRecording(NoRecordingReason.TooShort)))
        rule.onNodeWithText("Shorter than your keep limits (400 m / 1 min), so this ride wasn't kept.").assertIsDisplayed()
        rule.onNodeWithText("Change").performClick()
        assertEquals(listOf("change"), log)
    }

    @Test fun landscapeKeepsTheSameThingsReachable() {
        show(model(FinishUi.Recorded(ride)), landscape = true)
        rule.onNodeWithText("Share GPX").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsDisplayed()
        rule.onNodeWithText("Replay").assertIsDisplayed()
        rule.onNodeWithText("1 h 00 min").assertIsDisplayed() // the time sits in the title row
        rule.onNodeWithText("Done").assertHeightIsAtLeast(56.dp)
        assertTrue(log.isEmpty())
    }
}
