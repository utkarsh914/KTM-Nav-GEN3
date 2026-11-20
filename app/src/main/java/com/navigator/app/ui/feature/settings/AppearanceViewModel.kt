package com.navigator.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.ui.theme.FontMode
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppearanceUi(val theme: ThemeMode, val ridingScreens: NavThemeMode, val font: FontMode, val accentId: String?)

/** ST6: theme, riding screens, font and accent. Each change applies at once; the whole app is the preview. */
class AppearanceViewModel(private val settings: SettingsRepository) : ViewModel() {
    val ui: StateFlow<AppearanceUi> = settings.snapshot.map { AppearanceUi(it.themeMode, it.ridingScreens, it.fontMode, it.accentColorId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.snapshot.value.let { AppearanceUi(it.themeMode, it.ridingScreens, it.fontMode, it.accentColorId) })

    fun setTheme(mode: ThemeMode) = settings.update { themeMode = mode }
    fun setRidingScreens(mode: NavThemeMode) = settings.update { navThemeMode = mode }
    fun setFont(mode: FontMode) = settings.update { fontMode = mode }

    /** A preset's id, a "#RRGGBB" custom colour, or null for the KTM orange. */
    fun setAccent(id: String?) = settings.update { accentColorId = id }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppearanceViewModel(container.settings) as T
        }
    }
}
