package com.navigator.app.ui.feature.mirror

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.data.mirror.Freshness
import com.navigator.app.data.mirror.MirrorSetupKind
import com.navigator.app.data.mirror.MirrorSetupRow
import com.navigator.app.data.mirror.MirrorStatus
import com.navigator.app.data.mirror.MirrorTurn
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.BikeStatusDot
import com.navigator.app.ui.components.BikeStatusPill
import com.navigator.app.ui.components.isNarrowWindow
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.TurnIconRef
import com.navigator.app.ui.feature.navigate.BikePillUi
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class MirrorActions(
    val onBike: () -> Unit,
    val onGrantAccess: () -> Unit,
    val onReconnect: () -> Unit,
    val onAllowBattery: () -> Unit,
    val onSending: (Boolean) -> Unit,
    val onOpenMaps: () -> Unit,
    val onRecord: () -> Unit,
)

/** The mirror screen's state for the bike pill, which the host builds from the connection. */
class MirrorBike(val pill: BikePillUi, val label: String, val stateDescription: String)

/**
 * MR1-MR7: the screen shown on the Navigate tab when the engine is Mirror. Setup is one checklist (done rows green,
 * collapsing to a "Ready" line); once Google Maps guides, the dash's own layout is previewed with its freshness in
 * the header, a stale banner when updates stop, and a Pause switch row with its reason. Landscape (mounted) turns the
 * preview into one big turn display with the controls in a column on the right. Stateless.
 */
@Composable
fun MirrorContent(
    status: MirrorStatus,
    bike: MirrorBike,
    time: String,
    landscape: Boolean,
    actions: MirrorActions,
    modifier: Modifier = Modifier,
    /** A recording is running (an auto ride in its grace, or the rider's own): Record a ride gives way to the strip. */
    recording: Boolean = false,
) {
    if (landscape) {
        Row(modifier.fillMaxSize().padding(KtmSpacing.lg), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                Header(status, bike, actions, dot = isNarrowWindow())
                Spacer(Modifier.height(KtmSpacing.md))
                when (status) {
                    is MirrorStatus.Live -> BigTurn(status)
                    is MirrorStatus.Ready -> ReadyArea(status, time, actions, compact = true)
                    is MirrorStatus.Setup -> Checklist(status.rows, actions)
                }
            }
            Column(
                Modifier.width(KtmLayout.panelWide).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) { Controls(status, actions, landscape = true, recording) }
        }
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(KtmSpacing.md),
        ) {
            Header(status, bike, actions)
            when (status) {
                is MirrorStatus.Setup -> Checklist(status.rows, actions)
                is MirrorStatus.Ready -> ReadyArea(status, time, actions, compact = false)
                is MirrorStatus.Live -> {
                    if (status.stale) StaleBanner(status.freshness)
                    Box {
                        DashPreview(
                            turnPreview(status.turn, time),
                            Modifier.alpha(if (status.paused) PAUSED_ALPHA else 1f),
                        )
                        if (status.paused) {
                            KtmBadge(
                                stringResource(R.string.mirror_paused_badge), Modifier.align(Alignment.TopEnd).padding(KtmSpacing.md),
                                tone = BadgeTone.Warn,
                            )
                        }
                    }
                }
            }
            Controls(status, actions, landscape = false, recording)
        }
    }
}

private const val PAUSED_ALPHA = 0.45f

@Composable
private fun Header(status: MirrorStatus, bike: MirrorBike, actions: MirrorActions, dot: Boolean = false) {
    val c = KtmTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.mirror_title), style = MaterialTheme.typography.headlineSmall, color = c.fg, modifier = Modifier.semantics { heading() })
            if (status is MirrorStatus.Live) {
                val label = freshnessLabel(status.freshness)
                // Not a live region: it changes every second. The stale banner announces itself instead.
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (status.stale) c.warn else c.fg2)
            }
        }
        if (dot) BikeStatusDot(bike.label, bike.pill.tone, actions.onBike, stateDescription = bike.stateDescription)
        else BikeStatusPill(bike.label, bike.pill.tone, actions.onBike, stateDescription = bike.stateDescription)
    }
}

@Composable
private fun freshnessLabel(f: Freshness): String = when (f) {
    Freshness.JustNow -> stringResource(R.string.mirror_live_now)
    is Freshness.Seconds -> stringResource(R.string.mirror_live_seconds, f.n)
    is Freshness.Minutes -> pluralStringResource(R.plurals.mirror_live_minutes, f.n, f.n)
}

@Composable
private fun StaleBanner(f: Freshness) {
    val gap = when (f) {
        Freshness.JustNow -> stringResource(R.string.mirror_age_seconds, 0)
        is Freshness.Seconds -> stringResource(R.string.mirror_age_seconds, f.n)
        is Freshness.Minutes -> pluralStringResource(R.plurals.mirror_age_minutes, f.n, f.n)
    }
    InfoBanner(
        title = stringResource(R.string.mirror_stale_title, gap),
        message = stringResource(R.string.mirror_stale_body),
        tone = BannerTone.Warn,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The setup checklist: each row says why, with a 48 dp button; a done row is green. */
@Composable
private fun Checklist(rows: List<MirrorSetupRow>, actions: MirrorActions) {
    val c = KtmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
        // The listener can only be reconnected once access is granted: until then it is not a row of its own.
        val accessDone = rows.firstOrNull { it.kind == MirrorSetupKind.Access }?.done == true
        rows.filter { it.kind != MirrorSetupKind.Listener || accessDone }.forEach { row ->
            val (title, why, action) = rowText(row.kind)
            ListRow(
                title = stringResource(title),
                supporting = if (row.done) null else stringResource(why),
                supportingLines = 3,
                leading = {
                    Icon(
                        if (row.done) KtmIcons.DoneCircle else KtmIcons.Warning, null,
                        tint = if (row.done) c.ok else c.warn,
                    )
                },
                trailing = {
                    if (!row.done) {
                        KtmButton(
                            stringResource(action),
                            when (row.kind) {
                                MirrorSetupKind.Access -> actions.onGrantAccess
                                MirrorSetupKind.Listener -> actions.onReconnect
                                MirrorSetupKind.Battery -> actions.onAllowBattery
                            },
                            style = if (row.required) ButtonStyle.Primary else ButtonStyle.Tonal, size = ButtonSize.Small,
                        )
                    }
                },
            )
        }
    }
}

private data class RowText(@StringRes val title: Int, @StringRes val why: Int, @StringRes val action: Int)

private fun rowText(kind: MirrorSetupKind) = when (kind) {
    MirrorSetupKind.Access -> RowText(R.string.mirror_access_title, R.string.mirror_access_why, R.string.mirror_allow)
    MirrorSetupKind.Listener -> RowText(R.string.mirror_listener_title, R.string.mirror_listener_why, R.string.mirror_reconnect)
    MirrorSetupKind.Battery -> RowText(R.string.mirror_battery_title, R.string.mirror_battery_why, R.string.mirror_allow)
}

/** MR2: the dash preview says "Ready", one green line confirms the setup (tap for the details). */
@Composable
private fun ReadyArea(status: MirrorStatus.Ready, time: String, actions: MirrorActions, compact: Boolean) {
    val c = KtmTheme.colors
    var details by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        if (!compact) {
            DashPreview(DashPreviewState.NotConnected(stringResource(R.string.mirror_ready_title), stringResource(R.string.mirror_ready_hint)))
        } else {
            Text(stringResource(R.string.mirror_ready_title), style = MaterialTheme.typography.displaySmall, color = c.fg)
            Text(stringResource(R.string.mirror_ready_hint), style = MaterialTheme.typography.bodyLarge, color = c.fg2)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = KtmLayout.targetRest).clickable(role = Role.Button) { details = !details },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
        ) {
            Icon(KtmIcons.DoneCircle, null, tint = c.ok)
            Text(stringResource(R.string.mirror_ready_line), style = MaterialTheme.typography.bodyMedium, color = c.okText, modifier = Modifier.weight(1f))
            Icon(if (details) KtmIcons.ChevronUp else KtmIcons.ChevronDown, null, tint = c.fg2)
        }
        if (details) Checklist(status.rows, actions)
    }
}

/** The controls under the preview (portrait) or in the right-hand column (landscape, 64 dp). */
@Composable
private fun Controls(status: MirrorStatus, actions: MirrorActions, landscape: Boolean, recording: Boolean) {
    val size = if (landscape) ButtonSize.Riding else ButtonSize.Rest
    Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        when {
            status is MirrorStatus.Live -> PauseRow(status.paused, actions.onSending)
            status is MirrorStatus.Ready && status.paused -> PauseRow(true, actions.onSending)
        }
        if (status !is MirrorStatus.Setup) {
            KtmButton(
                stringResource(R.string.mirror_open_maps), actions.onOpenMaps, Modifier.fillMaxWidth(),
                size = size, icon = KtmIcons.Navigation,
            )
        }
        if (status is MirrorStatus.Ready && !recording) {
            KtmButton(
                stringResource(R.string.record_ride), actions.onRecord, Modifier.fillMaxWidth(),
                style = ButtonStyle.Tonal, size = size, icon = KtmIcons.Record,
            )
        }
    }
}

/** The pause switch with its reason; amber-outlined while paused so the state is plain at a glance (MR4). */
@Composable
private fun PauseRow(paused: Boolean, onSending: (Boolean) -> Unit) {
    val c = KtmTheme.colors
    KtmSwitchRow(
        title = stringResource(R.string.mirror_sending_title),
        checked = !paused,
        onCheckedChange = onSending,
        supporting = stringResource(if (paused) R.string.mirror_sending_paused else R.string.mirror_sending_on),
        supportingLines = 2,
        leadingIcon = KtmIcons.Dash,
        modifier = if (paused) Modifier.border(KtmLayout.outline, c.warn, KtmShapes.Card) else Modifier,
    )
}

private fun turnPreview(t: MirrorTurn, time: String) = DashPreviewState.Turn(
    time = time, icon = t.icon, distance = t.distance ?: t.instruction.orEmpty(),
    road = if (t.distance != null) listOfNotNull(t.instruction, t.road).joinToString(" · ") else t.road.orEmpty(),
    eta = t.eta ?: "—", remaining = t.remaining ?: "—",
)

/** MR6: the mounted display: glyph 96 dp, distance 64 sp, road 28 sp. */
@Composable
private fun BigTurn(live: MirrorStatus.Live) {
    val c = KtmTheme.colors
    val t = live.turn
    Column(
        Modifier.alpha(if (live.paused) PAUSED_ALPHA else 1f).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
    ) {
        if (live.stale) StaleBanner(live.freshness)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.lg)) {
            // With no instruction text the glyph is the only thing that says which way to turn.
            t.icon?.let {
                TurnIconRef(
                    it, size = KtmLayout.mirrorGlyph, color = c.fg,
                    contentDescription = if (t.instruction.isNullOrBlank()) com.navigator.app.ui.components.TurnIconNames.human(it) else null,
                )
            }
            Column {
                Text(t.distance ?: t.instruction.orEmpty(), style = MaterialTheme.typography.displayLarge, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (t.distance != null) t.instruction?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = c.fg2, maxLines = 1) }
            }
        }
        t.road?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        val line = listOfNotNull(t.eta, t.remaining).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge, color = c.fg2, textAlign = TextAlign.Start)
        if (live.paused) KtmBadge(stringResource(R.string.mirror_paused_badge), tone = BadgeTone.Warn)
    }
}
