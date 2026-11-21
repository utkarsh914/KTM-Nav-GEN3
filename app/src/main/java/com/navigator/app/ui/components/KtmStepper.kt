package com.navigator.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.navigator.app.data.settings.IntSpec
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a held stepper button repeats: a pause, then steps that speed up to a floor. */
object RepeatSchedule {
    const val FIRST_DELAY_MS = 400L
    const val START_INTERVAL_MS = 160L
    const val MIN_INTERVAL_MS = 40L

    /** Delay before repeat number [n] (0 = the first repeat after the initial pause). */
    fun delayBefore(n: Int): Long =
        if (n == 0) FIRST_DELAY_MS else maxOf(MIN_INTERVAL_MS, START_INTERVAL_MS - (n - 1) * 20L)
}

/**
 * Replaces text-entry dialogs for numbers: tap − / + (hold to repeat), or tap the value to type it
 * ([onEditValue], which opens the edit-value sheet). The range shows below; a button dims at its end.
 * [onValueChange] must update [value] synchronously: a hold steps every 40 ms at full speed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KtmStepper(
    value: Int,
    spec: IntSpec,
    onValueChange: (Int) -> Unit,
    unit: String,
    rangeText: String,
    decreaseDescription: String,
    increaseDescription: String,
    editDescription: String,
    onEditValue: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Sits after the + button (a Play for the value); it wraps under the stepper on a narrow screen. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = KtmTheme.colors
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm), itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md, Alignment.CenterHorizontally),
        ) {
            StepButton(KtmIcons.Remove, decreaseDescription, enabled && current > spec.min) { change(spec.stepDown(current)) }
            Box(
                // Wide enough for the largest value, so − and + stay put while the digits change.
                Modifier.widthIn(min = ((spec.max.toString().length * 14 + 40) * LocalDensity.current.fontScale).dp)
                    .heightIn(min = KtmLayout.targetPrimary).clip(KtmShapes.Control)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = editDescription, onClick = onEditValue)
                    .padding(horizontal = KtmSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                // The number and its unit share a baseline, and the pair is centred on the − and + buttons.
                Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
                    Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = if (enabled) c.fg else c.fg3, textAlign = TextAlign.Center, modifier = Modifier.alignByBaseline())
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = c.fg2, modifier = Modifier.alignByBaseline())
                }
            }
            StepButton(KtmIcons.Add, increaseDescription, enabled && current < spec.max) { change(spec.stepUp(current)) }
            trailing?.invoke()
        }
        Text(rangeText, style = MaterialTheme.typography.labelSmall, color = c.fg3, modifier = Modifier.padding(top = KtmSpacing.xs))
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit) {
    val c = KtmTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    val stepEnabled by rememberUpdatedState(enabled)
    Box(
        Modifier.size(KtmLayout.targetPrimary).clip(KtmShapes.Pill)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // A tap steps once, on release. A hold waits, then repeats; a scroll that
                        // starts here cancels without changing the value.
                        var repeated = false
                        val repeat = scope.launch {
                            var n = 0
                            while (stepEnabled) {
                                delay(RepeatSchedule.delayBefore(n))
                                if (!stepEnabled) break
                                repeated = true
                                haptics.segmentTick(); step(); n++
                            }
                        }
                        val released = tryAwaitRelease()
                        repeat.cancel()
                        if (released && !repeated && stepEnabled) { haptics.segmentTick(); step() }
                    },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick(label = description) { if (stepEnabled) { step(); true } else false }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) c.fg else c.fg3, modifier = Modifier.size(KtmLayout.iconMedium)) }
}
