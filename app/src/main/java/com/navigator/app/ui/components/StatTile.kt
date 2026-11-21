package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** A labelled number (Distance 6.3 km). The label and the number are on separate lines; the unit is small. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    val c = KtmTheme.colors
    Column(
        modifier.background(c.surf2, KtmShapes.Control).padding(KtmSpacing.md).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.fg2, maxLines = 2)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
            Text(value, style = valueStyle, color = c.fg, maxLines = 1)
            if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall, color = c.fg2, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
