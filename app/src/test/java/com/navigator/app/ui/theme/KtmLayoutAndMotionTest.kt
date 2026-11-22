package com.navigator.app.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtmLayoutAndMotionTest {
    @Test fun spacingStaysOnTheFourDpGrid() {
        listOf(KtmSpacing.xs, KtmSpacing.sm, KtmSpacing.md, KtmSpacing.lg, KtmSpacing.xl, KtmSpacing.xxl).forEach {
            assertEquals(0f, it.value % 4f, 0f)
        }
        assertEquals(16.dp, KtmSpacing.edge)
        assertEquals(8.dp, KtmSpacing.ridingEdge)
    }

    @Test fun rowHeightsAndTargetsAreTheDesignOnes() {
        assertEquals(listOf(56.dp, 64.dp, 80.dp, 72.dp), listOf(KtmLayout.row1, KtmLayout.row2, KtmLayout.row3, KtmLayout.rowThumb))
        assertEquals(listOf(48.dp, 56.dp, 64.dp), listOf(KtmLayout.targetRest, KtmLayout.targetPrimary, KtmLayout.targetRiding))
        assertEquals(64.dp, KtmLayout.topBar)
    }

    @Test fun landscapePanelWidthFollowsTheWindow() {
        assertEquals(KtmLayout.panelWide, KtmLayout.panelWidth(874f))
        assertEquals(KtmLayout.panelWide, KtmLayout.panelWidth(800f))
        assertEquals(KtmLayout.panelNarrow, KtmLayout.panelWidth(780f))
        assertEquals(352.dp, KtmLayout.panelWide)
        assertEquals(320.dp, KtmLayout.panelNarrow)
    }

    @Test fun motionUsesSpringsAndReducedMotionDoesNot() {
        val normal = KtmMotionSpecs(reduced = false)
        val reduced = KtmMotionSpecs(reduced = true)
        assertTrue(normal.spatialDefault<Float>() is SpringSpec)
        assertTrue(reduced.spatialDefault<Float>() is TweenSpec)
        assertEquals(400, normal.cameraRecenterMs)
        assertEquals(600, normal.cameraFitMs)
        assertEquals(0, reduced.cameraFitMs)
    }

    @Test fun ridingMotionNeverExceedsTheCeilingAndNeverOvershoots() {
        val spec = KtmMotionSpecs(reduced = false).ridingSpatial<Float>()
        assertTrue(spec is TweenSpec)
        assertTrue((spec as TweenSpec).durationMillis <= KtmMotionSpecs.RIDING_MAX_MS)
        assertEquals(100, (KtmMotionSpecs().ridingColor<Float>() as TweenSpec).durationMillis)
    }

    private fun damping(spec: Any?) = (spec as SpringSpec<*>).dampingRatio

    @Test fun pagesAndEffectsNeverOvershootAndOnlyPlayfulThingsBounce() {
        val m = KtmMotionSpecs(reduced = false)
        assertEquals(1f, damping(m.page<Float>()))
        assertEquals(KtmMotionSpecs.PAGE_STIFFNESS, (m.page<Float>() as SpringSpec).stiffness)
        listOf(m.effectsFast<Float>(), m.effectsDefault<Float>(), m.effectsSlow<Float>()).forEach { assertEquals(1f, damping(it)) }
        listOf(m.spatialFast<Float>(), m.spatialDefault<Float>(), m.spatialSlow<Float>()).forEach {
            assertTrue("elements overshoot at most a hair", damping(it) in 0.85f..1f)
        }
        assertTrue("the playful spring is the only one that bounces visibly", damping(m.playful<Float>()) < 0.85f)
    }

    @Test fun reducedMotionTurnsEveryFluidTokenIntoAShortFadeWithNoDelay() {
        val m = KtmMotionSpecs(reduced = true)
        listOf(m.page<Float>(), m.spatialFast<Float>(), m.spatialDefault<Float>(), m.spatialSlow<Float>(), m.playful<Float>(),
            m.effectsFast<Float>(), m.effectsDefault<Float>(), m.effectsSlow<Float>(), m.staggeredFade<Float>(9)).forEach { spec ->
            assertTrue(spec is TweenSpec)
            assertEquals(0, (spec as TweenSpec).delay)
            assertTrue(spec.durationMillis <= KtmMotionSpecs.EFFECTS_SLOW_MS / 2)
        }
    }

    @Test fun theStaggerStopsGrowingAfterTenRows() {
        val m = KtmMotionSpecs(reduced = false)
        assertEquals(0, (m.staggeredFade<Float>(0) as TweenSpec).delay)
        assertEquals(20, (m.staggeredFade<Float>(1) as TweenSpec).delay)
        assertEquals(200, (m.staggeredFade<Float>(10) as TweenSpec).delay)
        assertEquals(200, (m.staggeredFade<Float>(50) as TweenSpec).delay)
    }

    @Test fun everyRidingTokenIsATweenWithinTheCeiling() {
        val m = KtmMotionSpecs(reduced = false)
        listOf(m.ridingSpatial<Float>(), m.ridingSpatial<Float>(KtmMotionSpecs.RIDING_BAR_DELAY_MS), m.ridingFade<Float>(KtmMotionSpecs.RIDING_BAR_DELAY_MS),
            m.ridingSwapOut<Float>(), m.ridingQuick<Float>(), m.ridingColor<Float>()).forEach { spec ->
            assertTrue("riding never springs", spec is TweenSpec)
            assertTrue((spec as TweenSpec).durationMillis <= KtmMotionSpecs.RIDING_MAX_MS)
        }
        val bar = m.ridingFade<Float>(KtmMotionSpecs.RIDING_BAR_DELAY_MS) as TweenSpec
        assertTrue("the entrance finishes inside the ceiling plus its stagger", bar.delay + bar.durationMillis <= 2 * KtmMotionSpecs.RIDING_MAX_MS)
    }

    @Test fun theMaterialSchemeUsesTheSameTokensAndTheCalmOneNeverBounces() {
        val app = KtmMotionScheme()
        assertEquals(KtmMotionSpecs.ELEMENT_DAMPING, damping(app.defaultSpatialSpec<Float>()))
        assertEquals(KtmMotionSpecs.ELEMENT_STIFFNESS, (app.defaultSpatialSpec<Float>() as SpringSpec).stiffness)
        assertEquals(KtmMotionSpecs.EFFECTS_FAST_STIFFNESS, (app.fastEffectsSpec<Float>() as SpringSpec).stiffness)
        val calm = KtmMotionScheme(calm = true)
        listOf(calm.defaultSpatialSpec<Float>(), calm.fastSpatialSpec<Float>(), calm.slowSpatialSpec<Float>(),
            calm.defaultEffectsSpec<Float>(), calm.fastEffectsSpec<Float>(), calm.slowEffectsSpec<Float>()).forEach { assertEquals(1f, damping(it)) }
        listOf(calm.defaultSpatialSpec<Float>(), calm.fastSpatialSpec<Float>(), calm.slowSpatialSpec<Float>()).forEach {
            assertTrue("a calm spring settles within the riding ceiling", settleMs(it) <= KtmMotionSpecs.RIDING_MAX_MS)
        }
    }

    /** A critically damped spring is within 1 % of its target after about 6.6 / sqrt(stiffness) seconds. */
    private fun settleMs(spec: Any?) = 6.6 / Math.sqrt((spec as SpringSpec<*>).stiffness.toDouble()) * 1000

    @Test fun movesInPixelsAndDpStopWithinAPixelNotAHundredthOfOne() {
        val m = KtmMotionSpecs(reduced = false)
        assertEquals(Rect.VisibilityThreshold, (m.spatialDefault<Rect>() as SpringSpec).visibilityThreshold)
        assertEquals(IntOffset.VisibilityThreshold, (m.spatialDefault<IntOffset>() as SpringSpec).visibilityThreshold)
        assertEquals(IntSize.VisibilityThreshold, (m.page<IntSize>() as SpringSpec).visibilityThreshold)
        assertEquals(Dp.VisibilityThreshold, (m.effectsDefault<Dp>() as SpringSpec).visibilityThreshold)
        assertEquals(null, (m.effectsDefault<Float>() as SpringSpec).visibilityThreshold)
    }
}
