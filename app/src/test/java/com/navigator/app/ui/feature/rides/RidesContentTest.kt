package com.navigator.app.ui.feature.rides

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSource
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class RidesContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val actions = RidesActions(
        { log += "open:$it" }, { log += "star:$it" }, { log += "search" }, { log += "closeSearch" }, { log += "q:$it" },
        { log += "sort:${it.field}:${it.desc}" }, { log += "select:$it" }, { log += "toggle:$it" }, { log += "all:$it" }, { log += "stop" },
        { log += "starSel" }, { log += "merge" }, { log += "delete" }, { log += "import" }, { log += "recSettings" }, { log += "record" }, { log += "autoOn" },
    )
    private val today = LocalDate.of(2026, 11, 20)
    private val zone = ZoneId.systemDefault()
    private fun start(h: Int) = today.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val a = RecordedRide("a", start(18), start(18) + 1_260_000, 6300, 1260, destinationLabel = "Toit Brewpub", maxSpeedKmh = 61f, source = RideSource.NAV)
    private val g = RecordedRide("g", start(7), start(7) + 18_600_000, 212_000, 18_600, destinationLabel = "Coorg loop", source = RideSource.GPX, saved = true)

    private fun ui(
        items: List<RideListItem> = RidesListBuilder.build(listOf(a, g), "", RideSort()),
        body: RidesBody = RidesBody.Items(items), selecting: Boolean = false, selected: Set<String> = emptySet(), query: String = "", searchOpen: Boolean = false,
    ) = RidesUi(body, query, searchOpen, RideSort(), selecting, selected, starredSelected = selected.count { it == "g" }, allSelected = false, starWould = true)

    private fun show(ui: RidesUi?, autoRecord: Boolean = true, recording: Boolean = false) = rule.setContent {
        KtmTheme(darkTheme = false) { RidesContent(ui, autoRecord, recording, { emptyList() }, actions, today = today) }
    }

    @Test fun listShowsTitlesHowTheyStartedStatsAndAGpxBadge() {
        show(ui())
        rule.onNodeWithText("Toit Brewpub").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("6.3 km · 21 min · 61 km/h").assertIsDisplayed()
        rule.onNodeWithText("GPX").assertIsDisplayed()
        rule.onNodeWithText("5 h 10", substring = true).assertIsDisplayed()
    }

    @Test fun theListAndTheReplayAgreeOnTheRidingTime() {
        val moving = a.copy(movingSeconds = 600)
        show(ui(items = RidesListBuilder.build(listOf(moving), "", RideSort())))
        rule.onNodeWithText("6.3 km · 10 min · 61 km/h").assertIsDisplayed()
    }

    @Test fun tapOpensLongPressSelectsAndTheStarIsItsOwnTarget() {
        show(ui())
        rule.onNodeWithText("Toit Brewpub").performClick()
        rule.onNodeWithText("Coorg loop").performTouchInput { longClick() }
        rule.onAllNodesWithText("Remove star").assertCountEquals(0)
        rule.onNodeWithContentDescription("Remove star").performClick()
        assertEquals(listOf("open:a", "select:g", "star:g"), log)
    }

    @Test fun selectionBarShowsTheCountAndItsActions() {
        show(ui(selecting = true, selected = setOf("a", "g")))
        rule.onNodeWithText("2 selected").assertIsDisplayed()
        rule.onNodeWithText("Select all").assertIsDisplayed()
        rule.onNodeWithText("2 rides").assertIsDisplayed()
        rule.onNodeWithContentDescription("Star selected rides").performClick()
        rule.onNodeWithContentDescription("Delete selected rides").performClick()
        rule.onNodeWithContentDescription("Cancel selection").performClick()
        rule.onAllNodesWithText("Record").assertCountEquals(0)
        assertEquals(listOf("starSel", "delete", "stop"), log)
    }

    @Test fun mergeAppearsOnlyForTwoOrMoreRides() {
        show(ui(selecting = true, selected = setOf("a")))
        rule.onAllNodesWithContentDescription("Merge selected rides").assertCountEquals(0)
    }

    @Test fun mergeIsOfferedForTwoSelectedRides() {
        show(ui(selecting = true, selected = setOf("a", "g")))
        rule.onNodeWithContentDescription("Merge selected rides").performClick()
        assertEquals(listOf("merge"), log)
    }

    @Test fun emptyExplainsAndOffersBothWaysIn() {
        show(ui(body = RidesBody.Empty, items = emptyList()))
        rule.onNodeWithText("No rides yet").assertIsDisplayed()
        rule.onNodeWithText("Trips you navigate are recorded here automatically.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Record a ride").performClick()
        rule.onNodeWithText("Import GPX").performClick()
        assertEquals(listOf("record", "import"), log)
        rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search rides")).assertCountEquals(0)
    }

    @Test fun emptyWithAutoRecordOffSaysSoAndLinksToTurnItOn() {
        show(ui(body = RidesBody.Empty, items = emptyList()), autoRecord = false)
        rule.onNodeWithText("Recording trips automatically is off.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Import GPX").assertIsDisplayed()
        rule.onNodeWithText("Turn on automatic recording").performClick()
        assertEquals(listOf("autoOn"), log)
    }

    @Test fun noMatchNamesTheQueryAndClears() {
        show(ui(body = RidesBody.NoMatch, items = emptyList(), query = "nandi", searchOpen = true))
        rule.onNodeWithText("No rides match 'nandi'").assertIsDisplayed()
        rule.onNodeWithText("Clear search").performClick()
        assertEquals(listOf("closeSearch"), log)
    }

    @Test fun theRecordButtonHidesWhileRecordingAndIsThereOtherwise() {
        show(ui(), recording = true)
        rule.onAllNodesWithText("Record").assertCountEquals(0)
    }

    @Test fun recordButtonStartsARecording() {
        show(ui())
        rule.onNodeWithText("Record").performClick()
        assertEquals(listOf("record"), log)
    }

    @Test fun menuHasImportSelectAndRecordingSettings() {
        show(ui())
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Select rides").performClick()
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Recording settings").performClick()
        assertEquals(listOf("select:null", "recSettings"), log)
    }

    @Test fun theSortSheetOffersAdaptiveOptionsAndPicksOne() {
        show(ui())
        rule.onNodeWithContentDescription("Sort rides").performClick()
        rule.onNodeWithText("Top speed").performClick()
        rule.onNodeWithText("Fastest first").assertIsDisplayed()
        rule.onNodeWithText("Slowest first").performClick()
        assertEquals("nothing changes until Show rides", emptyList<String>(), log)
        rule.onNodeWithText("Show rides").performClick()
        assertEquals(listOf("sort:TopSpeed:false"), log)
    }

    @Test fun searchingSaysHowManyRidesMatchAndWhatIsSearched() {
        show(ui(query = "toit", searchOpen = true, items = RidesListBuilder.build(listOf(a), "toit", RideSort())))
        rule.onNodeWithText("1 ride · searching names and notes").assertIsDisplayed()
    }

    @Test fun selectAllIsARowThatTogglesEverything() {
        show(ui(selecting = true, selected = setOf("a")))
        rule.onNodeWithText("Select all").performClick()
        assertEquals(listOf("all:true"), log)
    }

    @Test fun loadingShowsNoEmptyStateFlash() {
        show(null)
        rule.onAllNodesWithText("No rides yet").assertCountEquals(0)
    }

    @Test fun whileSelectingACardReportsItsToggleState() {
        show(ui(selecting = true, selected = setOf("a")))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)).assertCountEquals(1)
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off)).assertCountEquals(2) // the other card and the selection bar's toggle
    }

    @Test fun outsideSelectionACardIsNotAToggle() {
        show(ui())
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertCountEquals(0)
    }
}
