package com.navigator.app.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens (design §3.5), the one source of physics for the app. Off the bike everything is a spring with
 * little or no overshoot, so a move that is interrupted (a second tap, a back swipe) carries on from where it is.
 * With "Remove animations" on ([reduced]) moves collapse to short fades and camera moves jump.
 *
 * Riding surfaces use the riding tier: plain tweens of at most [RIDING_MAX_MS], never overshooting.
 * Feature code asks this class for a spec and never writes a numeric `tween(` or `spring(` (check-ui-rules.sh).
 */
class KtmMotionSpecs(val reduced: Boolean = false) {
    /** A page, a tab or a stage sheet travelling: critically damped, about 250 ms, never visibly overshoots. */
    fun <T> page(): FiniteAnimationSpec<T> = move(PAGE_DAMPING, PAGE_STIFFNESS)

    /** Elements that move or resize: sheets, dialogs, chips, shared bounds, list placement. A hair of overshoot at most. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = move(ELEMENT_DAMPING, ELEMENT_FAST_STIFFNESS)
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = move(ELEMENT_DAMPING, ELEMENT_STIFFNESS)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = move(ELEMENT_SLOW_DAMPING, ELEMENT_SLOW_STIFFNESS)

    /** Only tiny things may bounce: a press released, a pin dropped, a switch. Never on a page or while riding. */
    fun <T> playful(): FiniteAnimationSpec<T> = move(PLAYFUL_DAMPING, PLAYFUL_STIFFNESS)

    /** Alpha, colour and scrim: critically damped springs, so a fade that is interrupted retargets instead of restarting. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = effect(EFFECTS_FAST_STIFFNESS, EFFECTS_FAST_MS)
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = effect(EFFECTS_STIFFNESS, EFFECTS_DEFAULT_MS)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = effect(EFFECTS_SLOW_STIFFNESS, EFFECTS_SLOW_MS)

    /** SE2: a list result fades in [STAGGER_MS] after the one above it (at most [STAGGER_ROWS] deep); reduced motion drops the delay. */
    fun <T> staggeredFade(index: Int): FiniteAnimationSpec<T> =
        if (reduced) tween(EFFECTS_FAST_MS) else tween(EFFECTS_DEFAULT_MS, delayMillis = index.coerceIn(0, STAGGER_ROWS) * STAGGER_MS)

    /** The skeleton pulse, one half cycle. */
    fun <T> skeletonPulse(): DurationBasedAnimationSpec<T> = tween(SKELETON_PULSE_MS)

    /** Riding: a plain ease with no overshoot, and a hard ceiling of [RIDING_MAX_MS]. */
    fun <T> ridingSpatial(delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(RIDING_MAX_MS, delayMillis = delayMs)

    /** A riding fade that starts [delayMs] late (the staggered entrance); reduced motion drops the delay. */
    fun <T> ridingFade(delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) tween(EFFECTS_FAST_MS) else tween(EFFECTS_DEFAULT_MS, delayMillis = delayMs)

    /** The old trip-sheet content leaves this fast, before the new one fades in ([RIDING_SWAP_IN_DELAY_MS]). */
    fun <T> ridingSwapOut(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(RIDING_SWAP_OUT_MS)

    /** MO3: the next-maneuver swap, 160 ms; reduced motion jumps the slide and keeps a short fade (see [effectsFast]). */
    fun <T> ridingQuick(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(MANEUVER_SWAP_MS)

    /** The overspeed colour change: a plain 100 ms fade, never a flash. */
    fun <T> ridingColor(): FiniteAnimationSpec<T> = tween(EFFECTS_FAST_MS)

    val cameraRecenterMs: Int get() = if (reduced) 0 else CAMERA_RECENTER_MS
    val cameraFitMs: Int get() = if (reduced) 0 else CAMERA_FIT_MS

    private fun <T> move(damping: Float, stiffness: Float): FiniteAnimationSpec<T> =
        if (reduced) tween(EFFECTS_FAST_MS) else spring(dampingRatio = damping, stiffness = stiffness)

    private fun <T> effect(stiffness: Float, reducedMs: Int): FiniteAnimationSpec<T> =
        if (reduced) tween(reducedMs / 2) else spring(dampingRatio = 1f, stiffness = stiffness)

    companion object {
        const val PAGE_DAMPING = 1f
        const val PAGE_STIFFNESS = 400f
        const val ELEMENT_DAMPING = 0.85f
        const val ELEMENT_STIFFNESS = 380f
        const val ELEMENT_FAST_STIFFNESS = 800f
        const val ELEMENT_SLOW_DAMPING = 0.9f
        const val ELEMENT_SLOW_STIFFNESS = 200f
        const val PLAYFUL_DAMPING = 0.7f
        const val PLAYFUL_STIFFNESS = 800f
        const val EFFECTS_FAST_STIFFNESS = 3800f
        const val EFFECTS_STIFFNESS = 1600f
        const val EFFECTS_SLOW_STIFFNESS = 800f

        /** Reduced-motion fade lengths (half of these), and the riding fades. */
        const val EFFECTS_FAST_MS = 100
        const val EFFECTS_DEFAULT_MS = 200
        const val EFFECTS_SLOW_MS = 300

        const val RIDING_MAX_MS = 250
        const val MANEUVER_SWAP_MS = 160
        const val RIDING_CARD_DELAY_MS = 100
        const val RIDING_BAR_DELAY_MS = 150
        const val RIDING_SWAP_OUT_MS = 60
        const val RIDING_SWAP_IN_DELAY_MS = 50

        const val STAGGER_MS = 20
        const val STAGGER_ROWS = 10
        const val SKELETON_PULSE_MS = 900

        /** Page and tab leave, dialog and greeting start sizes. */
        const val DIALOG_SCALE = 0.92f
        const val GREETING_SCALE = 0.4f

        const val CAMERA_RECENTER_MS = 400
        const val CAMERA_FIT_MS = 600

        /** True when the system animator scale is 0 (Developer options or Accessibility). */
        fun isReducedMotion(context: Context): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The Material components' motion, built from the same tokens as [KtmMotionSpecs] so a sheet, a switch or a nav-bar
 * indicator moves like the rest of the app. [calm] (riding surfaces) uses critically damped springs: no overshoot.
 */
class KtmMotionScheme(private val calm: Boolean = false) : MotionScheme {
    private val damping get() = if (calm) 1f else KtmMotionSpecs.ELEMENT_DAMPING
    private val slowDamping get() = if (calm) 1f else KtmMotionSpecs.ELEMENT_SLOW_DAMPING

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(damping, KtmMotionSpecs.ELEMENT_STIFFNESS)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(damping, KtmMotionSpecs.ELEMENT_FAST_STIFFNESS)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(slowDamping, KtmMotionSpecs.ELEMENT_SLOW_STIFFNESS)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, KtmMotionSpecs.EFFECTS_STIFFNESS)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, KtmMotionSpecs.EFFECTS_FAST_STIFFNESS)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, KtmMotionSpecs.EFFECTS_SLOW_STIFFNESS)
}

val LocalKtmMotion = staticCompositionLocalOf { KtmMotionSpecs() }

/** Whether "Remove animations" is on, kept current while the app is open (the setting can change from the shade). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(KtmMotionSpecs.isReducedMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = KtmMotionSpecs.isReducedMotion(context) }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = KtmMotionSpecs.isReducedMotion(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
