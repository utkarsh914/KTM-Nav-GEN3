package com.navigator.app.ui.feature.navigate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RidingOverlayStateTest {
    @Test fun theEndConfirmDoesNotSurviveTheTripEnding() {
        val s = RidingOverlayState()
        s.showEndConfirm = true
        s.onStageChanged(NavStage.TRIP_FINISHED, openAddStop = false)
        assertFalse(s.showEndConfirm)
    }

    @Test fun aRequestCanOpenTheConfirmAsItBringsTheRidingStageUp() {
        val s = RidingOverlayState()
        s.showEndConfirm = true
        s.showTripSheet = true
        s.onStageChanged(NavStage.NAVIGATING, openAddStop = false)
        assertTrue(s.showEndConfirm)
        assertFalse(s.showTripSheet)
    }

    @Test fun pickingAStopOnTheMapEndsWithTheStage() {
        val s = RidingOverlayState()
        s.pickingStop = true
        s.onStageChanged(NavStage.TRIP_FINISHED, openAddStop = false)
        assertFalse(s.pickingStop)
    }

    @Test fun addAStopOnlyOpensWhenRiding() {
        val s = RidingOverlayState()
        s.onStageChanged(NavStage.BROWSE, openAddStop = true)
        assertFalse(s.showTripStops)
        s.onStageChanged(NavStage.NAVIGATING, openAddStop = true)
        assertTrue(s.showTripStops)
    }
}
