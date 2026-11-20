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
        var hsv by mutableStateOf(initial)
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

    @Test fun theCornersOfTheShadePanelTakeTouchesToo() {
        val result = hsvAfter(Hsv(200f, 0.5f, 0.5f)) {
            rule.onNodeWithContentDescription("Shade").performTouchInput { click(Offset(width - 1f, 1f)) }
        }
        assertTrue("sat ${result.sat}", result.sat > 0.95f)
        assertTrue("value ${result.value}", result.value > 0.95f)
    }

    @Test fun twoGesturesInARowKeepTheFirstOnesResult() {
        val result = hsvAfter(Hsv(0f, 0.2f, 0.2f)) {
            rule.onNodeWithContentDescription("Hue").performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
            rule.onNodeWithContentDescription("Shade").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        }
        assertEquals(90f, result.hue, 5f)
        assertEquals(0.5f, result.sat, 0.03f)
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

    private fun setTrim(from0: Float, to0: Float, enabled: Boolean = true, onNudge: (Int) -> Unit = {}): () -> Pair<Float, Float> {
        var from by mutableStateOf(from0)
        var to by mutableStateOf(to0)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                TrimRangeBar(
                    from = from, to = to, onMoveStart = { from = it }, onMoveEnd = { to = it },
                    onMoveNearest = { f -> if (kotlin.math.abs(f - from) <= kotlin.math.abs(f - to)) from = f else to = f },
                    onNudgeStart = { onNudge(it) }, onNudgeEnd = { onNudge(it * 10) }, nudge = 1, valueText = "kept",
                    startText = "10:00", endText = "11:00", modifier = Modifier.width(300.dp), enabled = enabled,
                )
            }
        }
        return { from to to }
    }

    @Test fun aTapMovesTheNearerHandle() {
        val state = setTrim(0.2f, 0.8f)
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep")
        bar.performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        rule.waitForIdle()
        assertTrue("start moved: ${state().first}", state().first < 0.15f)
        assertEquals(0.8f, state().second, 0.001f)
        bar.performTouchInput { click(Offset(width * 0.95f, height / 2f)) }
        rule.waitForIdle()
        assertTrue("end moved: ${state().second}", state().second > 0.9f)
    }

    @Test fun draggingFromNearAHandleMovesThatHandle() {
        val state = setTrim(0.2f, 0.8f)
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep")
        bar.performTouchInput { swipe(Offset(width * 0.8f, height / 2f), Offset(width * 0.6f, height / 2f)) }
        rule.waitForIdle()
        assertEquals(0.2f, state().first, 0.001f)
        assertEquals(0.6f, state().second, 0.06f)
        bar.performTouchInput { swipe(Offset(width * 0.2f, height / 2f), Offset(width * 0.35f, height / 2f)) }
        rule.waitForIdle()
        assertEquals(0.35f, state().first, 0.06f)
    }

    @Test fun eachHandleIsItsOwnAdjustableNode() {
        val state = setTrim(0.2f, 0.8f)
        val start = rule.onNodeWithContentDescription("Start of the part to keep")
        val end = rule.onNodeWithContentDescription("End of the part to keep")
        start.assert(SemanticsMatcher("start 0.2 in 0..0.8") {
            val info = it.config[SemanticsProperties.ProgressBarRangeInfo]
            kotlin.math.abs(info.current - 0.2f) < 0.001f && info.range == 0f..0.8f
        })
        end.assert(SemanticsMatcher("end 0.8 in 0.2..1") {
            val info = it.config[SemanticsProperties.ProgressBarRangeInfo]
            kotlin.math.abs(info.current - 0.8f) < 0.001f && info.range == 0.2f..1f
        })
        start.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10:00"))
        end.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "11:00"))
        start.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        rule.waitForIdle()
        assertEquals(0.5f, state().first, 0.001f)
        // A handle can't be set past the other one.
        end.performSemanticsAction(SemanticsActions.SetProgress) { it(0.1f) }
        rule.waitForIdle()
        assertEquals(0.5f, state().second, 0.001f)
    }

    @Test fun theNudgeActionsAreLabelledAndWired() {
        val nudges = mutableListOf<Int>()
        setTrim(0.2f, 0.8f, onNudge = { nudges += it })
        val actions = rule.onNodeWithContentDescription("Part of the ride to keep").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(
            listOf("Move the start earlier", "Move the start later", "Move the end earlier", "Move the end later"),
            actions.map { it.label },
        )
        actions.forEach { it.action() }
        assertEquals(listOf(-1, 1, -10, 10), nudges)
    }

    @Test fun aDisabledBarOffersNoActionsAndIgnoresTouches() {
        val state = setTrim(0.2f, 0.8f, enabled = false)
        val bar = rule.onNodeWithContentDescription("Part of the ride to keep")
        assertTrue(bar.fetchSemanticsNode().config[SemanticsActions.CustomActions].isEmpty())
        bar.performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        rule.waitForIdle()
        assertEquals(0.2f, state().first, 0.001f)
        val end = rule.onNodeWithContentDescription("End of the part to keep").fetchSemanticsNode()
        assertTrue(SemanticsActions.SetProgress !in end.config)
    }

    @Test fun theTrimBarKeepsTheStartOnTheLeftInARightToLeftLanguage() {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                KtmTheme(darkTheme = true) {
                    TrimRangeBar(
                        from = 0.2f, to = 0.8f, onMoveStart = {}, onMoveEnd = {}, onMoveNearest = {}, onNudgeStart = {}, onNudgeEnd = {},
                        nudge = 1, valueText = "kept", startText = "10:00", endText = "11:00", modifier = Modifier.width(300.dp),
                    )
                }
            }
        }
        val start = rule.onNodeWithContentDescription("Start of the part to keep").fetchSemanticsNode().boundsInRoot
        val end = rule.onNodeWithContentDescription("End of the part to keep").fetchSemanticsNode().boundsInRoot
        assertTrue("start ${start.left} before end ${end.left}", start.left < end.left)
    }
}
