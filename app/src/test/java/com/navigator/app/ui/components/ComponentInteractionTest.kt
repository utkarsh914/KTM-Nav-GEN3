package com.navigator.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.ui.theme.KtmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behaviour of the interactive components: semantics, taps, holds and guards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComponentInteractionTest {
    @get:Rule val rule = createComposeRule()

    private var value by mutableIntStateOf(80)

    private fun stepper(initial: Int = 80, enabled: Boolean = true, edits: () -> Unit = {}) {
        value = initial
        rule.setContent {
            KtmTheme(darkTheme = true) {
                KtmStepper(value, SettingSpecs.overspeedLimit, { value = it }, "km/h", "30–200 km/h", "Decrease", "Increase", "Edit value", edits, enabled = enabled)
            }
        }
    }

    @Test fun aTapStepsOnceOnTheGrid() {
        stepper()
        rule.onNodeWithContentDescription("Increase").performClick()
        assertEquals(85, value)
        rule.onNodeWithContentDescription("Decrease").performClick()
        assertEquals(80, value)
    }

    @Test fun aButtonDimsAndIgnoresTapsAtItsBound() {
        stepper(initial = SettingSpecs.overspeedLimit.max)
        rule.onNodeWithContentDescription("Increase").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Increase").performClick()
        assertEquals(SettingSpecs.overspeedLimit.max, value)
    }

    @Test fun holdingRepeatsAndStopsOnRelease() {
        stepper(initial = 40)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Increase").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300)
        assertEquals("no step before the first pause ends", 40, value)
        rule.mainClock.advanceTimeBy(1_200)
        val whileHeld = value
        assertTrue("repeated while held: $whileHeld", whileHeld >= 55)
        rule.onNodeWithContentDescription("Increase").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("stopped on release, and the release did not add a step", whileHeld, value)
    }

    @Test fun holdingStopsAtTheBound() {
        stepper(initial = SettingSpecs.overspeedLimit.max - 10)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Increase").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals(SettingSpecs.overspeedLimit.max, value)
        rule.onNodeWithContentDescription("Increase").performTouchInput { up() }
    }

    @Test fun tappingTheValueAsksToEditIt() {
        var edits = 0
        stepper { edits++ }
        rule.onNodeWithText("80").performClick()
        assertEquals(1, edits)
    }

    @Test fun aChipExposesItsSelectedState() {
        var selected by mutableStateOf(false)
        rule.setContent { KtmTheme(darkTheme = true) { KtmFilterChip("Fuel", selected, { selected = !selected }) } }
        rule.onNodeWithText("Fuel").assertIsOff()
        rule.onNodeWithText("Fuel").performClick()
        rule.onNodeWithText("Fuel").assertIsOn()
    }

    private fun assertSegmentedRejects(options: Int) {
        try {
            rule.setContent { KtmTheme(darkTheme = true) { KtmSegmented((1..options).toList(), 1, {}, { it.toString() }) } }
            fail("expected a failure for $options options")
        } catch (e: Throwable) {
            var t: Throwable? = e
            while (t != null && t !is IllegalArgumentException) t = t.cause
            assertTrue("options=$options: $e", t != null)
        }
    }

    @Test fun aSegmentedControlRejectsASingleOption() = assertSegmentedRejects(1)

    @Test fun aSegmentedControlRejectsFiveOptions() = assertSegmentedRejects(5)

    @Test fun aDialogShowsItsTitleAndRunsTheConfirmAction() {
        var confirmed = false
        rule.setContent {
            KtmTheme(darkTheme = true) {
                KtmDialog(
                    title = "Delete this ride?", onDismiss = {}, message = "It moves to Deleted.",
                    confirm = DialogAction("Delete", { confirmed = true }, ButtonStyle.Danger),
                    dismiss = DialogAction("Cancel", {}),
                )
            }
        }
        rule.onNodeWithText("Delete this ride?").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        assertTrue(confirmed)
    }

    @Test fun aRidingDialogStacksTheSafeChoiceFirst() {
        rule.setContent {
            KtmTheme(darkTheme = true) {
                KtmDialog(
                    title = "End this trip?", onDismiss = {}, riding = true,
                    confirm = DialogAction("End trip", {}, ButtonStyle.DangerTonal),
                    dismiss = DialogAction("Keep riding", {}),
                )
            }
        }
        val keep = rule.onNodeWithText("Keep riding").fetchSemanticsNode().boundsInRoot
        val end = rule.onNodeWithText("End trip").fetchSemanticsNode().boundsInRoot
        assertTrue("safe choice above the destructive one", keep.top < end.top)
        assertEquals("riding buttons are 64 dp (1 px = 1 dp at the default density)", 64f, keep.height, 1f)
    }
}
