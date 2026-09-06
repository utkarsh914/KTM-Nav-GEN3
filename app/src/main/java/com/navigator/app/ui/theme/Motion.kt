package com.navigator.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * Central motion tokens — the single source of truth for durations, easing and
 * spring feel across the app. Screen and stage transitions, press feedback and
 * "pop" animations all read from here so timing stays consistent and any future
 * UI inherits the same feel.
 *
 * Rule of thumb: enter/appear uses [Medium]; exits are a touch quicker ([Fast]);
 * large hero transitions use [Slow]. Tactile press uses [pressScale]; playful
 * confirmations (star/select) use [pop].
 */
object Motion {

    // ---- Durations (ms) ---------------------------------------------------
    const val Fast = 160
    const val Medium = 240
    const val Slow = 320

    /** Standard easing for entering/moving content. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** Slightly emphasised easing for hero/route-level transitions. */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // ---- Spring specs -----------------------------------------------------
    /** Subtle, non-bouncy spring for press scale-down/settle. */
    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        spring(stiffness = Spring.StiffnessMediumLow)

    /** Playful spring for "pop" feedback (select/save). */
    fun <T> popSpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Scale applied to tappable chrome while pressed (see rememberPressScale). */
    const val PressedScale = 0.97f

    // ---- Cross-screen (route) transition ---------------------------------
    /**
     * Direction-aware route transition: a gentle fade with a small (~6%)
     * horizontal drift. Forward slides in from the right; back reverses it.
     */
    fun routeTransition(back: Boolean): ContentTransform {
        val dir = if (back) -1 else 1
        return (fadeIn(tween(Medium, easing = StandardEasing)) +
            slideInHorizontally(tween(Medium, easing = StandardEasing)) { w -> dir * w / 16 }) togetherWith
            (fadeOut(tween(Fast, easing = StandardEasing)) +
                slideOutHorizontally(tween(Medium, easing = StandardEasing)) { w -> -dir * w / 16 })
    }

    /**
     * Vertical stage transition used by full-screen panels that slide up from /
     * down to the bottom (e.g. the search panel). [rise] chooses the direction.
     */
    fun bottomSheetTransition(rising: Boolean): ContentTransform =
        if (rising) {
            (slideInVertically(tween(Medium, easing = StandardEasing)) { it } + fadeIn(tween(Medium))) togetherWith
                fadeOut(tween(Fast))
        } else {
            fadeIn(tween(Medium)) togetherWith
                (slideOutVertically(tween(Medium, easing = StandardEasing)) { it } + fadeOut(tween(Fast)))
        }

    /** Gentle fade + small vertical drift for card/overlay stage changes. */
    fun driftTransition(): ContentTransform =
        (fadeIn(tween(Medium, easing = StandardEasing)) + slideInVertically(tween(Medium, easing = StandardEasing)) { it / 12 }) togetherWith
            (fadeOut(tween(Fast, easing = StandardEasing)) + slideOutVertically(tween(Medium, easing = StandardEasing)) { -it / 12 })
}
