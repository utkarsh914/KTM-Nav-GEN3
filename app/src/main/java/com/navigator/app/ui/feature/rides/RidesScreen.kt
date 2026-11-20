package com.navigator.app.ui.feature.rides

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.data.rides.ImportResult
import com.navigator.app.nav.ktm.DistanceFormatter
import com.navigator.app.nav.model.DistanceUnits
import com.navigator.app.ride.RideSession
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.feature.record.rememberStartRecording
import com.navigator.app.ui.shell.LocalFeedback

/** The Rides tab: wires [RidesContent] to the repository, the file picker and the snackbars. */
@Composable
fun RidesScreen(onOpenRide: (String) -> Unit, onMerge: (List<String>) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.appContainer
    val feedback = LocalFeedback.current
    val vm: RidesViewModel = viewModel(factory = RidesViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    val snap by container.settings.snapshot.collectAsState()
    val session by RideSession.state.collectAsState()
    val startRecording = rememberStartRecording()
    var confirmStarred by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        vm.importGpx({ context.contentResolver.openInputStream(uri) }, name)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is RidesEvent.Deleted -> feedback.snackbar(
                    resources.getQuantityString(R.plurals.rides_deleted, event.ids.size, event.ids.size),
                    actionLabel = resources.getString(R.string.common_undo),
                    onAction = { vm.undoDelete(event.ids) },
                    onDismiss = { vm.purge(event.ids) },
                )
                is RidesEvent.Imported -> feedback.snackbar(
                    when (val r = event.result) {
                        is ImportResult.Imported -> resources.getString(
                            R.string.rides_import_done, DistanceFormatter.format(r.ride.distanceMeters, DistanceUnits.METRIC),
                        )
                        ImportResult.Duplicate -> resources.getString(R.string.rides_import_duplicate)
                        ImportResult.Unreadable -> resources.getString(R.string.rides_import_unreadable)
                        ImportResult.TooShort -> resources.getString(R.string.rides_import_short)
                    },
                )
            }
        }
    }

    // Back leaves the selection or the search first, not the tab.
    BackHandler(enabled = ui?.selecting == true) { vm.stopSelecting() }
    BackHandler(enabled = ui?.searchOpen == true && ui?.selecting != true) { vm.closeSearch() }

    val actions = RidesActions(
        onOpenRide = onOpenRide,
        onToggleStar = vm::toggleStar,
        onOpenSearch = vm::openSearch,
        onCloseSearch = vm::closeSearch,
        onQuery = vm::setQuery,
        onSort = vm::setSort,
        onSelect = vm::startSelecting,
        onToggleSelected = vm::toggle,
        onSelectAll = vm::selectAll,
        onStopSelecting = vm::stopSelecting,
        onStarSelected = vm::starSelected,
        onMerge = { onMerge(ui?.selected.orEmpty().toList()) },
        onDelete = { if ((ui?.starredSelected ?: 0) > 0) confirmStarred = true else vm.deleteSelected() },
        onImport = { runCatching { picker.launch(arrayOf("*/*")) } },
        onRecordingSettings = onOpenSettings,
        onRecord = startRecording,
        onTurnOnAutoRecord = { container.settings.update { rideRecordingEnabled = true } },
    )
    var resumes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumes++; onPauseOrDispose { } }
    // "Today" and "Yesterday" move on at midnight even if the tab stays open.
    val today by androidx.compose.runtime.produceState(java.time.LocalDate.now(), resumes) {
        // Coming back to the app also refreshes it: a timer doesn't tick while the phone sleeps, or across a DST or clock change.
        value = java.time.LocalDate.now()
        while (true) {
            val now = java.time.LocalDateTime.now()
            val untilMidnight = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
            kotlinx.coroutines.delay(untilMidnight + MIDNIGHT_SLACK_MS)
            value = java.time.LocalDate.now()
        }
    }
    RidesContent(
        ui = ui, autoRecordOn = snap.rideRecordingEnabled, recording = session.active,
        thumbnail = { container.rides.thumbnail(it) }, actions = actions, modifier = modifier, today = today,
    )

    if (confirmStarred) {
        val n = ui?.starredSelected ?: 0
        KtmDialog(
            title = pluralStringResource(R.plurals.rides_delete_starred_title, n, n),
            message = stringResource(R.string.rides_delete_starred_message),
            onDismiss = { confirmStarred = false },
            confirm = DialogAction(stringResource(R.string.rides_delete_confirm), { confirmStarred = false; vm.deleteSelected() }),
            dismiss = DialogAction(stringResource(R.string.rides_delete_cancel), { confirmStarred = false }),
        )
    }
}

private const val MIDNIGHT_SLACK_MS = 1_000L
