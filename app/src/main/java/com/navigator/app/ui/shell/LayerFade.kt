package com.navigator.app.ui.shell

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The fade of a screen-sized container as it enters or leaves an animated scope, without an offscreen layer. Compose's
 * own fadeIn/fadeOut render the content into an offscreen buffer of the container's size for as long as the alpha is
 * below 1: for a full-screen container that is a fresh 10 MB GPU buffer every frame, which on a phone short of memory
 * stalls the render thread for 100 ms (docs/ENGINEERING_NOTES.md, "The map layer"). Here the alpha is applied to each
 * draw instead (children that overlap show through each other for the length of a fade, which is under 250 ms).
 *
 * The scope's own enter must then be None and its exit [holdUntilFaded] (the same spec), or the content is removed
 * before it has faded out.
 */
@Composable
fun Modifier.layerFade(
    scope: AnimatedVisibilityScope,
    enter: FiniteAnimationSpec<Float>,
    exit: FiniteAnimationSpec<Float>,
): Modifier {
    val alpha = scope.transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
        label = "layerFade",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return graphicsLayer {
        this.alpha = alpha.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** An exit that changes nothing and lasts [spec]: keeps the content in the tree while [layerFade] fades it. */
fun holdUntilFaded(spec: FiniteAnimationSpec<Float>): ExitTransition = fadeOut(spec, targetAlpha = 1f)
