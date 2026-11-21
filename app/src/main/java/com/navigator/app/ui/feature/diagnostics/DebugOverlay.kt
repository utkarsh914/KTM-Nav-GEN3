package com.navigator.app.ui.feature.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.navigator.app.R
import com.navigator.app.nav.DashPreview
import com.navigator.app.ui.components.DashPalette
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlin.math.roundToInt

enum class OverlayCorner(val alignment: Alignment) {
    TopStart(Alignment.TopStart), TopEnd(Alignment.TopEnd), BottomStart(Alignment.BottomStart), BottomEnd(Alignment.BottomEnd),
}

object DebugOverlayRules {
    /** Debug builds only is the caller's business; riding screens hide it unless it is pinned. */
    fun visible(enabled: Boolean, riding: Boolean, pinned: Boolean): Boolean = enabled && (!riding || pinned)

    /** The corner closest to where the panel was dropped (its centre, in the host's coordinates). */
    fun nearest(centerX: Float, centerY: Float, width: Float, height: Float): OverlayCorner {
        val right = centerX >= width / 2f
        val bottom = centerY >= height / 2f
        return when {
            bottom && right -> OverlayCorner.BottomEnd
            bottom -> OverlayCorner.BottomStart
            right -> OverlayCorner.TopEnd
            else -> OverlayCorner.TopStart
        }
    }
}

/**
 * DG4, debug builds: what the dash would currently show. Docked top-right, collapses to a one-line chip,
 * can be dragged to any corner, and stays off riding screens unless pinned.
 */
@Composable
fun DebugOverlay(riding: Boolean, modifier: Modifier = Modifier) {
    val enabled by DashPreview.overlayEnabled.collectAsState()
    val pinned by DashPreview.overlayPinned.collectAsState()
    val s by DashPreview.state.collectAsState()
    val c = KtmTheme.colors
    // Declared before the visibility check, so hiding it on a riding screen doesn't forget where it was.
    var expanded by rememberSaveable { mutableStateOf(false) }
    var corner by rememberSaveable { mutableStateOf(OverlayCorner.TopEnd) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var host by remember { mutableStateOf(IntSize.Zero) }
    var panel by remember { mutableStateOf(Rect.Zero) }
    if (!DebugOverlayRules.visible(enabled, riding, pinned)) return

    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = KtmSpacing.sm, end = KtmSpacing.sm, bottom = KtmSpacing.sm, top = KtmLayout.topBar).onSizeChanged { host = it }) {
        Column(
            Modifier.align(corner.alignment)
                .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                .onGloballyPositioned { panel = it.boundsInParent() }
                .background(DashPalette.bg.copy(alpha = OVERLAY_ALPHA), KtmShapes.Control)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, amount -> change.consume(); drag += amount },
                        onDragEnd = {
                            if (host != IntSize.Zero) corner = DebugOverlayRules.nearest(panel.center.x, panel.center.y, host.width.toFloat(), host.height.toFloat())
                            drag = Offset.Zero
                        },
                        onDragCancel = { drag = Offset.Zero },
                    )
                }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = KtmSpacing.sm, vertical = KtmSpacing.xs),
        ) {
            val mono = KtmTheme.type.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = DashPalette.debugText)
            if (!expanded) {
                Text(stringResource(R.string.overlay_chip, s.icon?.name ?: "-"), style = mono, maxLines = 1)
            } else {
                listOf(
                    stringResource(R.string.overlay_title, s.updates),
                    stringResource(R.string.overlay_nav, s.guidanceOn.toString(), s.gpsIconOn.toString()),
                    stringResource(R.string.overlay_icon, s.icon?.name ?: "-"),
                    stringResource(R.string.overlay_dist, s.distance ?: "-"),
                    stringResource(R.string.overlay_road, s.road ?: "-"),
                    stringResource(R.string.overlay_eta, s.eta ?: "-", s.remaining ?: "-"),
                    stringResource(R.string.overlay_offline, s.offlineBanner.toString()),
                    stringResource(R.string.overlay_last, s.lastEvent),
                ).forEach { Text(it, style = mono) }
                Text(
                    stringResource(if (pinned) R.string.overlay_unpin else R.string.overlay_pin), style = mono.copy(color = KtmTheme.accent.text),
                    modifier = Modifier.heightIn(min = KtmLayout.targetRest).clickable(role = Role.Switch) { DashPreview.setOverlayPinned(!pinned) }.padding(vertical = KtmSpacing.md),
                )
            }
        }
    }
}

private const val OVERLAY_ALPHA = 0.82f
