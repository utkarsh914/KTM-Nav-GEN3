package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/** Host for the one at-rest feedback pattern: a snackbar with at most one action (View, Undo, Retry). */
@Composable
fun KtmSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier = modifier.padding(horizontal = KtmSpacing.sm)) { data -> SwipeAway(data) { KtmSnackbar(data) } }
}

/** FB: a snackbar is swiped away sideways. Past a third of its width it goes (as if it timed out); less, and it springs back. */
@Composable
private fun SwipeAway(data: SnackbarData, content: @Composable () -> Unit) {
    var dx by remember(data) { mutableFloatStateOf(0f) }
    var width by remember(data) { mutableFloatStateOf(1f) }
    Box(
        Modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(dx.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(dx) / width).coerceIn(0f, 1f) }
            .pointerInput(data) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(dx) > width / SWIPE_AWAY_SHARE) data.dismiss() else dx = 0f },
                    onDragCancel = { dx = 0f },
                ) { change, delta -> change.consume(); dx += delta }
            },
    ) { content() }
}

private const val SWIPE_AWAY_SHARE = 3f

/** Inverse-surface snackbar: a text line and one 48 dp action in the inverse accent. */
@Composable
fun KtmSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val visuals = data.visuals
    Row(
        modifier.fillMaxWidth()
            .shadow(KtmElevation.onMap, KtmShapes.Control)
            .background(c.inv, KtmShapes.Control)
            .heightIn(min = 52.dp)
            .padding(start = KtmSpacing.lg, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        Text(visuals.message, style = MaterialTheme.typography.bodyMedium, color = c.onInv, modifier = Modifier.weight(1f), maxLines = 3)
        visuals.actionLabel?.let { label ->
            Box(
                Modifier.heightIn(min = KtmLayout.targetRest)
                    .clickable(role = Role.Button) { haptics.tap(); data.performAction() }
                    .padding(horizontal = KtmSpacing.md),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = KtmTheme.type.buttonCompact, color = a.inverseAccent) }
        }
    }
}
