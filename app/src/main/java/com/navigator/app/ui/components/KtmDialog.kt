package com.navigator.app.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A dialog button. Name the verb ("Delete", "Keep riding"), never "OK". */
@Immutable
class DialogAction(val label: String, val onClick: () -> Unit, val style: ButtonStyle = ButtonStyle.Text)

/**
 * Standard confirm. At rest the buttons are 48 dp, side by side, and labelled with the verb. While
 * riding ([riding]) they stack at 64 dp with the safe choice on top, so a gloved thumb doesn't land
 * on the destructive one by default. Dismissing (back, outside tap) is the safe choice.
 */
@Composable
fun KtmDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: DialogAction,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismiss: DialogAction? = null,
    riding: Boolean = false,
    /** Full-width 64 dp buttons one under another (a choice with a safe option first), also off the riding screen. */
    stacked: Boolean = riding,
    /** A quiet extra choice under the buttons (only with [stacked]). */
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val c = KtmTheme.colors
    val motion = KtmTheme.motion
    // Side by side labels do not fit at a large font: stack them, as on the riding screen.
    val bigText = LocalDensity.current.fontScale >= BIG_TEXT_SCALE
    // Fade and settle in from 92 %; a dialog window can't play an exit, it goes with its window (so a back swipe previews the shrink instead).
    val entered = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The window's own dim appears at once; the scrim is drawn here so it can fade in with the card.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val scrim = remember { Animatable(0f) }
        LaunchedEffect(Unit) { scrim.animateTo(KtmScrim.alpha, motion.effectsDefault()) }
        // A back swipe previews the card shrinking and fading, as the system's does; letting go early eases it back, and
        // a committed swipe finishes the fade before the window goes, so nothing pops out.
        var backProgress by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        var easing by remember { mutableStateOf<Job?>(null) }
        fun easeTo(target: Float) {
            easing?.cancel()
            easing = scope.launch { animate(backProgress, target, animationSpec = if (riding) motion.ridingQuick() else motion.spatialFast()) { v, _ -> backProgress = v } }
        }
        PredictiveBackHandler { events ->
            easing?.cancel()
            try {
                events.collect { backProgress = it.progress }
                if (!motion.reduced) animate(backProgress, 1f, animationSpec = if (riding) motion.ridingQuick() else motion.effectsFast()) { v, _ -> backProgress = v }
                onDismiss()
                // If the caller keeps the dialog, it comes back whole.
                easeTo(0f)
            } catch (cancelled: CancellationException) {
                easeTo(0f)
                throw cancelled
            }
        }
        val back = dialogBackFeedback(backProgress, motion.reduced, riding)
        Box(Modifier.fillMaxSize().drawBehind { drawRect(KtmScrim.copy(alpha = scrim.value * back.scrim)) })
        // Tapping outside the card (including its side margins) dismisses: the safe choice.
        AnimatedVisibility(
            visibleState = entered,
            enter = fadeIn(motion.effectsDefault()) + (if (motion.reduced) EnterTransition.None else scaleIn(if (riding) motion.ridingSpatial() else motion.spatialDefault(), initialScale = KtmMotionSpecs.DIALOG_SCALE)),
        ) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding()
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .padding(KtmSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .graphicsLayer { scaleX = back.scale; scaleY = back.scale; alpha = back.alpha }
                    .widthIn(max = KtmLayout.dialogMaxWidth)
                    .shadow(KtmElevation.dialog, KtmShapes.Sheet)
                    .background(c.surf, KtmShapes.Sheet)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(KtmSpacing.xl)
                    .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.fg, modifier = Modifier.semantics { heading() })
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                content?.invoke()
                if (stacked || bigText) {
                    Column(Modifier.padding(top = KtmSpacing.sm), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                        // Safe choice first.
                        dismiss?.let { DialogButton(it, ButtonSize.Riding, ButtonStyle.Neutral, Modifier.fillMaxWidth()) }
                        DialogButton(confirm, ButtonSize.Riding, confirm.style, Modifier.fillMaxWidth())
                        footer?.invoke()
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(top = KtmSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm, Alignment.End),
                    ) {
                        dismiss?.let { DialogButton(it, ButtonSize.Small, it.style, Modifier) }
                        DialogButton(confirm, ButtonSize.Small, confirm.style, Modifier)
                    }
                }
            }
        }
        }
    }
}

private const val BIG_TEXT_SCALE = 1.3f
private const val RIDING_BACK_SHRINK = 0.05f

/** What a dialog looks like [progress] of the way through a back swipe: the card shrinks and fades, and the dim goes with it. */
internal data class DialogBackFeedback(val scale: Float, val alpha: Float, val scrim: Float)

internal fun dialogBackFeedback(progress: Float, reduced: Boolean, riding: Boolean): DialogBackFeedback {
    if (reduced) return DialogBackFeedback(1f, 1f, 1f)
    val p = progress.coerceIn(0f, 1f)
    // Riding is milder: glanceable, and nothing the rider didn't ask for.
    val shrink = if (riding) RIDING_BACK_SHRINK else 1f - KtmMotionSpecs.BACK_SCALE
    return DialogBackFeedback(1f - shrink * p, 1f - p, 1f - p)
}

@Composable
private fun DialogButton(action: DialogAction, size: ButtonSize, style: ButtonStyle, modifier: Modifier) {
    KtmButton(action.label, action.onClick, modifier, style = style, size = size)
}
