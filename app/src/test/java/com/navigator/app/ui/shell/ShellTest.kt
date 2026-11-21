package com.navigator.app.ui.shell

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.navigator.app.ui.components.PillTone
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.ThemeMode
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
class ShellTest {
    @get:Rule val rule = createComposeRule()

    private var nav: NavHostController? = null
    private var tab by mutableStateOf<Tab?>(null)
    private var immersive by mutableStateOf(false)
    private var attention by mutableStateOf(false)
    private var bike by mutableStateOf<BikeRailStatus?>(null)
    private lateinit var feedback: Feedback
    private val selected = mutableListOf<Tab>()

    @Composable
    private fun Shell(content: @Composable () -> Unit) {
        feedback = rememberFeedback()
        KtmTheme(darkTheme = true) {
            MainShell(
                currentTab = tab, immersive = immersive, settingsNeedsAttention = attention, bike = { bike },
                onSelectTab = { selected += it }, feedback = feedback, strip = { Text("STRIP") }, content = content,
            )
        }
    }

    @Test fun showsFourTabsAndReportsTheSelectedOne() {
        tab = Tab.Rides
        rule.setContent { Shell { Box {} } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (t in Tab.entries) rule.onNodeWithText(context.getString(t.label)).assertIsDisplayed()
        rule.onNodeWithText("Rides").assertIsSelected()
        rule.onNodeWithText("Places").performClick()
        assertEquals(listOf(Tab.Places), selected)
    }

    @Test fun theSettingsDotIsAnnouncedAndOnlyWhileSomethingNeedsAttention() {
        tab = Tab.Navigate
        attention = true
        rule.setContent { Shell { Box {} } }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs attention")).assertExists()
        attention = false
        rule.waitForIdle()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs attention")).assertDoesNotExist()
    }

    @Test fun theBikeDotIsOnlyInTheLandscapeRail() {
        tab = Tab.Rides
        bike = BikeRailStatus(PillTone.Connected, "Connected")
        rule.setContent { Shell { Box {} } }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Connected")).assertDoesNotExist()
    }

    @Test @Config(sdk = [36], qualifiers = "w780dp-h360dp") fun theRailCarriesTheBikeStateOnTheNavigateItem() {
        tab = Tab.Rides
        bike = BikeRailStatus(PillTone.Connected, "Connected")
        rule.setContent { Shell { Box {} } }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Connected")).assertExists()
        bike = null
        rule.waitForIdle()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Connected")).assertDoesNotExist()
    }

    @Test fun theStripShowsAboveTheBarUnlessRidingTakesTheScreen() {
        tab = Tab.Navigate
        rule.setContent { Shell { Box {} } }
        rule.onNodeWithText("STRIP").assertIsDisplayed()
        immersive = true
        rule.waitForIdle()
        rule.onAllNodesWithText("STRIP").assertCountEquals(0)
    }

    @Test fun feedbackShowsOneSnackbarAtATimeAndRunsItsAction() {
        tab = Tab.Navigate
        var undone = 0
        rule.setContent { Shell { Box {} } }
        feedback.snackbar("Ride deleted", "Undo") { undone++ }
        rule.waitUntil(2_000) { rule.onAllNodes(hasText("Ride deleted")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(1, undone)
        feedback.snackbar("First")
        feedback.snackbar("Second")
        rule.waitUntil(2_000) { rule.onAllNodes(hasText("Second")).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("First").assertCountEquals(0)
    }

    @Test fun anOfferThatIsReplacedReportsItsDismissalButATakenOneDoesNot() {
        tab = Tab.Navigate
        var dismissed = 0
        var undone = 0
        rule.setContent { Shell { Box {} } }
        feedback.snackbar("Deleted", "Undo", onDismiss = { dismissed++ }, onAction = { undone++ })
        rule.waitUntil(2_000) { rule.onAllNodes(hasText("Deleted")).fetchSemanticsNodes().isNotEmpty() }
        feedback.snackbar("Something else")
        rule.waitUntil(2_000) { rule.onAllNodes(hasText("Something else")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, dismissed)
        assertEquals(0, undone)
        feedback.snackbar("Deleted again", "Undo", onDismiss = { dismissed++ }, onAction = { undone++ })
        rule.waitUntil(2_000) { rule.onAllNodes(hasText("Deleted again")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(1, undone)
        assertEquals("taking the action is not a dismissal", 1, dismissed)
    }

    @Test fun theRidingChipIsReplacedByTheNewestMessage() {
        tab = Tab.Navigate
        rule.setContent { Shell { Box {} } }
        feedback.chip("Stop added", ChipTone.Ok)
        val first = feedback.chip!!.id
        feedback.chip("Couldn't add the stop", ChipTone.Warn)
        assertTrue(feedback.chip!!.id > first)
        assertEquals("Couldn't add the stop", feedback.chip!!.text)
        assertEquals(ChipTone.Warn, feedback.chip!!.tone)
    }

    @Test fun theRidingChipClearsAfterFourSecondsUnlessReplaced() {
        tab = Tab.Navigate
        rule.setContent { Shell { Box {} } }
        rule.mainClock.autoAdvance = false
        feedback.chip("One", ChipTone.Info)
        rule.mainClock.advanceTimeBy(Feedback.CHIP_MS - 500)
        feedback.chip("Two", ChipTone.Info)
        rule.mainClock.advanceTimeBy(Feedback.CHIP_MS - 500)
        assertEquals("the first timer must not clear the newer chip", "Two", feedback.chip?.text)
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(null, feedback.chip)
    }

    @Composable
    private fun Host() {
        val controller = rememberNavController().also { nav = it }
        val entry by controller.currentBackStackEntryAsState()
        tab = entry?.destination.tab()
        NavHost(controller, startDestination = Route.Navigate) {
            composable<Route.Navigate> { Counter("nav") }
            composable<Route.Rides> { Counter("rides") }
            composable<Route.Settings> { Counter("settings") }
            composable<Route.Logs> { Text("LOGS") }
            composable<Route.Places> { Text("PLACES") }
        }
    }

    @Composable
    private fun Counter(name: String) {
        var n by rememberSaveable { mutableIntStateOf(0) }
        Text("$name:$n")
        Button(onClick = { n++ }) { Text("inc-$name") }
    }

    @Test fun tabsKeepTheirStateWhenYouComeBack() {
        rule.setContent { KtmTheme(darkTheme = true) { Host() } }
        rule.onNodeWithText("inc-nav").performClick()
        rule.onNodeWithText("nav:1").assertIsDisplayed()
        rule.runOnIdle { nav!!.openTab(Tab.Rides) }
        rule.onNodeWithText("rides:0").assertIsDisplayed()
        rule.runOnIdle { nav!!.openTab(Tab.Navigate) }
        rule.waitForIdle()
        rule.onNodeWithText("nav:1").assertIsDisplayed()
        assertEquals(Tab.Navigate, tab)
    }

    @Test fun backFromAnotherTabReturnsToNavigateAndThenLeaves() {
        rule.setContent { KtmTheme(darkTheme = true) { Host() } }
        rule.runOnIdle { nav!!.openTab(Tab.Settings) }
        rule.runOnIdle { nav!!.openTab(Tab.Rides) }
        rule.waitForIdle()
        assertEquals(Tab.Rides, tab)
        rule.runOnIdle { assertTrue(nav!!.popBackStack()) }
        rule.waitForIdle()
        assertEquals("one Back from any tab returns to Navigate", Tab.Navigate, tab)
        rule.runOnIdle { assertFalse("then the system handles Back", nav!!.popBackStack()) }
    }

    @Test fun aPushedPageHasNoTabAndBackReturnsToItsTab() {
        rule.setContent { KtmTheme(darkTheme = true) { Host() } }
        rule.runOnIdle { nav!!.openTab(Tab.Settings) }
        rule.runOnIdle { nav!!.navigate(Route.Logs) }
        rule.waitForIdle()
        assertEquals(null, tab)
        rule.runOnIdle { nav!!.popBackStack() }
        rule.waitForIdle()
        assertEquals(Tab.Settings, tab)
    }

    @Test fun openingATabClosesAPageOverNavigateInsteadOfRestoringIt() {
        rule.setContent { KtmTheme(darkTheme = true) { Host() } }
        rule.runOnIdle { nav!!.push(Route.Logs) }
        rule.waitForIdle()
        assertEquals(null, tab)
        rule.runOnIdle { nav!!.openTab(Tab.Navigate) }
        rule.waitForIdle()
        assertEquals("a notification tap must land on Navigate, not put Logs back", Tab.Navigate, tab)
        rule.onAllNodesWithText("LOGS").assertCountEquals(0)
    }

    @Test fun openingAnotherTabFromAPushedPageKeepsTheOtherTabsState() {
        rule.setContent { KtmTheme(darkTheme = true) { Host() } }
        rule.runOnIdle { nav!!.openTab(Tab.Rides) }
        rule.onNodeWithText("inc-rides").performClick()
        rule.runOnIdle { nav!!.push(Route.Logs) }
        rule.runOnIdle { nav!!.openTab(Tab.Settings) }
        rule.waitForIdle()
        assertEquals(Tab.Settings, tab)
        rule.runOnIdle { nav!!.openTab(Tab.Rides) }
        rule.waitForIdle()
        rule.onNodeWithText("rides:1").assertIsDisplayed()
    }

    @Test fun aSecondTapOnAPushRowDoesNotStackACopyAndASecondBackDoesNotPopTheRoot() {
        rule.setContent { KtmTheme(darkTheme = true) { Host() } }
        rule.runOnIdle { nav!!.push(Route.Logs); nav!!.push(Route.Logs) }
        rule.waitForIdle()
        rule.runOnIdle {
            val entry = nav!!.currentBackStackEntry!!
            nav!!.popFrom(entry)
            nav!!.popFrom(entry) // the page is leaving: this tap must not pop the root
        }
        rule.waitForIdle()
        assertEquals(Tab.Navigate, tab)
        assertEquals(Route.Navigate::class.simpleName, nav!!.currentDestination?.route?.substringAfterLast('.'))
    }

    @Test fun immersiveStaysOnUntilEveryScreenThatAskedHasLeft() {
        val chrome = ShellChrome()
        var a by mutableStateOf(true)
        var b by mutableStateOf(true)
        rule.setContent { ImmersiveHosts(chrome, a, b) }
        rule.runOnIdle { assertTrue(chrome.immersive) }
        a = false
        rule.waitForIdle()
        assertTrue("the second screen still needs it", chrome.immersive)
        b = false
        rule.waitForIdle()
        assertFalse(chrome.immersive)
    }

    @Test fun aPageOverTheFirstRunPairingStillCountsAsSetup() {
        var controller: NavHostController? = null
        val tracker = SetupTracker()
        rule.setContent {
            KtmTheme(darkTheme = true) {
                val c = rememberNavController().also { controller = it }
                NavHost(c, startDestination = Route.Pairing(forced = true)) {
                    composable<Route.Pairing> { Text("PAIRING") }
                    composable<Route.Logs> { Text("LOGS") }
                    composable<Route.Navigate> { Text("NAV") }
                }
            }
        }
        rule.runOnIdle { assertTrue(tracker.inSetup(controller!!.currentBackStackEntry)) }
        rule.runOnIdle { controller!!.push(Route.Logs) }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue("Logs over forced pairing is still setup", tracker.inSetup(controller!!.currentBackStackEntry)) }
        rule.runOnIdle { controller!!.openRoot(Route.Navigate) }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse(tracker.inSetup(controller!!.currentBackStackEntry)) }
        rule.runOnIdle { controller!!.push(Route.Logs) }
        rule.waitForIdle()
        rule.runOnIdle { assertFalse("Logs over the tabs is not setup", tracker.inSetup(controller!!.currentBackStackEntry)) }
    }

    @Composable
    private fun ImmersiveHosts(chrome: ShellChrome, a: Boolean, b: Boolean) {
        CompositionLocalProvider(LocalShellChrome provides chrome) {
            ImmersiveEffect(a)
            ImmersiveEffect(b)
        }
    }

    @Test fun theRailIsUsedForWideWindowsAndTheBarForPortrait() {
        assertEquals(NavigationSuiteType.ShortNavigationBarCompact, shellNavigationType(minWidthDp = 360))
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, shellNavigationType(minWidthDp = 874))
    }

    @Test fun lightAndDarkResolveAsDesigned() {
        assertTrue(resolveAppDark(ThemeMode.DARK, systemDark = false))
        assertFalse(resolveAppDark(ThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveAppDark(ThemeMode.SYSTEM, systemDark = true))
        assertTrue("browse map follows the app", resolveSurfaceDark(true, NavThemeMode.DAY_NIGHT, ridingNow = false, night = false))
        assertFalse("riding by day", resolveSurfaceDark(true, NavThemeMode.DAY_NIGHT, ridingNow = true, night = false))
        assertTrue("riding at night", resolveSurfaceDark(false, NavThemeMode.DAY_NIGHT, ridingNow = true, night = true))
        assertFalse("riding follows the app when Day/Night is off", resolveSurfaceDark(false, NavThemeMode.APP, ridingNow = true, night = true))
    }
}
