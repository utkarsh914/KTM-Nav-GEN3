package com.navigator.app.ui.feature.rides.merge

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ride.RideSource
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.components.StatTile
import com.navigator.app.ui.components.fullBleed
import com.navigator.app.ui.components.rideDuration
import com.navigator.app.ui.feature.rides.RideSourceText
import com.navigator.app.ui.feature.rides.ThumbnailGeometry
import com.navigator.app.ui.feature.rides.drawRoute
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import java.time.Instant
import java.time.ZoneId

/**
 * RD7: what merging the selected rides would give. The joined route (each stop a dashed link), the parts with the
 * stop between them, the combined stats, and Merge rides, which is disabled with the reason when the rides overlap.
 * The route is drawn from the stored points, like the list thumbnails, so it works offline. Stateless.
 */
@Composable
fun MergeContent(ui: MergeUi, onBack: () -> Unit, onMerge: () -> Unit, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        val count = (ui as? MergeUi.Ready)?.plan?.parts?.size
        KtmTopBar(
            title = if (count != null) pluralStringResource(R.plurals.merge_title_count, count, count) else stringResource(R.string.merge_title),
            onBack = onBack,
        )
        val motionSpecs = KtmTheme.motion
        AnimatedContent(targetState = ui, modifier = Modifier.pageInsets(), contentKey = { it::class }, transitionSpec = { motionSpecs.contentSwap() }, label = "loading") { shown ->
            when (shown) {
                MergeUi.Loading -> Column { repeat(3) { SkeletonRow(announce = it == 0) } }
                MergeUi.Gone -> EmptyState(
                    KtmIcons.Warning, stringResource(R.string.merge_gone_title), stringResource(R.string.merge_gone_body),
                )
                is MergeUi.Ready -> Ready(shown, onMerge)
            }
        }
    }
}

@Composable
private fun Ready(ui: MergeUi.Ready, onMerge: () -> Unit) {
    val plan = ui.plan
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KtmSpacing.edge),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            when (val b = plan.block) {
                is MergeBlock.Overlap -> InfoBanner(
                    title = stringResource(R.string.merge_overlap_title),
                    message = stringResource(R.string.merge_overlap_body, rideName(b.first), rideName(b.second)),
                    tone = BannerTone.Warn,
                )
                MergeBlock.Active -> InfoBanner(message = stringResource(R.string.merge_active_body), tone = BannerTone.Warn)
                MergeBlock.TooFew, null -> Unit
            }
            if (plan.route.isNotEmpty()) RouteMap(plan)
            Parts(plan)
            if (plan.block == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    StatTile(stringResource(R.string.merge_stat_distance), DistanceFormatter.format(plan.distanceMeters, DistanceUnits.METRIC), Modifier.weight(1f))
                    StatTile(stringResource(R.string.merge_stat_riding), rideDuration(plan.ridingSeconds), Modifier.weight(1f))
                    StatTile(stringResource(R.string.merge_stat_total), rideDuration(plan.totalSeconds), Modifier.weight(1f))
                }
                ListRow(
                    modifier = Modifier.fullBleed(),
                    title = stringResource(R.string.merge_name_title),
                    supporting = stringResource(if (plan.anyStarred) R.string.merge_notes_joined_starred else R.string.merge_notes_joined),
                    supportingLines = 2,
                    trailing = { Text(rideName(plan.name, RideSource.MERGED), style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2, maxLines = 1) },
                )
                Text(pluralStringResource(R.plurals.merge_note_replace, plan.parts.size, plan.parts.size), style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2)
            }
        }
        KtmButton(
            stringResource(R.string.merge_action), onMerge,
            Modifier.fillMaxWidth().padding(KtmSpacing.edge),
            size = ButtonSize.Rest, enabled = plan.block == null, loading = ui.merging, icon = KtmIcons.Merge,
        )
    }
}

@Composable
private fun rideName(label: String?, source: String?): String =
    label?.takeIf { it.isNotBlank() } ?: stringResource(RideSourceText.title(source))

/** A ride without a name is told apart by when it started ("Recorded ride · 14:05"). */
@Composable
private fun rideName(ride: RecordedRide): String {
    val label = ride.destinationLabel?.takeIf { it.isNotBlank() }
    if (label != null) return label
    val time = remember(ride.startMs) {
        ClockFormat.formatter().format(Instant.ofEpochMilli(ride.startMs).atZone(ZoneId.systemDefault()))
    }
    return stringResource(R.string.merge_unnamed_ride, stringResource(RideSourceText.title(ride.source)), time)
}

@Composable
private fun RouteMap(plan: MergePlan) {
    val c = KtmTheme.colors
    val description = stringResource(R.string.merge_route_description)
    val neutral = c.fg2
    val gap = c.warn
    Canvas(
        Modifier.fillMaxWidth().height(KtmLayout.mergeMap).background(c.surf3, KtmShapes.Card)
            .semantics { contentDescription = description },
    ) {
        val px = ThumbnailGeometry.project(plan.route, size.width, size.height, pad = KtmSpacing.xl.toPx())
        drawRoute(px, KtmLayout.routeStroke.toPx(), neutral, gapColor = gap)
    }
}

@Composable
private fun Parts(plan: MergePlan) {
    // The start carries its date (parts can be days apart); the end only when it falls on another day.
    val time = ClockFormat.formatter()
    val dateTime = ClockFormat.formatter(date = "yMd")
    fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    Column {
        plan.parts.forEachIndexed { i, part ->
            val r = part.ride
            if (i > 0 && plan.gaps.getOrNull(i - 1) == null) ListDivider()
            ListRow(
                modifier = Modifier.fullBleed(),
                title = stringResource(R.string.merge_part_title, i + 1, rideName(r.destinationLabel, r.source)),
                supporting = stringResource(
                    R.string.merge_part_times, dateTime.format(zoned(r.startMs)),
                    (if (zoned(r.endMs).toLocalDate() == zoned(r.startMs).toLocalDate()) time else dateTime).format(zoned(r.endMs)), DistanceFormatter.format(r.distanceMeters, DistanceUnits.METRIC),
                ),
            )
            plan.gaps.getOrNull(i)?.let { gap ->
                Text(
                    gapText(gap), style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.warn,
                    modifier = Modifier.padding(vertical = KtmSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun gapText(gap: MergeGap): String {
    val minutes = gap.seconds / 60
    return when {
        minutes < 1 -> stringResource(R.string.merge_gap_short)
        minutes < 60 -> pluralStringResource(R.plurals.merge_gap_min, minutes, minutes)
        else -> pluralStringResource(R.plurals.merge_gap_hours, minutes / 60, minutes / 60, minutes % 60)
    }
}
