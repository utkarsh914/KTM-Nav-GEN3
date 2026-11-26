package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.navigator.app.ble.BccuProtocol.TurnIcon
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class ManeuverCardSemanticsTest {
    @get:Rule val rule = createComposeRule()

    private fun active(distance: String, instruction: String = "Turn right onto", road: String? = "Jalaun Rd") =
        ManeuverUi.Active(TurnIcon.QUITE_RIGHT, distance, instruction, road, null, ManeuverFooter.None, false)

    private val live = SemanticsMatcher("is a live region") { it.config.getOrNull(SemanticsProperties.LiveRegion) != null }

    private fun spoken(): String = rule.onAllNodes(live).fetchSemanticsNodes().single().config.let {
        (it.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + it.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text }).joinToString(", ")
    }

    @Test fun theLiveNodeNamesTheTurnAndLeavesTheFallingDistanceOut() {
        val ui = mutableStateOf(active("450 m"))
        rule.setContent { KtmTheme(darkTheme = true) { RidingTheme(night = true) { ManeuverCard(ui.value) } } }
        rule.onAllNodes(live).assertCountEquals(1)
        val first = spoken()
        assertTrue(first, "Jalaun Rd" in first && "Turn right onto" in first && "right" in first.lowercase())
        assertFalse("the distance is not announced with the turn: $first", "450" in first)
        rule.onNodeWithText("450 m").assertIsDisplayed()
        // Every second the distance falls: what the live node says must not change, or TalkBack re-reads the card.
        ui.value = active("440 m")
        rule.waitForIdle()
        assertEquals(first, spoken())
        rule.onNodeWithText("440 m").assertIsDisplayed()
    }

    @Test fun aNewRoadChangesWhatIsAnnounced() {
        val ui = mutableStateOf(active("450 m"))
        rule.setContent { KtmTheme(darkTheme = true) { RidingTheme(night = true) { ManeuverCard(ui.value) } } }
        val first = spoken()
        ui.value = active("450 m", road = "Bypass Rd")
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertTrue(spoken() != first && "Bypass Rd" in spoken())
    }

    @Test fun whileReroutingTheDimmedLastInstructionIsNotAnnouncedAgain() {
        val busy = ManeuverUi.Active(TurnIcon.QUITE_RIGHT, null, "Turn right onto", "Jalaun Rd", null, ManeuverFooter.None, true)
        rule.setContent { KtmTheme(darkTheme = true) { RidingTheme(night = true) { ManeuverCard(busy) } } }
        // The one live node is the card's own "Rerouting" line.
        rule.onAllNodes(live).assertCountEquals(1)
        assertFalse("Jalaun Rd" in spoken())
    }
}
