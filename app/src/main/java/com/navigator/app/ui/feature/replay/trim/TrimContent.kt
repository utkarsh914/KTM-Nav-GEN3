package com.navigator.app.ui.feature.replay.trim

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import com.navigator.app.R
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.IconButtonStyle
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.LandscapePanel
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.feature.replay.PlaybackEngine
import com.navigator.app.ui.feature.replay.ReplayFormat
import com.navigator.app.ui.feature.replay.trackFraction
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.theme.KtmElevation
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlin.math.abs
import kotlin.math.roundToInt

class TrimActions(
    val onBack: () -> Unit,
    val onMoveStart: (Float) -> Unit,
    val onMoveEnd: (Float) -> Unit,
    val onMoveNearest: (Float) -> Unit,
    val onNudgeStart: (Int) -> Unit,
    val onNudgeEnd: (Int) -> Unit,
    val onApply: () -> Unit,
)

/**
 * RP5: the chrome of the trim page over the map (which shows what is kept against what goes). A bar with a handle at
 * each end of the kept part, what the cut leaves, and Trim, enabled once something changes. A refusal shows here, not
 * in a toast. Stateless; [onInsets] reports what the sheet or panel covers so the screen can frame the route.
 */
@Composable
fun TrimContent(ui: TrimUi, actions: TrimActions, onInsets: (MapInsets) -> Unit, modifier: Modifier = Modifier) {
    when (ui) {
        TrimUi.Loading -> Unit
        TrimUi.Gone -> Column(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
            Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(KtmSpacing.xs)) {
                KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
            }
            EmptyState(KtmIcons.Warning, stringResource(R.string.trim_gone_title), stringResource(R.string.trim_gone_message))
        }
        is TrimUi.Ready -> BoxWithConstraints(modifier.fillMaxSize()) {
            if (LandscapePanelSpec.isLandscape(maxWidth, maxHeight)) {
                LandscapePanel(onFootprint = { onInsets(MapInsets(left = it)) }, gap = KtmSpacing.md) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KtmIconButton(KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack)
                        Text(stringResource(R.string.trim_title), style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
                        Body(ui, actions)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    KtmIconButton(
                        KtmIcons.ArrowLeft, stringResource(R.string.common_back), actions.onBack,
                        modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(KtmSpacing.sm),
                        style = IconButtonStyle.Tonal, size = IconButtonSize.Map,
                    )
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .onSizeChanged { onInsets(MapInsets(bottom = it.height)) }
                            .shadow(KtmElevation.sheet, KtmShapes.SheetTop).background(KtmTheme.colors.surf, KtmShapes.SheetTop)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
                    ) {
                        Text(stringResource(R.string.trim_title), style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg)
                        Body(ui, actions)
                        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Body(ui: TrimUi.Ready, actions: TrimActions) {
    val c = KtmTheme.colors
    val last = ui.range.last
    Text(stringResource(R.string.trim_hint), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
    TrimRangeBar(
        from = PlaybackEngine.fractionOf(ui.range.from, last + 1), to = PlaybackEngine.fractionOf(ui.range.to, last + 1),
        onMoveStart = actions.onMoveStart, onMoveEnd = actions.onMoveEnd, onMoveNearest = actions.onMoveNearest,
        onNudgeStart = actions.onNudgeStart, onNudgeEnd = actions.onNudgeEnd, nudge = (last / NUDGE_DIVISOR).coerceAtLeast(1),
        valueText = stringResource(R.string.trim_range_value, timeAt(ui, ui.range.from), timeAt(ui, ui.range.to)),
        enabled = !ui.applying,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.trim_start_label, timeAt(ui, ui.range.from)), style = MaterialTheme.typography.labelMedium, color = c.fg)
        Text(stringResource(R.string.trim_end_label, timeAt(ui, ui.range.to)), style = MaterialTheme.typography.labelMedium, color = c.fg)
    }
    ui.kept?.let {
        Text(
            stringResource(R.string.trim_keeps, DistanceFormatter.format(it.distanceMeters, DistanceUnits.METRIC), duration(it.durationSeconds)),
            style = MaterialTheme.typography.titleSmall, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
    if (ui.failed) InfoBanner(message = stringResource(R.string.trim_failed), tone = BannerTone.Bad)
    Text(stringResource(R.string.trim_permanent), style = MaterialTheme.typography.bodySmall, color = c.fg2)
    Row(horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        KtmButton(stringResource(R.string.trim_cancel), actions.onBack, Modifier.weight(1f), style = ButtonStyle.Outline, size = ButtonSize.Rest, enabled = !ui.applying)
        KtmButton(
            stringResource(R.string.trim_action), actions.onApply, Modifier.weight(1f), style = ButtonStyle.Danger, size = ButtonSize.Rest,
            enabled = ui.canApply, loading = ui.applying, icon = KtmIcons.Trim,
        )
    }
}

private fun timeAt(ui: TrimUi.Ready, index: Int): String = ReplayFormat.timeOfDay(ui.points[index.coerceIn(0, ui.points.lastIndex)].timeMs)

@Composable
private fun duration(seconds: Int): String {
    val minutes = (seconds + 59) / 60
    return if (minutes < 60) stringResource(R.string.ride_duration_min, minutes)
    else stringResource(R.string.ride_duration_h, minutes / 60, minutes % 60)
}

/** A touch moves the nearer handle (to the touch, by tap or drag); the 28 dp handles sit in 48 dp touch bands. */
@Composable
fun TrimRangeBar(
    from: Float,
    to: Float,
    onMoveStart: (Float) -> Unit,
    onMoveEnd: (Float) -> Unit,
    onMoveNearest: (Float) -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    nudge: Int,
    valueText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val start by rememberUpdatedState(onMoveStart)
    val end by rememberUpdatedState(onMoveEnd)
    val nearest by rememberUpdatedState(onMoveNearest)
    val fromNow by rememberUpdatedState(from)
    val toNow by rememberUpdatedState(to)
    val description = stringResource(R.string.trim_range_description)
    val startEarlier = stringResource(R.string.trim_start_earlier)
    val startLater = stringResource(R.string.trim_start_later)
    val endEarlier = stringResource(R.string.trim_end_earlier)
    val endLater = stringResource(R.string.trim_end_later)
    Canvas(
        modifier.fillMaxWidth().height(KtmLayout.targetRest)
            .semantics {
                contentDescription = description
                stateDescription = valueText
                customActions = if (!enabled) emptyList() else listOf(
                    CustomAccessibilityAction(startEarlier) { onNudgeStart(-nudge); true },
                    CustomAccessibilityAction(startLater) { onNudgeStart(nudge); true },
                    CustomAccessibilityAction(endEarlier) { onNudgeEnd(-nudge); true },
                    CustomAccessibilityAction(endLater) { onNudgeEnd(nudge); true },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val handle = KtmLayout.trimHandle.toPx()
                detectTapGestures { at -> nearest(trackFraction(at.x, this.size.width.toFloat(), handle)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val handle = KtmLayout.trimHandle.toPx()
                var movingStart = true
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        val f = trackFraction(at.x, this.size.width.toFloat(), handle)
                        movingStart = abs(f - fromNow) <= abs(f - toNow)
                        if (movingStart) start(f) else end(f)
                    },
                    onHorizontalDrag = { change, _ ->
                        val f = trackFraction(change.position.x, this.size.width.toFloat(), handle)
                        if (movingStart) start(f) else end(f)
                    },
                )
            },
    ) {
        val handle = KtmLayout.trimHandle.toPx()
        val track = KtmLayout.seekTrack.toPx()
        val left = handle / 2
        val width = (size.width - handle).coerceAtLeast(1f)
        val y = size.height / 2
        val x1 = left + width * from
        val x2 = left + width * to
        drawLine(c.surf3, Offset(left, y), Offset(left + width, y), strokeWidth = track, cap = StrokeCap.Round)
        drawLine(a.fill, Offset(x1, y), Offset(x2, y), strokeWidth = track, cap = StrokeCap.Round)
        for (x in listOf(x1, x2)) {
            drawCircle(c.surf, radius = handle / 2, center = Offset(x, y))
            drawCircle(c.line2, radius = handle / 2, center = Offset(x, y), style = Stroke(KtmLayout.hairline.toPx()))
            drawCircle(a.fill, radius = handle / 2 - track / 2, center = Offset(x, y))
        }
    }
}

/** A screen-reader nudge moves a handle by this fraction (1/100) of the track, at least one fix. */
private const val NUDGE_DIVISOR = 100
