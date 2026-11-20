package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class PlanSheetsTest {
    @get:Rule val rule = createComposeRule()

    private val log = mutableListOf<String>()

    private fun placeActions() = PlaceSheetActions(
        onBack = { log += "back" }, onClose = { log += "close" }, onToggleSave = { log += "save" },
        onDirections = { log += "directions" }, onAddAsStop = { log += "stop" },
    )

    private fun place(saved: Boolean = false, trip: Boolean = false) =
        PlaceSheetState("Toit Brewpub", "Indiranagar", "3.2 km", saved, trip)

    @Test fun thePlaceSheetAlwaysHasAWayBack() {
        rule.setContent { KtmTheme(darkTheme = true) { PlaceSheet(place(), placeActions()) } }
        rule.onNodeWithText("Toit Brewpub").assertIsDisplayed()
        rule.onNodeWithText("3.2 km away").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Directions").performClick()
        assertEquals(listOf("back", "close", "directions"), log)
    }

    @Test fun saveReadsSavedOnceSavedAndOffersNoAddAsStopWithoutATrip() {
        rule.setContent { KtmTheme(darkTheme = true) { PlaceSheet(place(saved = true), placeActions()) } }
        rule.onNodeWithText("Saved").assertIsDisplayed()
        rule.onAllNodesWithText("Add as stop").assertCountEquals(0)
        rule.onNodeWithText("Saved").performClick()
        assertEquals(listOf("save"), log)
    }

    @Test fun whileATripRunsDirectionsBecomesReplaceTripAndAddAsStopAppears() {
        rule.setContent { KtmTheme(darkTheme = true) { PlaceSheet(place(trip = true), placeActions()) } }
        rule.onAllNodesWithText("Directions").assertCountEquals(0)
        rule.onNodeWithText("Replace trip…").performClick()
        rule.onNodeWithText("Add as stop").performClick()
        assertEquals(listOf("directions", "stop"), log)
    }

    private fun option(time: String, badge: RouteBadge, alt: Int = 1, tone: DelayTone = DelayTone.OnTime, delay: Int = 0) =
        RouteOptionUi(time, "12 km", badge, alt, tone, delay)

    private fun routeActions() = RouteSheetActions(
        onBack = { log += "back" }, onClose = { log += "close" }, onSelect = { log += "select$it" },
        onAddStop = { log += "addStop" }, onRemoveStop = { log += "remove$it" }, onMoveStop = { i, d -> log += "move$i,$d" },
        onStart = { log += "start" },
    )

    private fun routeState(
        options: List<RouteOptionUi> = listOf(option("30 min", RouteBadge.Fastest), option("35 min", RouteBadge.Alternative, 1, DelayTone.Minor, 6)),
        stops: List<String> = emptyList(), selected: Int = 0, loading: Boolean = false, failed: Boolean = false,
        legLines: List<String> = emptyList(),
    ) = RouteSheetState("Toit", stops, options, selected, loading, failed, legLines, options.getOrNull(selected)?.let { "${it.time} · ${it.distance}" })

    @Test fun routesAreRowsWithABadgeAndTrafficInWords() {
        rule.setContent { KtmTheme(darkTheme = true) { RouteSheet(routeState(), routeActions()) } }
        rule.onNodeWithText("30 min").assertIsDisplayed()
        rule.onNodeWithText("Fastest").assertIsDisplayed()
        rule.onNodeWithText("Alternative 1").assertIsDisplayed()
        rule.onNodeWithText("On time").assertIsDisplayed()
        rule.onNodeWithText("+6 min").assertIsDisplayed()
    }

    @Test fun tappingARowSelectsItAndTheSelectedRowReadsSelected() {
        var selected by mutableStateOf(0)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                RouteSheet(routeState(selected = selected), RouteSheetActions(
                    {}, {}, { selected = it }, {}, {}, { _, _ -> }, {},
                ))
            }
        }
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Fastest, 30 min, 12 km, On time")).assertIsSelected()
        rule.onNodeWithText("35 min").performClick()
        assertEquals(1, selected)
    }

    @Test fun startIsOfferedAndAFailureRelabelsItStartAnyway() {
        rule.setContent { KtmTheme(darkTheme = true) { RouteSheet(routeState(options = emptyList(), failed = true), routeActions()) } }
        rule.onNodeWithText("Couldn't load a route preview. You can still start navigation.").assertIsDisplayed()
        rule.onNodeWithText("Start anyway").performClick()
        assertEquals(listOf("start"), log)
    }

    @Test fun loadingShowsPlaceholdersInsteadOfRoutes() {
        rule.setContent { KtmTheme(darkTheme = true) { RouteSheet(routeState(options = emptyList(), loading = true), routeActions()) } }
        rule.onNodeWithContentDescription("Finding routes").assertIsDisplayed()
        rule.onAllNodesWithText("Fastest").assertCountEquals(0)
        rule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test fun withNoStopsThereIsAStopButtonAndWithStopsAListRowThatCountsWhatIsLeft() {
        var state by mutableStateOf(routeState())
        rule.setContent { KtmTheme(darkTheme = true) { RouteSheet(state, routeActions()) } }
        rule.onNodeWithText("Stop").performClick()
        assertEquals(listOf("addStop"), log)
        state = routeState(stops = listOf("Fuel", "Cafe"), options = listOf(option("40 min", RouteBadge.Only)))
        rule.waitForIdle()
        rule.onNodeWithText("Add a stop · 2 left").assertIsDisplayed()
        rule.onNodeWithText("Add a stop · 2 left").performClick()
        assertEquals(listOf("addStop", "addStop"), log)
        // With stops there is one route: no list of alternatives, the summary sits in the header.
        rule.onAllNodesWithText("Fastest").assertCountEquals(0)
        rule.onNodeWithText("40 min · 12 km").assertIsDisplayed()
    }

    @Test fun atFourStopsTheRowStatesTheLimitAndIsInert() {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                RouteSheet(routeState(stops = listOf("A", "B", "C", "D"), options = listOf(option("50 min", RouteBadge.Only))), routeActions())
            }
        }
        rule.onNodeWithText("Stop limit reached").assertIsDisplayed()
        rule.onNodeWithText("Stop limit reached").performClick()
        assertEquals(emptyList<String>(), log)
    }

    @Test fun eachStopIsNumberedWithItsLegAndCanBeRemovedOrMoved() {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                RouteSheet(
                    routeState(
                        stops = listOf("Fuel", "Cafe"), options = listOf(option("40 min", RouteBadge.Only)),
                        legLines = listOf("arrive 10:10 · 10 min · 5 km", "arrive 10:22 · 12 min · 6 km", "arrive 10:31 · 9 min · 4 km"),
                    ),
                    routeActions(),
                )
            }
        }
        rule.onNodeWithText("Fuel").assertIsDisplayed()
        rule.onNodeWithText("arrive 10:10 · 10 min · 5 km").assertIsDisplayed()
        rule.onNodeWithText("arrive 10:31 · 9 min · 4 km").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove stop 2").performClick()
        assertEquals(listOf("remove1"), log)
        rule.onNodeWithContentDescription("Reorder stop 1").performClick()
        rule.onNodeWithText("Move down").performClick()
        assertEquals(listOf("remove1", "move0,1"), log)
        rule.onAllNodesWithText("Move up").assertCountEquals(0)
    }

    @Test fun replaceTripAsksThreeWaysAndKeepingIsTheDefault() {
        var shown by mutableStateOf(true)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                if (shown) ReplaceTripDialog("Toit", onReplace = { log += "replace" }, onKeep = { log += "keep"; shown = false }, onAddAsStop = { log += "stop" })
            }
        }
        rule.onNodeWithText("Replace current trip?").assertIsDisplayed()
        rule.onNode(hasText("Your current navigation will stop and a new trip to Toit will start.")).assertIsDisplayed()
        rule.onNodeWithText("Add as a stop instead").performClick()
        rule.onNodeWithText("Replace").performClick()
        rule.onNodeWithText("Keep current").performClick()
        assertEquals(listOf("stop", "replace", "keep"), log)
    }

    @Test fun aSpotOnTheMapNamesItsStopNumberAndWhereItGoes() {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                StopCandidateSheet("Indian Oil", 2, "Toit", canAdd = true, onAdd = { log += "add" }, onCancel = { log += "cancel" })
            }
        }
        rule.onNodeWithText("Indian Oil").assertIsDisplayed()
        rule.onNodeWithText("Stop 2 · goes before Toit").assertIsDisplayed()
        rule.onNodeWithText("Add stop here").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("add", "cancel"), log)
    }

    @Test fun atTheStopLimitAddingFromTheMapIsDisabled() {
        rule.setContent { KtmTheme(darkTheme = true) { StopCandidateSheet("New stop", 5, "Toit", canAdd = false, onAdd = { log += "add" }, onCancel = {}) } }
        rule.onNodeWithText("Stop limit reached").assertIsNotEnabled()
    }
}
