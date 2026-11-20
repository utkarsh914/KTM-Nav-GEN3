package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape
import com.navigator.app.ui.components.TurnIconGlyph
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

private val GLYPH = KtmLayout.ridingGlyph
private val LANE_ARROW = KtmLayout.laneArrow
private val LANE_CELL_W = KtmLayout.laneCellWidth
private val LANE_CELL_H = KtmLayout.laneCellHeight

/**
 * NV1-NV5: the card at the top of the riding screen. Glyph, distance (46 sp), the instruction, then the road
 * on its own bold line; the band at its foot is the turn after this one, or the lanes once the turn is near,
 * both inside the same card so nothing floats under it. While rerouting the glyph is a spinner (a faded arrow
 * can be read as a real instruction) and the last instruction stays dimmed. Before guidance has an
 * instruction the card is a neutral "Starting navigation", so the layout does not jump.
 * Numbers never animate: a new maneuver replaces the card's content with a short fade.
 */
@Composable
fun ManeuverCard(ui: ManeuverUi, modifier: Modifier = Modifier) {
    val r = KtmTheme.riding
    AnimatedContent(
        targetState = ui,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { maneuverTransition() },
        contentKey = { if (it is ManeuverUi.Active) ManeuverKey(it.icon, it.road, it.instruction, it.rerouting) else it },
        label = "maneuver",
    ) { shown ->
        when (shown) {
            ManeuverUi.Starting -> StartingCard()
            is ManeuverUi.Active -> ActiveCard(shown, r.guidance, r.guidanceBand, r.onGuidance)
        }
    }
}

private data class ManeuverKey(val icon: TurnIcon, val road: String?, val instruction: String, val rerouting: Boolean)

/** A short fade with a small drift; no overshoot, well under 250 ms. */
private fun maneuverTransition(): ContentTransform =
    (fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 12 }) togetherWith
        (fadeOut(tween(100)) + slideOutVertically(tween(100)) { it / 12 })

@Composable
private fun StartingCard() {
    val r = KtmTheme.riding
    val text = stringResource(R.string.riding_starting)
    Row(
        Modifier.fillMaxWidth().clip(KtmShapes.Card).background(r.surfaceHigh).padding(KtmSpacing.lg)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        CircularProgressIndicator(Modifier.size(GLYPH * 0.6f), color = r.onSurfaceVariant, strokeWidth = KtmSpacing.xs)
        Text(text, style = MaterialTheme.typography.titleMedium, color = r.onSurface)
    }
}

@Composable
private fun ActiveCard(ui: ManeuverUi.Active, bg: androidx.compose.ui.graphics.Color, band: androidx.compose.ui.graphics.Color, on: androidx.compose.ui.graphics.Color) {
    val dim = if (ui.rerouting) 0.6f else 1f
    val rerouting = stringResource(R.string.riding_rerouting)
    Column(Modifier.fillMaxWidth().clip(KtmShapes.Card).background(bg)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            Box(Modifier.size(GLYPH), contentAlignment = Alignment.Center) {
                if (ui.rerouting) {
                    CircularProgressIndicator(Modifier.size(GLYPH * 0.8f), color = on, strokeWidth = KtmSpacing.xs)
                } else {
                    TurnIconRef(ui.icon, size = GLYPH, color = on)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    if (ui.rerouting) {
                        Text(
                            rerouting, style = MaterialTheme.typography.headlineSmall, color = on,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else if (ui.distance != null) {
                        Text(ui.distance, style = MaterialTheme.typography.displayMedium, color = on, maxLines = 1, modifier = Modifier.alpha(dim))
                    }
                    if (ui.exit != null && !ui.rerouting) ExitBadge(ui.exit)
                }
                if (ui.instruction.isNotBlank()) {
                    Text(ui.instruction, style = MaterialTheme.typography.titleMedium, color = on, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(dim))
                }
                if (ui.road != null) {
                    Text(
                        ui.road, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = on,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(dim),
                    )
                }
            }
        }
        when (val footer = ui.footer) {
            ManeuverFooter.None -> {}
            is ManeuverFooter.Then -> Row(
                Modifier.fillMaxWidth().background(band).padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            ) {
                Text(stringResource(R.string.riding_then), style = KtmTheme.type.ridingLabel, color = on)
                TurnIconRef(footer.icon, size = KtmLayout.iconRiding, color = on)
            }
            is ManeuverFooter.Lanes -> Box(Modifier.fillMaxWidth().background(band).padding(horizontal = KtmSpacing.sm, vertical = KtmSpacing.xs)) {
                LaneBand(footer.lanes)
            }
        }
    }
}

/** The exit number, white on the card (18 sp) so it reads from the saddle. */
@Composable
private fun ExitBadge(exit: String) {
    val r = KtmTheme.riding
    Text(
        stringResource(R.string.riding_exit, exit), style = KtmTheme.type.ridingBadge,
        color = r.guidance,
        modifier = Modifier.clip(KtmShapes.Badge).background(r.onGuidance).padding(horizontal = KtmSpacing.sm, vertical = KtmSpacing.xs),
        maxLines = 1,
    )
}

/**
 * Lanes: 28 dp arrows in 40 x 44 dp cells. The lanes that lead to the turn are bright, the rest dimmed, and the
 * recommended ones are also marked with an underline so the difference does not rest on brightness alone.
 */
@Composable
fun LaneBand(lanes: List<LaneInfo>, modifier: Modifier = Modifier) {
    val on = KtmTheme.riding.onGuidance
    val description = stringResource(R.string.riding_lanes_description)
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        lanes.forEach { lane ->
            val recommended = stringResource(R.string.riding_lane_recommended)
            Column(
                Modifier.widthIn(min = LANE_CELL_W).heightIn(min = LANE_CELL_H)
                    .semantics { if (lane.recommended) contentDescription = recommended },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row {
                    val shapes = lane.directions.ifEmpty { listOf(LaneShape.STRAIGHT) }
                    shapes.forEach { shape ->
                        TurnIconGlyph(laneShapeIcon(shape), size = LANE_ARROW, color = if (lane.recommended) on else on.copy(alpha = 0.4f))
                    }
                }
                if (lane.recommended) Box(Modifier.width(LANE_ARROW).size(width = LANE_ARROW, height = KtmLayout.laneRecommendedMark).background(on, KtmShapes.Pill))
            }
        }
    }
}

private fun laneShapeIcon(shape: LaneShape): TurnIcon = when (shape) {
    LaneShape.STRAIGHT -> TurnIcon.GO_STRAIGHT
    LaneShape.SLIGHT_LEFT -> TurnIcon.LIGHT_LEFT
    LaneShape.LEFT -> TurnIcon.QUITE_LEFT
    LaneShape.SHARP_LEFT -> TurnIcon.HEAVY_LEFT
    LaneShape.UTURN_LEFT -> TurnIcon.UTURN_LEFT
    LaneShape.SLIGHT_RIGHT -> TurnIcon.LIGHT_RIGHT
    LaneShape.RIGHT -> TurnIcon.QUITE_RIGHT
    LaneShape.SHARP_RIGHT -> TurnIcon.HEAVY_RIGHT
    LaneShape.UTURN_RIGHT -> TurnIcon.UTURN_RIGHT
    LaneShape.UNKNOWN -> TurnIcon.GO_STRAIGHT
}
