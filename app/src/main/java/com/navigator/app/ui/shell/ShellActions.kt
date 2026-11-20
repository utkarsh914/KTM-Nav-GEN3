package com.navigator.app.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the logs page from anywhere (the Having trouble? sheets); provided by the app shell. */
val LocalOpenLogs = staticCompositionLocalOf<() -> Unit> { {} }
