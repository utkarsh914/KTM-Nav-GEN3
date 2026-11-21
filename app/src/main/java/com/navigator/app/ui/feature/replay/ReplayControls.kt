package com.navigator.app.ui.feature.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.navigator.app.R
import com.navigator.app.ui.components.IconButtonSize
import com.navigator.app.ui.components.IconButtonStyle
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

/**
 * The seek bar: a 6 dp track with a 24 dp thumb in a 48 dp touch band. Tap anywhere to jump, drag to scrub. The stop
 * between two parts of a merged ride is a hatched gap that playback skips. It reads [position] only while drawing, so
 * a playback frame redraws it without recomposing anything.
 */
@Composable
fun ReplaySeekBar(
    position: State<Int>,
    size: Int,
    seams: List<Float>,
    mergeSeams: List<Float>,
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrubStart)
    val description = stringResource(R.string.replay_seek)
    Canvas(
        modifier.fillMaxWidth().height(KtmLayout.targetRest)
            .semantics {
                contentDescription = description
                // Whole percents: a playback frame must not invalidate the accessibility tree 60 times a second.
                progressBarRangeInfo = ProgressBarRangeInfo((PlaybackEngine.fractionOf(position.value, size) * PERCENT).toInt() / PERCENT, 0f..1f)
                setProgress { target -> onSeek(target); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { at -> seek(trackFraction(at.x, this.size.width.toFloat(), KtmLayout.seekThumb.toPx())) }
            }
            .pointerInput(Unit) {
                val thumb = KtmLayout.seekThumb.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { at -> scrub(); seek(trackFraction(at.x, this.size.width.toFloat(), thumb)) },
                    onHorizontalDrag = { change, _ -> seek(trackFraction(change.position.x, this.size.width.toFloat(), thumb)) },
                )
            },
    ) {
        val thumb = KtmLayout.seekThumb.toPx()
        val track = KtmLayout.seekTrack.toPx()
        val left = thumb / 2
        val width = (this.size.width - thumb).coerceAtLeast(1f)
        val y = this.size.height / 2
        val progress = left + width * PlaybackEngine.fractionOf(position.value, size)
        drawLine(c.surf3, Offset(left, y), Offset(left + width, y), strokeWidth = track, cap = StrokeCap.Round)
        drawLine(a.fill, Offset(left, y), Offset(progress, y), strokeWidth = track, cap = StrokeCap.Round)
        // A join between merged rides is a wide warning hatch; a pause inside one ride is a narrower, quieter one.
        seams.forEach {
            val join = it in mergeSeams
            drawStop(left + width * it, y, track, KtmLayout.seekGap.toPx() * (if (join) 1f else PAUSE_GAP_SCALE), c.surf, if (join) c.warn else c.fg2)
        }
        drawCircle(c.surf, radius = thumb / 2, center = Offset(progress, y))
        drawCircle(a.fill, radius = thumb / 2 - track / 2, center = Offset(progress, y))
    }
}

private const val PERCENT = 100f
private const val PAUSE_GAP_SCALE = 0.6f

/** The thumb sits inside the track's ends, so a position maps to the track and not to the whole band. */
internal fun trackFraction(x: Float, widthPx: Float, thumbPx: Float): Float =
    ((x - thumbPx / 2) / (widthPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)

/** A stop between two parts: the track cleared and hatched in the warning colour. */
private fun DrawScope.drawStop(x: Float, y: Float, track: Float, gap: Float, clear: Color, hatch: Color) {
    clipRect(x - gap / 2, y - track / 2, x + gap / 2, y + track / 2) {
        drawRect(clear, Offset(x - gap / 2, y - track / 2), Size(gap, track))
        var k = -track
        while (k < gap + track) {
            drawLine(hatch, Offset(x - gap / 2 + k, y + track / 2), Offset(x - gap / 2 + k + track, y - track / 2), strokeWidth = track / 4)
            k += track / 1.5f
        }
    }
}

/**
 * A − value + stepper in a 48 dp row. [highlight] outlines it in the accent (the speed when it isn't 1×). The end of the
 * range dims its button.
 */
@Composable
fun MiniStepper(
    valueText: String,
    valueDescription: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    lessDescription: String,
    moreDescription: String,
    canLess: Boolean,
    canMore: Boolean,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    valueWidth: Dp = KtmLayout.targetRest,
) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    Row(
        modifier.height(KtmLayout.targetRest)
            .then(if (highlight) Modifier.border(KtmLayout.outline, a.fill, KtmShapes.Control) else Modifier.background(c.surf2, KtmShapes.Control)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KtmIconButton(KtmIcons.Remove, lessDescription, onLess, enabled = canLess)
        Text(
            valueText, style = MaterialTheme.typography.titleSmall, color = if (highlight) a.text else c.fg, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.widthIn(min = valueWidth - KtmSpacing.lg).semantics { contentDescription = valueDescription },
        )
        KtmIconButton(KtmIcons.Add, moreDescription, onMore, enabled = canMore)
    }
}

/** Play (56 dp), Restart, Follow and the speed stepper: one row. */
@Composable
fun ReplayPlayRow(
    playing: Boolean,
    following: Boolean,
    speed: Int,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onToggleFollow: () -> Unit,
    onStepSpeed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtmTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        KtmIconButton(
            if (playing) KtmIcons.Pause else KtmIcons.Play,
            stringResource(if (playing) R.string.replay_pause else R.string.replay_play),
            onTogglePlay, style = IconButtonStyle.Accent, size = IconButtonSize.Map, circular = true,
        )
        KtmIconButton(KtmIcons.Restart, stringResource(R.string.replay_restart), onRestart, style = IconButtonStyle.Tonal)
        val a = KtmTheme.accent
        KtmIconButton(
            KtmIcons.Navigation, stringResource(if (following) R.string.replay_follow_stop else R.string.replay_follow), onToggleFollow,
            modifier = Modifier.background(if (following) a.container else Color.Transparent, KtmShapes.Control),
            tint = if (following) a.onContainer else c.fg,
            style = if (following) IconButtonStyle.Plain else IconButtonStyle.Tonal,
        )
        MiniStepper(
            valueText = stringResource(R.string.replay_speed_value, speed),
            valueDescription = stringResource(R.string.replay_speed_description, speed),
            onLess = { onStepSpeed(-1) }, onMore = { onStepSpeed(1) },
            lessDescription = stringResource(R.string.replay_speed_slower), moreDescription = stringResource(R.string.replay_speed_faster),
            canLess = speed > PlaybackEngine.SPEEDS.first(), canMore = speed < PlaybackEngine.SPEEDS.last(),
            highlight = speed != 1,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
