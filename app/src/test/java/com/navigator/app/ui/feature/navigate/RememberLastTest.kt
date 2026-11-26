package com.navigator.app.ui.feature.navigate

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RememberLastTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aLeavingStageKeepsWhatItShowedAfterTheStateIsCleared() {
        var place by mutableStateOf<String?>("Toit")
        rule.setContent { Text(rememberLast(place) ?: "empty") }
        rule.onNodeWithText("Toit").assertIsDisplayed()
        place = null
        rule.waitForIdle()
        rule.onNodeWithText("Toit").assertIsDisplayed()
        place = "Hindustan Petroleum"
        rule.waitForIdle()
        rule.onNodeWithText("Hindustan Petroleum").assertIsDisplayed()
    }

    @Test fun nothingShownYetStaysNull() {
        var seen: String? = "unset"
        rule.setContent { seen = rememberLast<String>(null) }
        rule.waitForIdle()
        assertEquals(null, seen)
    }
}
