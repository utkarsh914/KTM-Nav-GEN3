package com.navigator.app.ui.feature.pairing

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.R
import com.navigator.app.ble.ScannedBike
import com.navigator.app.ble.SignalStrength
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmStep
import com.navigator.app.ui.components.KtmStepHeader
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.LargeTitle
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.components.StateAction
import com.navigator.app.ui.components.StepState
import com.navigator.app.ui.components.TonalIconBadge
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class PairingActions(
    /** Null while pairing is the only way forward (first run). */
    val onBack: (() -> Unit)?,
    val onPick: (ScannedBike) -> Unit,
    val onScanAgain: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
    val onViewLogs: () -> Unit,
    val onShareLog: () -> Unit,
    val onBluetoothSettings: () -> Unit,
    /** Null when there is nowhere to go instead (re-pairing from Settings). */
    val onPairLater: (() -> Unit)?,
    val onFixBlock: (BlockReason) -> Unit,
    val onDebugSkip: () -> Unit,
)

/**
 * ON6 to ON9: one frame, four bodies. [progress] is the first-run bar (its last segment); null when re-pairing from
 * Settings. Stateless.
 */
@Composable
fun PairingContent(ui: PairingUi, actions: PairingActions, modifier: Modifier = Modifier, progress: Pair<Int, Int>? = null) {
    var troubleOpen by rememberSaveable { mutableStateOf(false) }
    // The failure opens the help by itself; closing it leaves the page with Try again.
    var failureHelpClosed by rememberSaveable { mutableStateOf(false) }
    val failed = ui.phase is PairingPhase.Failed
    LaunchedEffect(failed) { if (!failed) failureHelpClosed = false }
    // A second failure straight after a retry must open the help again.
    val retry = { failureHelpClosed = false; actions.onRetry() }
    val helpOpen = troubleOpen || (failed && !failureHelpClosed)

    Column(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        val confirming = ui.phase is PairingPhase.Confirming
        // No Back while confirming: a half-paired state is worse than waiting. The rider's way out there is Cancel.
        val back = actions.onBack.takeUnless { confirming }
        if (progress != null) KtmStepHeader(progress.first, progress.second, back) else KtmTopBar(onBack = back)
        when (val phase = ui.phase) {
            is PairingPhase.Blocked -> Blocked(phase.reason, actions) { troubleOpen = true }
            PairingPhase.Scanning -> Scanning(ui, actions) { troubleOpen = true }
            is PairingPhase.Confirming -> Confirming(phase, actions)
            PairingPhase.Failed -> Failed(actions, retry, dimmed = helpOpen) { troubleOpen = true }
        }
    }
    if (helpOpen) {
        HavingTroubleSheet(
            onViewLogs = actions.onViewLogs, onShareLog = actions.onShareLog, onBluetoothSettings = actions.onBluetoothSettings,
            onDismiss = { troubleOpen = false; failureHelpClosed = true },
            onTryAgain = retry.takeIf { failed },
        )
    }
}

@Composable
private fun Scanning(ui: PairingUi, actions: PairingActions, onTrouble: () -> Unit) {
    var othersOpen by rememberSaveable { mutableStateOf(false) }
    val c = KtmTheme.colors
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LargeTitle(stringResource(R.string.pair_title))
            Column(Modifier.padding(horizontal = KtmSpacing.edge)) {
                KtmStep(1, stringResource(R.string.pair_step_bluetooth), StepState.Done)
                KtmStep(2, stringResource(R.string.pair_step_ignition), StepState.Active)
                KtmStep(3, stringResource(R.string.pair_step_close), StepState.Active)
            }

            if (ui.scanFailed) {
                InfoBanner(
                    stringResource(R.string.pair_scan_failed), Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
                    tone = BannerTone.Warn,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm),
            ) {
                Text(
                    stringResource(R.string.pair_bikes), style = MaterialTheme.typography.titleSmall, color = c.fg2,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (ui.scanning) {
                    CircularProgressIndicator(Modifier.size(KtmSpacing.lg), color = KtmTheme.accent.text, strokeWidth = KtmSpacing.xs / 2)
                    Text(stringResource(R.string.pair_scanning), style = MaterialTheme.typography.bodySmall, color = c.fg2)
                } else {
                    // Scanning stops by itself; this is the way to start it again.
                    KtmButton(stringResource(R.string.pair_scan_again), actions.onScanAgain, style = ButtonStyle.Text, size = ButtonSize.Small, icon = KtmIcons.Refresh)
                }
            }
            Column(Modifier.padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.md), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                when {
                    ui.bikes.isNotEmpty() -> ui.bikes.forEachIndexed { i, bike -> BikeCard(bike, best = i == 0 && (bike.pairedBefore || bike.signal == SignalStrength.VERY_CLOSE), actions.onPick) }
                    ui.scanning -> { SkeletonRow(); SkeletonRow() }
                    else -> Text(stringResource(R.string.pair_none), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                }
            }

            if (ui.others.isNotEmpty()) {
                val othersState = stringResource(if (othersOpen) R.string.pair_others_expanded else R.string.pair_others_collapsed)
                ListRow(
                    title = pluralStringResource(R.plurals.pair_others, ui.others.size, ui.others.size),
                    supporting = stringResource(R.string.pair_others_hint), supportingLines = 1,
                    leadingIcon = KtmIcons.Bluetooth,
                    trailing = { Icon(if (othersOpen) KtmIcons.ChevronUp else KtmIcons.ChevronDown, null, tint = c.fg2) },
                    onClick = { othersOpen = !othersOpen },
                    modifier = Modifier.padding(top = KtmSpacing.sm).semantics { stateDescription = othersState },
                )
                if (othersOpen) ui.others.forEach { OtherRow(it, actions.onPick) }
            }
            if (ui.debugSkip) {
                KtmButton(
                    stringResource(R.string.pair_skip_debug), actions.onDebugSkip,
                    Modifier.fillMaxWidth().padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.lg), style = ButtonStyle.Text,
                )
            }
        }
        // ON6: the help is at the foot, where a rider who is stuck looks.
        KtmButton(
            stringResource(R.string.pair_trouble), onTrouble, Modifier.align(Alignment.CenterHorizontally).padding(vertical = KtmSpacing.sm),
            style = ButtonStyle.Text, icon = KtmIcons.Help,
        )
    }
}

/** ON6: a bike as a card with its signal and a Pair button; the strongest one is outlined in the accent. No address: it says nothing to a rider. */
@Composable
private fun BikeCard(bike: ScannedBike, best: Boolean, onPick: (ScannedBike) -> Unit) {
    val c = KtmTheme.colors
    val a = KtmTheme.accent
    val name = bike.name ?: stringResource(R.string.pair_unknown_device)
    val (label, tone) = signalBadge(bike)
    val pairDescription = stringResource(R.string.pair_action_cd, name)
    Row(
        Modifier.fillMaxWidth().background(c.surf, KtmShapes.Card)
            .border(if (best) KtmLayout.selectedOutline else KtmLayout.hairline, if (best) a.fill else c.line, KtmShapes.Card)
            .padding(KtmSpacing.lg),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md),
    ) {
        TonalIconBadge(if (bike.isLikelyDash) KtmIcons.Bike else KtmIcons.Bluetooth)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (label != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                    Icon(
                        if (bike.pairedBefore) KtmIcons.Done else KtmIcons.Signal, null,
                        tint = if (tone == BadgeTone.Ok) c.okText else c.fg2, modifier = Modifier.size(KtmSpacing.lg),
                    )
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = c.fg2)
                }
            }
        }
        KtmButton(
            stringResource(R.string.pair_action), { onPick(bike) }, Modifier.semantics { contentDescription = pairDescription },
            size = ButtonSize.Small,
        )
    }
}

/** A device from the folded list: its name and what is known of its signal, one tap to pair. */
@Composable
private fun OtherRow(bike: ScannedBike, onPick: (ScannedBike) -> Unit) {
    // Two nameless devices would read the same: the tail of the address tells them apart.
    val signal = signalBadge(bike).first?.let { stringResource(it) }
    ListRow(
        title = bike.name ?: stringResource(R.string.pair_unknown_device),
        supporting = if (bike.name == null) listOfNotNull(signal, bike.address.takeLast(ADDRESS_TAIL)).joinToString(" · ") else signal,
        leadingIcon = KtmIcons.Bluetooth,
        onClick = { onPick(bike) },
        trailing = { RowChevron() },
    )
}

/** Paired before wins over the signal (a bonded dash may not advertise at all); no reading says nothing. */
private fun signalBadge(bike: ScannedBike): Pair<Int?, BadgeTone> = when {
    bike.pairedBefore -> R.string.pair_paired_before to BadgeTone.Info
    else -> when (bike.signal) {
        SignalStrength.VERY_CLOSE -> R.string.pair_signal_very_close to BadgeTone.Ok
        SignalStrength.NEARBY -> R.string.pair_signal_nearby to BadgeTone.Neutral
        SignalStrength.FAR -> R.string.pair_signal_far to BadgeTone.Warn
        SignalStrength.UNKNOWN -> null to BadgeTone.Neutral
    }
}

@Composable
private fun Confirming(phase: PairingPhase.Confirming, actions: PairingActions) {
    val bikeName = phase.bike.name ?: stringResource(R.string.pair_unknown_device)
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.lg),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.pair_confirm_title), style = MaterialTheme.typography.headlineLarge, color = KtmTheme.colors.fg,
                modifier = Modifier.semantics { heading() }.padding(top = KtmSpacing.lg),
            )
            Text(
                stringResource(R.string.pair_confirm_body), style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
                modifier = Modifier.padding(top = KtmSpacing.sm),
            )
            // ON7: what the dash is showing, so the rider knows which button to press on it.
            DashPreview(
                DashPreviewState.Prompt(stringResource(R.string.pair_prompt, Build.MODEL), stringResource(R.string.pair_yes), stringResource(R.string.pair_no)),
                Modifier.padding(vertical = KtmSpacing.lg),
            )
            KtmStep(1, stringResource(R.string.pair_found, bikeName), StepState.Done)
            if (phase.securing) {
                KtmStep(2, stringResource(R.string.pair_dash_answered), StepState.Done)
                KtmStep(3, stringResource(R.string.pair_securing), StepState.Busy)
            } else {
                KtmStep(2, stringResource(R.string.pair_waiting_clock, PairingPolicy.clock(phase.elapsedSec)), StepState.Busy)
                KtmStep(3, stringResource(R.string.pair_securing), StepState.Pending)
            }
        }
        KtmButton(stringResource(R.string.common_cancel), actions.onCancel, Modifier.fillMaxWidth(), style = ButtonStyle.Outline)
    }
}

@Composable
private fun Blocked(reason: BlockReason, actions: PairingActions, onTrouble: () -> Unit) {
    val noPermission = reason == BlockReason.NO_PERMISSION
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            EmptyState(
                icon = if (noPermission) KtmIcons.Bluetooth else KtmIcons.BluetoothOff,
                title = stringResource(if (noPermission) R.string.pair_blocked_permission_title else R.string.pair_blocked_off_title),
                message = stringResource(if (noPermission) R.string.pair_blocked_permission else R.string.pair_blocked_off),
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                primary = StateAction(stringResource(if (noPermission) R.string.pair_allow_nearby else R.string.pair_turn_on)) { actions.onFixBlock(reason) },
                // If the system's "turn on" dialog was refused, the rider needs another way to Bluetooth.
                secondary = if (noPermission) actions.onPairLater?.let { StateAction(stringResource(R.string.pair_later), it) }
                else StateAction(stringResource(R.string.pair_open_bluetooth), actions.onBluetoothSettings),
                tertiary = if (noPermission) null else actions.onPairLater?.let { StateAction(stringResource(R.string.pair_later), it) },
            )
        }
        // The help stays reachable here too: logs and Share log matter most when pairing cannot even start.
        KtmButton(
            stringResource(R.string.pair_trouble), onTrouble,
            Modifier.align(Alignment.CenterHorizontally).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(vertical = KtmSpacing.sm),
            style = ButtonStyle.Text, icon = KtmIcons.Help,
        )
    }
}

/** ON9: the page behind the help sheet (dimmed while it is open), with Try again once the sheet is closed. */
@Composable
private fun Failed(actions: PairingActions, onRetry: () -> Unit, dimmed: Boolean, onTrouble: () -> Unit) {
    val alpha by animateFloatAsState(if (dimmed) KtmLayout.dimmedContent else 1f, KtmTheme.motion.effectsFast(), label = "failed-dim")
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .alpha(alpha),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            LargeTitle(stringResource(R.string.pair_failed_title))
            Text(
                stringResource(R.string.pair_failed_body), style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
                modifier = Modifier.padding(horizontal = KtmSpacing.edge),
            )
        }
        // While the help sheet is up it carries Try again itself.
        if (!dimmed) {
            Column(Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.lg), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(stringResource(R.string.pair_try_again), onRetry, Modifier.fillMaxWidth())
                KtmButton(stringResource(R.string.pair_trouble), onTrouble, Modifier.fillMaxWidth(), style = ButtonStyle.Tonal, icon = KtmIcons.Help)
                actions.onPairLater?.let { KtmButton(stringResource(R.string.pair_later), it, Modifier.fillMaxWidth(), style = ButtonStyle.Text) }
            }
        }
    }
}

private const val ADDRESS_TAIL = 5
