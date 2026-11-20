package com.navigator.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.feature.replay.trim.TrimRangeBar
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GestureControlsTest {
    @get:Rule val rule = createComposeRule()

    private fun hsvAfter(initial: Hsv, act: () -> Unit): Hsv {
        var hsv = initial
        rule.setContent {
            KtmTheme(darkTheme = true) { Box(Modifier.width(300.dp)) { KtmColorPicker(hsv, { hsv = it }, "Shade", "Hue") } }
        }
        act()
        rule.waitForIdle()
        return hsv
    }

    @Test fun tappingTheShadePanelSetsSaturationAndBrightness() {
        val result = hsvAfter(Hsv(200f, 0.5f, 0.5f)) {
            // Near the top-right: saturated and bright.
            rule.onNodeWithContentDescription("Shade").performTouchInput { click(Offset(width * 0.9f, height * 0.1f)) }
        }
        assertEquals(0.9f, result.sat, 0.03f)
        assertEquals(0.9f, result.value, 0.03f)
        assertEquals(200f, result.hue, 0.001f)
    }

    @Test fun draggingTheShadePanelFollowsTheFinger() {
        val result = hsvAfter(Hsv(10f, 1f, 1f)) {
            rule.onNodeWithContentDescription("Shade").performTouchInput { swipe(Offset(width - 2f, 2f), Offset(2f, height - 2f)) }
        }
        assertTrue("sat ${result.sat}", result.sat < 0.1f)
        assertTrue("value ${result.value}", result.value < 0.1f)
    }

    @Test fun theHueBarMovesOnlyTheHue() {
        val result = hsvAfter(Hsv(0f, 0.4f, 0.6f)) {
            rule.onNodeWithContentDescription("Hue").performTouchInput { click(Offset(width / 2f, height / 2f)) }
        }
        assertEquals(180f, result.hue, 5f)
        assertEquals(0.4f, result.sat, 0.001f)
        assertEquals(0.6f, result.value, 0.001f)
    }

    @Test fun theHueBarIsAdjustableForAScreenReader() {
        val result = hsvAfter(Hsv(10f, 1f, 1f)) {
            rule.onNodeWithContentDescription("Hue").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            rule.onNodeWithContentDescription("Hue").performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        }
        assertEquals(90f, result.hue, 0.001f)
    }

    @Test fun theTrimBarMovesTheNearerHandleAndOffersNudgeActions() {
        var from by mutableStateOf(0.2f)
        var to by mutableStateOf(0.8f)
        var nudged = 0
        rule.setContent {
            KtmTheme(darkTheme = true) {
                TrimRangeBar(
                    from = from, to = to, onMoveStart = { from = it }, onMoveEnd = { to = it },
                    onMoveNearest = { f -> if (kotlin.math.abs(f - from) <= kotlin.math.abs(f - to)) from = f else to = f },
                    onNudgeStart = { nudged += it }, onNudgeEnd = { nudged += it * 10 }, nudge = 1, valueText = "kept",
                    modifier = Modifier.width(300.dp).fillMaxWidth(),
                )
            }
        }
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep")
        // The semantics tell a screen reader how much is kept.
        bar.assert(SemanticsMatcher("kept share is 0.6") {
            val info = it.config[SemanticsProperties.ProgressBarRangeInfo]
            kotlin.math.abs(info.current - 0.6f) < 0.001f && info.range == 0f..1f
        })
        // A tap near the left moves the start; near the right the end.
        bar.performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        rule.waitForIdle()
        assertTrue("start moved: $from", from < 0.15f)
        assertEquals(0.8f, to, 0.001f)
        bar.performTouchInput { click(Offset(width * 0.95f, height / 2f)) }
        rule.waitForIdle()
        assertTrue("end moved: $to", to > 0.9f)
        // The four custom actions go through the nudge callbacks.
        val actions = rule.onNodeWithContentDescription("Part of the ride to keep").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(4, actions.size)
        actions[1].action()
        actions[3].action()
        assertEquals(11, nudged)
    }
}
