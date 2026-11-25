package com.navigator.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.BuildConfig
import com.navigator.app.app.AppContainer
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.ui.feature.navigate.BikePillMapper
import com.navigator.app.ui.feature.navigate.BikePillUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The bike's name when the status carries one. */
internal fun BikeStatus.nameOrNull(): String? = when (this) {
    is BikeStatus.Connected -> name
    is BikeStatus.Connecting -> name
    is BikeStatus.Reconnecting -> name
    is BikeStatus.Disconnected -> name
    is BikeStatus.NeedsRepair -> name
    else -> null
}

/** [pairedMs] is when the bike was paired, null when unknown (paired before the date was kept). */
data class BikeCard(val name: String?, val status: BikePillUi, val paired: Boolean, val pairedMs: Long? = null)

data class CategoryRow(val page: SettingsPage, val summary: CategorySummary)

data class SettingsHomeUi(val bike: BikeCard, val repairSuggested: Boolean, val rows: List<CategoryRow>)

/** ST1/ST10: the bike card, the re-pair banner, and the eight categories with their live summaries. */
class SettingsHomeViewModel(
    private val settings: SettingsRepository,
    private val connection: ConnectionRepository,
    permissions: PermissionsRepository,
    version: String,
) : ViewModel() {
    val ui: StateFlow<SettingsHomeUi> = combine(settings.snapshot, connection.status, connection.detail, permissions.state) { snap, status, detail, perms ->
        SettingsHomeUi(
            bike = BikeCard(snap.bondedDeviceName ?: status.nameOrNull(), BikePillMapper.map(status), snap.isPaired, snap.bondedPairedMs.takeIf { it > 0 }),
            repairSuggested = detail.repairSuggested && snap.isPaired,
            rows = SettingsPage.entries.map { CategoryRow(it, SettingsSummary.of(it, snap, status, perms, version, settings.engineInUse(snap))) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial(settings, connection, permissions, version))

    /** Forget the bike; the caller opens pairing. */
    fun rePair() = connection.rePair()

    companion object {
        private fun initial(settings: SettingsRepository, connection: ConnectionRepository, permissions: PermissionsRepository, version: String): SettingsHomeUi {
            val snap = settings.snapshot.value
            val status = connection.status.value
            return SettingsHomeUi(
                bike = BikeCard(snap.bondedDeviceName ?: status.nameOrNull(), BikePillMapper.map(status), snap.isPaired, snap.bondedPairedMs.takeIf { it > 0 }),
                repairSuggested = connection.detail.value.repairSuggested && snap.isPaired,
                rows = SettingsPage.entries.map { CategoryRow(it, SettingsSummary.of(it, snap, status, permissions.state.value, version, settings.engineInUse(snap))) },
            )
        }

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsHomeViewModel(container.settings, container.connection, container.permissions, BuildConfig.VERSION_NAME) as T
        }
    }
}
