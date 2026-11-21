package com.navigator.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A snackbar sits clear of whatever a screen draws over the foot of the content area. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class SnackbarClearanceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theLargestClearanceWinsAndLeavingRemovesIt() {
        val feedback = Feedback(CoroutineScope(Dispatchers.Unconfined), androidx.compose.material3.SnackbarHostState())
        val a = Any(); val b = Any()
        feedback.cover(a, 120.dp, 0.dp)
        feedback.cover(b, 80.dp, 352.dp)
        assertEquals(Cleared(120.dp, 352.dp), feedback.cleared)
        feedback.uncover(a)
        assertEquals(Cleared(80.dp, 352.dp), feedback.cleared)
        feedback.uncover(b)
        assertEquals(Cleared.None, feedback.cleared)
    }

    @Test fun aSnackbarRisesAboveASheetAndComesBackWhenItGoes() {
        var sheet by mutableStateOf(true)
        lateinit var feedback: Feedback
        rule.setContent {
            feedback = rememberFeedback()
            KtmTheme(darkTheme = true) {
                CompositionLocalProvider(LocalSnackbarCover provides feedback) {
                    MainShell(
                        currentTab = Tab.Navigate, immersive = true, settingsNeedsAttention = false, bike = { null },
                        onSelectTab = {}, feedback = feedback, strip = {},
                    ) {
                        Box(Modifier.fillMaxSize()) { if (sheet) SnackbarClearance(bottom = 200.dp) }
                    }
                }
            }
        }
        feedback.snackbar("Removed")
        rule.waitForIdle()
        val lifted = rule.onNodeWithText("Removed").getUnclippedBoundsInRoot()
        sheet = false
        feedback.snackbar("Removed again")
        rule.waitForIdle()
        val rest = rule.onNodeWithText("Removed again").getUnclippedBoundsInRoot()
        assertTrue("lifted ${lifted.bottom} vs rest ${rest.bottom}", rest.bottom - lifted.bottom >= 150.dp)
    }
}
