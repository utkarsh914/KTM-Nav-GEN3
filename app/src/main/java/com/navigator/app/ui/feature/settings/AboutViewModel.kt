package com.navigator.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.BuildConfig
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.DashPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AboutUi(val version: String, val copyLogs: Boolean, val showOverlayToggle: Boolean, val overlay: Boolean)

/** ST9: the log tools, the developer tools, and the version. */
class AboutViewModel(
    private val settings: SettingsRepository,
    private val version: String,
    private val debugBuild: Boolean,
    private val readCopyLogs: () -> Boolean,
    private val writeCopyLogs: (Boolean) -> Unit,
    private val applyOverlay: (Boolean) -> Unit,
) : ViewModel() {
    private val copyLogs = MutableStateFlow(readCopyLogs())

    private fun build(overlay: Boolean, copy: Boolean) = AboutUi(version, copy, debugBuild, overlay)

    val ui: StateFlow<AboutUi> = combine(settings.snapshot, copyLogs) { s, copy -> build(s.dashPreviewOverlay, copy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(settings.snapshot.value.dashPreviewOverlay, copyLogs.value))

    fun setCopyLogs(on: Boolean) { writeCopyLogs(on); copyLogs.value = on }

    fun setOverlay(on: Boolean) {
        settings.update { dashPreviewOverlay = on }
        applyOverlay(on)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AboutViewModel(
                container.settings, BuildConfig.VERSION_NAME, BuildConfig.DEBUG,
                readCopyLogs = { AppLogger.publicMirrorEnabled }, writeCopyLogs = { AppLogger.publicMirrorEnabled = it },
                applyOverlay = DashPreview::setOverlayEnabled,
            ) as T
        }
    }
}
