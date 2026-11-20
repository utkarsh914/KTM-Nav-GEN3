package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape
import com.navigator.app.ui.shell.ChipTone
import com.navigator.app.ui.shell.RidingChipMessage
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RidingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class RidingUiTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun riding(night: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { KtmTheme(darkTheme = true) { RidingTheme(night = night, content = content) } }

    private fun active(footer: ManeuverFooter = ManeuverFooter.None, rerouting: Boolean = false, exit: String? = null) =
        ManeuverUi.Active(TurnIcon.QUITE_RIGHT, "450 m", "Turn right onto", "Jalaun Rd", exit, footer, rerouting)

    @Test fun theCardShowsDistanceInstructionAndTheRoadOnItsOwnLine() {
        riding { ManeuverCard(active(ManeuverFooter.Then(TurnIcon.QUITE_LEFT), exit = "2")) }
        rule.onNodeWithText("450 m").assertIsDisplayed()
        rule.onNodeWithText("Turn right onto").assertIsDisplayed()
        rule.onNodeWithText("Jalaun Rd").assertIsDisplayed()
        rule.onNodeWithText("Exit 2").assertIsDisplayed()
        rule.onNodeWithText("Then").assertIsDisplayed()
    }

    @Test fun lanesReplaceThenAndMarkTheRecommendedOnes() {
        val lanes = listOf(LaneInfo(listOf(LaneShape.STRAIGHT), false), LaneInfo(listOf(LaneShape.RIGHT), true))
        riding { ManeuverCard(active(ManeuverFooter.Lanes(lanes))) }
        rule.onAllNodesWithText("Then").assertCountEquals(0)
        rule.onNodeWithContentDescription("Lanes").assertIsDisplayed()
        rule.onAllNodes(hasContentDescription("Recommended lane")).assertCountEquals(1)
    }

    @Test fun reroutingSaysSoAndDropsTheDistance() {
        riding { ManeuverCard(active(rerouting = true)) }
        rule.onNodeWithText("Rerouting").assertIsDisplayed()
        rule.onAllNodesWithText("450 m").assertCountEquals(0)
        rule.onNodeWithText("Jalaun Rd").assertIsDisplayed()
    }

    @Test fun beforeTheFirstInstructionItSaysStarting() {
        riding { ManeuverCard(ManeuverUi.Starting) }
        rule.onNodeWithText("Starting navigation").assertIsDisplayed()
    }

    private fun bar(speed: String = "42", over: Boolean = false, starting: Boolean = false) = TripBarUi(
        speed = speed, speedKnown = speed != "–", overspeed = over, alert = if (over) 80 else null, timeLeft = if (starting) null else TimeLeft(0, 25),
        distance = "12 km", arrival = "14:32", progress = 0.5f, destination = "Toit", starting = starting,
    )

    @Test fun theBarShowsSpeedTimeDistanceAndArrivalAndOpensTheSheet() {
        riding { TripBar(bar(), onOpen = { log += "open" }) }
        rule.onNodeWithText("42").assertIsDisplayed()
        rule.onNodeWithText("25 min").assertIsDisplayed()
        rule.onNodeWithText("12 km · 14:32").assertIsDisplayed()
        rule.onNodeWithText("km/h").assertIsDisplayed()
        rule.onNodeWithText("25 min").performClick()
        assertEquals(listOf("open"), log)
    }

    @Test fun overspeedShowsTheAlertValueAndNoFixShowsADash() {
        riding { TripBar(bar(speed = "85", over = true), onOpen = {}) }
        rule.onNodeWithText("Limit 80").assertIsDisplayed()
        rule.onNodeWithContentDescription("85 km/h, over your limit of 80").assertIsDisplayed()
    }

    @Test fun startingShowsTheDestinationAndADashForSpeed() {
        riding { TripBar(bar(speed = "–", starting = true), onOpen = {}) }
        rule.onNodeWithText("Toit").assertIsDisplayed()
        rule.onNodeWithText("–").assertIsDisplayed()
        rule.onNodeWithContentDescription("No GPS speed yet").assertIsDisplayed()
    }

    @Test fun longTimesReadAsHoursAndMinutes() {
        riding { TripBar(bar().copy(timeLeft = TimeLeft(2, 5)), onOpen = {}) }
        rule.onNodeWithText("2 h 05 min").assertIsDisplayed()
    }

    @Test fun theMapButtonIsOverviewUntilTheMapMovesThenRecenter() {
        var recenter = androidx.compose.runtime.mutableStateOf(false)
        riding { RidingMapButton(recenter.value, bearing = 40f, onTap = { log += "tap" }, onNorthUp = { log += "north" }) }
        rule.onNodeWithContentDescription("Overview").performClick()
        rule.onAllNodes(hasContentDescription("North up")).assertCountEquals(0)
        recenter.value = true
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Recenter").performClick()
        rule.onNodeWithContentDescription("North up").performClick()
        assertEquals(listOf("tap", "tap", "north"), log)
    }

    @Test fun chipsShowTheirTextAndATappableOneActs() {
        val msg = RidingChipMessage(1, "Dash disconnected", ChipTone.Bad) { log += "chip" }
        riding { RidingChipHost(ChipSource.Timed(msg)) }
        rule.onNodeWithText("Dash disconnected").performClick()
        assertEquals(listOf("chip"), log)
    }

    @Test fun theOfflineChipChangesItsWordsAfterTheRerouteWait() {
        val state = androidx.compose.runtime.mutableStateOf<ChipSource?>(ChipSource.Offline(false))
        riding { RidingChipHost(state.value) }
        rule.onNodeWithText("Offline · guidance continues").assertIsDisplayed()
        state.value = ChipSource.Offline(true)
        rule.waitForIdle()
        rule.onNodeWithText("Offline · reroutes when back online").assertIsDisplayed()
    }

    private fun sheetActions() = TripSheetActions(
        onAddStop = { log += "stop" }, onOverview = { log += "overview" }, onMinimise = { log += "min" },
        onBeeps = { log += "beeps$it" }, onMessages = { log += "msg$it" }, onDash = { log += "dash" }, onRemoveNextStop = { log += "removeNext" },
        onEnd = { log += "end" }, onDismiss = { log += "dismiss" },
    )

    @Test fun theTripSheetHasTheTilesTogglesDashAndEnd() {
        riding { TripSheetForTest(beeps = true, messages = false, dashStatus = "Connected", canAddStop = true, nextStop = null, actions = sheetActions()) }
        rule.onNodeWithText("Add stop").performClick()
        rule.onNodeWithText("Overview").performClick()
        rule.onNodeWithText("Minimise").performClick()
        rule.onNodeWithText("Turn beeps").performClick()
        rule.onNodeWithText("Phone messages on the dash").performClick()
        rule.onNodeWithText("Connected").performClick()
        rule.onNodeWithText("End trip").performClick()
        assertEquals(listOf("stop", "overview", "min", "beepsfalse", "msgtrue", "dash", "end"), log)
    }

    @Test fun addStopIsDisabledWhenNoTripCanTakeOne() {
        riding { TripSheetForTest(beeps = true, messages = true, dashStatus = null, canAddStop = false, nextStop = null, actions = sheetActions()) }
        rule.onNodeWithText("Add stop").assertIsNotEnabled()
    }

    @Test fun endingAsksFirstAndKeepingIsOffered() {
        riding { EndTripDialog(onEnd = { log += "end" }, onKeep = { log += "keep" }) }
        rule.onNodeWithText("End this trip?").assertIsDisplayed()
        rule.onNodeWithText("Keep riding").performClick()
        rule.onNodeWithText("End trip").performClick()
        assertEquals(listOf("keep", "end"), log)
    }
}
