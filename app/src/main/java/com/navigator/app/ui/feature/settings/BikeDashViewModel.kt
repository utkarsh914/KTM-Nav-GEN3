package com.navigator.app.ui.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.ble.DashText
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.ui.feature.navigate.BikePillMapper
import com.navigator.app.ui.feature.settings.picker.AndroidAppInstalled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class BikeDashUi(
    val bike: BikeCard,
    val repairSuggested: Boolean,
    /** Test sends need a live link. */
    val connected: Boolean,
    val showOnDash: Boolean,
    val appCount: Int,
    val marquee: Boolean,
    val latinOnly: Boolean,
    val userName: String,
    val accessGranted: Boolean,
)

/** ST2: everything about the bike and what the dash shows, in one place. */
class BikeDashViewModel(
    private val settings: SettingsRepository,
    private val connection: ConnectionRepository,
    permissions: PermissionsRepository,
    /** Whether an app is still on the phone: a source that was uninstalled is not counted. */
    private val installed: (String) -> Boolean = { true },
) : ViewModel() {
    private fun build(
        snap: SettingsSnapshot, status: BikeStatus, suggested: Boolean,
        access: Boolean,
    ) = BikeDashUi(
        bike = BikeCard(snap.bondedDeviceName ?: status.nameOrNull(), BikePillMapper.map(status), snap.isPaired),
        repairSuggested = suggested && snap.isPaired,
        connected = status is BikeStatus.Connected,
        showOnDash = snap.mirrorEnabled,
        appCount = snap.notificationSourceApps.count(installed),
        marquee = snap.marqueeEnabled,
        latinOnly = snap.latinDashText,
        userName = snap.userName.orEmpty(),
        accessGranted = access,
    )

    val ui: StateFlow<BikeDashUi> = combine(settings.snapshot, connection.status, connection.detail, permissions.state) { snap, status, detail, perms ->
        build(snap, status, detail.repairSuggested, perms[PermissionId.NOTIFICATION_ACCESS].granted)
    }.flowOn(Dispatchers.IO).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        build(settings.snapshot.value, connection.status.value, connection.detail.value.repairSuggested, permissions.state.value[PermissionId.NOTIFICATION_ACCESS].granted),
    )

    fun setShowOnDash(on: Boolean) = settings.update { mirrorEnabled = on }
    fun setMarquee(on: Boolean) = settings.update { marqueeEnabled = on }

    fun setLatinOnly(on: Boolean) {
        settings.update { latinDashText = on }
        DashText.enabled = on
    }

    fun setName(name: String) = settings.update { userName = name }

    /** [message] is the banner text, already in the rider's language. */
    fun sendTestMessage(message: String) = connection.sendTestNotification(message)
    fun sendTestTurn() = connection.sendTestTurn()
    fun rePair() = connection.rePair()

    companion object {
        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BikeDashViewModel(container.settings, container.connection, container.permissions, AndroidAppInstalled(context.applicationContext)) as T
        }
    }
}
