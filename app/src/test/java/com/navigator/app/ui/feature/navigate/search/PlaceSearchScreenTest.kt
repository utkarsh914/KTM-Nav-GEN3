package com.navigator.app.ui.feature.navigate.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class PlaceSearchScreenTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()

    private fun show(state: PlaceSearchState) = rule.setContent {
        KtmTheme(darkTheme = false) {
            PlaceSearchScreen(
                state, SearchMode.Destination, voiceAvailable = false,
                actions = PlaceSearchActions({}, {}, {}, {}, {}, {}, {}, {}, null, onRetry = { log += "retry" }),
            )
        }
    }

    @Test fun offlineSaysSoOffersTryAgainAndStillListsMatchingPlaces() {
        show(PlaceSearchState(query = "toit", view = SearchView.Results, offline = true, localResults = listOf(SavedPlace(12.0, 77.0, "Toit Brewpub", "100 Feet Rd"))))
        rule.onNodeWithText("You're offline").assertIsDisplayed()
        rule.onNodeWithText("Matching saved & recent").assertIsDisplayed()
        rule.onNodeWithText("Toit Brewpub").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), log)
    }

    @Test fun noMatchesNamesWhatWasSearchedFor() {
        show(PlaceSearchState(query = "toitt", view = SearchView.Results))
        rule.onNodeWithText("Nothing found for 'toitt'. Check the spelling or try a category.").assertIsDisplayed()
    }

    @Test fun aFailedCategoryOffersTryAgain() {
        show(PlaceSearchState(view = SearchView.Category, category = PlaceCategory.FUEL, categoryFailed = true))
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), log)
    }
}
