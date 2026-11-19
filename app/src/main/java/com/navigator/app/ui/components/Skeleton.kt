package com.navigator.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** A placeholder block that pulses gently (static with reduced motion). Skeletons, not spinners, for lists and sheets. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = KtmShapes.Badge) {
    val reduced = KtmTheme.motion.reduced
    val pulse = if (reduced) null else {
        rememberInfiniteTransition(label = "skeleton")
            .animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    }
    // The alpha is read while drawing, so the pulse never recomposes the list.
    Box(
        modifier.clearAndSetSemantics {}
            .graphicsLayer { alpha = pulse?.value ?: 0.8f }
            .background(KtmTheme.colors.surf3, shape),
    )
}

/** A list row of placeholders: a leading square, a title line and a shorter supporting line. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.common_loading)
    Row(
        modifier.semantics { contentDescription = loading }.fillMaxWidth().heightIn(min = KtmLayout.row2).padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg),
    ) {
        Skeleton(Modifier.size(40.dp), KtmShapes.Control)
        Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Skeleton(Modifier.size(width = 200.dp, height = 16.dp))
            Skeleton(Modifier.size(width = 120.dp, height = 12.dp))
        }
    }
}
