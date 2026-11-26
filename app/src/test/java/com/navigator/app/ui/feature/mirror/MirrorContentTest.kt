package com.navigator.app.ui.feature.mirror

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.data.mirror.Freshness
import com.navigator.app.data.mirror.MirrorSetupKind
import com.navigator.app.data.mirror.MirrorSetupRow
import com.navigator.app.data.mirror.MirrorStatus
import com.navigator.app.data.mirror.MirrorTurn
import com.navigator.app.ui.components.PillTone
import com.navigator.app.ui.feature.navigate.BikePillUi
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.components.BikeStatusDot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class MirrorContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val bike = MirrorBike(BikePillUi(0, null, PillTone.Connected, false, 0), "Your bike", "Connected")
    private val actions = MirrorActions(
        { log += "bike" }, { log += "access" }, { log += "reconnect" }, { log += "battery" },
        { log += "sending:$it" }, { log += "maps" }, { log += "record" },
    )
    private val turn = MirrorTurn(TurnIcon.QUITE_RIGHT, "400 m", "Turn right", "MG Road", "17:42", "12 km")

    private fun rows(access: Boolean = true, listener: Boolean = true, battery: Boolean = true) = listOf(
        MirrorSetupRow(MirrorSetupKind.Access, access, true),
        MirrorSetupRow(MirrorSetupKind.Listener, access && listener, true),
        MirrorSetupRow(MirrorSetupKind.Battery, battery, false),
    )

    private fun show(status: MirrorStatus, landscape: Boolean = false) =
        rule.setContent { KtmTheme(darkTheme = false) { MirrorContent(status, bike, "10:05", landscape, actions) } }

    @Test fun missingAccessShowsTheChecklistWithItsReasonAndFix() {
        show(MirrorStatus.Setup(rows(access = false)))
        rule.onNodeWithText("Notification access").assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        assertEquals(listOf("access"), log)
        rule.onNodeWithText("Set up mirror mode").assertIsDisplayed()
        rule.onNodeWithText("Open Google Maps").assertIsDisplayed() // MR1: Maps stays one tap away during setup
    }

    @Test fun theListenerRowWaitsForAccess() {
        show(MirrorStatus.Setup(rows(access = false)))
        rule.onAllNodesWithText("Reconnect").assertCountEquals(0)
        rule.onAllNodesWithText("Notification listener").assertCountEquals(0)
    }

    @Test fun recordARideGivesWayWhileARecordingRuns() {
        rule.setContent { KtmTheme(darkTheme = false) { MirrorContent(MirrorStatus.Ready(rows()), bike, "10:05", false, actions, recording = true) } }
        rule.onAllNodesWithText("Record a ride").assertCountEquals(0)
        rule.onNodeWithText("Open Google Maps").assertIsDisplayed()
    }

    @Test fun aStoppedListenerHasAReconnectButton() {
        show(MirrorStatus.Setup(rows(listener = false)))
        rule.onNodeWithText("Reconnect").performClick()
        assertEquals(listOf("reconnect"), log)
    }

    @Test fun readyIsOneGreenLineWithRecordAndOpenMaps() {
        show(MirrorStatus.Ready(rows(battery = false)))
        rule.onNodeWithText("Mirror mode is set up").assertIsDisplayed()
        rule.onAllNodesWithText("Notification access").assertCountEquals(0)
        rule.onNodeWithText("Mirror mode is set up").performClick() // the details open
        rule.onNodeWithText("Keep it running").assertIsDisplayed()
        rule.onNodeWithText("Record a ride").performClick()
        rule.onNodeWithText("Open Google Maps").performClick()
        assertEquals(listOf("record", "maps"), log)
    }

    @Test fun liveShowsTheDashPreviewFreshnessAndAPauseSwitchThatIsOn() {
        show(MirrorStatus.Live(turn, Freshness.Seconds(2), paused = false, stale = false))
        rule.onNodeWithText("Live · 2 s ago").assertIsDisplayed()
        rule.onNodeWithText("400 m").assertIsDisplayed()
        rule.onNodeWithText("Send turns to the dash").assertIsDisplayed()
        rule.onNode(isToggleable()).assertIsOn()
        rule.onNode(isToggleable()).performClick()
        assertEquals(listOf("sending:false"), log)
        rule.onAllNodesWithText("Record a ride").assertCountEquals(0)
    }

    @Test fun pausingSendsOffAndTheStateIsSaid() {
        show(MirrorStatus.Live(turn, Freshness.JustNow, paused = true, stale = false))
        rule.onNodeWithText("Paused").assertIsDisplayed()
        rule.onNodeWithText("Paused: the dash isn't being updated. Turn on to resume.").assertIsDisplayed()
    }

    @Test fun aPausedScreenWithNothingYetStillOffersTheSwitch() {
        show(MirrorStatus.Ready(rows(), paused = true))
        rule.onNodeWithText("Send turns to the dash").assertIsDisplayed()
        rule.onNodeWithText("Paused: the dash isn't being updated. Turn on to resume.").assertIsDisplayed()
    }

    @Test fun staleUpdatesSayHowLongAndWhatToCheck() {
        show(MirrorStatus.Live(turn, Freshness.Seconds(45), paused = false, stale = true))
        rule.onNodeWithText("Google Maps went quiet").assertIsDisplayed()
        rule.onNodeWithText("No update · 45 s").assertIsDisplayed()
        rule.onNodeWithText("On your dash now").assertIsDisplayed()
        rule.onNodeWithText("400 m").assertIsDisplayed() // the last turn stays
    }

    @Config(sdk = [36], qualifiers = "w874dp-h402dp")
    @Test fun landscapeIsABigTurnWithTheControlsBesideIt() {
        show(MirrorStatus.Live(turn, Freshness.Seconds(2), paused = false, stale = false), landscape = true)
        rule.onNodeWithText("400 m").assertIsDisplayed()
        rule.onNodeWithText("MG Road").assertIsDisplayed()
        rule.onNodeWithText("Open Google Maps").performClick()
        assertEquals(listOf("maps"), log)
    }

    @Config(sdk = [36], qualifiers = "w874dp-h402dp")
    @Test fun theMountedPanelKeepsTheEtaAndDistanceLeftAndSaysWhatTheDashShowsOnce() {
        show(MirrorStatus.Live(turn, Freshness.Seconds(2), paused = false, stale = false), landscape = true)
        rule.onAllNodesWithText("On your dash now").assertCountEquals(1)
        rule.onNodeWithText("17:42").assertIsDisplayed()
        rule.onNodeWithText("12 km").assertIsDisplayed()
    }

    @Config(sdk = [36], qualifiers = "w874dp-h402dp")
    @Test fun theMountedSendToDashCardIsOneShortLineWithoutAnIcon() {
        show(MirrorStatus.Live(turn, Freshness.Seconds(2), paused = false, stale = false), landscape = true)
        rule.onNodeWithText("Send to dash").assertIsDisplayed()
        rule.onNodeWithText("On").assertIsDisplayed()
        rule.onAllNodesWithText("Send turns to the dash").assertCountEquals(0)
    }

    @Test fun theMountedBikeDotIsAFiftySixDpTarget() {
        rule.setContent { KtmTheme(darkTheme = false) { BikeStatusDot("Your bike", PillTone.Connected, {}) } }
        rule.onNodeWithContentDescription("Your bike").assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
    }
}
