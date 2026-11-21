package com.navigator.app.ui.feature.navigate.finished

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.displayAvgKmh
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.Skeleton
import com.navigator.app.ui.components.StatTile
import com.navigator.app.ui.components.isNarrowWindow
import com.navigator.app.ui.feature.navigate.riding.TimeLeft
import com.navigator.app.ui.feature.navigate.riding.shortDurationText
import com.navigator.app.ui.feature.navigate.riding.timeLeftText
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import java.time.Instant
import java.time.ZoneId

/** Everything the screen shows; the host builds it from the trip, the resolved ride and the settings. */
data class TripFinishedModel(
    val destination: String?,
    val ui: FinishUi,
    val fallbackDurationSec: Int,
    val avgMode: AvgSpeedMode,
    val placeSaved: Boolean,
    val canSavePlace: Boolean,
    val canRideBack: Boolean,
    val minDistanceM: Int,
    val minDurationS: Int,
    /** Recording was just switched on from here: the line says the next trip will be kept. */
    val recordingNowOn: Boolean = false,
)

class TripFinishedActions(
    val onDone: () -> Unit,
    val onReplay: () -> Unit,
    val onSavePlace: () -> Unit,
    val onRideBack: () -> Unit,
    val onShare: () -> Unit,
    val onTurnOnRecording: () -> Unit,
    val onChangeLimits: () -> Unit,
)

/**
 * TF1-TF5: the summary that replaces the riding screen when the trip ends. Portrait it is a sheet (the track is
 * framed above it) with 36 sp stat tiles and 72 dp follow-up tiles; in landscape it takes the riding panel's place
 * with 24 sp stats and 48 dp follow-up buttons, Done and Replay at 56 dp at the foot. Time is known at once; the
 * stats that need the saved track are skeletons until it is there (TF2). Without a recording it says why and
 * offers the fix (TF3); Share and Replay are then left out rather than greyed. Done is always enabled.
 * Place it in a panel that gives it the sheet or the side panel.
 */
@Composable
fun TripFinishedContent(model: TripFinishedModel, actions: TripFinishedActions, landscape: Boolean, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    // TF4 is the 352 dp panel (874 wide): all four stats. TF5 is the 320 dp one (780 wide): the time moves up beside the title.
    val compact = landscape && isNarrowWindow()
    // Landscape fills the panel so Done and Replay sit at its foot, where the trip bar was.
    Column(
        modifier.fillMaxWidth().then(if (landscape) Modifier.fillMaxHeight() else Modifier),
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
            // A success-tinted flag, not the rider's accent: arriving is the same good news in every colour.
            Box(
                Modifier.size(KtmLayout.targetRest).background(c.okContainer, KtmShapes.Pill),
                contentAlignment = Alignment.Center,
            ) { Icon(KtmIcons.Flag, null, tint = c.ok) }
            Column(Modifier.weight(1f)) {
                val span = (model.ui as? FinishUi.Recorded)?.ride?.takeIf { it.startMs > 0 && it.endMs > it.startMs }
                val arrived = if (span == null) stringResource(R.string.finished_arrived) else {
                    val clock = ClockFormat.formatter()
                    val zone = ZoneId.systemDefault()
                    stringResource(
                        R.string.finished_arrived_times,
                        clock.format(Instant.ofEpochMilli(span.startMs).atZone(zone)), clock.format(Instant.ofEpochMilli(span.endMs).atZone(zone)),
                    )
                }
                Text(arrived, style = MaterialTheme.typography.labelMedium, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    model.destination?.takeIf { it.isNotBlank() } ?: stringResource(R.string.finished_reached),
                    style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            // TF5: on the short screen the time moves up here and the stats get one row.
            if (compact) {
                val ride = (model.ui as? FinishUi.Recorded)?.ride
                Text(
                    timeLeftText(TimeLeft.of(ride?.durationSeconds ?: model.fallbackDurationSec)),
                    style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1,
                )
            }
        }
        // The middle scrolls (a short landscape screen); the buttons at the foot never do.
        Column(
            Modifier.weight(1f, fill = landscape).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            // One live region around the stats: the swap from skeletons to numbers is announced.
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { Stats(model, landscape, compact) }
            (model.ui as? FinishUi.NoRecording)?.let { WhyNoRecording(model, it.reason, actions) }
            FollowUps(model, actions, landscape)
        }
        val recorded = model.ui is FinishUi.Recorded
        Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            // Shown disabled while the ride is still being saved (TF2), left out when there is nothing to replay (TF3).
            if (recorded || model.ui == FinishUi.Saving) {
                KtmButton(
                    stringResource(R.string.finished_replay), actions.onReplay, Modifier.weight(1f),
                    style = ButtonStyle.Outline, size = ButtonSize.Rest, icon = KtmIcons.Play, enabled = recorded,
                )
            }
            KtmButton(
                stringResource(R.string.finished_done), actions.onDone, Modifier.weight(1f),
                size = ButtonSize.Rest,
            )
        }
    }
}

@Composable
private fun Stats(model: TripFinishedModel, landscape: Boolean, compact: Boolean) {
    val style = if (landscape) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall
    val ride: RecordedRide? = (model.ui as? FinishUi.Recorded)?.ride
    val saving = model.ui == FinishUi.Saving
    val time = stringResource(if (model.ui is FinishUi.NoRecording) R.string.finished_time else R.string.finished_riding_time)
    val timeValue = timeLeftText(TimeLeft.of(ride?.durationSeconds ?: model.fallbackDurationSec))
    val unit = stringResource(R.string.riding_speed_unit)
    val avgLabel = stringResource(if (model.avgMode == AvgSpeedMode.OVERALL) R.string.finished_avg_overall else R.string.finished_avg_moving)
    when {
        compact && ride != null -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            valueAndUnit(DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC)).let { (v, u) -> StatTile(stringResource(R.string.finished_distance), v, Modifier.weight(1f).fillMaxHeight(), unit = u, valueStyle = style) }
            StatTile(avgLabel, ride.displayAvgKmh(model.avgMode).toInt().toString(), Modifier.weight(1f).fillMaxHeight(), unit = unit, valueStyle = style)
            StatTile(stringResource(R.string.finished_max), ride.maxSpeedKmh.toInt().toString(), Modifier.weight(1f).fillMaxHeight(), unit = unit, valueStyle = style)
        }
        compact && saving -> Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            repeat(3) { StatSkeleton(Modifier.weight(1f)) }
        }
        compact -> Unit
        ride != null -> Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                valueAndUnit(DistanceFormatter.format(ride.distanceMeters, DistanceUnits.METRIC)).let { (v, u) -> StatTile(stringResource(R.string.finished_distance), v, Modifier.weight(1f), unit = u, valueStyle = style) }
                valueAndUnit(timeValue).let { (v, u) -> StatTile(time, v, Modifier.weight(1f), unit = u, valueStyle = style) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                StatTile(avgLabel, ride.displayAvgKmh(model.avgMode).toInt().toString(), Modifier.weight(1f), unit = unit, valueStyle = style)
                StatTile(stringResource(R.string.finished_max), ride.maxSpeedKmh.toInt().toString(), Modifier.weight(1f), unit = unit, valueStyle = style)
            }
        }
        saving -> Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            val saved = stringResource(R.string.finished_saving)
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                StatSkeleton(Modifier.weight(1f))
                valueAndUnit(timeValue).let { (v, u) -> StatTile(time, v, Modifier.weight(1f), unit = u, valueStyle = style) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                StatSkeleton(Modifier.weight(1f))
                StatSkeleton(Modifier.weight(1f))
            }
            Text(saved, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2)
        }
        else -> valueAndUnit(timeValue).let { (v, u) -> StatTile(time, v, Modifier.fillMaxWidth(), unit = u, valueStyle = style) }
    }
}

/** "6.3 km" → ("6.3", "km"): the unit gets its own, smaller role next to the big number. Anything else stays whole. */
private fun valueAndUnit(text: String): Pair<String, String?> {
    val i = text.indexOf(' ')
    return if (i > 0 && text.indexOf(' ', i + 1) < 0) text.substring(0, i) to text.substring(i + 1) else text to null
}

@Composable
private fun StatSkeleton(modifier: Modifier) {
    Skeleton(modifier.heightIn(min = KtmLayout.rowThumb), KtmShapes.Control)
}

/** TF3: the reason in words, with the one tap that fixes it. */
@Composable
private fun WhyNoRecording(model: TripFinishedModel, reason: NoRecordingReason, actions: TripFinishedActions) {
    val limits = stringResource(
        R.string.finished_limits,
        DistanceFormatter.format(model.minDistanceM, DistanceUnits.METRIC),
        shortDurationText(model.minDurationS),
    )
    val turnedOn = model.recordingNowOn && reason == NoRecordingReason.RecordingOff
    InfoBanner(
        when (reason) {
            NoRecordingReason.RecordingOff -> stringResource(if (turnedOn) R.string.finished_recording_next else R.string.finished_recording_off)
            NoRecordingReason.TooShort -> stringResource(R.string.finished_too_short, limits)
        },
        action = if (turnedOn) null else {
            {
                KtmButton(
                    stringResource(if (reason == NoRecordingReason.RecordingOff) R.string.finished_recording_on else R.string.finished_change),
                    if (reason == NoRecordingReason.RecordingOff) actions.onTurnOnRecording else actions.onChangeLimits,
                    style = ButtonStyle.Text, size = ButtonSize.Small,
                )
            }
        },
    )
}

/** Save place, Ride back, Share GPX: 72 dp labelled tiles in portrait, 48 dp buttons in landscape (TF4). */
@Composable
private fun FollowUps(model: TripFinishedModel, actions: TripFinishedActions, landscape: Boolean) {
    val size = if (landscape) ButtonSize.Small else ButtonSize.Tile
    // One height for the follow-ups: a label that wraps (a large font) makes its neighbours as tall.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        if (model.canSavePlace) {
            KtmButton(
                stringResource(if (model.placeSaved) R.string.finished_place_saved else if (landscape) R.string.finished_save_short else R.string.finished_save_place), actions.onSavePlace,
                Modifier.weight(1f).fillMaxHeight(), style = ButtonStyle.Tonal, size = size, enabled = !model.placeSaved,
                icon = if (model.placeSaved) KtmIcons.Done else if (landscape) null else KtmIcons.Bookmark,
            )
        }
        KtmButton(
            stringResource(R.string.finished_ride_back), actions.onRideBack, Modifier.weight(1f).fillMaxHeight(),
            style = ButtonStyle.Tonal, size = size, enabled = model.canRideBack, icon = if (landscape) null else KtmIcons.Restart,
        )
        if (model.ui is FinishUi.Recorded) {
            KtmButton(
                stringResource(R.string.finished_share), actions.onShare, Modifier.weight(1f).fillMaxHeight(),
                style = ButtonStyle.Tonal, size = size, icon = if (landscape) null else KtmIcons.Share,
            )
        }
    }
}
