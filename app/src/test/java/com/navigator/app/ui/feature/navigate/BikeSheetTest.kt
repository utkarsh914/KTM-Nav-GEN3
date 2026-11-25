package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.ui.shell.LocalOpenBikeSettings
import com.navigator.app.ui.theme.KtmTheme
import java.time.ZoneId
import java.time.ZonedDateTime
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

    private fun show(status: BikeStatus, tripRunning: Boolean = false, lastSeen: Long? = null, retryAtMs: Long? = null, settings: (() -> Unit)? = { log += "settings" }) = rule.setContent {
        KtmTheme(darkTheme = true) {
            CompositionLocalProvider(LocalOpenBikeSettings provides settings) {
                BikeSheet(
                    state = NavigateUiState(bikeStatus = status, bike = BikePillMapper.map(status)),
                    dashTime = "6:12",
                    onBeeps = {}, onMessages = {}, onSendTest = { log += "test" }, onRePair = { log += "repair" }, onRetryNow = { log += "retry" },
                    onDismiss = { log += "dismiss" }, tripRunning = tripRunning, lastSeenMs = lastSeen, retryAtMs = retryAtMs,
                )
            }
        }
    }

    @Test fun connectedShowsTheDashTheTogglesTheTestAndTheSettingsRow() {
        show(BikeStatus.Connected("KTM 7F21"))
        rule.onNodeWithText("KTM 7F21").assertIsDisplayed()
        rule.onNodeWithText("On your dash now").assertIsDisplayed()
        rule.onNodeWithText("Beeps").assertIsDisplayed()
        rule.onNodeWithText("Trouble?").assertIsDisplayed()
        rule.onNodeWithText("Send a test").performClick()
        rule.onNodeWithText("Bike & dash settings").performClick()
        assertEquals(listOf("test", "dismiss", "settings"), log)
    }

    @Test fun theTestWaitsWhileATripIsGuiding() {
        show(BikeStatus.Connected("KTM 7F21"), tripRunning = true)
        rule.onNodeWithText("Send a test").assertIsNotEnabled()
    }

    @Test fun duringTheDashCooldownRetryCountsDownAndIsDisabled() {
        show(BikeStatus.Reconnecting("KTM 7F21", 2), retryAtMs = System.currentTimeMillis() + 12_500)
        rule.onNodeWithText("Retry in 0:13").assertIsNotEnabled()
        rule.onAllNodesWithText("Retry now").assertCountEquals(0)
    }

    @Test fun reconnectingWithoutAnAttemptNumberSaysJustReconnecting() {
        assertEquals(R.string.bike_sheet_reconnecting_plain, BikeSheetMapper.status(BikeStatus.Reconnecting("KTM", 0)).title)
        assertEquals(R.string.bike_sheet_reconnecting, BikeSheetMapper.status(BikeStatus.Reconnecting("KTM", 2)).title)
    }

    @Test fun theCountdownIsWholeSecondsRoundedUpAndEndsAtZero() {
        assertEquals(13, RetryCountdown.secondsLeft(12_500, 0))
        assertEquals(1, RetryCountdown.secondsLeft(1, 0))
        assertEquals(null, RetryCountdown.secondsLeft(5_000, 5_000))
        assertEquals(null, RetryCountdown.secondsLeft(null, 5_000))
        assertEquals("1:05", RetryCountdown.format(65))
        assertEquals("0:09", RetryCountdown.format(9))
    }

    @Test fun reconnectingListsWhatToCheckAndKeepsRePairAwayFromTheToggles() {
        show(BikeStatus.Reconnecting("KTM 7F21", 3))
        rule.onNodeWithText("Reconnecting · attempt 3").assertIsDisplayed()
        rule.onNodeWithText("Is the ignition on?").assertIsDisplayed()
        rule.onNodeWithText("Yes").assertIsDisplayed()
        rule.onAllNodesWithText("Beeps").assertCountEquals(0)
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
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 11, 21, 20, 0, 0, 0, zone).toInstant().toEpochMilli()

    @org.junit.After fun reset() { ClockFormat.use24h = { true } }

    @Test fun todayIsJustTheTime() {
        val seen = ZonedDateTime.of(2026, 11, 21, 18, 4, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("18:04", LastSeen.format(seen, now, zone))
        ClockFormat.use24h = { false }
        assertEquals(true, LastSeen.format(seen, now, zone).contains("6:04"))
    }

    @Test fun anEarlierDayCarriesTheDate() {
        val seen = ZonedDateTime.of(2026, 11, 12, 18, 4, 0, 0, zone).toInstant().toEpochMilli()
        val text = LastSeen.format(seen, now, zone)
        assertEquals(true, text.contains("18:04") && text.contains("12"))
    }
}
