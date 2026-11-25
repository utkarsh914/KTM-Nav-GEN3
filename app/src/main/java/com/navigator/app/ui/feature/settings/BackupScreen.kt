package com.navigator.app.ui.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.shell.LocalFeedback
import java.time.LocalDate

private const val RETRY_OFFER = "backup-retry"
private val RESTORE_TYPES = arrayOf("application/zip", "application/octet-stream", "*/*")

/** The Backup & restore page: Android's file screens, the restore sheet, and the results as snackbars with a useful action. */
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val feedback = LocalFeedback.current
    val vm: BackupViewModel = viewModel(factory = BackupViewModel.factory(context.appContainer, context.applicationContext))
    val ui by vm.ui.collectAsState()
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::backUpTo) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; vm.openRestore(uri) }
    }
    // The result is told on this page, so the page stays until the file work is done.
    BackHandler(enabled = ui.busy) {}
    // Retry needs this page's launchers and view model; once it is left the offer ends.
    DisposableEffect(Unit) { onDispose { feedback.endOffer(RETRY_OFFER) } }

    fun startBackup() { runCatching { backupLauncher.launch("ktm-navigator-backup-${LocalDate.now()}.zip") } }
    fun chooseFile() { runCatching { restoreLauncher.launch(RESTORE_TYPES) } }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BackupEvent.BackedUp -> feedback.snackbar(
                    if (event.skipped > 0) {
                        resources.getQuantityString(R.plurals.backup_done_skipped, event.rides, event.rides, event.skipped)
                    } else resources.getQuantityString(R.plurals.backup_done, event.rides, event.rides),
                    resources.getString(R.string.backup_share),
                    onAction = {
                        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, event.uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(Intent.createChooser(send, resources.getString(R.string.backup_share_title))) }
                    },
                )
                BackupEvent.BackupFailed -> feedback.snackbar(
                    resources.getString(R.string.backup_failed), resources.getString(R.string.backup_retry), tag = RETRY_OFFER, onAction = { startBackup() },
                )
                is BackupEvent.Restored -> {
                    val parts = event.parts.joinToString(", ") { it.text(resources) }
                    val message = event.limitRaisedTo?.let { resources.getString(R.string.backup_restored_limit, parts, it) }
                        ?: resources.getString(R.string.backup_restored, parts)
                    if (event.restartNeeded) {
                        feedback.snackbar(message, resources.getString(R.string.backup_restart_now), onAction = { AppRestart.now(context) })
                    } else feedback.snackbar(message)
                }
                is BackupEvent.RestoreFailed -> feedback.snackbar(
                    resources.getString(R.string.backup_restore_failed), resources.getString(R.string.backup_retry),
                    tag = RETRY_OFFER, onAction = { vm.restore(event.uri, event.rides, event.places, event.settings) },
                )
            }
        }
    }

    BackupContent(
        ui, modifier = modifier,
        actions = BackupActions(
            onBack = { if (!ui.busy) onBack() }, onBackup = { startBackup() }, onRestore = { chooseFile() }, onRestart = { AppRestart.now(context) },
            restore = RestoreActions(
                onConfirm = { choice -> restoreUri?.let { vm.restore(it, choice.rides, choice.places, choice.settings) } },
                onChooseAnother = { vm.dismissRestore(); chooseFile() },
                onDismiss = vm::dismissRestore,
            ),
        ),
    )
}
