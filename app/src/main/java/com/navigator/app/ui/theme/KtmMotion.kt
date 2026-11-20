package com.navigator.app.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens (design §3.5). The spring numbers match the table; with "Remove animations" on
 * ([reduced]) spatial moves collapse to short crossfades and camera moves jump.
 *
 * Riding surfaces use the riding variants: functional, at most 250 ms, never overshooting.
 */
class KtmMotionSpecs(val reduced: Boolean = false) {
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spatial(1400f)
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spatial(700f)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spatial(300f)

    fun <T> effectsFast(): FiniteAnimationSpec<T> = fade(EFFECTS_FAST_MS)
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = fade(EFFECTS_DEFAULT_MS)

    /** Riding: a plain ease with no overshoot, and a hard ceiling of [RIDING_MAX_MS]. */
    fun <T> ridingSpatial(delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(RIDING_MAX_MS, delayMillis = delayMs)

    /** The overspeed colour change: a plain 100 ms fade, never a flash. */
    fun <T> ridingColor(): FiniteAnimationSpec<T> = tween(EFFECTS_FAST_MS)

    val cameraRecenterMs: Int get() = if (reduced) 0 else CAMERA_RECENTER_MS
    val cameraFitMs: Int get() = if (reduced) 0 else CAMERA_FIT_MS

    private fun <T> spatial(stiffness: Float): FiniteAnimationSpec<T> =
        if (reduced) tween(EFFECTS_FAST_MS) else spring(dampingRatio = 0.9f, stiffness = stiffness)

    private fun <T> fade(ms: Int): FiniteAnimationSpec<T> = if (reduced) tween(ms / 2) else tween(ms)

    companion object {
        const val EFFECTS_FAST_MS = 100
        const val EFFECTS_DEFAULT_MS = 200
        const val RIDING_MAX_MS = 250
        const val CAMERA_RECENTER_MS = 400
        const val CAMERA_FIT_MS = 600

        /** True when the system animator scale is 0 (Developer options or Accessibility). */
        fun isReducedMotion(context: Context): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
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
