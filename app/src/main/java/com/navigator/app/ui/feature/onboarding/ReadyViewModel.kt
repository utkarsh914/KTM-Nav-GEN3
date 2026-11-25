package com.navigator.app.ui.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.permissions.PermissionsState
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.nav.providers.GoogleNavSdkController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** ON10: what was set up, and whether the dash has said hello. */
data class ReadyUi(
    val bikeName: String?,
    /** The dash is connected right now, so the greeting has appeared on it. */
    val connected: Boolean,
    val engine: NavEngine,
    val userName: String,
    /** Optional permissions still off: the row that leads to them (ON10). */
    val optionalMissing: Int = 0,
) {
    companion object {
        fun of(snap: SettingsSnapshot, status: BikeStatus, engineAvailable: Boolean, optionalMissing: Int = 0) = ReadyUi(
            bikeName = when (status) {
                is BikeStatus.Connected -> status.name
                is BikeStatus.Connecting -> status.name
                is BikeStatus.Reconnecting -> status.name
                is BikeStatus.Disconnected -> status.name
                is BikeStatus.NeedsRepair -> status.name
                else -> null
            } ?: snap.bondedDeviceName,
            connected = status is BikeStatus.Connected,
            engine = NavEngine.inUse(snap.engine, engineAvailable),
            userName = snap.userName.orEmpty().trim(),
            optionalMissing = optionalMissing,
        )
    }
}

class ReadyViewModel(container: AppContainer, engineAvailable: Boolean) : ViewModel() {
    private fun optional(state: PermissionsState) = state.items.count { !it.required && !it.granted }

    val ui: StateFlow<ReadyUi> = combine(container.settings.snapshot, container.connection.status, container.permissions.state) { snap, status, perms ->
        ReadyUi.of(snap, status, engineAvailable, optional(perms))
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        ReadyUi.of(container.settings.snapshot.value, container.connection.status.value, engineAvailable, optional(container.permissions.state.value)),
    )

    companion object {
        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReadyViewModel(container, GoogleNavSdkController.isAvailable(context.applicationContext)) as T
        }
    }
}
