package com.navigator.app.ui.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared map sits behind the shell, as in KtmApp: the shell must let a drag or a tap on an empty spot reach it
 * (a Material Surface, as NavigationSuiteScaffold uses, swallows them), and must still keep a tab tap to itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class ShellTouchThroughTest {
    @get:Rule val rule = createComposeRule()

    private var drags = 0f
    private var taps = 0
    private val selected = mutableListOf<Tab>()

    @Composable
    private fun BehindTheShell() {
        val feedback = rememberFeedback()
        KtmTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) { detectDragGestures { _, d -> drags += d.getDistance() } }
                        .pointerInput(Unit) { detectTapGestures { taps++ } },
                )
                MainShell(
                    currentTab = Tab.Navigate, immersive = false, settingsNeedsAttention = false, bike = { null },
                    onSelectTab = { selected += it }, feedback = feedback, strip = { Text("STRIP") },
                    content = { Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    private fun dragOnEmptyContent() = rule.onRoot().performTouchInput {
        val start = Offset(width * 0.6f, height * 0.3f)
        down(start)
        moveTo(start + Offset(12f, 9f))
        moveTo(start + Offset(60f, 40f))
        moveTo(start + Offset(120f, 55f))
        up()
    }

    @Test fun aDragOnEmptyContentReachesTheMapBehind() {
        rule.setContent { BehindTheShell() }
        dragOnEmptyContent()
        assertTrue("the map behind got no drag: $drags", drags > 50f)
    }

    @Test fun aTapOnEmptyContentReachesTheMapBehind() {
        rule.setContent { BehindTheShell() }
        rule.onRoot().performTouchInput {
            down(Offset(200f, 400f)); moveTo(Offset(202f, 401f)); up()
        }
        assertEquals(1, taps)
    }

    @Test fun aTabTapSelectsTheTabAndStaysOffTheMap() {
        rule.setContent { BehindTheShell() }
        rule.onNodeWithText("Rides").performTouchInput {
            down(center); moveBy(Offset(2f, 1f)); up()
        }
        assertEquals(listOf(Tab.Rides), selected)
        assertEquals(0, taps)
    }

    @Test @Config(sdk = [36], qualifiers = "w780dp-h360dp") fun theSameInLandscapeWithTheRail() {
        rule.setContent { BehindTheShell() }
        dragOnEmptyContent()
        assertTrue("the map behind got no drag: $drags", drags > 50f)
        rule.onNodeWithText("Places").performTouchInput { down(center); moveBy(Offset(1f, 1f)); up() }
        assertEquals(listOf(Tab.Places), selected)
    }
}
