package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.R
import com.navigator.app.nav.destination.CategoryResult
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.providers.StartFailure
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
class RidingSheetsTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun riding(content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { KtmTheme(darkTheme = true) { RidingTheme(night = true, content = content) } }

    private fun failedActions() = StartFailedActions({ log += "retry" }, { log += "back" }, { log += "close" }, { log += "mirror" })

    @Test fun aRouteFailureOffersTryAgainBackAndClose() {
        riding { StartFailedSheet(StartFailure.ROUTE, canRetry = true, actions = failedActions()) }
        rule.onNodeWithText("Navigation didn't start").assertIsDisplayed()
        rule.onNodeWithText("Couldn't get a route from here. Try again, or choose another place.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        rule.onNodeWithText("Back to route").performClick()
        rule.onNodeWithText("Close").performClick()
        rule.onAllNodesWithText("Switch to mirror mode").assertCountEquals(0)
        assertEquals(listOf("retry", "back", "close"), log)
    }

    @Test fun anUnsupportedPhoneOffersMirrorModeInsteadOfRetrying() {
        riding { StartFailedSheet(StartFailure.UNAVAILABLE, canRetry = true, actions = failedActions()) }
        rule.onAllNodesWithText("Try again").assertCountEquals(0)
        rule.onNodeWithText("Switch to mirror mode").performClick()
        assertEquals(listOf("mirror"), log)
    }

    @Test fun withoutATripThereIsNothingToRetry() {
        riding { StartFailedSheet(StartFailure.NAVIGATOR_UNAVAILABLE, canRetry = false, actions = failedActions()) }
        rule.onNodeWithText("Google navigation isn't ready yet. Check your connection and Play services.").assertIsDisplayed()
        rule.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test fun everyFailureHasRidersWordsAndTheRightWayOut() {
        for (f in StartFailure.entries) {
            val ui = StartFailureMapper.of(f)
            assertTrue(f.name, ui.message != 0)
            assertEquals(f == StartFailure.UNAVAILABLE, ui.offerMirror)
            assertEquals("retrying can't fix an unsupported phone", f != StartFailure.UNAVAILABLE, ui.canRetry)
        }
    }

    @Test fun stopRoomIsTheLimitMinusWhatIsAhead() {
        assertEquals(4, TripStopRules.left(0, 0))
        assertEquals(2, TripStopRules.left(2, 9))
        assertEquals("unknown next: use the planned count", 3, TripStopRules.left(null, 1))
        assertEquals(0, TripStopRules.left(7, 0))
        assertTrue(TripStopRules.canAdd(1, online = true))
        assertFalse(TripStopRules.canAdd(0, online = true))
        assertFalse(TripStopRules.canAdd(2, online = false))
    }

    private val fuel = CategoryResult("Indian Oil", "NH 48", 77.0, 12.0, 1_200)

    private fun stopUi(left: Int = 2, online: Boolean = true, category: PlaceCategory? = PlaceCategory.FUEL, loading: Boolean = false,
                       failed: Boolean = false, results: List<CategoryResult> = listOf(fuel)) =
        TripStopUi(left, online, category, loading, failed, results)

    private fun stopActions() = TripStopActions({ log += "cat:${it.name}" }, { log += "add:${it.name}" }, { log += "search" }, { log += "retry" }, { log += "dismiss" })

    @Test fun theStopSheetSaysHowManyAreLeftAndAddsFromItsOwnButton() {
        riding { TripStopSheet(stopUi(), stopActions()) }
        rule.onNodeWithText("2 of 4 stops left").assertIsDisplayed()
        rule.onNodeWithText("Indian Oil").assertIsDisplayed()
        rule.onNodeWithText("Indian Oil").performClick() // the row itself adds nothing
        assertEquals(emptyList<String>(), log)
        rule.onNodeWithContentDescription("Add Indian Oil as a stop").performClick()
        rule.onNodeWithText("Fuel").performClick()
        rule.onNodeWithText("Search by name").performClick()
        assertEquals(listOf("add:Indian Oil", "cat:FUEL", "search"), log)
    }

    @Test fun offlineDisablesAddAndSaysWhy() {
        riding { TripStopSheet(stopUi(online = false), stopActions()) }
        rule.onNodeWithText("Needs internet").assertIsDisplayed()
        rule.onNode(hasContentDescription("Add Indian Oil as a stop")).assertIsNotEnabled()
    }

    @Test fun atTheLimitAddIsDisabledAndTheLimitIsStated() {
        riding { TripStopSheet(stopUi(left = 0), stopActions()) }
        rule.onNodeWithText("Stop limit reached").assertIsDisplayed()
        rule.onNode(hasContentDescription("Add Indian Oil as a stop")).assertIsNotEnabled()
    }

    @Test fun beforeACategoryThereIsAHintAndAfterAFailureAnotherOne() {
        riding { TripStopSheet(stopUi(category = null, results = emptyList()), stopActions()) }
        rule.onNodeWithText("Pick a category to see places along your route").assertIsDisplayed()
    }

    @Test fun failureAndEmptyResultsAreSaidInWords() {
        riding { TripStopSheet(stopUi(failed = true, results = emptyList()), stopActions()) }
        rule.onNodeWithText("Couldn't load places. Try again.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), log)
    }

    @Test fun theTripSheetShowsTheNextStopWithARemoveButton() {
        val actions = TripSheetActions({}, {}, {}, {}, {}, {}, { log += "removeNext" }, {}, {})
        riding { TripSheet(beeps = true, messages = true, dashStatus = null, canAddStop = true, nextStop = "Indian Oil", actions = actions) }
        rule.onNodeWithText("Indian Oil").assertIsDisplayed()
        rule.onNodeWithText("Next stop").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove next stop").performClick()
        assertEquals(listOf("removeNext"), log)
    }

    @Test fun noNextStopNoRow() {
        riding { TripSheet(beeps = true, messages = true, dashStatus = null, canAddStop = true, nextStop = null, actions = TripSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {})) }
        rule.onAllNodesWithText("Next stop").assertCountEquals(0)
    }
}
