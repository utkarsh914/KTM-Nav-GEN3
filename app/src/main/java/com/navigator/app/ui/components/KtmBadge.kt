package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.AccentScheme
import com.navigator.app.ui.theme.KtmColors
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmTheme

enum class BadgeTone { Ok, Warn, Bad, Info, Accent, Neutral }

/** Container and content of a [BadgeTone]. Status colours are never the accent. */
fun badgeColors(tone: BadgeTone, c: KtmColors, a: AccentScheme): Pair<Color, Color> = when (tone) {
    BadgeTone.Ok -> c.okContainer to c.okText
    BadgeTone.Warn -> c.warnContainer to c.warn
    BadgeTone.Bad -> c.badContainer to c.badText
    BadgeTone.Info -> c.infoContainer to c.infoText
    BadgeTone.Accent -> a.container to a.onContainer
    BadgeTone.Neutral -> c.surf3 to c.fg2
}

/** A short status label (Connected, Offline, Needs access, Active, Imported). */
@Composable
fun KtmBadge(text: String, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Neutral) {
    val (container, content) = badgeColors(tone, KtmTheme.colors, KtmTheme.accent)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        maxLines = 1,
        modifier = modifier.background(container, KtmShapes.Badge).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
