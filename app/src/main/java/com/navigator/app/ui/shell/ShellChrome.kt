package com.navigator.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets a screen take the whole window (riding mode): the tab bar and activity strip hide while [immersive]. */
@Stable
class ShellChrome {
    var immersive by mutableStateOf(false)
        internal set
}

val LocalShellChrome = staticCompositionLocalOf { ShellChrome() }

/** Hide the shell's chrome for as long as [enabled] and this call stay in the composition. */
@Composable
fun ImmersiveEffect(enabled: Boolean) {
    val chrome = LocalShellChrome.current
    DisposableEffect(enabled) {
        if (enabled) chrome.immersive = true
        onDispose { if (enabled) chrome.immersive = false }
    }
}
