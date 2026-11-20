package com.navigator.app.ui.feature.navigate.search

import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPolicyTest {
    @Test fun searchesFromThreeCharactersIgnoringSpaces() {
        assertFalse(SearchPolicy.searchable("to"))
        assertFalse(SearchPolicy.searchable("  to  "))
        assertTrue(SearchPolicy.searchable("toi"))
        assertTrue(SearchPolicy.searchable(" toi "))
    }

    @Test fun whichListShows() {
        assertEquals(SearchView.Browse, SearchPolicy.view("", null))
        assertEquals(SearchView.Browse, SearchPolicy.view("to", null))
        assertEquals(SearchView.Category, SearchPolicy.view("", PlaceCategory.FUEL))
        assertEquals("typing beats a category", SearchView.Results, SearchPolicy.view("toit", PlaceCategory.FUEL))
        assertEquals("two letters with a category open keep the category list hidden", SearchView.Browse, SearchPolicy.view("to", PlaceCategory.FUEL))
    }

    private val home = FavoritePlace(SavedPlace(12.0, 77.0, "Flat", "Indiranagar"), FavoriteSlot.HOME)
    private val saved = FavoritePlace(SavedPlace(12.1, 77.1, "Toit Brewpub", "100 Feet Rd"), FavoriteSlot.OTHER)
    private val recents = listOf(SavedPlace(12.1, 77.1, "Toit Brewpub", "100 Feet Rd"), SavedPlace(12.2, 77.2, "Cubbon Park"))

    @Test fun offlineSearchMatchesNameOrAddressSavedFirstAndOnce() {
        val m = SearchPolicy.localMatches("toit", listOf(home, saved), recents)
        assertEquals(listOf("Toit Brewpub"), m.map { it.label })
        assertEquals(listOf("Flat"), SearchPolicy.localMatches("INDIRA", listOf(home, saved), recents).map { it.label })
        assertEquals(listOf("Toit Brewpub", "Cubbon Park"), SearchPolicy.localMatches("o", listOf(saved), recents).map { it.label })
        assertTrue(SearchPolicy.localMatches("   ", listOf(home), recents).isEmpty())
    }

    @Test fun matchRangeIsTheFirstOccurrenceInAnyCase() {
        assertEquals(0..3, SearchPolicy.matchRange("Toit Brewpub", "TOIT"))
        assertEquals(5..7, SearchPolicy.matchRange("Toit Brewpub", " bre "))
        assertNull(SearchPolicy.matchRange("Toit", "xyz"))
        assertNull(SearchPolicy.matchRange("Toit", " "))
    }

    @Test fun homeAndWorkAreListedWhenAddingAStopButNotForADestination() {
        val home = com.navigator.app.nav.destination.FavoritePlace(SavedPlace(1.0, 1.0, "Home"), com.navigator.app.nav.destination.FavoriteSlot.HOME)
        val work = com.navigator.app.nav.destination.FavoritePlace(SavedPlace(2.0, 2.0, "Work"), com.navigator.app.nav.destination.FavoriteSlot.WORK)
        val cafe = com.navigator.app.nav.destination.FavoritePlace(SavedPlace(3.0, 3.0, "Cafe"), com.navigator.app.nav.destination.FavoriteSlot.OTHER)
        val all = listOf(cafe, work, home)
        assertEquals(listOf("Cafe"), SearchPolicy.browseFavorites(all, SearchMode.Destination).map { it.place.label })
        assertEquals(listOf("Home", "Work", "Cafe"), SearchPolicy.browseFavorites(all, SearchMode.Stop).map { it.place.label })
        assertEquals(listOf("Home", "Work", "Cafe"), SearchPolicy.browseFavorites(all, SearchMode.TripStop).map { it.place.label })
    }
}
