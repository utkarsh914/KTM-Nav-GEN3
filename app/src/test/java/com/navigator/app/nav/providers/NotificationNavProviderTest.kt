package com.navigator.app.nav.providers

import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.notifications.NotificationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavProviderTest {
    @After fun tearDown() = NotificationNavProvider.detach()

    @Test fun switchingToTheInAppEngineStopsTheMirroredTurnAtOnce() {
        NotificationNavProvider.pushGuidance("200 m", "Foo St", null, null, null)
        NotificationRepository.updateNavText("com.google.android.apps.maps", "200 m Foo St")
        assertEquals(NavSessionState.ENROUTE, NotificationNavProvider.state.value.sessionState)
        NotificationNavProvider.engineDisabled()
        assertEquals("STOPPED clears the dash at once; IDLE would only schedule a clear", NavSessionState.STOPPED, NotificationNavProvider.state.value.sessionState)
        assertNull(NotificationRepository.currentNavText.value)
        assertNull(NotificationRepository.currentNavPackage.value)
    }
}
