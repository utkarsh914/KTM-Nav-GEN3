package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.animation.core.snap
import androidx.compose.animation.SizeTransform
import com.navigator.app.ui.shell.tripStripBounds
import com.navigator.app.ui.shell.LocalTripShared
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.navigator.app.R
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmSheetContent
import com.navigator.app.ui.components.SheetHandle
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.CancellationException

/**
 * MO2: the trip bar and the trip sheet are one container. Opening grows the bar into the sheet (the bar's content
 * fades out in the first 60 ms, the sheet's fades in as it settles); swipe down on the handle, the chevron, the
 * scrim or Back reverses it, and the back gesture previews the closing as it is dragged. The maneuver card stays put.
 *
 * The sheet is drawn here rather than in a window, which is what lets the two share bounds. It keeps what the
 * window sheet had: a titled pane for screen readers with a Close action, dismiss by scrim, drag and Back.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TripBarSheet(
    bar: TripBarUi,
    open: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    maxSheetHeight: Dp,
    modifier: Modifier = Modifier,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    val motion = KtmTheme.motion
    var drag by remember { mutableFloatStateOf(0f) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(open) { drag = 0f; backProgress = 0f }
    PredictiveBackHandler(enabled = open) { events ->
        try {
            events.collect { backProgress = it.progress }
            onDismiss()
        } catch (cancelled: CancellationException) {
            backProgress = 0f
            throw cancelled
        }
    }
    val dismissPx = with(LocalDensity.current) { DISMISS_DRAG.toPx() }
    // MO6: inside the riding stage, the whole bar also shares bounds with the activity strip; on its own (tests) it brings its own scope.
    val stage = LocalTripShared.current
    if (stage != null) {
        TripBarSheetBody(stage.scope, bar, open, onOpen, onDismiss, maxSheetHeight, drag, backProgress, dismissPx, { dy -> drag = (drag + dy).coerceAtLeast(0f) }, { if (drag > dismissPx) onDismiss(); drag = 0f }, modifier.tripStripBounds(), sheet)
    } else {
        SharedTransitionLayout(modifier) {
            TripBarSheetBody(this, bar, open, onOpen, onDismiss, maxSheetHeight, drag, backProgress, dismissPx, { dy -> drag = (drag + dy).coerceAtLeast(0f) }, { if (drag > dismissPx) onDismiss(); drag = 0f }, Modifier, sheet)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TripBarSheetBody(
    scope: SharedTransitionScope,
    bar: TripBarUi,
    open: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    maxSheetHeight: Dp,
    drag: Float,
    backProgress: Float,
    dismissPx: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    val motion = KtmTheme.motion
    with(scope) {
        AnimatedContent(
            targetState = open,
            modifier = modifier.graphicsLayer {
                translationY = drag
                val shrink = 1f - BACK_SHRINK * backProgress
                scaleX = shrink; scaleY = shrink
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
            // The shared bounds do the growing; the default size spring would outlast the 250 ms riding limit and stream sizes to the map.
            transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false) { _, _ -> snap() }) },
            label = "tripBarSheet",
        ) { isOpen ->
            val container = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = CONTAINER_KEY),
                animatedVisibilityScope = this,
                enter = fadeIn(motion.ridingFade(CONTENT_IN_DELAY_MS)),
                exit = fadeOut(tween(CONTENT_OUT_MS)),
                boundsTransform = { _, _ -> motion.ridingSpatial<Rect>() },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(KtmShapes.Card),
            )
            if (isOpen) {
                SheetPane(
                    onDismiss = onDismiss, maxHeight = maxSheetHeight, modifier = container,
                    onDrag = onDrag, onDragEnd = onDragEnd,
                    content = sheet,
                )
            } else {
                TripBar(bar, onOpen, container)
            }
        }
    }
}

@Composable
private fun SheetPane(
    onDismiss: () -> Unit,
    maxHeight: Dp,
    modifier: Modifier,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val r = KtmTheme.riding
    val title = stringResource(R.string.trip_sheet_title)
    val close = stringResource(R.string.trip_sheet_close)
    Column(
        modifier.fillMaxWidth().heightIn(max = maxHeight).clip(KtmShapes.SheetTop).background(r.surface)
            .semantics {
                paneTitle = title
                isTraversalGroup = true
                customActions = listOf(CustomAccessibilityAction(close) { onDismiss(); true })
            },
    ) {
        SheetHandle(
            Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, dy -> change.consume(); onDrag(dy) }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(start = KtmSpacing.lg, end = KtmSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall, color = KtmTheme.colors.fg, maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            KtmIconButton(KtmIcons.ChevronDown, close, onDismiss)
        }
        KtmSheetContent(content = content)
    }
}

private const val CONTAINER_KEY = "trip-container"

/** MO2: the bar's content is gone within 60 ms; the sheet's content arrives as the container settles. */
private const val CONTENT_OUT_MS = 60
private const val CONTENT_IN_DELAY_MS = 50

/** How far the sheet is dragged before letting go closes it. */
private val DISMISS_DRAG = KtmSpacing.xxxl + KtmSpacing.xxxl

/** The sheet shrinks this much (at most) as the back gesture is dragged. */
private const val BACK_SHRINK = 0.08f
