package com.navigator.app.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performSemanticsAction
import com.navigator.app.ui.shell.rideContainer
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionAndSemanticsTest {
    @get:Rule val rule = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        val ctx: Context = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        ctx.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
    }

    @Test fun reducedMotionFollowsTheSystemSettingWhileTheAppIsOpen() {
        setAnimatorScale(1f)
        var reduced = true
        rule.setContent { reduced = rememberReducedMotion() }
        rule.waitForIdle()
        assertFalse(reduced)
        setAnimatorScale(0f)
        rule.waitForIdle()
        assertTrue("turning animations off takes effect without restarting", reduced)
        setAnimatorScale(1f)
        rule.waitForIdle()
        assertFalse(reduced)
    }

    @Test fun reducedMotionCrossfadesQuicklyAndTheCameraJumps() {
        val reduced = KtmMotionSpecs(reduced = true)
        val normal = KtmMotionSpecs(reduced = false)
        assertEquals(0, reduced.cameraFitMs)
        assertEquals(KtmMotionSpecs.CAMERA_FIT_MS, normal.cameraFitMs)
        assertTrue("spatial moves become short tweens", reduced.spatialDefault<Float>() is androidx.compose.animation.core.TweenSpec)
        assertTrue(normal.spatialDefault<Float>() is androidx.compose.animation.core.SpringSpec)
        assertTrue("fades stay fades, only shorter", reduced.effectsDefault<Float>() is androidx.compose.animation.core.TweenSpec)
        assertTrue("a riding slide jumps when reduced: callers zero the offset and keep the fade", reduced.ridingSpatial<Float>() is androidx.compose.animation.core.SnapSpec)
        val riding = normal.ridingSpatial<Float>(delayMs = 50) as androidx.compose.animation.core.TweenSpec
        assertEquals(KtmMotionSpecs.RIDING_MAX_MS, riding.durationMillis)
        assertEquals(50, riding.delay)
    }

    @Test fun aRideContainerWithoutASharedScopeIsJustAModifier() {
        rule.setContent { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.rideContainer("1")) { androidx.compose.material3.Text("card") } }
        rule.onNodeWithText("card").assertIsDisplayed()
    }

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    @Test fun aRideContainerSharesBoundsWithItsPageUnderASharedScope() {
        var showPage by mutableStateOf(false)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                androidx.compose.animation.SharedTransitionLayout {
                    androidx.compose.animation.AnimatedContent(showPage, label = "t") { page ->
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.navigator.app.ui.shell.LocalSharedElements provides com.navigator.app.ui.shell.SharedElements(this@SharedTransitionLayout, this),
                        ) {
                            if (page) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.rideContainer("7", remeasure = false)) { androidx.compose.material3.Text("page") }
                            else androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.rideContainer("7")) { androidx.compose.material3.Text("card") }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("card").assertIsDisplayed()
        showPage = true
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("page").assertIsDisplayed()
    }

    @Test fun theMapCoverShowsTheRouteUntilTheMapIsFramed() {
        var visible by mutableStateOf(true)
        rule.setContent {
            KtmTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.Text("map")
                    com.navigator.app.ui.feature.replay.MapCover(visible, listOf(com.navigator.app.data.rides.ThumbPoint(0.0, 0.0), com.navigator.app.data.rides.ThumbPoint(1.0, 1.0)), Modifier.testTag("cover"))
                }
            }
        }
        rule.onNodeWithTag("cover").assertIsDisplayed()
        visible = false
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("cover").assertDoesNotExist()
        rule.onNodeWithText("map").assertIsDisplayed()
    }

    @Test fun turnIconNamesReadAsWords() {
        assertEquals("Keep left", TurnIconNames.human("KEEP_LEFT"))
        assertEquals("Roundabout exit 3 (RH)", TurnIconNames.human("RAB_SECT_3_RH"))
    }

    @Test fun theHueSliderIsAdjustableByScreenReaders() {
        var hsv by mutableStateOf(Hsv(120f, 0.5f, 0.5f))
        rule.setContent { KtmTheme(darkTheme = true) { KtmColorPicker(hsv, { hsv = it }, "Shade panel", "Hue slider") } }
        val hue = rule.onNodeWithContentDescription("Hue slider")
        hue.assertIsDisplayed()
        hue.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        hue.performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        assertEquals(200f, hsv.hue, 0.01f)
        rule.onNode(hasContentDescription("Shade panel")).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    }

    @Test fun theSearchFieldHasALabelForScreenReaders() {
        rule.setContent { KtmTheme(darkTheme = true) { KtmSearchField("", {}, "Search places") } }
        rule.onNodeWithText("Search places").assertIsDisplayed()
    }
}
