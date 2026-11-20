package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.app.appContainer
import com.navigator.app.ui.shell.rememberLifecycleStarted

/**
 * Live GPS speed (km/h) while [active] and the screen is started, from the app's one
 * [com.navigator.app.location.SpeedSource]. Null until the first fix with a speed arrives.
 */
@Composable
fun rememberSpeedKmh(active: Boolean): Float? {
    val source = LocalContext.current.appContainer.speed
    val started = rememberLifecycleStarted()
    val on = active && started
    DisposableEffect(on) {
        if (on) source.acquire()
        onDispose { if (on) source.release() }
    }
    val speed by source.speedKmh.collectAsState()
    return if (on) speed else null
}
