package com.navigator.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navigator.app.ui.components.consumeMapTouches
import com.navigator.app.ui.theme.KtmTheme

/**
 * The app's ground for anything that covers the shared map layer: opaque, and a touch target of its own, so a tap or
 * drag on an empty spot cannot fall through to the map behind (a background alone does not stop that). It must not
 * consume every event the way a catch-all would: a parent that consumes a finger's small moves cancels its children's taps.
 */
@Composable
fun Modifier.coverGround(): Modifier = background(KtmTheme.colors.bg).consumeMapTouches()
