package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** Where a step of a checklist stands (ON6, ON7, ON9). */
enum class StepState { Done, Active, Busy, Pending }

/** One checklist line: a disc (a tick, its number or a spinner) and what to do, wrapped over as many lines as it needs. */
@Composable
fun KtmStep(number: Int, text: String, state: StepState, modifier: Modifier = Modifier, badge: String? = null) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val stateText = when (state) {
        StepState.Done -> stringResource(R.string.step_done)
        StepState.Busy -> stringResource(R.string.step_busy)
        else -> null
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest).semantics(mergeDescendants = true) { stateText?.let { stateDescription = it } },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Box(
            Modifier.size(KtmLayout.stepDisc).background(
                when (state) {
                    StepState.Done -> c.okContainer
                    StepState.Pending -> c.surf3
                    else -> a.container
                },
                KtmShapes.Pill,
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                StepState.Done -> Icon(KtmIcons.Done, null, tint = c.okText, modifier = Modifier.size(KtmSpacing.lg))
                StepState.Busy -> CircularProgressIndicator(Modifier.size(KtmSpacing.lg), color = a.text, strokeWidth = KtmSpacing.xs / 2)
                else -> Text(
                    number.toString(), style = MaterialTheme.typography.labelLarge,
                    color = if (state == StepState.Pending) c.fg3 else a.onContainer,
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (state == StepState.Pending) c.fg3 else c.fg, modifier = Modifier.weight(1f))
        // The answer already known ("Yes"): said in a badge at the end of the line.
        if (badge != null) KtmBadge(badge, tone = BadgeTone.Ok)
    }
}
