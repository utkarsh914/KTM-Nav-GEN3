package com.navigator.app.ui.shell

import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NotificationNavProvider

/** Quit asks first only while a navigation session is running (either engine): quitting then ends guidance and recording. */
internal object QuitPolicy {
    fun needsConfirm(sdkNavigating: Boolean, mirrorNavigating: Boolean): Boolean = sdkNavigating || mirrorNavigating

    fun needsConfirmNow(): Boolean = needsConfirm(
        GoogleNavSdkProvider.state.value.sessionState.isActiveNav(),
        NotificationNavProvider.state.value.sessionState.isActiveNav(),
    )
}
