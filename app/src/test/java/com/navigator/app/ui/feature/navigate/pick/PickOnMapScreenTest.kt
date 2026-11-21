package com.navigator.app.ui.feature.navigate.pick

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class PickOnMapScreenTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val here = PickOnMapState(center = MapPoint(12.9, 77.6), street = "12th Main Rd")

    @Test fun aDroppedPinAsksToMoveTheMapNamesTheStreetAndUsesThePoint() {
        rule.setContent { KtmTheme(darkTheme = true) { PickOnMapScreen(here, onBack = { log += "back" }, onUse = { log += "use" }) } }
        rule.onNodeWithText("Move the map to place the pin").assertIsDisplayed()
        rule.onNodeWithText("Dropped pin").assertIsDisplayed()
        rule.onNodeWithText("Near 12th Main Rd").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Use this point").performClick()
        assertEquals(listOf("back", "use"), log)
    }

    @Test fun aStopShowsItsNumberAndWhereItGoes() {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                PickOnMapScreen(
                    here, onBack = {}, onUse = { log += "use" }, hint = "Move the map to place the stop", title = "New stop",
                    stopNumber = 3, subtitle = "Goes in before Toit. You can reorder it in the stop list.", useLabel = "Add stop here",
                )
            }
        }
        rule.onNodeWithText("3").assertIsDisplayed()
        rule.onNodeWithText("New stop").assertIsDisplayed()
        rule.onNodeWithText("Goes in before Toit. You can reorder it in the stop list.").assertIsDisplayed()
        rule.onNodeWithText("Add stop here").performClick()
        assertEquals(listOf("use"), log)
    }

    @Test fun theActionWaitsWhileTheStreetIsLookedUp() {
        rule.setContent { KtmTheme(darkTheme = true) { PickOnMapScreen(here.copy(street = null, resolving = true), onBack = {}, onUse = {}) } }
        rule.onNodeWithText("Use this point").assertIsNotEnabled()
        rule.onAllNodesWithText("Near 12th Main Rd").assertCountEquals(0)
    }
}
