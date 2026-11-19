package com.navigator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.navigator.app.ui.theme.AccentScheme
import com.navigator.app.ui.theme.KtmColors
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

enum class BannerTone { Info, Warn, Bad, Accent }

/** Container, text and icon colours of a banner tone. Body text stays high-contrast; the icon carries the tone. */
fun bannerColors(tone: BannerTone, c: KtmColors, a: AccentScheme): Triple<Color, Color, Color> = when (tone) {
    BannerTone.Info -> Triple(c.surf2, c.fg, c.info)
    BannerTone.Warn -> Triple(c.warnContainer, c.fg, c.warn)
    BannerTone.Bad -> Triple(c.badContainer, c.fg, c.bad)
    BannerTone.Accent -> Triple(a.container, a.onContainer, a.onContainer)
}

internal fun defaultBannerIcon(tone: BannerTone): ImageVector = when (tone) {
    BannerTone.Info, BannerTone.Accent -> KtmIcons.Info
    BannerTone.Warn, BannerTone.Bad -> KtmIcons.Warning
}

/** Inline, in-context message that stays until the cause is resolved. Optional [action] sits below the text. */
@Composable
fun InfoBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: BannerTone = BannerTone.Info,
    icon: ImageVector = defaultBannerIcon(tone),
    action: (@Composable () -> Unit)? = null,
) {
    val (container, text, iconColor) = bannerColors(tone, KtmTheme.colors, KtmTheme.accent)
    Row(
        modifier.fillMaxWidth().background(container, KtmShapes.Card).padding(horizontal = 14.dp, vertical = KtmSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = text)
            Text(message, style = MaterialTheme.typography.bodySmall, color = text)
            if (action != null) action()
        }
    }
}
