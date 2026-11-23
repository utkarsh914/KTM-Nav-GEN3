package com.navigator.app.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitPolicyTest {
    @Test fun quitsAtOnceWhenNothingIsNavigating() = assertFalse(QuitPolicy.needsConfirm(sdkNavigating = false, mirrorNavigating = false))

    @Test fun asksWhileEitherEngineNavigates() {
        assertTrue(QuitPolicy.needsConfirm(sdkNavigating = true, mirrorNavigating = false))
        assertTrue(QuitPolicy.needsConfirm(sdkNavigating = false, mirrorNavigating = true))
    }
}
