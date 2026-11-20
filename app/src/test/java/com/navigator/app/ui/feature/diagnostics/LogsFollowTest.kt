package com.navigator.app.ui.feature.diagnostics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogsFollowTest {
    @get:Rule val rule = createComposeRule()

    private fun entries(n: Int) = (0 until n).map { LogEntry("12:00:%02d".format(it % 60), "BLE", "line $it", LogLevel.Info, key = "k$it") }

    private val noActions = LogsActions({}, {}, {}, {})

    @Test fun followingKeepsTheNewestLineInViewAsLinesArrive() {
        var ui by mutableStateOf(LogsUi(entries = entries(60), tags = listOf("BLE")))
        rule.setContent { KtmTheme(darkTheme = true) { LogsContent(ui, noActions) } }
        rule.waitForIdle()
        rule.onNodeWithText("line 59").assertIsDisplayed()
        rule.onNodeWithText("line 0").assertDoesNotExist()
        ui = LogsUi(entries = entries(70), tags = listOf("BLE"))
        rule.waitForIdle()
        rule.onNodeWithText("line 69").assertIsDisplayed()
    }

    @Test fun pausingStopsTheAutoScrollUntilFollowingIsTurnedBackOn() {
        var ui by mutableStateOf(LogsUi(entries = entries(60), tags = listOf("BLE")))
        rule.setContent { KtmTheme(darkTheme = true) { LogsContent(ui, noActions) } }
        rule.waitForIdle()
        rule.onNodeWithText("Following live").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Paused").assertIsDisplayed()
        ui = LogsUi(entries = entries(80), tags = listOf("BLE"))
        rule.waitForIdle()
        rule.onNodeWithText("line 79").assertDoesNotExist()
        rule.onNodeWithText("Paused").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("line 79").assertIsDisplayed()
    }

    @Test fun theAutoScrollDoesNotPauseItselfAndADragAwayFromTheEndDoes() {
        val ui = LogsUi(entries = entries(60), tags = listOf("BLE"))
        rule.setContent { KtmTheme(darkTheme = true) { LogsContent(ui, noActions) } }
        rule.waitForIdle()
        rule.onNodeWithText("Following live").assertIsDisplayed()
        rule.onNodeWithText("line 59").performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onNodeWithText("Paused").assertIsDisplayed()
    }
}
