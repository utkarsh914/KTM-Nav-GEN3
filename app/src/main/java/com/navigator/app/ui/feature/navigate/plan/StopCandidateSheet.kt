package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/**
 * PL6 for a spot chosen on the route preview map (a long press, or a tapped place label): its name, which
 * stop number it becomes and where it goes. Pick on map has the same words on its own screen.
 */
@Composable
fun StopCandidateSheet(
    name: String,
    number: Int,
    destination: String,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            Box(
                Modifier.size(KtmLayout.stopMarker).background(KtmTheme.accent.fill, KtmShapes.Pill),
                contentAlignment = Alignment.Center,
            ) { Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = KtmTheme.accent.onFill) }
            Text(
                name, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.stop_goes_in_before, destination), style = MaterialTheme.typography.bodyMedium,
            color = c.fg2,
        )
        Spacer(Modifier.height(KtmSpacing.sm))
        KtmButton(
            stringResource(if (canAdd) R.string.stop_add_here else R.string.route_stop_limit), onAdd,
            Modifier.fillMaxWidth(), enabled = canAdd,
        )
        KtmButton(stringResource(R.string.common_cancel), onCancel, Modifier.fillMaxWidth(), style = ButtonStyle.Text, size = ButtonSize.Small)
    }
}
