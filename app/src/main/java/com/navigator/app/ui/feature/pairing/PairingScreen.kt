package com.navigator.app.ui.feature.pairing

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.ui.feature.diagnostics.rememberLogSharer
import com.navigator.app.ui.feature.permissions.PermissionFix
import com.navigator.app.ui.feature.permissions.rememberPermissionFixer
import com.navigator.app.ui.shell.LocalOpenLogs
import com.navigator.app.ui.shell.openBluetoothSettings

/**
 * The pairing flow: [PairingContent] over its view model, with Android's Bluetooth and permission prompts.
 * [onPaired] fires when the handshake completes, even while the app is paused, so it is not a tap handler.
 */
@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    onBack: (() -> Unit)?,
    onPairLater: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** The first-run bar, its last segment; null when pairing from Settings. */
    progress: Pair<Int, Int>? = null,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: PairingViewModel = viewModel(factory = PairingViewModel.factory(container, context))
    val ui by vm.ui.collectAsState()
    val openLogs = LocalOpenLogs.current
    val shareLog = rememberLogSharer()
    val fixer = rememberPermissionFixer(onResult = vm::refreshPermissions)
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    val paired by rememberUpdatedState(onPaired)
    LaunchedEffect(vm) { vm.refreshPermissions(); vm.events.collect { if (it == PairingEvent.Paired) paired() } }
    BackHandler(enabled = ui.phase is PairingPhase.Confirming) { vm.cancel() }

    fun openBluetoothSettings() = context.openBluetoothSettings()

    PairingContent(
        ui,
        PairingActions(
            onBack = onBack, onPick = vm::pick, onScanAgain = vm::scanAgain, onCancel = vm::cancel, onRetry = vm::retry,
            onViewLogs = openLogs, onShareLog = shareLog, onBluetoothSettings = ::openBluetoothSettings, onPairLater = onPairLater,
            onFixBlock = { reason ->
                when (reason) {
                    BlockReason.NO_PERMISSION -> fixer.fix(PermissionFix.stepFor(PermissionId.NEARBY_DEVICES, container.permissions.state.value))
                    BlockReason.BLUETOOTH_OFF ->
                        if (!runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }.isSuccess) openBluetoothSettings()
                }
            },
            onDebugSkip = vm::skipForDebug,
        ),
        modifier, progress,
    )
    fixer.Sheet()
}
