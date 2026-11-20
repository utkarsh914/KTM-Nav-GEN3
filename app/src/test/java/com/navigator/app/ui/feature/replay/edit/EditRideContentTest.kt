package com.navigator.app.ui.feature.replay.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class EditRideContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun show(ui: EditUi) = rule.setContent {
        KtmTheme(darkTheme = false) {
            EditRideContent(
                ui, EditRideActions({ log += "back" }, { log += "name:$it" }, { log += "notes:$it" }, { log += "trim" }, { log += "star:$it" }, { log += "delete" }),
            )
        }
    }

    @Test fun theFormHasEverythingAndNoSaveButton() {
        show(EditUi.Ready("Nandi", "windy", false))
        rule.onNodeWithText("Nandi").assertIsDisplayed()
        rule.onNodeWithText("Trim ride").assertIsDisplayed()
        rule.onNodeWithText("Keep forever").assertIsDisplayed()
        rule.onNodeWithText("Changes are saved as you type.").assertIsDisplayed()
        rule.onNode(hasText("Delete ride") and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Test fun rowsReportTheirActions() {
        show(EditUi.Ready("Nandi", "", false))
        rule.onNodeWithText("Trim ride").performClick()
        rule.onNodeWithText("Keep forever").performClick()
        rule.onNode(hasText("Delete ride") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("trim", "star:true", "delete"), log)
    }

    @Test fun typingIsReported() {
        show(EditUi.Ready("", "", false))
        rule.onNodeWithText("Name").performTextInput("Hi")
        assertEquals(listOf("name:Hi"), log)
    }

    @Test fun aMissingRideSaysSo() {
        show(EditUi.Gone)
        rule.onNodeWithText("Ride not found").assertIsDisplayed()
    }
}
