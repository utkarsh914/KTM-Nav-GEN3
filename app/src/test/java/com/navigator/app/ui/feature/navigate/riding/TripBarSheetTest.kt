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
import com.navigator.app.ui.shell.tripStripBounds
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
internal fun TripSheetForTest(beeps: Boolean, messages: Boolean, dashStatus: String?, canAddStop: Boolean, nextStop: String?, actions: TripSheetActions, nextStopNumber: Int? = null, nextStopDistance: String? = null) {
    androidx.compose.foundation.layout.Column {
        TripControlsBody(beeps, messages, dashStatus, canAddStop, nextStop, actions, nextStopNumber = nextStopNumber, nextStopDistance = nextStopDistance)
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
        rule.onNodeWithText("Toit").assertIsDisplayed()
        rule.onNodeWithText("Arrive 14:32 · 25 min · 12 km").assertIsDisplayed()
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

    @androidx.compose.animation.ExperimentalSharedTransitionApi
    @Test fun insideTheRidingStageTheBarAndTheStripShareBoundsAndTheSheetStillOpens() {
        var stage by mutableStateOf(true)
        var open by mutableStateOf(false)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                RidingTheme(night = true) {
                    androidx.compose.animation.SharedTransitionLayout {
                        androidx.compose.animation.AnimatedContent(stage, label = "stage") { riding ->
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.navigator.app.ui.shell.LocalTripShared provides com.navigator.app.ui.shell.SharedElements(this@SharedTransitionLayout, this),
                            ) {
                                if (riding) TripBarSheet(bar, open, { log += "open" }, { log += "dismiss" }, 600.dp) { androidx.compose.material3.Text("sheet body") }
                                else androidx.compose.foundation.layout.Box(Modifier.tripStripBounds()) { androidx.compose.material3.Text("strip") }
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("42").assertIsDisplayed()
        open = true
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("sheet body").assertIsDisplayed()
        open = false
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_000)
        stage = false
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("strip").assertIsDisplayed()
        stage = true
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("42").assertIsDisplayed()
    }
}
