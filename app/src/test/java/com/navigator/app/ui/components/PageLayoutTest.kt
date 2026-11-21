package com.navigator.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How pages lay themselves out when the window is short or has bars: nothing is cut off, nothing sits under a bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PageLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun anEmptyPageShorterThanItsContentScrollsToItsLastButton() {
        rule.setContent {
            KtmTheme(darkTheme = false) {
                Box(Modifier.height(160.dp)) {
                    EmptyState(
                        KtmIcons.Route, "No rides", "Record one.",
                        primary = StateAction("Record a ride") {}, secondary = StateAction("Import GPX") {}, tertiary = StateAction("Turn on automatic recording") {},
                    )
                }
            }
        }
        rule.onNodeWithText("Turn on automatic recording").performScrollTo().assertIsDisplayed()
    }

    @Test fun pageInsetsKeepContentAboveTheGestureArea() {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent {
            KtmTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().pageInsets()) { Box(Modifier.fillMaxSize().testTag("page")) }
            }
        }
        val before = rule.onNodeWithTag("page").getUnclippedBoundsInRoot()
        rule.runOnUiThread {
            val bars = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 96)).build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, bars)
        }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("page").getUnclippedBoundsInRoot()
        assertEquals((before.bottom - before.top).value - 96f / rule.activity.resources.displayMetrics.density, (after.bottom - after.top).value, 0.5f)
    }

    @Test fun aSheetLiftsItsContentAboveTheKeyboard() {
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        rule.setContent {
            KtmTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    KtmSheetContent(title = "Name") { Box(Modifier.fillMaxWidth().height(40.dp).testTag("field")) }
                }
            }
        }
        val before = rule.onNodeWithTag("field").getUnclippedBoundsInRoot()
        val keyboardPx = 300
        rule.runOnUiThread {
            val ime = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardPx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, ime)
        }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("field").getUnclippedBoundsInRoot()
        assertEquals(keyboardPx / rule.activity.resources.displayMetrics.density, (before.bottom - after.bottom).value, 0.5f)
    }

    @Test fun anEmptyStateThatDoesNotScrollFitsInsideAList() {
        rule.setContent {
            KtmTheme(darkTheme = false) {
                androidx.compose.foundation.lazy.LazyColumn {
                    item { EmptyState(KtmIcons.Bookmark, "Nothing saved", "Save a place.", scrollable = false) }
                }
            }
        }
        rule.onNodeWithText("Nothing saved").assertIsDisplayed()
    }
}
