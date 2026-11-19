package com.navigator.app.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
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
}
