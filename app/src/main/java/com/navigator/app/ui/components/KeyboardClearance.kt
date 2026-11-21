package com.navigator.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Keeps the foot of a list clear of the keyboard by exactly as much as the keyboard covers it. A tab page cannot use
 * `imePadding()`: the tab bar already sits under it, so the keyboard's full height would leave a bar-sized gap.
 * The overlap is measured against the window, so it is right with or without a bar, rail or gesture area below.
 * The host must fill the space it is given (a list with `fillMaxSize`): the measured bottom would otherwise grow with the padding.
 */
@Composable
fun Modifier.keyboardClearance(): Modifier {
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val keyboardTop = windowHeight - WindowInsets.ime.getBottom(density)
    val overlap: Dp = with(density) { (bottomPx - keyboardTop).coerceAtLeast(0).toDp() }
    // Measured before the padding below, so the padding does not move what is measured.
    return this.onGloballyPositioned { bottomPx = it.boundsInWindow().bottom.roundToInt() }.padding(bottom = overlap)
}
