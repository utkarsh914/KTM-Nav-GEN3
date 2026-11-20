package com.navigator.app.ui.feature.replay

import com.navigator.app.ride.RidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pts(vararg timesMs: Long) = timesMs.mapIndexed { i, t -> RidePoint(12.0, 77.0 + i * 0.001, t, 30f) }

class PlaybackEngineTest {
    @Test fun advanceWalksTheRecordedTime() {
        val p = pts(0, 1_000, 2_000, 3_000)
        val a = PlaybackEngine.advance(p, 0, 0, 1_500)
        assertEquals(Advance(1, 500), a)
        assertEquals(Advance(2, 0), PlaybackEngine.advance(p, a.index, a.carryMs, 500))
    }

    @Test fun aFasterSpeedCoversMoreTrackPerFrame() {
        val p = pts(0, 1_000, 2_000, 3_000, 4_000)
        assertEquals(1, PlaybackEngine.advance(p, 0, 0, 16L * 1).index.coerceAtLeast(1))
        assertEquals(4, PlaybackEngine.advance(p, 0, 0, 16_000L).index)
    }

    @Test fun theEndKeepsNoLeftoverTime() {
        val a = PlaybackEngine.advance(pts(0, 1_000), 0, 0, 5_000)
        assertEquals(Advance(1, 0), a)
    }

    @Test fun aStopBetweenMergedPartsCostsNothing() {
        val seam = pts(0, 1_000, 2_000).toMutableList().also { it += RidePoint(12.5, 77.5, 600_000, 30f, gapBefore = true) }
        // Reaching the last fix of part one carries straight across the seam: it costs no time.
        assertEquals(Advance(1, 500), PlaybackEngine.advance(seam, 0, 0, 1_500))
        assertEquals(Advance(3, 0), PlaybackEngine.advance(seam, 0, 0, 2_000))
    }

    @Test fun aLongGapWithinARideIsCapped() {
        val p = pts(0, 600_000)
        assertEquals(1, PlaybackEngine.advance(p, 0, 0, PlaybackEngine.MAX_STEP_MS).index)
        assertEquals(0, PlaybackEngine.advance(p, 0, 0, PlaybackEngine.MAX_STEP_MS - 1).index)
    }

    @Test fun speedStepsStayInRange() {
        assertEquals(8, PlaybackEngine.stepSpeed(4, 1))
        assertEquals(2, PlaybackEngine.stepSpeed(4, -1))
        assertEquals(1, PlaybackEngine.stepSpeed(1, -1))
        assertEquals(64, PlaybackEngine.stepSpeed(64, 1))
        assertEquals(8, PlaybackEngine.stepSpeed(3, 1)) // an unknown value starts from the default
    }

    @Test fun seekingMapsFractionsToFixes() {
        assertEquals(0, PlaybackEngine.indexAt(-1f, 10))
        assertEquals(9, PlaybackEngine.indexAt(2f, 10))
        assertEquals(4, PlaybackEngine.indexAt(0.5f, 10))
        assertEquals(0, PlaybackEngine.indexAt(0.5f, 1))
        assertEquals(0.5f, PlaybackEngine.fractionOf(5, 11), 0.001f)
        assertEquals(0f, PlaybackEngine.fractionOf(0, 1), 0f)
    }

    @Test fun seamsAreWhereTracksAreJoined() {
        val p = pts(0, 1, 2, 3).toMutableList()
        p[2] = p[2].copy(gapBefore = true)
        assertEquals(listOf(2f / 3), PlaybackEngine.seams(p))
        assertTrue(PlaybackEngine.seams(pts(0, 1, 2)).isEmpty())
    }

    @Test fun aTapSeeksToTheNearestFixOnlyWhenCloseToTheTrack() {
        val p = pts(0, 1_000, 2_000)
        assertEquals(1, PlaybackEngine.nearest(p, 12.0, 77.001))
        assertNull(PlaybackEngine.nearest(p, 12.1, 77.001))
        assertNull(PlaybackEngine.nearest(emptyList(), 12.0, 77.0))
    }
}

class ChartAxisTest {
    @Test fun speedTicksAreRoundAndCoverTheTop() {
        assertEquals(listOf(0, 20, 40, 60, 80), ChartAxis.speedTicks(62f))
        assertEquals(listOf(0, 50, 100, 150), ChartAxis.speedTicks(130f))
        assertEquals(listOf(0, 5, 10), ChartAxis.speedTicks(3f))
        assertTrue(ChartAxis.speedTicks(400f).size <= 6)
        assertTrue(ChartAxis.speedTicks(400f).last() >= 400)
    }

    @Test fun timeTicksRunFromStartToEnd() {
        assertEquals(listOf(0L, 500L, 1_000L), ChartAxis.timeTicks(0, 1_000, 3))
        assertEquals(listOf(5L), ChartAxis.timeTicks(5, 5, 3))
    }

    @Test fun touchesMapToTheTrackAndPlotNotTheWholeWidth() {
        assertEquals(0f, trackFraction(0f, 400f, 24f), 0f)
        assertEquals(1f, trackFraction(400f, 400f, 24f), 0f)
        assertEquals(0.5f, trackFraction(200f, 400f, 24f), 0.001f)
        assertEquals(0f, plotFraction(10f, 400f, 36f), 0f)
        assertEquals(0.5f, plotFraction(218f, 400f, 36f), 0.001f)
    }
}
