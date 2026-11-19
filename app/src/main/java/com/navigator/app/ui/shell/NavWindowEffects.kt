package com.navigator.app.ui.shell

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NotificationNavProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * While a navigation session is active (either engine) keep the screen awake and let the app show
 * over the lock screen, like Google Maps; cleared the moment guidance ends so the app doesn't sit
 * over the keyguard. Applied straight from the collector, so it also holds while the activity is
 * in the background.
 */
@Composable
fun NavWindowEffects() {
    val activity = LocalActivity.current ?: return
    LaunchedEffect(activity) {
        combine(GoogleNavSdkProvider.state, NotificationNavProvider.state) { sdk, mirror ->
            sdk.sessionState.isActiveNav() || mirror.sessionState.isActiveNav()
        }.distinctUntilChanged().collect { navigating ->
            if (navigating) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setShowWhenLocked(navigating)
            activity.setTurnScreenOn(navigating)
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setShowWhenLocked(false)
            activity.setTurnScreenOn(false)
        }
    }
}
