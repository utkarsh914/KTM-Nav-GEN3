package com.navigator.app.ui.feature.navigate.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
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
        Text(
            name, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.stop_goes_before, number, destination), style = MaterialTheme.typography.bodyMedium,
            color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        KtmButton(
            stringResource(if (canAdd) R.string.stop_add_here else R.string.route_stop_limit), onAdd,
            Modifier.fillMaxWidth(), enabled = canAdd,
        )
        KtmButton(stringResource(R.string.common_cancel), onCancel, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
    }
}
