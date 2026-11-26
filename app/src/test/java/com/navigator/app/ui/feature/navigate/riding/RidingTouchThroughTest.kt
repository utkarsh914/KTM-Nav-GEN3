package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.RidingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The maneuver card sits over the map: a brush of a finger on it (with drift) must not pan the map or stop following. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class RidingTouchThroughTest {
    @get:Rule val rule = createComposeRule()
    private var drags = 0f
    private var taps = 0

    private fun setUp() = rule.setContent {
        KtmTheme(darkTheme = true) {
            RidingTheme(night = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectDragGestures { _, d -> drags += d.getDistance() } }
                            .pointerInput(Unit) { detectTapGestures { taps++ } },
                    )
                    RidingScreen(
                        maneuver = ManeuverUi.Active(TurnIcon.QUITE_RIGHT, "450 m", "Turn right onto", "Jalaun Rd", null, ManeuverFooter.None, false),
                        bar = TripBarUi(
                            speed = "42", speedKnown = true, overspeed = false, alert = null, timeLeft = TimeLeft(0, 25),
                            distance = "12 km", arrival = "14:32", progress = 0.5f, destination = "Toit", starting = false,
                        ),
                        chip = null, recenter = false, bearing = { 0f },
                        actions = RidingActions(onOpenSheet = {}, onMapButton = {}, onNorthUp = {}, onDismissSheet = {}),
                        onInsets = { _, _ -> },
                    )
                }
            }
        }
    }

    @Test fun aBrushOfAFingerOnTheManeuverCardDoesNotReachTheMap() {
        setUp()
        rule.onNodeWithText("Jalaun Rd").performTouchInput {
            down(center); moveBy(Offset(30f, 20f)); moveBy(Offset(60f, 10f)); up()
        }
        rule.runOnIdle { assertEquals("no pan", 0f, drags, 0f); assertEquals("no tap", 0, taps) }
    }

    @Test fun anEmptySpotBesideTheCardStillReachesTheMap() {
        setUp()
        rule.onRoot().performTouchInput {
            val start = Offset(width * 0.5f, height * 0.5f)
            down(start); moveTo(start + Offset(40f, 20f)); moveTo(start + Offset(120f, 50f)); up()
        }
        rule.runOnIdle { assertTrue("the map got the drag: $drags", drags > 50f) }
    }

    @Test fun withTheSheetOpenATapOnTheDimmedCardClosesIt() {
        var dismissed = 0
        rule.setContent {
            KtmTheme(darkTheme = true) {
                RidingTheme(night = true) {
                    RidingScreen(
                        maneuver = ManeuverUi.Active(TurnIcon.QUITE_RIGHT, "450 m", "Turn right onto", "Jalaun Rd", null, ManeuverFooter.None, false),
                        bar = TripBarUi(
                            speed = "42", speedKnown = true, overspeed = false, alert = null, timeLeft = TimeLeft(0, 25),
                            distance = "12 km", arrival = "14:32", progress = 0.5f, destination = "Toit", starting = false,
                        ),
                        chip = null, recenter = false, bearing = { 0f },
                        actions = RidingActions(onOpenSheet = {}, onMapButton = {}, onNorthUp = {}, onDismissSheet = { dismissed++ }),
                        onInsets = { _, _ -> }, sheetOpen = true,
                    )
                }
            }
        }
        rule.onRoot().performTouchInput {
            val at = Offset(width * 0.5f, 40f + height * 0.08f)
            down(at); moveTo(at + Offset(2f, 1f)); up()
        }
        rule.runOnIdle { assertEquals("the scrim under the card gets the tap", 1, dismissed) }
    }
}
