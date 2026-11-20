package com.navigator.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets a screen take the whole window (riding mode): the tab bar and activity strip hide while [immersive]. */
@Stable
class ShellChrome {
    private var requests by mutableIntStateOf(0)

    /** True while any screen asked for it: two screens may ask at once and each can leave first. */
    val immersive: Boolean get() = requests > 0

    internal fun request() { requests++ }
    internal fun release() { if (requests > 0) requests-- }
}

val LocalShellChrome = staticCompositionLocalOf { ShellChrome() }

/** Hide the shell's chrome for as long as [enabled] and this call stay in the composition. */
@Composable
fun ImmersiveEffect(enabled: Boolean) {
    val chrome = LocalShellChrome.current
    DisposableEffect(enabled) {
        if (enabled) chrome.request()
        onDispose { if (enabled) chrome.release() }
    }
}
