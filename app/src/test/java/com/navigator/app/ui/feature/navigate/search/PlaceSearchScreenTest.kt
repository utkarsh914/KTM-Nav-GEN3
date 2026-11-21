package com.navigator.app.ui.feature.navigate.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.shell.LocalOpenPlaces
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

    private fun show(state: PlaceSearchState, mode: SearchMode = SearchMode.Destination, openPlaces: (() -> Unit)? = null) = rule.setContent {
        CompositionLocalProvider(LocalOpenPlaces provides openPlaces) {
            KtmTheme(darkTheme = false) {
                PlaceSearchScreen(
                    state, mode, voiceAvailable = false,
                    actions = PlaceSearchActions({ log += "back" }, {}, {}, {}, {}, {}, {}, {}, null, onRetry = { log += "retry" }),
                )
            }
        }
    }

    private val saved = PlaceSearchState(favorites = listOf(FavoritePlace(SavedPlace(12.0, 77.0, "Cubbon Park"))))

    @Test fun seeAllClosesSearchAndOpensPlaces() {
        show(saved, openPlaces = { log += "places" })
        rule.onNodeWithText("See all").performClick()
        assertEquals(listOf("back", "places"), log)
    }

    @Test fun seeAllIsHiddenWithoutTheShellHook() {
        show(saved)
        rule.onNodeWithText("Cubbon Park").assertIsDisplayed()
        rule.onAllNodesWithText("See all").assertCountEquals(0)
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
