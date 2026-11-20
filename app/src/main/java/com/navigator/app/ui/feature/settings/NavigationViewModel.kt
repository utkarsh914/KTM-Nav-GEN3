package com.navigator.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.settings.AppSettings
import com.navigator.app.settings.DrivingSidePreference
import com.navigator.app.ui.feature.settings.picker.AndroidAppLabels
import com.navigator.app.ui.feature.settings.picker.AppLabels
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NavigationUi(
    /** The in-app engine needs Google's SDK on this phone; without it there is nothing to choose. */
    val engineAvailable: Boolean,
    val engine: NavEngine,
    val vehicle: TravelMode,
    val marker: String,
    val liveUpdates: Boolean,
    val autoDetect: Boolean,
    /** The chosen nav app's name; null while detecting. */
    val navAppLabel: String?,
    val drivingSide: DrivingSidePreference,
)

/** ST3: the engine, how routes are calculated and drawn, and the settings that only the notification mirror uses. */
class NavigationViewModel(
    private val settings: SettingsRepository,
    private val engineAvailable: Boolean,
    private val labels: AppLabels,
    private val onEngineChanged: (NavEngine) -> Unit,
) : ViewModel() {
    private fun build(snap: SettingsSnapshot) = NavigationUi(
        engineAvailable = engineAvailable,
        engine = if (engineAvailable) snap.engine else NavEngine.MIRROR,
        vehicle = snap.vehicleMode,
        marker = snap.locationMarkerStyle,
        liveUpdates = snap.liveUpdatesEnabled,
        autoDetect = snap.navAppOverride == null,
        navAppLabel = snap.navAppOverride?.let(labels::label),
        drivingSide = snap.drivingSide,
    )

    val ui: StateFlow<NavigationUi> = settings.snapshot.map(::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(settings.snapshot.value))

    fun setEngine(engine: NavEngine) {
        if (!engineAvailable || engine == settings.snapshot.value.engine) return
        settings.update { googleNavEnabled = engine == NavEngine.IN_APP }
        onEngineChanged(engine)
    }

    fun setVehicle(mode: TravelMode) = settings.update { vehicleMode = mode }
    fun setMarker(style: String) = settings.update { locationMarkerStyle = style }
    fun setLiveUpdates(on: Boolean) = settings.update { liveUpdatesEnabled = on }
    fun setDrivingSide(side: DrivingSidePreference) = settings.update { drivingSidePreference = side }

    /** Back to detecting. Turning it off is the nav-app picker's job: it saves the choice. */
    fun detectNavApp() = settings.update { navAppOverride = null }

    companion object {
        val MARKERS = listOf(AppSettings.LOCATION_MARKER_CHEVRON, AppSettings.LOCATION_MARKER_BEAM)

        fun factory(container: AppContainer, context: android.content.Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NavigationViewModel(
                container.settings, GoogleNavSdkController.isAvailable(app), AndroidAppLabels(app),
                // Switching to the mirror ends any in-app trip, so the two engines never drive the dash at once.
                onEngineChanged = { if (it == NavEngine.MIRROR) container.trip.cancel() },
            ) as T
        }
    }
}
