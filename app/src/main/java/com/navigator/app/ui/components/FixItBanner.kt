package com.navigator.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.navigator.app.ui.theme.KtmSpacing

/** A banner that names what is missing and gives the one button that fixes it (Allow, Turn on, Re-pair). */
@Composable
fun FixItBanner(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Warn,
    dangerAction: Boolean = false,
) {
    InfoBanner(
        message = message,
        title = title,
        tone = tone,
        modifier = modifier,
        action = {
            KtmButton(
                actionLabel, onAction,
                modifier = Modifier.padding(top = KtmSpacing.sm),
                style = if (dangerAction) ButtonStyle.Danger else ButtonStyle.Neutral,
                size = ButtonSize.Small,
            )
        },
    )
}
