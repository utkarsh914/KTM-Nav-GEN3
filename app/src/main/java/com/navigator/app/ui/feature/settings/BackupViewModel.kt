package com.navigator.app.ui.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.R
import com.navigator.app.app.AppContainer
import com.navigator.app.backup.BackupManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Restore sheet (DL6) is showing. */
sealed interface RestoreState {
    data object Reading : RestoreState
    data class Ready(val preview: BackupManager.Preview, val fileName: String? = null) : RestoreState
    data object Unreadable : RestoreState
}

/** [busy] is any work in progress; [restoring] says it is the restore (otherwise the backup). */
data class BackupUi(val busy: Boolean = false, val restore: RestoreState? = null, val restoring: Boolean = false, val restartPending: Boolean = false)

/** A result the page tells the rider about in a snackbar, with the action that helps. */
sealed interface BackupEvent {
    data class BackedUp(val rides: Int, val uri: Uri, val skipped: Int = 0) : BackupEvent
    data object BackupFailed : BackupEvent
    data class Restored(val parts: List<UiText>, val restartNeeded: Boolean, val note: UiText? = null) : BackupEvent
    data class RestoreFailed(val uri: Uri, val rides: Boolean, val places: Boolean, val settings: Boolean) : BackupEvent
}

/**
 * The Restore sheet starts with rides and places on for whatever the file holds that is new. Settings start off:
 * they replace the current ones, so that is the rider's own choice (DL6).
 */
data class RestoreChoice(val rides: Boolean, val places: Boolean, val settings: Boolean) {
    val any: Boolean get() = rides || places || settings

    companion object {
        fun defaultsFor(p: BackupManager.Preview) = RestoreChoice(p.newRides > 0, p.places > 0, settings = false)
    }
}

/** ST8: back up, restore, and the results. */
class BackupViewModel(private val gateway: BackupGateway, private val onRestored: () -> Unit = {}) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()
    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun backUpTo(uri: Uri) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = gateway.backup(uri)
            _ui.update { it.copy(busy = false, restoring = false) }
            _events.send(if (r.ok) BackupEvent.BackedUp(r.rides, uri, r.skipped) else BackupEvent.BackupFailed)
        }
    }

    /** A file was chosen: the sheet opens at once and says what it found, or that it is not a backup. */
    fun openRestore(uri: Uri) {
        _ui.update { it.copy(restore = RestoreState.Reading) }
        // A newer pick (Choose another) replaces a lookup still running.
        inspecting?.cancel()
        inspecting = viewModelScope.launch {
            val preview = gateway.inspect(uri)
            val name = if (preview != null) gateway.displayName(uri) else null
            _ui.update { if (it.restore == null) it else it.copy(restore = preview?.let { p -> RestoreState.Ready(p, name) } ?: RestoreState.Unreadable) }
        }
    }

    private var inspecting: Job? = null

    fun dismissRestore() {
        inspecting?.cancel()
        _ui.update { it.copy(restore = null) }
    }

    fun restore(uri: Uri, rides: Boolean, places: Boolean, settings: Boolean) {
        _ui.update { it.copy(busy = true, restore = null, restoring = true) }
        viewModelScope.launch {
            val r = gateway.restore(uri, rides, places, settings)
            _ui.update { it.copy(busy = false, restoring = false, restartPending = it.restartPending || (r.ok && settings)) }
            if (r.ok) onRestored()
            _events.send(
                if (r.ok) BackupEvent.Restored(
                    restoredParts(r, rides, places, settings), restartNeeded = settings,
                    note = r.limitRaisedTo?.let { UiText.Res(R.string.backup_limit_raised, listOf(it)) },
                )
                else BackupEvent.RestoreFailed(uri, rides, places, settings),
            )
        }
    }

    companion object {
        /** What was added, only for the parts asked for. */
        fun restoredParts(r: BackupManager.Restored, rides: Boolean, places: Boolean, settings: Boolean): List<UiText> = buildList {
            if (rides) add(UiText.Plural(R.plurals.backup_restored_rides, r.ridesAdded))
            if (places) add(UiText.Plural(R.plurals.backup_restored_places, r.placesAdded))
            if (settings) add(UiText.Plural(R.plurals.backup_restored_settings, r.settingsApplied))
        }

        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupViewModel(AndroidBackupGateway(context)) { container.settings.refresh() } as T
        }
    }
}
