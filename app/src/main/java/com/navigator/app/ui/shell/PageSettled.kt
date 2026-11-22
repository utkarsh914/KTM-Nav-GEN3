package com.navigator.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

private val AlwaysSettled: State<Boolean> = mutableStateOf(true)

internal val LocalPageSettled = compositionLocalOf { AlwaysSettled }

/**
 * False while the page is moving in or out. The shared map blocks the UI thread on its own render thread for every
 * camera, padding or overlay change, so a map page does that work once the page has arrived, not under the animation.
 * Always true outside a page (previews, tests).
 */
@Composable
fun rememberPageSettled(): State<Boolean> = LocalPageSettled.current
