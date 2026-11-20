package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RidingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the trip sheet shows: the controls and End trip, as the sheet the trip bar grows into and the landscape panel use them. */
@Composable
internal fun TripSheetForTest(beeps: Boolean, messages: Boolean, dashStatus: String?, canAddStop: Boolean, nextStop: String?, actions: TripSheetActions) {
    androidx.compose.foundation.layout.Column {
        TripControlsBody(beeps, messages, dashStatus, canAddStop, nextStop, actions)
        Spacer(Modifier.height(KtmSpacing.xl))
        EndTripButton(actions.onEnd)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h900dp")
class TripBarSheetTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val bar = TripBarUi(
        speed = "42", speedKnown = true, overspeed = false, alert = null, timeLeft = TimeLeft(0, 25),
        distance = "12 km", arrival = "14:32", progress = 0.5f, destination = "Toit", starting = false,
    )

    private fun show(open: () -> Boolean) {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                RidingTheme(night = true) {
                    TripBarSheet(bar, open(), { log += "open" }, { log += "dismiss" }, 600.dp) {
                        androidx.compose.material3.Text("sheet body")
                    }
                }
            }
        }
    }

    @Test fun closedItIsTheTripBarAndTappingItOpens() {
        show { false }
        rule.onNodeWithText("42").assertIsDisplayed()
        rule.onAllNodesWithText("sheet body").assertCountEquals(0)
        rule.onNodeWithText("42").performClick()
        assertEquals(listOf("open"), log)
    }

    @Test fun openItIsATitledPaneWithACloseAction() {
        var open by mutableStateOf(false)
        show { open }
        open = true
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("sheet body").assertIsDisplayed()
        rule.onNodeWithText("Trip").assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        rule.onNode(hasContentDescription("Close trip menu")).performClick()
        assertEquals(listOf("dismiss"), log)
    }

    @Test fun closingPutsTheBarBack() {
        var open by mutableStateOf(true)
        show { open }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("sheet body").assertIsDisplayed()
        open = false
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onAllNodesWithText("sheet body").assertCountEquals(0)
        rule.onNodeWithText("42").assertIsDisplayed()
    }
}
