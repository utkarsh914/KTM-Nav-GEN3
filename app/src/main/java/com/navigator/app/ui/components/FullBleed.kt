package com.navigator.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import com.navigator.app.ui.theme.KtmSpacing

/**
 * Lets a row run to the edge of a container that pads its content by [bleed] on both sides (a sheet, a padded page).
 * List rows pad themselves by the same amount, so without this their text sits twice as far in as the titles around it.
 */
fun Modifier.fullBleed(bleed: Dp = KtmSpacing.lg): Modifier = layout { measurable, constraints ->
    val extra = (bleed * 2).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}
