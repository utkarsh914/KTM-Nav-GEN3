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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.data.rides.ThumbPoint
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSource
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.highlightedText
import com.navigator.app.ui.components.rideDuration
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    /** A thumbnail that is already cached, so a card coming back into view draws it on its first frame. */
    thumbnailPeek: (RecordedRide) -> List<ThumbPoint>? = { null },
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val haptics = rememberHaptics()
    val ride = item.ride
    val title = ride.destinationLabel?.takeIf { it.isNotBlank() } ?: stringResource(RideSourceText.title(ride.source))
    val how = stringResource(RideSourceText.how(ride.source))
    val whenText = if (item.showDate) {
        stringResource(R.string.ride_when_date_how, rideDay(ride.startMs), rideTime(ride.startMs), how)
    } else stringResource(R.string.ride_when_how, rideTime(ride.startMs), how)
    val container = if (selected) a.container else c.surf
    val selectLabel = stringResource(R.string.rides_menu_select)
    Row(
        modifier.fillMaxWidth()
            .clip(KtmShapes.Card)
            .background(container)
            .border(if (selected) KtmLayout.outline else KtmLayout.hairline, if (selected) a.fill else c.line, KtmShapes.Card)
            .semantics { if (selecting) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off }
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
        RideThumbnailView(ride, thumbnail, thumbnailPeek)
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
    val duration = rideDuration(ride.durationSeconds)
    val top = ride.maxSpeedKmh.toInt()
    return if (top >= 1) stringResource(R.string.ride_stats_three, distance, duration, stringResource(R.string.ride_speed_kmh, top))
    else stringResource(R.string.ride_stats_two, distance, duration)
}

private val DAY: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEdMMM"))
}

private val DAY_YEAR: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEdMMMyyyy"))
}

/** With no day headers the card names its day, and the year when it is not this one. */
private fun rideDay(ms: Long): String {
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return (if (day.year == java.time.Year.now().value) DAY else DAY_YEAR).format(day)
}

private fun rideTime(ms: Long): String = ClockFormat.formatter().format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** A 64 dp route preview drawn from the stored points: speed-coloured, a neutral line for rides with no speed (GPX). */
@Composable
private fun RideThumbnailView(ride: RecordedRide, thumbnail: suspend (RecordedRide) -> List<ThumbPoint>, peek: (RecordedRide) -> List<ThumbPoint>?) {
    val c = KtmTheme.colors
    val points by produceState(peek(ride), ride.id, ride.pointCount) {
        value = runCatching { thumbnail(ride) }.getOrDefault(emptyList())
    }
    val stroke = KtmLayout.outline
    // Decorative: the card's text already says everything a screen reader needs.
    Box(Modifier.size(KtmLayout.rideThumb).background(c.surf3, KtmShapes.Control).clearAndSetSemantics {}) {
        val pts = points
        if (!pts.isNullOrEmpty()) {
            val neutral = c.fg2
            Canvas(Modifier.size(KtmLayout.rideThumb)) {
                val px = ThumbnailGeometry.project(pts, size.width, size.height, pad = KtmSpacing.sm.toPx())
                drawRoute(px, stroke.toPx(), neutral)
            }
        }
    }
}
