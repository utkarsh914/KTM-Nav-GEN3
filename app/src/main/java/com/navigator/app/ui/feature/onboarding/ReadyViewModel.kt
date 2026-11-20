package com.navigator.app.ui.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.connection.BikeStatus
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
) {
    companion object {
        fun of(snap: SettingsSnapshot, status: BikeStatus, engineAvailable: Boolean) = ReadyUi(
            bikeName = when (status) {
                is BikeStatus.Connected -> status.name
                is BikeStatus.Connecting -> status.name
                is BikeStatus.Reconnecting -> status.name
                is BikeStatus.Disconnected -> status.name
                is BikeStatus.NeedsRepair -> status.name
                else -> null
            } ?: snap.bondedDeviceName,
            connected = status is BikeStatus.Connected,
            engine = if (engineAvailable) snap.engine else NavEngine.MIRROR,
            userName = snap.userName.orEmpty().trim(),
        )
    }
}

class ReadyViewModel(container: AppContainer, engineAvailable: Boolean) : ViewModel() {
    val ui: StateFlow<ReadyUi> = combine(container.settings.snapshot, container.connection.status) { snap, status ->
        ReadyUi.of(snap, status, engineAvailable)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        ReadyUi.of(container.settings.snapshot.value, container.connection.status.value, engineAvailable),
    )

    companion object {
        fun factory(container: AppContainer, context: android.content.Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReadyViewModel(container, GoogleNavSdkController.isAvailable(context.applicationContext)) as T
        }
    }
}
