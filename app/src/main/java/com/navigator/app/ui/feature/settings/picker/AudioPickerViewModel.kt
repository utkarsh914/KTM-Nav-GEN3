package com.navigator.app.ui.feature.settings.picker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.ui.feature.settings.AndroidAudioDevices
import com.navigator.app.ui.feature.settings.AudioDevice
import com.navigator.app.ui.feature.settings.AudioDeviceList
import com.navigator.app.ui.feature.settings.AudioDevices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AudioPickerUi(
    val loading: Boolean,
    val devices: List<AudioDevice>,
    val selected: String?,
    /** Android lists paired headsets only once Nearby devices is allowed. */
    val permissionDenied: Boolean = false,
) {
    /** A device is chosen but is not in the list now (unpaired, Bluetooth off). */
    val selectedUnavailable: Boolean get() = !loading && selected != null && devices.none { it.address == selected }
}

/** DL8: pick the one device calls play through, or none. A tap saves. */
class AudioPickerViewModel(private val settings: SettingsRepository, private val source: AudioDevices) : ViewModel() {
    private val devices = MutableStateFlow<AudioDeviceList?>(null)

    init { refresh() }

    /** Read again when the page comes back: a speaker paired, or access allowed, while it was away. */
    fun refresh() {
        viewModelScope.launch { devices.value = source.bonded() }
    }

    val ui: StateFlow<AudioPickerUi> = combine(devices, settings.snapshot) { list, snap ->
        AudioPickerUi(list == null, list?.devices.orEmpty(), snap.callAudioDeviceAddress, list?.permissionDenied == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioPickerUi(true, emptyList(), settings.snapshot.value.callAudioDeviceAddress))

    fun choose(address: String?) = settings.update { preferredCallAudioDeviceAddress = address }

    companion object {
        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AudioPickerViewModel(container.settings, AndroidAudioDevices(app)) as T
        }
    }
}
