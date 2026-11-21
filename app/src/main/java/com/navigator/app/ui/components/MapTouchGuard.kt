package com.navigator.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * For a panel, sheet or screen drawn over the map: touches on it (including its empty parts) stop here instead
 * of falling through to the map, where a long press would drop a pin and a drag would pan it out of sight.
 */
fun Modifier.consumeMapTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }
