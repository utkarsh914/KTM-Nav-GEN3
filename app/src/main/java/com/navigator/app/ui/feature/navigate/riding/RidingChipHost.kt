package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.navigator.app.R
import com.navigator.app.ui.shell.ChipTone
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/**
 * MO12: the riding chip slot under the maneuver card. One chip at a time, 48 dp, 17 sp; a newer one replaces the
 * old in place, and chips slide down from behind the card and leave the same way, never covering it. Green is
 * done, amber degraded, red needs the rider. The timed ones come from [com.navigator.app.ui.shell.Feedback];
 * the offline chip stays while the phone is offline.
 */
@Composable
fun RidingChipHost(source: ChipSource?, modifier: Modifier = Modifier) {
    val motion = KtmTheme.motion
    Box(modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = source,
            transitionSpec = {
                (fadeIn(motion.effectsDefault()) + slideInVertically(motion.ridingSpatial()) { if (motion.reduced) 0 else -it }) togetherWith
                    (fadeOut(motion.effectsFast()) + slideOutVertically(motion.ridingSpatial()) { if (motion.reduced) 0 else -it })
            },
            contentKey = { chipKey(it) },
            label = "ridingChip",
        ) { shown ->
            if (shown != null) {
                val offline = stringResource(R.string.riding_chip_offline)
                val offlineReroute = stringResource(R.string.riding_chip_offline_reroute)
                when (shown) {
                    is ChipSource.Timed -> Chip(shown.message.text, shown.message.tone, shown.message.onTap)
                    is ChipSource.Offline -> Chip(if (shown.reroutePaused) offlineReroute else offline, ChipTone.Warn, null)
                }
            } else Box(Modifier)
        }
    }
}

private fun chipKey(source: ChipSource?): Any = when (source) {
    is ChipSource.Timed -> source.message.id
    is ChipSource.Offline -> source.reroutePaused
    null -> "none"
}

@Composable
private fun Chip(text: String, tone: ChipTone, onTap: (() -> Unit)?) {
    val r = KtmTheme.riding
    val c = KtmTheme.colors
    val (bg, fg) = when (tone) {
        ChipTone.Ok -> c.ok to c.onStatus
        ChipTone.Warn -> r.warnChip to r.onWarnChip
        ChipTone.Bad -> r.overspeed to r.onOverspeed
        ChipTone.Info -> r.surfaceHigh to r.onSurface
    }
    Text(
        text, style = KtmTheme.type.ridingLabel, color = fg, textAlign = TextAlign.Center, maxLines = 2,
        modifier = Modifier.padding(top = KtmSpacing.sm).heightIn(min = KtmLayout.targetRest).clip(KtmShapes.Pill).background(bg)
            .then(if (onTap != null) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier)
            .padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
