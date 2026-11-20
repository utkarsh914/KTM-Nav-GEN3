package com.navigator.app.ui.feature.navigate

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.destination.LinkFailure
import com.navigator.app.ui.feature.navigate.link.LinkOutcome
import com.navigator.app.ui.feature.navigate.link.SharedLinkController
import com.navigator.app.ui.feature.navigate.link.SharedLinkState
import com.navigator.app.ui.shell.Feedback
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigateEffectsTest {
    @get:Rule val rule = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val host = SnackbarHostState()
    private val feedback = Feedback(scope, host)
    private val toit = SavedPlace(12.97, 77.64, "Toit")

    @After fun tearDown() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    private class Harness(val controller: SharedLinkController) {
        var stage by mutableStateOf(NavStage.BROWSE)
        var link by mutableStateOf<String?>(null)
        var consumed = 0
        val chosen = mutableListOf<SavedPlace>()
    }

    private fun harness(outcome: suspend (String) -> LinkOutcome): Harness {
        val h = Harness(SharedLinkController(scope, outcome))
        rule.setContent {
            KtmTheme(darkTheme = true) {
                val state by h.controller.state.collectAsState()
                SharedLinkEffects(h.controller, state, h.link, { h.consumed++; h.link = null }, h.stage, feedback, onResolved = { h.chosen += it })
            }
        }
        return h
    }

    @Test fun aSharedLinkIsOpenedOnceAndItsPlaceOffered() {
        val h = harness { LinkOutcome.Found(toit) }
        h.link = "https://maps.app.goo.gl/x"
        rule.waitForIdle()
        assertEquals(1, h.consumed)
        assertEquals(listOf(toit), h.chosen)
    }

    @Test fun whileRidingAResolvedLinkDoesNotReplaceGuidance() {
        val h = harness { LinkOutcome.Found(toit) }
        h.stage = NavStage.NAVIGATING
        rule.waitForIdle()
        h.link = "https://maps.app.goo.gl/x"
        rule.waitForIdle()
        assertEquals(emptyList<SavedPlace>(), h.chosen)
        assertNotNull("the rider is told", host.currentSnackbarData)
    }

    @Test fun aFailureThatArrivesMidTripIsDroppedNotLeftAsAStaleCard() {
        val h = harness { LinkOutcome.Failed(LinkFailure.NO_CONNECTION) }
        h.stage = NavStage.NAVIGATING
        rule.waitForIdle()
        h.link = "https://maps.app.goo.gl/x"
        rule.waitForIdle()
        assertEquals(SharedLinkState.Idle, h.controller.state.value)
        assertNotNull(host.currentSnackbarData)
    }

    @Test fun aFailureWhileBrowsingKeepsItsCardForRetry() {
        val h = harness { LinkOutcome.Failed(LinkFailure.NO_CONNECTION) }
        h.link = "https://maps.app.goo.gl/x"
        rule.waitForIdle()
        assertEquals(SharedLinkState.Failed, h.controller.state.value)
        assertNull(host.currentSnackbarData)
    }
}
