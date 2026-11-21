package com.navigator.app.ui.feature.navigate.riding

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.navigator.app.R
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.contrastRatio
import com.navigator.app.ui.theme.rememberHaptics

private val PROGRESS_H = KtmLayout.progressLine
private const val PROGRESS_MIN_CONTRAST = 3.0

@Composable
fun shortDurationText(seconds: Int): String =
    if (seconds in 1..59) stringResource(R.string.riding_time_s, seconds) else timeLeftText(TimeLeft.of(seconds))

@Composable
fun timeLeftText(t: TimeLeft): String = when (t.kind) {
    TimeLeft.Kind.Minutes -> stringResource(R.string.riding_time_min, t.minutes)
    TimeLeft.Kind.HoursMinutes -> stringResource(R.string.riding_time_hm, t.hours, t.minutes)
    TimeLeft.Kind.Hours -> stringResource(R.string.riding_time_h, t.hours)
}

/**
 * NV1/NV3/NV5: speed, time left, distance and arrival, with a 4 dp progress line along the top. Tapping anywhere on it (or the
 * 56 dp chevron) opens the trip sheet. When the rider is over the limit the speed block turns solid red and shows
 * the limit, in one 100 ms colour change; the number itself is plain text that never animates.
 */
@Composable
fun TripBar(
    ui: TripBarUi,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed to the foot of the landscape panel: square corners (the panel clips them) and its own height. */
    flush: Boolean = false,
    minHeight: Dp = KtmLayout.tripBarHeight,
    speedWidth: Dp = KtmLayout.tripBarSpeed,
) {
    val r = KtmTheme.riding
    val haptics = rememberHaptics()
    val speedBg by animateColorAsState(if (ui.overspeed) r.overspeed else Color.Transparent, KtmTheme.motion.ridingColor(), label = "overspeed")
    val speedFg by animateColorAsState(if (ui.overspeed) r.onOverspeed else r.onSurface, KtmTheme.motion.ridingColor(), label = "overspeedText")
    val menu = stringResource(R.string.riding_trip_menu)
    val speedDescription = when {
        !ui.speedKnown -> stringResource(R.string.riding_speed_no_fix)
        ui.alert != null -> stringResource(R.string.riding_speed_over_description, ui.speed, ui.alert)
        else -> stringResource(R.string.riding_speed_description, ui.speed)
    }
    // The rider's accent, unless it would vanish against this surface (a near-white or near-black custom colour).
    val accent = KtmTheme.accent.fill.takeIf { contrastRatio(it, r.surface) >= PROGRESS_MIN_CONTRAST } ?: r.onSurface
    Box(
        modifier.fillMaxWidth().then(if (flush) Modifier else Modifier.clip(KtmShapes.Card)).background(r.surface)
            .clickable(role = Role.Button, onClickLabel = menu) { haptics.tap(); onOpen() },
    ) {
        Row(Modifier.heightIn(min = minHeight).height(IntrinsicSize.Min).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.fillMaxHeight().width(speedWidth).background(speedBg)
                    // A 1 px divider, drawn only while the block has no fill of its own.
                    .drawBehind { if (!ui.overspeed) drawRect(r.line, Offset(size.width - 1f, 0f), Size(1f, size.height)) }
                    .padding(vertical = KtmSpacing.sm)
                    .semantics(mergeDescendants = true) { contentDescription = speedDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(ui.speed, style = KtmTheme.type.ridingSpeed, color = if (ui.speedKnown) speedFg else r.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.Center)
                Text(
                    if (ui.alert != null) stringResource(R.string.riding_alert, ui.alert) else stringResource(R.string.riding_speed_unit),
                    style = MaterialTheme.typography.labelSmall, color = if (ui.overspeed) speedFg else r.onSurfaceVariant, maxLines = 1,
                )
            }
            Column(Modifier.weight(1f).padding(start = KtmSpacing.lg, end = KtmSpacing.md, top = KtmSpacing.sm, bottom = KtmSpacing.sm)) {
                if (ui.starting) {
                    Text(
                        ui.destination ?: stringResource(R.string.riding_calculating), style = MaterialTheme.typography.headlineSmall,
                        color = r.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(R.string.riding_starting_support), style = KtmTheme.type.ridingSupport, color = r.onSurfaceVariant, maxLines = 1)
                } else {
                    ui.timeLeft?.let {
                        Text(timeLeftText(it), style = MaterialTheme.typography.headlineMedium, color = r.onSurface, maxLines = 1)
                    }
                    // Too narrow (a small phone, a large font): the arrival is what the rider needs, so the distance goes first.
                    var arrivalOnly by remember(ui.arrival?.length) { mutableStateOf(false) }
                    val line = listOfNotNull(ui.distance.takeUnless { arrivalOnly && ui.arrival != null }, ui.arrival).joinToString(" · ")
                    if (line.isNotEmpty()) Text(
                        line, style = KtmTheme.type.ridingSupport, color = r.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        // A line that is too long wraps and is cut by maxLines: that is visual overflow, not width overflow.
                        onTextLayout = { if ((it.hasVisualOverflow || it.multiParagraph.didExceedMaxLines) && ui.arrival != null) arrivalOnly = true },
                    )
                }
            }
            // The chevron sits in its own tinted 56 dp tile, 12 dp from the edge.
            Box(
                Modifier.padding(end = KtmSpacing.md).size(KtmLayout.tripBarMore).clip(KtmShapes.Card).background(r.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KtmIcons.ChevronUp, contentDescription = null, tint = r.onSurface, modifier = Modifier.size(KtmLayout.iconRiding))
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(PROGRESS_H).background(r.line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(ui.progress).background(accent))
        }
    }
}
