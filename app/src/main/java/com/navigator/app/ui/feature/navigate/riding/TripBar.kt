package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics

private val PROGRESS_H = KtmLayout.progressLine

@Composable
fun timeLeftText(t: TimeLeft): String = when (t.kind) {
    TimeLeft.Kind.Minutes -> stringResource(R.string.riding_time_min, t.minutes)
    TimeLeft.Kind.HoursMinutes -> stringResource(R.string.riding_time_hm, t.hours, t.minutes)
    TimeLeft.Kind.Hours -> stringResource(R.string.riding_time_h, t.hours)
}

/**
 * NV1/NV3/NV5: speed, time left, distance and arrival, with a 4 dp progress line. Tapping anywhere on it (or the
 * 56 dp chevron) opens the trip sheet. When the rider is over the limit the speed block turns solid red and shows
 * the limit, in one 100 ms colour change; the number itself is plain text that never animates.
 */
@Composable
fun TripBar(ui: TripBarUi, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val r = KtmTheme.riding
    val haptics = rememberHaptics()
    val speedBg by animateColorAsState(if (ui.overspeed) r.overspeed else r.surfaceHigh, KtmTheme.motion.ridingColor(), label = "overspeed")
    val speedFg by animateColorAsState(if (ui.overspeed) r.onOverspeed else r.onSurface, KtmTheme.motion.ridingColor(), label = "overspeedText")
    val menu = stringResource(R.string.riding_trip_menu)
    val speedDescription = when {
        !ui.speedKnown -> stringResource(R.string.riding_speed_no_fix)
        ui.alert != null -> stringResource(R.string.riding_speed_over_description, ui.speed, ui.alert)
        else -> stringResource(R.string.riding_speed_description, ui.speed)
    }
    Column(
        modifier.fillMaxWidth().clip(KtmShapes.Card).background(r.surface)
            .clickable(role = Role.Button, onClickLabel = menu) { haptics.tap(); onOpen() },
    ) {
        Row(Modifier.height(IntrinsicSize.Min).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.fillMaxHeight().widthIn(min = KtmLayout.targetRiding + KtmSpacing.md).background(speedBg)
                    .padding(horizontal = KtmSpacing.md, vertical = KtmSpacing.sm)
                    .semantics(mergeDescendants = true) { contentDescription = speedDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(ui.speed, style = MaterialTheme.typography.headlineLarge, color = speedFg, maxLines = 1, textAlign = TextAlign.Center)
                Text(
                    if (ui.alert != null) stringResource(R.string.riding_alert, ui.alert) else stringResource(R.string.riding_speed_unit),
                    style = MaterialTheme.typography.labelSmall, color = speedFg, maxLines = 1,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = KtmSpacing.md, vertical = KtmSpacing.sm)) {
                if (ui.starting) {
                    Text(
                        ui.destination ?: stringResource(R.string.riding_calculating), style = MaterialTheme.typography.headlineSmall,
                        color = r.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    ui.timeLeft?.let {
                        Text(timeLeftText(it), style = MaterialTheme.typography.headlineSmall, color = r.onSurface, maxLines = 1)
                    }
                    val line = listOfNotNull(ui.distance, ui.arrival).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = KtmTheme.type.ridingSupport, color = r.onSurfaceVariant, maxLines = 1)
                }
            }
            Box(Modifier.size(KtmLayout.targetPrimary), contentAlignment = Alignment.Center) {
                Icon(KtmIcons.ChevronUp, contentDescription = null, tint = r.onSurface, modifier = Modifier.size(KtmLayout.iconRiding))
            }
        }
        Box(Modifier.fillMaxWidth().height(PROGRESS_H).background(r.line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(ui.progress).background(r.guidance))
        }
    }
}
