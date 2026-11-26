package com.navigator.app.ui.feature.navigate.riding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.navigator.app.R
import com.navigator.app.ble.BccuProtocol.TurnIcon
import com.navigator.app.nav.model.LaneInfo
import com.navigator.app.nav.model.LaneShape
import com.navigator.app.ui.components.TurnIconGlyph
import com.navigator.app.ui.components.TurnIconNames
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

private val LANE_ARROW = KtmLayout.laneArrow
private val LANE_CELL_W = KtmLayout.laneCellWidth
private val LANE_CELL_H = KtmLayout.laneCellHeight

/** The sizes the card is drawn at: the portrait card, the landscape block (NL1) and its narrow form (NL7). */
@Immutable
class ManeuverMetrics(
    val glyph: Dp,
    val distance: TextStyle,
    val instruction: TextStyle,
    val instructionLines: Int,
    val laneArrow: Dp,
    /**
     * The landscape block (NL1): it fills the height it is given with its band at the foot, runs flush with square corners
     * (the panel clips them), puts the instruction under the glyph and distance, and shrinks "km" next to the number.
     */
    val fill: Boolean,
    /** Space above the glyph row and between the glyph and the distance (tighter on the narrow panel, NL7). */
    val top: Dp = KtmSpacing.lg,
    val gap: Dp = KtmSpacing.md,
    val lanesHeight: Dp = KtmLayout.lanesHeightLand,
) {
    companion object {
        @Composable
        fun portrait() = ManeuverMetrics(
            KtmLayout.ridingGlyph, MaterialTheme.typography.displayMedium, KtmTheme.type.ridingInstruction, 2, LANE_ARROW, false,
        )

        @Composable
        fun landscape(narrow: Boolean): ManeuverMetrics = if (narrow) ManeuverMetrics(
            KtmLayout.ridingGlyphLand, KtmTheme.type.ridingDistanceNarrow, KtmTheme.type.ridingInstructionNarrow, 3, LANE_ARROW, true,
            top = KtmSpacing.md, gap = KtmSpacing.sm, lanesHeight = KtmLayout.lanesHeightLandNarrow,
        ) else ManeuverMetrics(
            KtmLayout.ridingGlyphLand, MaterialTheme.typography.displayLarge, KtmTheme.type.ridingInstructionLand, 3, KtmLayout.laneArrowLand, true,
        )
    }
}

/**
 * NV1-NV5: the card at the top of the riding screen. Glyph, distance (46 sp), the instruction, then the road
 * on its own bold line; the band at its foot is the turn after this one, or the lanes once the turn is near,
 * both inside the same card so nothing floats under it. While rerouting the glyph is a spinner (a faded arrow
 * can be read as a real instruction) and the last instruction stays dimmed. Before guidance has an
 * instruction the card is a neutral "Starting navigation", so the layout does not jump.
 * Numbers never animate: a new maneuver replaces the card's content with a short fade.
 */
@Composable
fun ManeuverCard(
    ui: ManeuverUi,
    modifier: Modifier = Modifier,
    metrics: ManeuverMetrics = ManeuverMetrics.portrait(),
    /** Named in the starting card ("Getting your route to X"). */
    destination: String? = null,
) {
    val r = KtmTheme.riding
    val motion = KtmTheme.motion
    AnimatedContent(
        targetState = ui,
        modifier = modifier.fillMaxWidth().then(if (metrics.fill) Modifier.fillMaxHeight() else Modifier),
        transitionSpec = { maneuverTransition(motion).using(SizeTransform(clip = true) { _, _ -> motion.ridingQuick() }) },
        contentKey = { if (it is ManeuverUi.Active) ManeuverKey(it.icon, it.road, it.instruction, it.busy) else it },
        label = "maneuver",
    ) { shown ->
        when (shown) {
            ManeuverUi.Starting -> StartingCard(metrics, destination)
            is ManeuverUi.Active -> ActiveCard(shown, metrics, r.guidance, r.guidanceBand, r.onGuidance)
        }
    }
}

private data class ManeuverKey(val icon: TurnIcon, val road: String?, val instruction: String, val rerouting: Boolean)

/** A short fade with a small drift; no overshoot, at most 250 ms; reduced motion keeps only the fade. */
private fun maneuverTransition(motion: KtmMotionSpecs): ContentTransform =
    (fadeIn(if (motion.reduced) motion.ridingFade() else motion.ridingQuick()) + slideInVertically(motion.ridingQuick()) { if (motion.reduced) 0 else -it / 12 }) togetherWith
        (fadeOut(motion.ridingFadeOut()) + slideOutVertically(motion.ridingQuick()) { if (motion.reduced) 0 else it / 12 })

@Composable
private fun StartingCard(m: ManeuverMetrics, destination: String?) {
    val r = KtmTheme.riding
    val text = stringResource(R.string.riding_starting)
    Row(
        Modifier.fillMaxWidth().then(if (m.fill) Modifier.fillMaxHeight() else Modifier.clip(KtmShapes.Card)).background(r.surfaceHigh).padding(KtmSpacing.lg)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        CircularProgressIndicator(Modifier.size(m.glyph * 0.6f), color = KtmTheme.accent.text, strokeWidth = KtmSpacing.xs)
        Column {
            Text(text, style = MaterialTheme.typography.headlineSmall, color = r.onSurface)
            destination?.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.riding_starting_route, it), style = MaterialTheme.typography.titleMedium, color = r.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActiveCard(ui: ManeuverUi.Active, m: ManeuverMetrics, bg: Color, band: Color, on: Color) {
    val dim = if (ui.busy) 0.6f else 1f
    // The band switching between Then and lanes grows the card smoothly, so the map below moves rather than jumps.
    Column(
        Modifier.fillMaxWidth()
            .then(if (m.fill) Modifier.fillMaxHeight() else Modifier.animateContentSize(KtmTheme.motion.ridingSpatial()).clip(KtmShapes.Card))
            .background(bg),
    ) {
        if (m.fill) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(start = KtmSpacing.md, top = m.top, end = KtmSpacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.gap)) {
                    ManeuverGlyph(ui, m, on)
                    DistanceLine(ui, m, on, dim, Modifier.weight(1f, fill = false))
                }
                Column(Modifier.padding(start = KtmSpacing.xs, top = KtmSpacing.sm).turnAnnouncement(ui)) { InstructionLines(ui, m, on, dim) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
            ) {
                ManeuverGlyph(ui, m, on)
                Column(Modifier.weight(1f)) {
                    DistanceLine(ui, m, on, dim)
                    Column(Modifier.turnAnnouncement(ui)) { InstructionLines(ui, m, on, dim) }
                }
            }
        }
        when (val footer = ui.footer) {
            ManeuverFooter.None -> {}
            is ManeuverFooter.Then -> Row(
                Modifier.fillMaxWidth().background(band).then(if (m.fill) Modifier.height(KtmLayout.bandHeightLand) else Modifier.padding(vertical = KtmSpacing.sm))
                    .padding(horizontal = KtmSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            ) {
                Text(stringResource(R.string.riding_then), style = KtmTheme.type.ridingLabel, color = on)
                TurnIconRef(footer.icon, size = KtmLayout.iconRiding, color = on, contentDescription = TurnIconNames.human(footer.icon))
            }
            is ManeuverFooter.Lanes -> Box(
                Modifier.fillMaxWidth().background(band).then(if (m.fill) Modifier.height(m.lanesHeight) else Modifier)
                    .padding(horizontal = KtmSpacing.sm, vertical = KtmSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                LaneBand(footer.lanes, arrow = m.laneArrow)
            }
        }
    }
}

/**
 * What TalkBack announces for a new turn: the icon's name, then the instruction and the road, as one live node. It
 * changes only with the maneuver, so it is not re-read every second as the distance falls; the distance is a node of
 * its own beside it (readable, not live).
 */
@Composable
private fun Modifier.turnAnnouncement(ui: ManeuverUi.Active): Modifier {
    val icon = TurnIconNames.human(ui.icon)
    return semantics(mergeDescendants = true) {
        // While rerouting the card's own "Rerouting" line is the live one: the dimmed last instruction is not announced again.
        if (!ui.busy) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = icon
        }
    }
}

@Composable
private fun ManeuverGlyph(ui: ManeuverUi.Active, m: ManeuverMetrics, on: Color) {
    // The announcement above names the icon: the glyph itself is silent.
    Box(Modifier.size(m.glyph).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (ui.busy) CircularProgressIndicator(Modifier.size(m.glyph * 0.8f), color = on, strokeWidth = KtmSpacing.xs)
        else TurnIconRef(ui.icon, size = m.glyph, color = on)
    }
}

/** The distance (or "Rerouting…") and the exit badge. On the landscape block a "km" unit is smaller, so the number stays the shape you read. */
@Composable
private fun DistanceLine(ui: ManeuverUi.Active, m: ManeuverMetrics, on: Color, dim: Float, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
        if (ui.busy) {
            Text(
                stringResource(ui.busyLabel), style = MaterialTheme.typography.headlineSmall, color = on,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else if (ui.distance != null) {
            Text(
                if (m.fill) withSmallUnit(ui.distance, m.distance.fontSize) else AnnotatedString(ui.distance),
                style = m.distance, color = on, maxLines = 1, modifier = Modifier.alpha(dim),
            )
        }
        if (ui.exit != null && !ui.busy) ExitBadge(ui.exit)
    }
}

/** "12 km" reads as a big 12 and a smaller km (NL3); metres keep one size. */
internal fun withSmallUnit(distance: String, size: TextUnit): AnnotatedString {
    val at = distance.lastIndexOf(' ')
    if (at < 0 || !distance.endsWith(" km")) return AnnotatedString(distance)
    return buildAnnotatedString {
        append(distance.substring(0, at + 1))
        withStyle(SpanStyle(fontSize = size * SMALL_UNIT)) { append(distance.substring(at + 1)) }
    }
}

private const val SMALL_UNIT = 0.56f

@Composable
private fun InstructionLines(ui: ManeuverUi.Active, m: ManeuverMetrics, on: Color, dim: Float) {
    if (ui.instruction.isNotBlank()) {
        Text(if (ui.busy) stringResource(R.string.riding_last_instruction, ui.instruction) else ui.instruction, style = m.instruction, color = on, maxLines = m.instructionLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(dim))
    }
    if (ui.road != null) {
        Text(
            ui.road, style = (if (m.fill) m.instruction else MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Bold), color = on,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(dim),
        )
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
fun LaneBand(lanes: List<LaneInfo>, modifier: Modifier = Modifier, arrow: Dp = LANE_ARROW) {
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
                        TurnIconGlyph(laneShapeIcon(shape), size = arrow, color = if (lane.recommended) on else on.copy(alpha = 0.4f))
                    }
                }
                if (lane.recommended) Box(Modifier.width(arrow).size(width = arrow, height = KtmLayout.laneRecommendedMark).background(on, KtmShapes.Pill))
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
