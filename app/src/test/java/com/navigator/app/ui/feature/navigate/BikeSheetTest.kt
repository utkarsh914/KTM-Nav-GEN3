package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.ui.shell.LocalOpenBikeSettings
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class BikeSheetTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun show(status: BikeStatus, tripRunning: Boolean = false, lastSeen: Long? = null, settings: (() -> Unit)? = { log += "settings" }) = rule.setContent {
        KtmTheme(darkTheme = true) {
            CompositionLocalProvider(LocalOpenBikeSettings provides settings) {
                BikeSheet(
                    state = NavigateUiState(bikeStatus = status, bike = BikePillMapper.map(status)),
                    dashTime = "6:12",
                    onBeeps = {}, onMessages = {}, onSendTest = { log += "test" }, onRePair = { log += "repair" }, onRetryNow = { log += "retry" },
                    onDismiss = { log += "dismiss" }, tripRunning = tripRunning, lastSeenMs = lastSeen,
                )
            }
        }
    }

    @Test fun connectedShowsTheDashTheTogglesTheTestAndTheSettingsRow() {
        show(BikeStatus.Connected("KTM 7F21"))
        rule.onNodeWithText("KTM 7F21").assertIsDisplayed()
        rule.onNodeWithText("On your dash now").assertIsDisplayed()
        rule.onNodeWithText("Turn beeps").assertIsDisplayed()
        rule.onNodeWithText("Trouble?").assertIsDisplayed()
        rule.onNodeWithText("Send a test").performClick()
        rule.onNodeWithText("Bike & dash settings").performClick()
        assertEquals(listOf("test", "dismiss", "settings"), log)
    }

    @Test fun theTestWaitsWhileATripIsGuiding() {
        show(BikeStatus.Connected("KTM 7F21"), tripRunning = true)
        rule.onNodeWithText("Send a test").assertIsNotEnabled()
    }

    @Test fun reconnectingListsWhatToCheckAndKeepsRePairAwayFromTheToggles() {
        show(BikeStatus.Reconnecting("KTM 7F21", 3))
        rule.onNodeWithText("Reconnecting · attempt 3").assertIsDisplayed()
        rule.onNodeWithText("Is the ignition on?").assertIsDisplayed()
        rule.onAllNodesWithText("Turn beeps").assertCountEquals(0)
        rule.onNodeWithText("Re-pair…").performClick()
        assertEquals("a confirmation comes first", emptyList<String>(), log)
    }

    @Test fun retryNowSitsBesideRePairAndRestsAfterATap() {
        show(BikeStatus.Reconnecting("KTM 7F21", 3))
        rule.onNodeWithText("Re-pair…").assertIsDisplayed()
        rule.onNodeWithText("Retry now").performClick()
        assertEquals(listOf("retry"), log)
        rule.onNodeWithText("Trying…").assertIsNotEnabled()
    }

    @Test fun retryNowIsOfferedOnlyWhereARetryCanHelp() {
        show(BikeStatus.BluetoothOff)
        rule.onAllNodesWithText("Retry now").assertCountEquals(0)
    }

    @Test fun whenTheKeysLookLostRePairLeadsButRetryIsStillThere() {
        show(BikeStatus.NeedsRepair("KTM 7F21"))
        rule.onNodeWithText("Retry now").assertIsDisplayed()
        rule.onNodeWithText("Re-pair…").assertIsDisplayed()
    }

    @Test fun lastSeenShowsUnderThePreviewOnlyWhileTheBikeIsAway() {
        show(BikeStatus.Disconnected("KTM 7F21"), lastSeen = System.currentTimeMillis() - 60_000)
        rule.onNodeWithText("Last seen", substring = true).assertIsDisplayed()
    }

    @Test fun noLastSeenWhenConnectedOrNeverSeen() {
        show(BikeStatus.Connected("KTM 7F21"), lastSeen = System.currentTimeMillis())
        rule.onAllNodesWithText("Last seen", substring = true).assertCountEquals(0)
    }

    @Test fun withoutAShellHookThereIsNoSettingsRow() {
        show(BikeStatus.Connected("KTM 7F21"), settings = null)
        rule.onAllNodesWithText("Bike & dash settings").assertCountEquals(0)
    }
}

class LastSeenTest {
    private val zone = java.time.ZoneId.of("UTC")
    private val now = java.time.ZonedDateTime.of(2026, 11, 21, 20, 0, 0, 0, zone).toInstant().toEpochMilli()

    @org.junit.After fun reset() { com.navigator.app.nav.ktm.ClockFormat.use24h = { true } }

    @Test fun todayIsJustTheTime() {
        val seen = java.time.ZonedDateTime.of(2026, 11, 21, 18, 4, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("18:04", LastSeen.format(seen, now, zone))
        com.navigator.app.nav.ktm.ClockFormat.use24h = { false }
        assertEquals(true, LastSeen.format(seen, now, zone).contains("6:04"))
    }

    @Test fun anEarlierDayCarriesTheDate() {
        val seen = java.time.ZonedDateTime.of(2026, 11, 12, 18, 4, 0, 0, zone).toInstant().toEpochMilli()
        val text = LastSeen.format(seen, now, zone)
        assertEquals(true, text.contains("18:04") && text.contains("12"))
    }
}
