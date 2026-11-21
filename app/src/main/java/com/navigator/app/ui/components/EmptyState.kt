package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Immutable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/** A button in an empty or error state. */
@Immutable
class StateAction(val label: String, val onClick: () -> Unit)

/**
 * What happened, what still works, and one action. Never a developer string. [secondary] is a text
 * button under the primary (Import GPX under Record a ride); [tertiary] is a second one (a link to a setting).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primary: StateAction? = null,
    secondary: StateAction? = null,
    tertiary: StateAction? = null,
    /** Scrolls when the page is shorter than it (landscape, a large font). False inside a list or another scroll, which already does. */
    scrollable: Boolean = true,
) {
    val c = KtmTheme.colors
    Column(
        modifier.fillMaxWidth().let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = KtmSpacing.xl, vertical = KtmSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        // An 88 dp tile with a soft corner, the same on every empty page (RD4, PC4).
        // A short screen (landscape) gets a smaller tile so the page and its buttons fit without scrolling.
        val heightPx = LocalWindowInfo.current.containerSize.height
        val short = heightPx > 0 && with(LocalDensity.current) { heightPx.toDp() } < SHORT_SCREEN_DP.dp
        Box(Modifier.size(if (short) KtmLayout.emptyTileShort else KtmLayout.emptyTile).background(c.surf3, KtmShapes.Sheet), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.fg3, modifier = Modifier.size(if (short) KtmLayout.emptyGlyphShort else KtmLayout.emptyGlyph))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.fg2, textAlign = TextAlign.Center)
        if (primary != null) {
            KtmButton(primary.label, primary.onClick, Modifier.padding(top = KtmSpacing.sm), style = ButtonStyle.Primary)
        }
        if (secondary != null) {
            KtmButton(secondary.label, secondary.onClick, style = ButtonStyle.Text, size = ButtonSize.Small)
        }
        if (tertiary != null) {
            KtmButton(tertiary.label, tertiary.onClick, style = ButtonStyle.Text, size = ButtonSize.Small)
        }
    }
}

private const val SHORT_SCREEN_DP = 480
