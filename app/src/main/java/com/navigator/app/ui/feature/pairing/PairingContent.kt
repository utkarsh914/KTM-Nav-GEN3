package com.navigator.app.ui.feature.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.navigator.app.R
import com.navigator.app.ble.ScannedBike
import com.navigator.app.ble.SignalStrength
import com.navigator.app.ui.components.BadgeTone
import com.navigator.app.ui.components.BannerTone
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.EmptyState
import com.navigator.app.ui.components.InfoBanner
import com.navigator.app.ui.components.KtmBadge
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.components.LargeTitle
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.components.SkeletonRow
import com.navigator.app.ui.components.StateAction
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class PairingActions(
    /** Null while pairing is the only way forward (first run). */
    val onBack: (() -> Unit)?,
    val onPick: (ScannedBike) -> Unit,
    val onScanAgain: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
    val onTrouble: () -> Unit,
    /** Null when there is nowhere to go instead (re-pairing from Settings). */
    val onPairLater: (() -> Unit)?,
    val onFixBlock: (BlockReason) -> Unit,
    val onDebugSkip: () -> Unit,
)

/** ON6 to ON9: one frame, four bodies. Stateless. */
@Composable
fun PairingContent(ui: PairingUi, actions: PairingActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        val confirming = ui.phase is PairingPhase.Confirming
        // No Back while confirming: a half-paired state is worse than waiting. The rider's way out there is Cancel.
        KtmTopBar(
            onBack = actions.onBack.takeUnless { confirming },
            actions = {
                if (!confirming) KtmIconButton(KtmIcons.Help, stringResource(R.string.pair_trouble), actions.onTrouble)
            },
        )
        when (val phase = ui.phase) {
            is PairingPhase.Blocked -> Blocked(phase.reason, actions)
            PairingPhase.Scanning -> Scanning(ui, actions)
            is PairingPhase.Confirming -> Confirming(phase, actions)
            PairingPhase.Failed -> Failed(actions)
        }
    }
}

@Composable
private fun Scanning(ui: PairingUi, actions: PairingActions) {
    var othersOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        LargeTitle(stringResource(R.string.pair_title))
        ListRow(title = stringResource(R.string.pair_step_ignition), leadingIcon = KtmIcons.Bike)
        ListRow(title = stringResource(R.string.pair_step_close), leadingIcon = KtmIcons.Phone)
        ListRow(title = stringResource(R.string.pair_step_pick), leadingIcon = KtmIcons.Bluetooth)

        if (ui.scanFailed) {
            InfoBanner(
                stringResource(R.string.pair_scan_failed), Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.sm),
                tone = BannerTone.Warn,
            )
        }

        SectionHeader(stringResource(R.string.pair_bikes))
        when {
            ui.bikes.isNotEmpty() -> ui.bikes.forEach { BikeRow(it, actions.onPick) }
            ui.scanning -> { SkeletonRow(); SkeletonRow() }
            else -> Text(
                stringResource(R.string.pair_none),
                style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2,
                modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
            )
        }

        if (ui.others.isNotEmpty()) {
            ListRow(
                title = pluralStringResource(R.plurals.pair_others, ui.others.size, ui.others.size),
                supporting = stringResource(R.string.pair_others_hint), supportingLines = 2,
                leadingIcon = if (othersOpen) KtmIcons.ChevronUp else KtmIcons.ChevronDown,
                onClick = { othersOpen = !othersOpen },
            )
            if (othersOpen) ui.others.forEach { BikeRow(it, actions.onPick) }
        }

        Column(Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.xl), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            KtmButton(
                stringResource(if (ui.scanning) R.string.pair_scanning else R.string.pair_scan_again), actions.onScanAgain,
                Modifier.fillMaxWidth(), style = ButtonStyle.Tonal, enabled = !ui.scanning, loading = ui.scanning,
                icon = KtmIcons.Refresh,
            )
            if (ui.debugSkip) {
                KtmButton(stringResource(R.string.pair_skip_debug), actions.onDebugSkip, Modifier.fillMaxWidth(), style = ButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun BikeRow(bike: ScannedBike, onPick: (ScannedBike) -> Unit) {
    val name = bike.name ?: stringResource(R.string.pair_unknown_device)
    ListRow(
        title = name,
        supporting = bike.address,
        leadingIcon = if (bike.isLikelyDash) KtmIcons.Bike else KtmIcons.Bluetooth,
        onClick = { onPick(bike) },
        trailing = {
            val (label, tone) = signalBadge(bike)
            if (label != null) KtmBadge(stringResource(label), tone = tone)
            RowChevron()
        },
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
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.pair_confirm_title), style = MaterialTheme.typography.headlineMedium,
                color = KtmTheme.colors.fg, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(KtmSpacing.md))
            Text(
                stringResource(R.string.pair_confirm_body, phase.bike.name ?: stringResource(R.string.pair_unknown_device)),
                style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(KtmSpacing.xl))
            Text(
                PairingPolicy.clock(phase.elapsedSec), style = MaterialTheme.typography.displaySmall, color = KtmTheme.colors.fg,
            )
            Spacer(Modifier.height(KtmSpacing.sm))
            Text(
                stringResource(if (phase.securing) R.string.pair_securing else R.string.pair_waiting),
                style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2, textAlign = TextAlign.Center,
            )
        }
        KtmButton(stringResource(R.string.common_cancel), actions.onCancel, Modifier.fillMaxWidth(), style = ButtonStyle.Outline)
    }
}

@Composable
private fun Blocked(reason: BlockReason, actions: PairingActions) {
    val noPermission = reason == BlockReason.NO_PERMISSION
    EmptyState(
        icon = if (noPermission) KtmIcons.Bluetooth else KtmIcons.BluetoothOff,
        title = stringResource(if (noPermission) R.string.pair_blocked_permission_title else R.string.pair_blocked_off_title),
        message = stringResource(if (noPermission) R.string.pair_blocked_permission else R.string.pair_blocked_off),
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        primary = StateAction(stringResource(if (noPermission) R.string.pair_allow_nearby else R.string.pair_turn_on)) { actions.onFixBlock(reason) },
        secondary = actions.onPairLater?.let { StateAction(stringResource(R.string.pair_later), it) },
    )
}

@Composable
private fun Failed(actions: PairingActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        LargeTitle(stringResource(R.string.pair_failed_title))
        Text(
            stringResource(R.string.pair_failed_body), style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.edge),
        )
        TroubleSteps()
        Column(Modifier.padding(horizontal = KtmSpacing.edge, vertical = KtmSpacing.xl), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
            KtmButton(stringResource(R.string.pair_try_again), actions.onRetry, Modifier.fillMaxWidth())
            actions.onPairLater?.let { KtmButton(stringResource(R.string.pair_later), it, Modifier.fillMaxWidth(), style = ButtonStyle.Text) }
        }
    }
}
