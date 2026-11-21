package com.navigator.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
class KtmSnackbarTest {
    @get:Rule val rule = createComposeRule()
    private val host = SnackbarHostState()

    private fun show(): () -> SnackbarResult? {
        var result: SnackbarResult? = null
        rule.setContent {
            KtmTheme(darkTheme = true) {
                KtmSnackbarHost(host)
                LaunchedEffect(Unit) {
                    result = host.showSnackbar("Ride deleted", "Undo", duration = SnackbarDuration.Indefinite)
                }
            }
        }
        rule.waitForIdle()
        return { result }
    }

    @Test fun theActionReportsItWasPerformed() {
        val result = show()
        rule.onNodeWithText("Ride deleted").assertIsDisplayed()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertEquals(SnackbarResult.ActionPerformed, result())
    }

    @Test fun aScreenReaderCanDismissItWithoutTheAction() {
        val result = show()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))[0].performSemanticsAction(SemanticsActions.Dismiss)
        rule.waitForIdle()
        assertEquals(SnackbarResult.Dismissed, result())
    }
}
