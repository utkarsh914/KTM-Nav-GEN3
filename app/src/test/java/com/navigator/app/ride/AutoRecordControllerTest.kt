package com.navigator.app.ride

import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.providers.GoogleNavSdkProvider.ActiveTrip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoRecordControllerTest {
    private val dest = NavDestination(13.0, 77.5, "Toit")
    private fun trip(id: Long, key: Long = id) = ActiveTrip(id, dest, key)

    @Test fun policyStopsOnlyWhatItStartedAndOnlyIfTheRiderDidNotTakeOver() {
        val rec = AutoRecordState(recording = true, rideKey = 1)
        assertEquals(AutoRecordStep(rec, false, false), AutoRecordPolicy.onTrip(rec, 1, manualSessionRunning = false))
        assertEquals(AutoRecordStep(AutoRecordState(), true, false), AutoRecordPolicy.onTrip(rec, null, false))
        assertEquals("a manual recording is theirs to stop", AutoRecordStep(AutoRecordState(), false, false), AutoRecordPolicy.onTrip(rec, null, true))
        assertEquals("a new ride waits for the old one to finalise", AutoRecordStep(AutoRecordState(), true, true), AutoRecordPolicy.onTrip(rec, 2, false))
        val idle = AutoRecordState()
        assertEquals(AutoRecordStep(idle, false, false), AutoRecordPolicy.onTrip(idle, 5, false))
    }

    @Test fun policyStartsOnlyWhenEverythingAllows() {
        val idle = AutoRecordState()
        assertTrue(AutoRecordPolicy.shouldStart(idle, 1, anyRecordingRunning = false, recordingEnabled = true, locationGranted = true))
        assertFalse(AutoRecordPolicy.shouldStart(idle, null, false, true, true))
        assertFalse(AutoRecordPolicy.shouldStart(idle, 1, true, true, true))
        assertFalse(AutoRecordPolicy.shouldStart(idle, 1, false, false, true))
        assertFalse(AutoRecordPolicy.shouldStart(idle, 1, false, true, false))
        assertFalse(AutoRecordPolicy.shouldStart(AutoRecordState(true, 1), 1, false, true, true))
    }

    private class Env {
        val trips = MutableStateFlow<ActiveTrip?>(null)
        var session = RideSessionState()
        var enabled = true
        var granted = true
        var startOk = true
        val started = mutableListOf<Long>()
        var stops = 0
        var cleared = 0
    }

    private fun TestScope.controller(e: Env) = AutoRecordController(
        scope = backgroundScope, trips = e.trips, session = { e.session },
        recordingEnabled = { e.enabled }, locationGranted = { e.granted },
        startRecording = { t -> e.started += t.rideKey; e.startOk },
        stopRecording = { e.stops++ }, clearFinish = { e.cleared++ }, restartGapMs = 600,
    ).also { it.start() }

    @Test fun recordsATripAndStopsWhenItEnds() = runTest {
        val e = Env(); controller(e); runCurrent()
        e.trips.value = trip(1); runCurrent()
        assertEquals(listOf(1L), e.started)
        e.trips.value = null; runCurrent()
        assertEquals(1, e.stops)
    }

    @Test fun doesNotRecordWhenSettingOffNoPermissionOrAlreadyRecording() = runTest {
        val e = Env(); controller(e); runCurrent()
        e.enabled = false; e.trips.value = trip(1); runCurrent()
        e.trips.value = null; runCurrent()
        e.enabled = true; e.granted = false; e.trips.value = trip(2); runCurrent()
        e.trips.value = null; runCurrent()
        e.granted = true; e.session = RideSessionState(active = true, manual = true); e.trips.value = trip(3); runCurrent()
        assertTrue(e.started.isEmpty())
        assertEquals("nothing was ours to stop", 0, e.stops)
    }

    @Test fun aNewTripIsANewRideAfterTheOldOneIsFinalised() = runTest {
        val e = Env(); controller(e); runCurrent()
        e.trips.value = trip(1); runCurrent()
        e.trips.value = trip(2); runCurrent()
        assertEquals("old ride stopped, new one not started yet", 1, e.stops)
        assertEquals(listOf(1L), e.started)
        advanceTimeBy(601); runCurrent()
        assertEquals(1, e.cleared)
        assertEquals(listOf(1L, 2L), e.started)
    }

    @Test fun aStopAddedMidTripKeepsTheSameRide() = runTest {
        val e = Env(); controller(e); runCurrent()
        e.trips.value = trip(1); runCurrent()
        e.trips.value = trip(2, key = 1); runCurrent()
        assertEquals(0, e.stops)
        assertEquals(listOf(1L), e.started)
    }

    @Test fun aFailedServiceStartIsRetriedOnTheNextTripChange() = runTest {
        val e = Env(); controller(e); runCurrent()
        e.startOk = false
        e.trips.value = trip(1); runCurrent()
        e.startOk = true
        e.trips.value = trip(2, key = 1); runCurrent()
        assertEquals(listOf(1L, 1L), e.started)
    }
}
