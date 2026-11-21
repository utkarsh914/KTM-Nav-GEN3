package com.navigator.app.ui.feature.navigate

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.navigator.app.ble.ReconnectPolicy
import com.navigator.app.nav.ktm.ClockFormat
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.KtmIconButton
import com.navigator.app.ui.components.KtmStep
import com.navigator.app.ui.components.ListDivider
import com.navigator.app.ui.components.StepState
import com.navigator.app.ui.components.fullBleed
import com.navigator.app.ui.feature.diagnostics.rememberLogSharer
import com.navigator.app.ui.feature.pairing.HavingTroubleSheet
import com.navigator.app.ui.shell.LocalOpenBikeSettings
import com.navigator.app.ui.shell.LocalOpenLogs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DashPreview
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmModalSheet
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.feature.settings.RePairDialog
import com.navigator.app.ui.shell.openBluetoothSettings
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmShapes
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/** What the status block of the Bike sheet says. */
data class BikeStatusText(val icon: ImageVector, @StringRes val title: Int, val arg: Int?, @StringRes val hint: Int?)

object BikeSheetMapper {
    fun status(status: BikeStatus): BikeStatusText = when (status) {
        is BikeStatus.Connected -> BikeStatusText(KtmIcons.Bike, R.string.bike_sheet_connected, null, null)
        is BikeStatus.Connecting -> BikeStatusText(
            KtmIcons.Bluetooth,
            if (status.firstPair) R.string.bike_sheet_connecting_first else R.string.bike_sheet_connecting, null, null,
        )
        is BikeStatus.Reconnecting -> BikeStatusText(KtmIcons.Bluetooth, R.string.bike_sheet_reconnecting, status.attempt, null)
        is BikeStatus.NeedsRepair -> BikeStatusText(KtmIcons.Warning, R.string.bike_sheet_cant_connect, null, R.string.bike_sheet_cant_connect_hint)
        BikeStatus.BluetoothOff -> BikeStatusText(KtmIcons.BluetoothOff, R.string.bike_sheet_bluetooth_off, null, R.string.bike_sheet_bluetooth_off_hint)
        is BikeStatus.Disconnected, BikeStatus.NotPaired ->
            BikeStatusText(KtmIcons.Bluetooth, R.string.bike_sheet_disconnected, null, R.string.bike_sheet_disconnected_hint)
    }
}

/** "Last seen" for the bike sheet: the time today (the phone's 12/24 h), with the day in front when it was before. */
object LastSeen {
    fun format(seenMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val seen = Instant.ofEpochMilli(seenMs).atZone(zone)
        if (seen.toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()) return ClockFormat.local(seenMs, zone)
        return ClockFormat.formatter(date = "MMMd").format(seen)
    }
}

/**
 * "Is my dash OK?" in one place: status in words, what the dash was last told, the two riding
 * toggles and a test send. Re-pair is the primary action only when the app thinks the keys are lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeSheet(
    state: NavigateUiState,
    dashTime: String,
    onBeeps: (Boolean) -> Unit,
    onMessages: (Boolean) -> Unit,
    onSendTest: (message: String) -> Unit,
    onRePair: () -> Unit,
    onDismiss: () -> Unit,
    /** Try the bike this moment instead of waiting out the reconnect pacing. */
    onRetryNow: () -> Unit = {},
    /** A trip is guiding (also while riding on screen): the test turn would replace its guidance on the dash. */
    tripRunning: Boolean = state.activity != null,
    /** When the bike was last linked (epoch ms); shown under the preview while it is not. */
    lastSeenMs: Long? = null,
) {
    var confirmRePair by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf(false) }
    val status = state.bikeStatus
    val openLogs = LocalOpenLogs.current
    val connected = status is BikeStatus.Connected
    val text = BikeSheetMapper.status(status)
    val name = state.bike.labelArg ?: stringResource(R.string.bike_sheet_title)
    val title = stringResource(text.title, text.arg ?: 0)
    val testMessage = stringResource(
        R.string.bike_test_message,
        state.userName.ifBlank { stringResource(R.string.bike_test_name_fallback) },
    )
    val idle = DashPreviewState.NotConnected(stringResource(R.string.dash_preview_idle_title), stringResource(R.string.dash_preview_idle_hint))
    val offline = DashPreviewState.NotConnected(stringResource(R.string.dash_preview_offline_title), stringResource(R.string.dash_preview_offline_hint))
    val preview = DashPreviewMapper.map(state.dash, connected, dashTime, idle, offline, stringResource(R.string.dash_preview_banner_app))

    val openSettings = LocalOpenBikeSettings.current
    KtmModalSheet(onDismiss = onDismiss) {
        BikeHeader(name, title, connected, status, onDismiss)
        Spacer(Modifier.height(KtmSpacing.lg))
        if (connected) {
            Text(
                stringResource(R.string.mirror_on_dash), style = MaterialTheme.typography.labelMedium, color = KtmTheme.colors.fg2,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(KtmSpacing.sm))
        }
        DashPreview(preview, if (connected) Modifier else Modifier.alpha(OFFLINE_PREVIEW_ALPHA))
        if (!connected && lastSeenMs != null) {
            Text(
                stringResource(R.string.bike_sheet_last_seen, LastSeen.format(lastSeenMs, System.currentTimeMillis())),
                style = MaterialTheme.typography.bodyMedium, color = KtmTheme.colors.fg2,
                modifier = Modifier.padding(top = KtmSpacing.sm),
            )
        }
        Spacer(Modifier.height(KtmSpacing.lg))
        if (connected) {
            Column(Modifier.fillMaxWidth().clip(KtmShapes.Card).background(KtmTheme.colors.surf2)) {
                KtmSwitchRow(
                    stringResource(R.string.bike_sheet_messages), state.messagesOn, onMessages,
                    supporting = stringResource(R.string.bike_sheet_messages_hint), leadingIcon = KtmIcons.Message,
                )
                ListDivider()
                KtmSwitchRow(
                    stringResource(R.string.bike_sheet_beeps), state.beepsOn, onBeeps,
                    supporting = stringResource(R.string.bike_sheet_beeps_hint), leadingIcon = KtmIcons.Sound,
                )
            }
            Spacer(Modifier.height(KtmSpacing.lg))
            // The test turn would replace the live guidance on the dash.
            if (tripRunning) {
                Text(
                    stringResource(R.string.bike_sheet_test_on_trip), style = MaterialTheme.typography.bodyMedium,
                    color = KtmTheme.colors.fg2, modifier = Modifier.padding(bottom = KtmSpacing.sm),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmButton(
                    stringResource(R.string.bike_sheet_test), { onSendTest(testMessage) }, Modifier.weight(1f),
                    style = ButtonStyle.Tonal, enabled = !tripRunning,
                )
                KtmButton(stringResource(R.string.bike_sheet_trouble), { trouble = true }, Modifier.weight(1f), style = ButtonStyle.Outline, icon = KtmIcons.Help)
            }
            if (openSettings != null) {
                ListRow(
                    modifier = Modifier.fullBleed(), title = stringResource(R.string.bike_sheet_settings), leadingIcon = KtmIcons.Settings,
                    onClick = { onDismiss(); openSettings() },
                    trailing = { Icon(KtmIcons.ChevronRight, null, tint = KtmTheme.colors.fg2) },
                )
            }
        } else {
            // HM6: say what to check, then the one way out. Re-pair leads only when the app thinks the keys are lost.
            if (status is BikeStatus.NeedsRepair || status == BikeStatus.BluetoothOff) {
                text.hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = KtmTheme.colors.fg2) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(KtmSpacing.xs)) {
                    KtmStep(1, stringResource(R.string.bike_check_ignition), StepState.Active)
                    KtmStep(2, stringResource(R.string.bike_check_bluetooth), StepState.Active, badge = stringResource(R.string.common_yes))
                    KtmStep(3, stringResource(R.string.bike_check_retry), StepState.Active)
                }
            }
            Spacer(Modifier.height(KtmSpacing.lg))
            // Retry now leads, unless the keys look lost: then Re-pair does and a retry is the quieter choice.
            val rePairFirst = status is BikeStatus.NeedsRepair
            val canRetry = status is BikeStatus.Reconnecting || status is BikeStatus.Disconnected || rePairFirst
            var retried by remember { mutableStateOf(false) }
            // One tap is one attempt: the button rests for as long as the service ignores a second tap.
            LaunchedEffect(retried) { if (retried) { delay(ReconnectPolicy.RETRY_NOW_MIN_GAP_MS); retried = false } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                if (canRetry) {
                    KtmButton(
                        stringResource(if (retried) R.string.bike_sheet_retrying else R.string.bike_sheet_retry_now),
                        { retried = true; onRetryNow() }, Modifier.weight(1f), icon = KtmIcons.Refresh,
                        style = if (rePairFirst) ButtonStyle.Outline else ButtonStyle.Primary, enabled = !retried,
                    )
                }
                KtmButton(
                    stringResource(R.string.bike_sheet_repair), { confirmRePair = true }, Modifier.weight(1f),
                    style = if (rePairFirst) ButtonStyle.Primary else ButtonStyle.Outline,
                )
            }
            KtmButton(
                stringResource(R.string.pair_trouble), { trouble = true }, Modifier.align(Alignment.CenterHorizontally),
                style = ButtonStyle.Text, size = ButtonSize.Small, icon = KtmIcons.Help,
            )
        }
    }

    if (trouble) {
        val context = LocalContext.current
        val shareLog = rememberLogSharer()
        HavingTroubleSheet(
            onViewLogs = openLogs,
            onShareLog = shareLog,
            onBluetoothSettings = { context.openBluetoothSettings() },
            onDismiss = { trouble = false },
        )
    }

    if (confirmRePair) {
        RePairDialog(onConfirm = { confirmRePair = false; onRePair() }, onDismiss = { confirmRePair = false })
    }
}

private const val OFFLINE_PREVIEW_ALPHA = 0.9f

/** HM5/HM6: a 48 dp tile (green when connected, amber otherwise), the bike's name and its state in words, and the ✕. */
@Composable
private fun BikeHeader(name: String, status: String, connected: Boolean, bike: BikeStatus, onClose: () -> Unit) {
    val c = KtmTheme.colors
    val working = bike is BikeStatus.Connecting || bike is BikeStatus.Reconnecting
    val tone = if (connected) c.okText else c.warn
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.md)) {
        Box(
            Modifier.size(KtmLayout.targetRest).background(if (connected) c.okContainer else c.warnContainer, KtmShapes.Control),
            contentAlignment = Alignment.Center,
        ) { Icon(KtmIcons.Bike, null, tint = if (connected) c.okText else c.warn) }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(name, style = MaterialTheme.typography.headlineSmall, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                if (working) CircularProgressIndicator(Modifier.size(KtmSpacing.md), color = c.warn, strokeWidth = KtmLayout.outline)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = tone, maxLines = 2)
            }
        }
        KtmIconButton(KtmIcons.Close, stringResource(R.string.common_close), onClose)
    }
}
