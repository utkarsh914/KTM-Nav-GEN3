package com.navigator.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.navigator.app.ui.feature.settings.SettingsPage
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D41: the system Back (button or swipe) closes a page exactly like the top-left arrow does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class PageBackTest {
    @get:Rule val rule = createComposeRule()

    private val input = DirectNavigationEventInput()
    private val dispatcher = NavigationEventDispatcher().also { it.addInput(input) }
    private var nav: NavHostController? = null

    private fun swipe(vararg progress: Float, release: Boolean) {
        drag(*progress)
        finish(release)
    }

    private fun drag(vararg progress: Float) = rule.runOnUiThread {
        input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, progress.first()))
        progress.drop(1).forEach { input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, it)) }
    }

    private fun finish(release: Boolean) = rule.runOnUiThread { if (release) input.backCompleted() else input.backCancelled() }

    private fun host(content: androidx.navigation.NavGraphBuilder.() -> Unit = {}) = rule.setContent {
        val owner = object : NavigationEventDispatcherOwner { override val navigationEventDispatcher = dispatcher }
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
            KtmTheme(darkTheme = true, reducedMotion = false) {
                val controller = rememberNavController().also { nav = it }
                CompositionLocalProvider(LocalPageNav provides controller) {
                    NavHost(controller, startDestination = Route.Rides) {
                        page<Route.Rides> { Text("rides") }
                        page<Route.Logs> { Text("logs") }
                        page<Route.SymbolTest> { Text("symbols") }
                        page<Route.Places> {
                            var open by rememberSaveable { mutableStateOf(false) }
                            BackHandler(enabled = open) { open = false }
                            Column {
                                Text(if (open) "places:open" else "places:closed")
                                Button(onClick = { open = true }) { Text("open") }
                            }
                        }
                        content()
                    }
                }
            }
        }
    }

    private fun settle() = rule.mainClock.advanceTimeBy(2_000)

    @Test fun aSwipeMovesNothingWhileTheFingerIsDownAndACancelLeavesEverythingAsItWas() {
        host()
        rule.runOnIdle { nav!!.push(Route.Logs) }
        settle()
        drag(0.1f, 0.4f, 0.8f)
        rule.mainClock.advanceTimeBy(300)
        // Nothing follows the finger: the page under is not even composed for a preview, and the page has not moved.
        rule.onNodeWithText("logs").assertIsDisplayed()
        rule.onAllNodesWithText("rides").assertCountEquals(0)
        finish(release = false)
        settle()
        rule.onNodeWithText("logs").assertIsDisplayed()
        rule.onAllNodesWithText("rides").assertCountEquals(0)
        assertEquals(Route.Logs::class.simpleName, nav!!.currentDestination?.route?.substringAfterLast('.'))
    }

    @Test fun releasingTheSwipeClosesThePageTheSameWayTheArrowDoes() {
        host()
        rule.runOnIdle { nav!!.push(Route.Logs) }
        settle()
        swipe(0.2f, 0.9f, release = true)
        settle()
        rule.onNodeWithText("rides").assertIsDisplayed()
        assertEquals(Route.Rides::class.simpleName, nav!!.currentDestination?.route?.substringAfterLast('.'))
    }

    @Test fun backClosesWhatThePageOpenedBeforeThePageItself() {
        host()
        rule.runOnIdle { nav!!.push(Route.Places) }
        settle()
        rule.onNodeWithText("open").performClick()
        rule.onNodeWithText("places:open").assertIsDisplayed()
        swipe(0.5f, release = true)
        settle()
        rule.onNodeWithText("places:closed").assertIsDisplayed()
        swipe(0.5f, release = true)
        settle()
        rule.onNodeWithText("rides").assertIsDisplayed()
    }

    @Test fun twoQuickBacksGoUpTwoLevels() {
        host()
        rule.runOnIdle { nav!!.push(Route.Logs) }
        rule.runOnIdle { nav!!.push(Route.SymbolTest) }
        settle()
        swipe(0.5f, release = true)
        swipe(0.5f, release = true) // before the first has finished
        settle()
        rule.onNodeWithText("rides").assertIsDisplayed()
    }

    @Test fun anotherSettingsSectionOpensOverTheOneBeforeItAndADoubleTapDoesNotStackACopy() {
        host { page<Route.SettingsPage> { Text("settings page") } }
        rule.runOnIdle {
            nav!!.push(Route.SettingsPage(SettingsPage.BIKE_DASH))
            nav!!.push(Route.SettingsPage(SettingsPage.BIKE_DASH))
        }
        settle()
        assertEquals("a second tap does not stack a copy", 2, rule.runOnIdle { nav!!.pageStackIds().size })
        rule.runOnIdle { nav!!.push(Route.SettingsPage(SettingsPage.PERMISSIONS)) }
        settle()
        assertEquals("Permissions opens over Bike & dash", 3, rule.runOnIdle { nav!!.pageStackIds().size })
        swipe(0.5f, release = true)
        settle()
        rule.runOnIdle {
            assertEquals("Back returns to Bike & dash", SettingsPage.BIKE_DASH, nav!!.currentBackStackEntry!!.toRoute<Route.SettingsPage>().page)
        }
    }

    @Test fun aPageClosesOnlyWhenItIsOnTopWithAPageUnderIt() {
        assertTrue(canClose(listOf("a", "b"), "b"))
        assertFalse("the root has nothing under it", canClose(listOf("a"), "a"))
        assertFalse("a page under another is not the one to close", canClose(listOf("a", "b"), "a"))
        assertFalse("a page that already left the stack", canClose(listOf("a"), "b"))
        assertFalse(canClose(emptyList(), "a"))
    }
}
