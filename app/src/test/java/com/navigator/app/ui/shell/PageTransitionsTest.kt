package com.navigator.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.ui.theme.KtmTheme
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
class PageTransitionsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aPageIsCoveredOnlyWhileSomethingIsPushedOverIt() {
        assertTrue(isCovered(listOf("graph", "a", "b"), "a"))
        assertFalse("the top page is not covered", isCovered(listOf("graph", "a", "b"), "b"))
        assertFalse("a popped page has left the stack", isCovered(listOf("graph", "a"), "b"))
    }

    @Test fun pushingAndPoppingAPageKeepsBothHostedAndShowsTheRightOne() {
        var nav: NavHostController? = null
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = false) {
                val controller = rememberNavController().also { nav = it }
                CompositionLocalProvider(LocalPageNav provides controller) {
                    NavHost(controller, startDestination = Route.Rides) {
                        page<Route.Rides> { Text("rides") }
                        page<Route.Logs> { Text("logs") }
                    }
                }
            }
        }
        rule.onNodeWithText("rides").assertIsDisplayed()
        rule.runOnIdle { nav!!.navigate(Route.Logs) }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("logs").assertIsDisplayed()
        rule.runOnIdle { nav!!.popBackStack() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("rides").assertIsDisplayed()
    }

    @Test fun aPushDimsThePageUnderneathExceptBetweenTabs() {
        var nav: NavHostController? = null
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = true) {
                val controller = rememberNavController().also { nav = it }
                CompositionLocalProvider(LocalPageNav provides controller) {
                    NavHost(controller, startDestination = Route.Rides) {
                        page<Route.Rides> { Text("rides") }
                        page<Route.Places> { Text("places") }
                        page<Route.Logs> { Text("logs") }
                    }
                }
            }
        }
        val rides = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        rule.runOnIdle { nav!!.navigate(Route.Places) }
        val places = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        rule.runOnIdle { nav!!.navigate(Route.Logs) }
        val logs = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        assertFalse("two tabs fade through each other", dimsUnder(rides, places))
        assertTrue("a page pushed over a tab dims it", dimsUnder(places, logs))
    }

    @Test fun mapPagesAreTheOnesThatShowTheSharedMapAndDissolveOverOtherPages() {
        var nav: NavHostController? = null
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = false) {
                val controller = rememberNavController().also { nav = it }
                CompositionLocalProvider(LocalPageNav provides controller) {
                    NavHost(controller, startDestination = Route.Rides) {
                        page<Route.Rides> { Text("rides") }
                        page<Route.Navigate> { Text("map") }
                        page<Route.TrimRide> { Text("trim") }
                    }
                }
            }
        }
        val rides = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        rule.runOnIdle { nav!!.navigate(Route.Navigate) }
        val navigate = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("map").assertIsDisplayed()
        rule.runOnIdle { nav!!.navigate(Route.TrimRide("r")) }
        val trim = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("trim").assertIsDisplayed()
        assertFalse(rides.isMapPage())
        assertTrue(navigate.isMapPage())
        assertTrue(trim.isMapPage())
        rule.runOnIdle { nav!!.popBackStack(); nav!!.popBackStack() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("rides").assertIsDisplayed()
    }

    /** A real finger: down, a few pixels of drift, up. performClick() sends none of the drift, which is what cancels taps. */
    private fun ComposeContentTestRule.fingerTap(tag: String) {
        onNodeWithTag(tag).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(3f, 2f))
            up()
        }
    }

    @Test fun anEmptySpotOnAnOpaquePageDoesNotReachTheMapBehindIt() {
        var mapTouches = 0
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = true) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().testTag("map").clickable { mapTouches++ })
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().testTag("page").coverGround()) { Text("settings") }
                }
            }
        }
        rule.fingerTap("page")
        rule.runOnIdle { assertEquals(0, mapTouches) }
    }

    @Test fun aButtonOnAnOpaquePageStillGetsATapWithARealFinger() {
        var taps = 0
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = true) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().coverGround()) {
                    Text("row", Modifier.testTag("row").clickable { taps++ })
                }
            }
        }
        rule.fingerTap("row")
        rule.runOnIdle { assertEquals(1, taps) }
    }
}
