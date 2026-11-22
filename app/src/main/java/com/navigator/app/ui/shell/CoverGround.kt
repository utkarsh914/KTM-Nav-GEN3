package com.navigator.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.navigator.app.ui.theme.KtmTheme

/**
 * The app's ground for anything that covers the shared map layer: opaque, and it takes the touches nobody inside it
 * wanted, so a tap or drag on an empty spot cannot fall through to the map behind (a background alone does not stop that).
 */
@Composable
fun Modifier.coverGround(): Modifier = background(KtmTheme.colors.bg).pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
