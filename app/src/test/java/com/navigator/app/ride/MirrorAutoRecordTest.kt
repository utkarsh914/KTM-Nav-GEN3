package com.navigator.app.ride

import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.notifications.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorRecordPolicyTest {
    @Test fun phaseFollowsTheEngineThenGuidance() {
        assertEquals(MirrorPhase.Off, MirrorRecordPolicy.phase(mirrorEngine = false, guiding = true))
        assertEquals(MirrorPhase.Guiding, MirrorRecordPolicy.phase(true, true))
        assertEquals(MirrorPhase.Idle, MirrorRecordPolicy.phase(true, false))
    }

    @Test fun startsOnlyWhenGuidingAndAllowedAndNothingElseRecords() {
        fun go(p: MirrorPhase = MirrorPhase.Guiding, owned: Boolean = false, any: Boolean = false, on: Boolean = true, loc: Boolean = true) =
            MirrorRecordPolicy.shouldStart(p, owned, any, on, loc)
        assertTrue(go())
        assertFalse(go(p = MirrorPhase.Idle)); assertFalse(go(p = MirrorPhase.Off))
        assertFalse(go(owned = true)); assertFalse(go(any = true)); assertFalse(go(on = false)); assertFalse(go(loc = false))
        assertEquals(120_000L, MirrorRecordPolicy.GRACE_MS)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MirrorAutoRecordControllerTest {
    private class Env(scope: TestScope) {
        val phase = MutableStateFlow(MirrorPhase.Idle)
        var session = RideSessionState()
        var enabled = true
        var location = true
        var starts = 0
        var stops = 0
        init {
            MirrorAutoRecordController(
                scope.backgroundScope, phase, { session }, { enabled }, { location },
                startRecording = { starts++; session = RideSessionState(active = true); true },
                stopRecording = { stops++; session = RideSessionState() },
            ).start()
        }
    }

    @Test fun startsWithGuidanceAndStopsTwoMinutesAfterItGoes() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        assertEquals(1, e.starts)
        e.phase.value = MirrorPhase.Idle
        advanceTimeBy(119_000)
        assertEquals("still inside the grace", 0, e.stops)
        advanceTimeBy(2_000)
        assertEquals(1, e.stops)
    }

    @Test fun pausingTheDashDoesNotChangeWhetherMapsIsGuiding() {
        NotificationRepository.setMapsGuidancePresent(true)
        NotificationNavProvider.setPaused(true) // clears the mirrored turn, as a pause does
        assertTrue(NotificationRepository.mapsGuidancePresent.value)
        NotificationNavProvider.setPaused(false)
        NotificationRepository.setMapsGuidancePresent(false)
        assertFalse(NotificationRepository.mapsGuidancePresent.value)
    }

    @Test fun mapsEndingWhilePausedStopsTheRideAfterTheGrace() = runTest(UnconfinedTestDispatcher()) {
        val engine = MutableStateFlow(true)
        val maps = MutableStateFlow(false)
        var starts = 0; var stops = 0
        var session = RideSessionState()
        MirrorAutoRecordController(
            backgroundScope, MirrorRecordPolicy.phaseFlow(engine, maps), { session }, { true }, { true },
            startRecording = { starts++; session = RideSessionState(active = true); true },
            stopRecording = { stops++; session = RideSessionState() },
        ).start()
        maps.value = true
        assertEquals(1, starts)
        maps.value = false // Maps ended while the dash was paused: the pause no longer hides it
        advanceTimeBy(121_000)
        assertEquals(1, stops)
    }

    @Test fun aRestartInsideTheGraceIsTheSameRide() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        e.phase.value = MirrorPhase.Idle
        advanceTimeBy(60_000)
        e.phase.value = MirrorPhase.Guiding
        advanceTimeBy(300_000)
        assertEquals(1, e.starts)
        assertEquals(0, e.stops)
    }

    @Test fun aSecondTripAfterTheGraceIsASecondRide() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        e.phase.value = MirrorPhase.Idle
        advanceTimeBy(121_000)
        e.phase.value = MirrorPhase.Guiding
        assertEquals(2, e.starts)
        assertEquals(1, e.stops)
    }

    @Test fun switchingToTheInAppEngineStopsAtOnce() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        e.phase.value = MirrorPhase.Off
        assertEquals(1, e.stops)
    }

    @Test fun aRidersOwnRecordingIsNeverStartedOverOrStopped() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.session = RideSessionState(active = true, manual = true)
        e.phase.value = MirrorPhase.Guiding
        assertEquals(0, e.starts)
        e.phase.value = MirrorPhase.Off
        assertEquals(0, e.stops)
    }

    @Test fun aManualRecordingThatTookOverMeanwhileIsLeftRunning() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        e.session = RideSessionState(active = true, manual = true) // the rider started their own
        e.phase.value = MirrorPhase.Idle
        advanceTimeBy(130_000)
        assertEquals(0, e.stops)
    }

    @Test fun recordingOffOrNoLocationMeansNoRide() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.enabled = false
        e.phase.value = MirrorPhase.Guiding
        assertEquals(0, e.starts)
        e.phase.value = MirrorPhase.Idle
        e.enabled = true; e.location = false
        e.phase.value = MirrorPhase.Guiding
        assertEquals(0, e.starts)
    }

    @Test fun aRiderWhoStoppedItIsNotRestartedDuringTheSameTrip() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        e.session = RideSessionState() // stopped from the notification
        // The same trip goes on (no new phase): nothing restarts it.
        advanceTimeBy(300_000)
        assertEquals(1, e.starts)
        assertEquals(0, e.stops)
    }

    @Test fun aNewTripInsideTheGraceAfterTheRiderStoppedTheRideIsRecorded() = runTest(UnconfinedTestDispatcher()) {
        val e = Env(this)
        e.phase.value = MirrorPhase.Guiding
        e.session = RideSessionState() // the rider stopped it
        e.phase.value = MirrorPhase.Idle
        advanceTimeBy(30_000)
        e.phase.value = MirrorPhase.Guiding
        assertEquals("a new trip is a new ride", 2, e.starts)
    }
}
