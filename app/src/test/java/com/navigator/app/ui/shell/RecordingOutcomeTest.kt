package com.navigator.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.navigator.app.ride.RideEvents
import com.navigator.app.ride.RideFinish
import com.navigator.app.ride.RideSession
import com.navigator.app.ui.theme.KtmTheme
import org.junit.After
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
class RecordingOutcomeTest {
    @get:Rule val rule = createComposeRule()
    private var feedback: Feedback? = null
    private val ui = RecordingUi()

    @After fun reset() { RideSession.ended(); RideEvents.clear() }

    private fun host() = rule.setContent {
        KtmTheme(darkTheme = true) {
            feedback = rememberFeedback()
            // Only the outcome is composed: the strip is not, as while the riding screen is up.
            RecordingOutcome(ui, feedback!!, onOpenRides = {})
        }
    }

    private fun message() = feedback!!.snackbarHost.currentSnackbarData?.visuals?.message

    @Test fun aRideTheStripAnswersForEndsWhileTheStripIsNotOnScreenAndIsStillAnnounced() {
        host()
        rule.runOnIdle { RideSession.started(manual = true, nowMs = 0L) }
        rule.waitForIdle()
        assertTrue("the ride is the strip's to announce", ui.awaitingResult)
        rule.runOnIdle { RideSession.ended(); RideEvents.publish(RideFinish.Discarded) }
        rule.waitForIdle()
        assertEquals("Ride too short to keep", message())
        assertFalse("announced once", ui.awaitingResult)
    }

    @Test fun aRideTheStripDoesNotAnswerForIsNotAnnounced() {
        host()
        // An automatic ride in the in-app engine: not the strip's.
        rule.runOnIdle { RideSession.started(manual = false, nowMs = 0L) }
        rule.waitForIdle()
        rule.runOnIdle { RideSession.ended(); RideEvents.publish(RideFinish.Discarded) }
        rule.waitForIdle()
        assertNull(message())
    }

    @Test fun aStopAskedForSurvivesTheStripLeaving() {
        host()
        rule.runOnIdle { ui.confirmStop = true }
        rule.waitForIdle()
        rule.onNodeWithText("Stop recording?").assertIsDisplayed()
    }
}
