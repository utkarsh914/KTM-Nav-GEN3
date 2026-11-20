package com.navigator.app.ui.shell

import com.navigator.app.location.lastKnownLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.SunTimes
import com.navigator.app.ui.theme.ThemeMode
import com.navigator.app.ui.theme.isNightTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The app's own light/dark: the chosen mode resolved against the phone's night setting. */
fun resolveAppDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}

/**
 * Light/dark for what is on screen. Day/Night applies only to the riding surfaces while a trip is
 * being guided; the browse map and every other screen follow the app's own appearance.
 */
fun resolveSurfaceDark(appDark: Boolean, ridingScreens: NavThemeMode, ridingNow: Boolean, night: Boolean): Boolean =
    if (ridingNow && ridingScreens == NavThemeMode.DAY_NIGHT) night else appDark

/**
 * Whether it is night here, re-evaluated about once a minute: from the sun's position at the last
 * known location, or the fixed 06:00-18:00 rule without a fix or location permission.
 */
@Composable
fun rememberNightNow(): State<Boolean> {
    val appContext = LocalContext.current.applicationContext
    return produceState(initialValue = isNightTime()) {
        while (true) {
            val here = withContext(Dispatchers.IO) { lastKnownLocation(appContext) }
            value = if (here != null) SunTimes.isNight(System.currentTimeMillis(), here.first, here.second) else isNightTime()
            delay(60_000)
        }
    }
}
