package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LayerFadeTest {
    @get:Rule val rule = createComposeRule()

    private var target by mutableStateOf("A")

    private fun show() = rule.setContent {
        val spec = tween<Float>(200)
        AnimatedContent(
            target,
            transitionSpec = { EnterTransition.None togetherWith holdUntilFaded(spec) },
            label = "t",
        ) { s ->
            Box(Modifier.fillMaxSize().layerFade(this, spec, spec)) { Text(s) }
        }
    }

    @Test fun theOldContentStaysUntilItHasFadedOutAndThenGoes() {
        rule.mainClock.autoAdvance = false
        show()
        rule.runOnUiThread { target = "B" }
        repeat(6) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithText("A").assertExists()
        rule.onNodeWithText("B").assertExists()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("A").assertDoesNotExist()
        rule.onNodeWithText("B").assertExists()
    }

    @Test fun aSpringSpecHoldsTheOldContentForItsRealDurationToo() {
        val spring = androidx.compose.animation.core.spring<Float>(1f, 1600f)
        assertTrue("a spring takes time to settle: ${durationMillis(spring)} ms", durationMillis(spring) > 50)
        assertEquals(200, durationMillis(tween(200)))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AnimatedContent(
                target, transitionSpec = { EnterTransition.None togetherWith holdUntilFaded(spring) }, label = "t",
            ) { s -> Box(Modifier.fillMaxSize().layerFade(this, spring, spring)) { Text(s) } }
        }
        rule.runOnUiThread { target = "B" }
        repeat(3) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
        rule.onNodeWithText("A").assertExists()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("A").assertDoesNotExist()
    }
}
