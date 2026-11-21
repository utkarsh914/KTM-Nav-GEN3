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

    private fun show(status: BikeStatus, tripRunning: Boolean = false, settings: (() -> Unit)? = { log += "settings" }) = rule.setContent {
        KtmTheme(darkTheme = true) {
            CompositionLocalProvider(LocalOpenBikeSettings provides settings) {
                BikeSheet(
                    state = NavigateUiState(bikeStatus = status, bike = BikePillMapper.map(status)),
                    dashTime = "6:12",
                    onBeeps = {}, onMessages = {}, onSendTest = { log += "test" }, onRePair = { log += "repair" },
                    onDismiss = { log += "dismiss" }, tripRunning = tripRunning,
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

    @Test fun withoutAShellHookThereIsNoSettingsRow() {
        show(BikeStatus.Connected("KTM 7F21"), settings = null)
        rule.onAllNodesWithText("Bike & dash settings").assertCountEquals(0)
    }
}
