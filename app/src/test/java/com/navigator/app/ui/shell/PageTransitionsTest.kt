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
import org.junit.Assert.assertNull
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

    private class Destinations(val rides: androidx.navigation.NavDestination, val places: androidx.navigation.NavDestination,
        val navigate: androidx.navigation.NavDestination, val replay: androidx.navigation.NavDestination,
        val trim: androidx.navigation.NavDestination, val logs: androidx.navigation.NavDestination)

    private fun realDestinations(): Destinations {
        var nav: NavHostController? = null
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = true) {
                val controller = rememberNavController().also { nav = it }
                NavHost(controller, startDestination = Route.Rides) {
                    page<Route.Rides> { Text("rides") }
                    page<Route.Places> { Text("places") }
                    page<Route.Navigate> { Text("map") }
                    page<Route.RideReplay> { Text("replay") }
                    page<Route.TrimRide> { Text("trim") }
                    page<Route.Logs> { Text("logs") }
                }
            }
        }
        fun at(route: Route) = rule.runOnIdle { nav!!.navigate(route); nav!!.currentBackStackEntry!!.destination }
        val rides = rule.runOnIdle { nav!!.currentBackStackEntry!!.destination }
        return Destinations(rides, at(Route.Places), at(Route.Navigate), at(Route.RideReplay("r")), at(Route.TrimRide("r")), at(Route.Logs))
    }

    @Test fun openingARideIsTheCardsOwnMoveNotAPageToMapDissolve() {
        val d = realDestinations()
        val open = pageMove(d.rides, d.replay)
        assertTrue(open.rideContainer)
        assertFalse("Rides must not fade out behind the growing card", open.pageToMap)
        assertFalse(open.mapToMap)
        val close = pageMove(d.replay, d.rides, pop = true)
        assertTrue(close.rideContainer)
        assertFalse(close.mapToPage)
        assertNull("Rides stays put while the replay covers it", pageFade(open, entering = false, pop = false, reduced = false))
        assertNull(pageFade(close, entering = true, pop = true, reduced = false))
        assertEquals(PageFade.Fast, pageFade(close, entering = false, pop = true, reduced = false))
    }

    @Test fun everyMoveBetweenRealDestinationsFollowsTheMatrix() {
        val d = realDestinations()
        val all = listOf(d.rides, d.places, d.navigate, d.replay, d.trim, d.logs)
        for (a in all) for (b in all) {
            if (a === b) continue
            for (pop in listOf(false, true)) {
                val m = pageMove(a, b, pop)
                val enter = pageFade(m, entering = true, pop = pop, reduced = false)
                val leave = pageFade(m, entering = false, pop = pop, reduced = false)
                val label = "${a.route} -> ${b.route} pop=$pop"
                if (m.rideContainer) {
                    assertFalse(label, m.pageToMap || m.mapToMap || m.mapToPage)
                    if (!pop) assertNull(label, leave)
                    if (pop) assertNull(label, enter)
                }
                if (pop && m.tab) {
                    assertNull("$label: the page it returns to does not fade", enter)
                    assertEquals(label, PageFade.Fast, leave)
                }
                if (pop && !m.tab && !m.rideContainer && !m.pageToMap && !m.mapToMap && !m.mapToPage) {
                    assertNull(label, enter); assertNull(label, leave)
                }
                if (!pop && !m.tab && !m.rideContainer && !m.pageToMap && !m.mapToMap && !m.mapToPage) {
                    assertNull("$label: a plain push only slides", enter); assertNull(label, leave)
                }
            }
        }
    }

    @Test fun aBackMoveIsRecognisedByTheArrivingPageNotTheLeavingOne() {
        var nav: NavHostController? = null
        lateinit var history: BackStackHistory
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = true) {
                val controller = rememberNavController().also { nav = it }
                history = androidx.compose.runtime.remember(controller) { BackStackHistory(controller) }
                androidx.compose.runtime.DisposableEffect(history) { history.start(); onDispose { history.stop() } }
                NavHost(controller, startDestination = Route.Navigate) {
                    page<Route.Navigate> { Text("map") }
                    page<Route.Rides> { Text("rides") }
                    page<Route.Places> { Text("places") }
                    page<Route.Logs> { Text("logs") }
                }
            }
        }
        fun top() = rule.runOnIdle { nav!!.currentBackStackEntry!!.id }
        val map = top()
        rule.runOnIdle { nav!!.openTab(Tab.Rides) }
        val rides = top()
        assertFalse("a tab switch away from Navigate is a push", history.isPop(rides))
        rule.runOnIdle { nav!!.openTab(Tab.Places) }
        val places = top()
        assertFalse("tab to tab is a push too, although the old tab leaves the stack", history.isPop(places))
        rule.runOnIdle { nav!!.navigate(Route.Logs) }
        val logs = top()
        assertFalse(history.isPop(logs))
        rule.runOnIdle { nav!!.popBackStack() }
        assertTrue("back to the tab under the page", history.isPop(top()))
        assertEquals(places, top())
        rule.runOnIdle { nav!!.openTab(Tab.Navigate) }
        assertEquals(map, top())
        assertTrue("any tab back to Navigate is a pop", history.isPop(map))
    }

    @Test fun revisitingATabIsStillAPushNotABack() {
        var nav: NavHostController? = null
        lateinit var history: BackStackHistory
        rule.setContent {
            KtmTheme(darkTheme = true, reducedMotion = true) {
                val controller = rememberNavController().also { nav = it }
                history = androidx.compose.runtime.remember(controller) { BackStackHistory(controller) }
                androidx.compose.runtime.DisposableEffect(history) { history.start(); onDispose { history.stop() } }
                NavHost(controller, startDestination = Route.Navigate) {
                    page<Route.Navigate> { Text("map") }
                    page<Route.Rides> { Text("rides") }
                    page<Route.Places> { Text("places") }
                    page<Route.Settings> { Text("settings") }
                    page<Route.Logs> { Text("logs") }
                }
            }
        }
        fun top() = rule.runOnIdle { nav!!.currentBackStackEntry!!.id }
        fun tab(t: Tab): String { rule.runOnIdle { nav!!.openTab(t) }; return top() }
        tab(Tab.Rides)
        tab(Tab.Navigate)
        assertTrue(history.isPop(top()))
        val rides = tab(Tab.Rides)
        assertFalse("Navigate, Rides, Navigate, Rides: the second Rides arrives like the first", history.isPop(rides))
        tab(Tab.Places)
        val ridesAgain = tab(Tab.Rides)
        assertFalse("Rides, Places, Rides", history.isPop(ridesAgain))
        rule.runOnIdle { nav!!.navigate(Route.Logs) }
        val settings = tab(Tab.Settings)
        assertFalse("a tab opened from a page pushed over another tab", history.isPop(settings))
        rule.runOnIdle { nav!!.navigate(Route.Logs) }
        rule.runOnIdle { nav!!.popBackStack() }
        assertTrue("back from the page", history.isPop(top()))
    }
}
