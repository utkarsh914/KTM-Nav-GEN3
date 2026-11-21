package com.navigator.app.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the logs page from anywhere (the Having trouble? sheets); provided by the app shell. */
val LocalOpenLogs = staticCompositionLocalOf<() -> Unit> { {} }

/** Opens Settings > Bike & dash from the bike sheet; null where the shell does not provide it (the sheet then has no such row). */
val LocalOpenBikeSettings = staticCompositionLocalOf<(() -> Unit)?> { null }
