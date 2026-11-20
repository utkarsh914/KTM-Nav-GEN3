package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RidingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class LandscapePanelSpecTest {
    @Test fun theWidePanelIs352AndTheNarrowOneIs320() {
        assertEquals(352.dp, LandscapePanelSpec.width(874f))
        assertEquals(352.dp, LandscapePanelSpec.width(800f))
        assertEquals(320.dp, LandscapePanelSpec.width(780f))
    }

    @Test fun theOpenMapStartsAfterTheInsetTheMarginsAndThePanel() {
        assertEquals(8.dp + 352.dp + 8.dp, LandscapePanelSpec.footprint(874f, 0.dp))
        assertEquals(37.dp + 8.dp + 320.dp + 8.dp, LandscapePanelSpec.footprint(780f, 37.dp))
    }

    @Test fun landscapeMeansWiderThanTall() {
        assertTrue(LandscapePanelSpec.isLandscape(874.dp, 402.dp))
        assertFalse(LandscapePanelSpec.isLandscape(402.dp, 874.dp))
        assertFalse(LandscapePanelSpec.isLandscape(500.dp, 500.dp))
        assertEquals(8.dp, KtmLayout.panelInset)
    }

    @Test fun controlsFlipBackAfterTenIdleSeconds() {
        assertFalse(ControlsIdle.shouldFlipBack(9_999))
        assertTrue(ControlsIdle.shouldFlipBack(10_000))
        assertFalse("a touch just now", ControlsIdle.shouldFlipBack(0))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w874dp-h402dp")
class RidingLandscapeTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private var insets: MapInsets? = null

    private val maneuver = ManeuverUi.Active(TurnIcon.QUITE_RIGHT, "300 m", "Turn right onto", "Dr Rajkumar Rd", null, ManeuverFooter.None, false)
    private val bar = TripBarUi("62", true, false, null, TimeLeft.of(4320), "38 km", "17:42", 0.4f, "Toit", false)
    private val controls = TripControlsUi(beeps = true, messages = false, dashStatus = "Connected", canAddStop = true, nextStop = null)

    private fun show(controlsUi: TripControlsUi?) {
        rule.setContent {
            KtmTheme(darkTheme = false) {
                RidingTheme(night = false) {
                    RidingLandscape(
                        maneuver, bar, chip = null, recenter = false, bearing = 0f, narrow = false, controls = controlsUi,
                        actions = RidingActions({ log += "open" }, { log += "map" }, { log += "north" }),
                        controlActions = TripSheetActions(
                            { log += "add" }, { log += "overview" }, { log += "minimise" }, {}, {}, {}, {}, { log += "end" }, { log += "flipBack" },
                        ),
                        onInsets = { insets = it },
                    )
                }
            }
        }
    }

    @Test fun guidanceAndTheTripBarShareOnePanelAndTheBarOpensTheControls() {
        show(null)
        rule.onNodeWithText("300 m").assertIsDisplayed()
        rule.onNodeWithText("1 h 12 min").assertIsDisplayed()
        rule.onNodeWithText("1 h 12 min").performClick()
        assertEquals(listOf("open"), log)
        rule.onNodeWithContentDescription("Overview").performClick()
        assertEquals(listOf("open", "map"), log)
    }

    @Test fun theMapIsToldHowMuchOfItsLeftThePanelCovers() {
        show(null)
        rule.waitForIdle()
        val left = insets?.left ?: 0
        assertTrue("panel plus margins, in px: $left", left > 0)
        assertEquals(0, insets?.top)
        assertEquals(0, insets?.bottom)
    }

    @Test fun theControlsReplaceTheGuidanceInTheSamePanelWithABackChevron() {
        show(controls)
        rule.onNodeWithText("End trip").assertIsDisplayed()
        rule.onNodeWithText("Add stop").assertIsDisplayed()
        rule.onNodeWithText("300 m").assertIsDisplayed() // the compact green row keeps the turn in view
        rule.onAllNodesWithText("1 h 12 min").let { assertEquals(0, it.fetchSemanticsNodes().size) }
        rule.onNodeWithContentDescription("Back to guidance").performClick()
        assertEquals(listOf("flipBack"), log)
    }

    @Test fun untouchedControlsFlipBackAfterTenSeconds() {
        show(controls)
        rule.mainClock.advanceTimeBy(9_000)
        assertEquals(emptyList<String>(), log)
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(listOf("flipBack"), log)
    }

    @Test fun aTouchRestartsTheTenSeconds() {
        show(controls)
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithText("Overview").performClick()
        assertEquals(listOf("overview"), log)
        rule.mainClock.advanceTimeBy(6_000)
        assertEquals("only 6 s since the touch", listOf("overview"), log)
    }
}
