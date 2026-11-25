package com.navigator.app.app

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitControllerTest {
    private class FakeSteps(var recording: Boolean = false, val failing: Set<String> = emptySet()) : QuitSteps {
        val calls = mutableListOf<String>()
        private fun run(name: String) { calls += name; if (name in failing) error("boom $name") }
        override fun setQuitUntilOpen() = run("flag")
        override suspend fun cancelTrip() = run("trip")
        override fun recordingActive() = recording
        override fun stopRecording() = run("stopRecording")
        override suspend fun awaitRecordingStopped() = run("awaitRecording")
        override suspend fun stopBike() = run("bike")
        override suspend fun stopNavService() = run("navService")
        override fun cancelNotifications() = run("notifications")
        override suspend fun finishActivity() = run("finish")
    }

    private fun controller(steps: QuitSteps, log: MutableList<String> = mutableListOf()) =
        QuitController(tripLive = { false }, recording = { false }, mirrorGuiding = { false }, steps = steps, log = { log += it })

    @Test fun theFlagComesFirstAndTheWindowCloseLast() = runTest {
        val steps = FakeSteps(recording = true)
        controller(steps).quit()
        assertEquals(
            listOf("flag", "trip", "stopRecording", "awaitRecording", "bike", "navService", "notifications", "finish"), steps.calls,
        )
    }

    @Test fun withNoRecordingItIsNotStoppedOrWaitedFor() = runTest {
        val steps = FakeSteps(recording = false)
        controller(steps).quit()
        assertFalse(steps.calls.any { it.contains("ecording") })
        assertEquals("flag", steps.calls.first())
    }

    @Test fun aStepThatFailsDoesNotStopTheRestAndIsLogged() = runTest {
        for (bad in listOf("flag", "trip", "bike", "navService", "notifications")) {
            val steps = FakeSteps(failing = setOf(bad))
            val log = mutableListOf<String>()
            controller(steps, log).quit()
            assertEquals(bad, "finish", steps.calls.last())
            assertEquals(bad, listOf("flag", "trip", "bike", "navService", "notifications", "finish"), steps.calls)
            assertTrue(bad, log.any { it.contains("failed") })
        }
    }

    @Test fun itAsksOnlyWhileSomethingRuns() {
        fun asks(trip: Boolean, rec: Boolean, mirror: Boolean) =
            QuitController({ trip }, { rec }, { mirror }, FakeSteps()).needsConfirm()
        assertFalse(asks(false, false, false))
        assertTrue(asks(true, false, false))
        assertTrue(asks(false, true, false))
        assertTrue(asks(false, false, true))
    }
}
