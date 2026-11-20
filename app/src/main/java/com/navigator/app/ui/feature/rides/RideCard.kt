package com.navigator.app.ui.feature.rides

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.data.rides.ThumbPoint
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideMetrics
import com.navigator.app.ride.RideSource
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.highlightedText
import com.navigator.app.ui.map.bandColor
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One ride on the list: a route thumbnail, what it is and how it began, one stats line, and the star. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RideCard(
    item: RideListItem.Ride,
    selecting: Boolean,
    selected: Boolean,
    thumbnail: suspend (RecordedRide) -> List<ThumbPoint>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val ride = item.ride
    val title = ride.destinationLabel?.takeIf { it.isNotBlank() } ?: stringResource(RideSourceText.title(ride.source))
    val how = stringResource(RideSourceText.how(ride.source))
    val whenText = stringResource(R.string.ride_when_how, rideTime(ride.startMs), how)
    val container = if (selected) a.container else c.surf
    val selectLabel = stringResource(R.string.rides_menu_select)
    Row(
        modifier.fillMaxWidth()
            .clip(KtmShapes.Card)
            .background(container)
            .border(if (selected) KtmLayout.outline else KtmLayout.hairline, if (selected) a.fill else c.line, KtmShapes.Card)
            .semantics { if (selecting) this.selected = selected }
            .combinedClickable(
                role = if (selecting) Role.Checkbox else Role.Button,
                onLongClickLabel = selectLabel,
                onLongClick = { haptics.tap(); onLongClick() },
                onClick = { haptics.tap(); onClick() },
            )
            .padding(start = KtmSpacing.md, top = KtmSpacing.md, bottom = KtmSpacing.md, end = KtmSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RideThumbnailView(ride, thumbnail)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
            Text(
                highlightedText(title, item.titleMatch.takeIf { ride.destinationLabel?.isNotBlank() == true }),
                style = MaterialTheme.typography.titleMedium, color = if (selected) a.onContainer else c.fg,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (item.notesMatch != null && !ride.notes.isNullOrBlank()) {
                Text(
                    highlightedText(ride.notes, item.notesMatch), style = MaterialTheme.typography.bodySmall, color = c.fg2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    Text(whenText, style = MaterialTheme.typography.bodySmall, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (ride.source == RideSource.GPX) KtmBadge(stringResource(R.string.ride_badge_gpx), tone = BadgeTone.Neutral)
                }
            }
            Text(rideStats(ride), style = MaterialTheme.typography.bodyMedium, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selecting) {
            Box(Modifier.size(KtmLayout.targetRest), contentAlignment = Alignment.Center) {
                Checkbox(
                    checked = selected, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = a.fill, checkmarkColor = a.onFill, uncheckedColor = c.line2),
                )
            }
        } else {
            KtmIconButton(
                if (ride.saved) KtmIcons.StarFilled else KtmIcons.Star,
                stringResource(if (ride.saved) R.string.ride_unstar else R.string.ride_star),
                onToggleStar,
                tint = if (ride.saved) a.text else c.fg2,
                size = IconButtonSize.Rest,
            )
        }
    }
}

@Composable
private fun rideStats(ride: RecordedRide): String {
    val distance = DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC)
    val minutes = (ride.durationSeconds + 59) / 60
    val duration = if (minutes < 60) stringResource(R.string.ride_duration_min, minutes)
    else stringResource(R.string.ride_duration_h, minutes / 60, minutes % 60)
    val top = ride.maxSpeedKmh.toInt()
    return if (top >= 1) stringResource(R.string.ride_stats_three, distance, duration, stringResource(R.string.ride_speed_kmh, top))
    else stringResource(R.string.ride_stats_two, distance, duration)
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun rideTime(ms: Long): String = TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** A 64 dp route preview drawn from the stored points: speed-coloured, a neutral line for rides with no speed (GPX). */
@Composable
private fun RideThumbnailView(ride: RecordedRide, thumbnail: suspend (RecordedRide) -> List<ThumbPoint>) {
    val c = KtmTheme.colors
    val points by produceState<List<ThumbPoint>?>(null, ride.id, ride.pointCount) {
        value = runCatching { thumbnail(ride) }.getOrDefault(emptyList())
    }
    val bands = RideMetrics.SpeedBands.DEFAULT
    val stroke = KtmLayout.outline
    // Decorative: the card's text already says everything a screen reader needs.
    Box(Modifier.size(KtmLayout.rideThumb).background(c.surf3, KtmShapes.Control).clearAndSetSemantics {}) {
        val pts = points
        if (!pts.isNullOrEmpty()) {
            val neutral = c.fg2
            Canvas(Modifier.size(KtmLayout.rideThumb)) {
                val px = ThumbnailGeometry.project(pts, size.width, size.height, pad = KtmSpacing.sm.toPx())
                val width = stroke.toPx()
                for (i in 1 until px.size) {
                    val b = px[i]
                    if (b.gap) continue
                    val color: Color = if (b.speedKmh == null) neutral else Color(bandColor(bands.bandOf(b.speedKmh), bands.bandCount))
                    drawLine(color, Offset(px[i - 1].x, px[i - 1].y), Offset(b.x, b.y), strokeWidth = width, cap = StrokeCap.Round)
                }
            }
        }
    }
}
