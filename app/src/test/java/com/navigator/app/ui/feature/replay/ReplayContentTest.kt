package com.navigator.app.ui.feature.replay

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideChart
import com.navigator.app.ride.RidePoint
import com.navigator.app.ride.RideSource
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class ReplayContentTest {
    @get:Rule val rule = createComposeRule()
    private val log = mutableListOf<String>()
    private val position = mutableIntStateOf(0)

    private val points = (0 until 40).map { RidePoint(12.0, 77.0 + it * 0.0003, 1_700_000_000_000 + 1_000L * it, 20f + it, altM = 900f + it) }
    private val ride = RecordedRide(
        "r", 1_700_000_000_000, 1_700_000_040_000, 12_300, 2_400, destinationLabel = "Nandi Hills", source = RideSource.NAV,
        maxSpeedKmh = 88f, avgSpeedKmh = 41f, elevGainM = 120, elevLossM = 98,
    )

    private fun ui(playing: Boolean = false, parts: Int = 1, speed: Int = 4, following: Boolean = false, noAlt: Boolean = false): ReplayUi.Ready {
        val pts = if (noAlt) points.map { it.copy(altM = null) } else points
        return ReplayUi.Ready(ride, pts, RideChart.build(pts), emptyList(), parts, playing, speed, following)
    }

    private fun actions() = ReplayActions(
        onBack = { log += "back" }, onShare = { log += "share" }, onEdit = { log += "edit" }, onTrim = { log += "trim" },
        onDelete = { log += "delete" }, onToggleStar = { log += "star" }, onTogglePlay = { log += "play" }, onRestart = { log += "restart" },
        onSeek = { log += "seek" }, onScrubStart = { log += "scrub" }, onToggleFollow = { log += "follow" },
        onStepSpeed = { log += "speed$it" }, onToggleElevation = { log += "elev" }, onStepSmoothing = { log += "smooth$it" },
        onRecenter = { log += "recenter" }, onResetNorth = { log += "north" },
    )

    private fun show(ui: ReplayUi, bearing: Float = 0f) = rule.setContent {
        KtmTheme(darkTheme = false) { ReplayContent(ui, position.asState(), bearing, AvgSpeedMode.MOVING, actions(), {}) }
    }

    private fun androidx.compose.runtime.MutableIntState.asState(): androidx.compose.runtime.State<Int> = this

    @Test fun peekShowsTheRideTheReadoutsAndTheControls() {
        show(ui())
        rule.onNodeWithText("Nandi Hills").assertIsDisplayed()
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        rule.onNodeWithContentDescription("Restart").assertIsDisplayed()
        rule.onNodeWithText("4×").assertIsDisplayed()
        rule.onNodeWithText("20 km/h").assertIsDisplayed()
        rule.onAllNodesWithText("Distance").assertCountEquals(0) // the details wait for the sheet to open
    }

    @Test fun controlsReportTheirActions() {
        show(ui())
        rule.onNodeWithContentDescription("Play").performClick()
        rule.onNodeWithContentDescription("Restart").performClick()
        rule.onNodeWithContentDescription("Follow the marker").performClick()
        rule.onNodeWithContentDescription("Faster").performClick()
        rule.onNodeWithContentDescription("Slower").performClick()
        rule.onNodeWithContentDescription("Star ride").performClick()
        rule.onNodeWithContentDescription("Share as GPX").performClick()
        rule.onNodeWithContentDescription("Zoom to the marker").performClick()
        assertEquals(listOf("play", "restart", "follow", "speed1", "speed-1", "star", "share", "recenter"), log)
    }

    @Test fun whilePlayingTheButtonOffersPause() {
        show(ui(playing = true))
        rule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test fun theSpeedStepperDimsAtItsEnds() {
        show(ui(speed = 1))
        rule.onNodeWithContentDescription("Slower").assertIsNotEnabled()
    }

    @Test fun aMergedRideCarriesAPartsBadge() {
        show(ui(parts = 2))
        rule.onNodeWithText("2 parts").assertIsDisplayed()
    }

    @Test fun anOrdinaryRideHasNoBadge() {
        show(ui())
        rule.onAllNodesWithText("1 part").assertCountEquals(0)
    }

    @Test fun theMenuOffersEditTrimAndDelete() {
        show(ui())
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Trim").performClick()
        rule.onNodeWithContentDescription("More").performClick()
        rule.onNodeWithText("Delete").performClick()
        assertEquals(listOf("edit", "trim", "delete"), log)
    }

    @Test fun tappingTheTitleOpensEdit() {
        show(ui())
        rule.onNodeWithText("Nandi Hills").performClick()
        assertEquals(listOf("edit"), log)
    }

    @Test fun theHandleOpensTheDetailsAndClosesThemAgain() {
        show(ui())
        val handle = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Details hidden")
        rule.onNode(handle).performClick()
        rule.onNodeWithText("Distance").assertIsDisplayed()
        rule.onNodeWithText("Top speed").assertIsDisplayed()
        rule.onNodeWithText("Track smoothing").assertExists()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Details shown")).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Distance").assertCountEquals(0)
    }

    @Test fun theDetailsHaveTheChartElevationAndSmoothingStepper() {
        show(ui())
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Details hidden")).performClick()
        rule.onNodeWithContentDescription("Speed chart, top 59 km/h, elevation 900 to 939 m").assertExists()
        rule.onNodeWithText("Elevation").performScrollTo().performClick()
        rule.onNodeWithText("Climb 120 m · Descent 98 m").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("More smoothing").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Less smoothing").performScrollTo().performClick()
        assertEquals(listOf("elev", "smooth1", "smooth-1"), log)
    }

    @Test fun withoutAltitudeThereIsNoElevationChip() {
        show(ui(noAlt = true))
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Details hidden")).performClick()
        rule.onAllNodesWithText("Elevation").assertCountEquals(0)
    }

    @Test fun noTrackAndMissingRidesSaySoWithABack() {
        show(ReplayUi.NoTrack(ride))
        rule.onNodeWithText("No track to replay").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), log)
    }

    @Test fun aMissingRideSaysItWasNotFound() {
        show(ReplayUi.NoTrack(null))
        rule.onNodeWithText("Ride not found").assertIsDisplayed()
    }

    @Test fun theCompassShowsOnlyWhenTheMapIsRotated() {
        show(ui(), bearing = 40f)
        rule.onNodeWithContentDescription("Reset the map to north").performClick()
        assertEquals(listOf("north"), log)
    }

    @Test fun noCompassWhenNorthUp() {
        show(ui())
        rule.onAllNodesWithContentDescription("Reset the map to north").assertCountEquals(0)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w874dp-h402dp")
class ReplayLandscapeTest {
    @get:Rule val rule = createComposeRule()
    private val points = (0 until 40).map { RidePoint(12.0, 77.0 + it * 0.0003, 1_700_000_000_000 + 1_000L * it, 20f + it, altM = 900f + it) }
    private val ride = RecordedRide("r", 1_700_000_000_000, 1_700_000_040_000, 12_300, 2_400, destinationLabel = "Nandi Hills", source = RideSource.NAV, maxSpeedKmh = 88f)

    @Test fun thePanelKeepsTheChartAndTheStatsInsteadOfDroppingThem() {
        val ui = ReplayUi.Ready(ride, points, RideChart.build(points), emptyList(), 1)
        val position = mutableIntStateOf(0)
        rule.setContent {
            KtmTheme(darkTheme = false) {
                ReplayContent(ui, position, 0f, AvgSpeedMode.MOVING, ReplayActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}), {})
            }
        }
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        rule.onNodeWithContentDescription("Speed chart, top 59 km/h, elevation 900 to 939 m").assertIsDisplayed()
        rule.onNodeWithText("Distance").performScrollTo().assertIsDisplayed()
    }
}
