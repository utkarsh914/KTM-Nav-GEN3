package com.navigator.app.ui.feature.navigate.pick

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.ui.map.MapPoint
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun portraitReportsNoCoveredSideAndKeepsGuidanceAboveTheHint() {
        val footprints = mutableListOf<Int>()
        rule.setContent {
            KtmTheme(darkTheme = true) {
                PickOnMapScreen(here, onBack = {}, onUse = {}, guidance = { Text("Turn right in 250 m") }, onFootprint = { footprints += it })
            }
        }
        rule.waitForIdle()
        assertEquals(listOf(0), footprints.distinct())
        val guidance = rule.onNodeWithText("Turn right in 250 m").getUnclippedBoundsInRoot()
        val hint = rule.onNodeWithText("Move the map to place the pin").getUnclippedBoundsInRoot()
        assertTrue("guidance ${guidance.bottom} must end above the hint ${hint.top}", guidance.bottom <= hint.top)
    }
}

/** Landscape: one panel at the left holds everything, and the host is told how much of the map it covers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp")
class PickOnMapLandscapeTest {
    @get:Rule val rule = createComposeRule()
    private val here = PickOnMapState(center = MapPoint(12.9, 77.6), street = "12th Main Rd")

    @Test fun thePanelHoldsTheHintTheStreetAndTheActionAndReportsItsFootprint() {
        val footprints = mutableListOf<Int>()
        val log = mutableListOf<String>()
        rule.setContent {
            KtmTheme(darkTheme = true) {
                PickOnMapScreen(here, onBack = { log += "back" }, onUse = { log += "use" }, guidance = { Text("Turn right in 250 m") }, onFootprint = { footprints += it })
            }
        }
        rule.waitForIdle()
        assertTrue(footprints.last() > 0)
        rule.onNodeWithText("Turn right in 250 m").assertIsDisplayed()
        rule.onNodeWithText("Move the map to place the pin").assertIsDisplayed()
        rule.onNodeWithText("Near 12th Main Rd").assertIsDisplayed()
        rule.onNodeWithText("Use this point").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("use", "back"), log)
        // Everything stays inside the left panel: nothing runs across the open map.
        val use = rule.onNodeWithText("Use this point").getUnclippedBoundsInRoot()
        val footprintDp = footprints.last() / rule.density.density
        assertTrue("action ends at ${use.right}, the panel's footprint is $footprintDp", use.right.value <= footprintDp)
    }
}
