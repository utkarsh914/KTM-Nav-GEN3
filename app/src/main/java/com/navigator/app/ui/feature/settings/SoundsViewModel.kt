package com.navigator.app.ui.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The four beep volumes, quietest first. */
enum class BeepVolume(val percent: Int) {
    LOW(20), MEDIUM(35), HIGH(60), MAX(100);

    companion object {
        /** The step closest to a stored volume (older versions allowed any 5..100). */
        fun of(percent: Int): BeepVolume = entries.first { it.percent == SettingSpecs.nearestBeepVolumeStep(percent) }
    }
}

data class SoundsUi(
    val beeps: Boolean,
    val volume: BeepVolume,
    val swapChannels: Boolean,
    val speedAlert: Boolean,
    val alertAtKmh: Int,
    /** The chosen call-audio device's name; null for none. */
    val callAudioName: String?,
    /** A device is chosen but cannot be named now (unpaired, Bluetooth off, access not allowed, still loading). */
    val callAudioUnavailable: Boolean = false,
)

/** ST4: turn beeps, the speed alert and the call-audio device. */
class SoundsViewModel(private val settings: SettingsRepository, private val devices: AudioDevices) : ViewModel() {
    private val bonded = MutableStateFlow<List<AudioDevice>>(emptyList())

    init {
        viewModelScope.launch { bonded.value = devices.bonded().devices }
    }

    private fun build(snap: SettingsSnapshot, list: List<AudioDevice>) = SoundsUi(
        beeps = snap.turnBeepEnabled,
        volume = BeepVolume.of(snap.beepVolumePercent),
        swapChannels = snap.swapBeepChannels,
        speedAlert = snap.overspeedEnabled,
        alertAtKmh = snap.overspeedLimitKmh,
        callAudioName = snap.callAudioDeviceAddress?.let { address -> list.firstOrNull { it.address == address }?.name },
        callAudioUnavailable = snap.callAudioDeviceAddress != null && list.none { it.address == snap.callAudioDeviceAddress },
    )

    val ui: StateFlow<SoundsUi> = combine(settings.snapshot, bonded, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(settings.snapshot.value, emptyList()))

    fun setBeeps(on: Boolean) = settings.update { turnBeepEnabled = on }
    fun setVolume(v: BeepVolume) = settings.update { beepVolumePercent = v.percent }
    fun setSwap(on: Boolean) = settings.update { swapBeepChannels = on }
    fun setSpeedAlert(on: Boolean) = settings.update { overspeedEnabled = on }
    fun setAlertAt(kmh: Int) = settings.update { overspeedLimitKmh = SettingSpecs.overspeedLimit.clamp(kmh) }

    companion object {
        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SoundsViewModel(container.settings, AndroidAudioDevices(app)) as T
        }
    }
}
