package com.navigator.app.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitPolicyTest {
    @Test fun quitsAtOnceWhenNothingIsRunning() = assertFalse(QuitPolicy.needsConfirm(tripLive = false, recording = false, mirrorGuiding = false))

    @Test fun asksWhileATripARecordingOrAMirroredTripRuns() {
        assertTrue(QuitPolicy.needsConfirm(tripLive = true, recording = false, mirrorGuiding = false))
        assertTrue(QuitPolicy.needsConfirm(tripLive = false, recording = true, mirrorGuiding = false))
        assertTrue(QuitPolicy.needsConfirm(tripLive = false, recording = false, mirrorGuiding = true))
    }
}
