package com.navigator.app.ui.feature.settings.picker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppPickerUi(
    val loading: Boolean,
    val query: String,
    val sections: List<PickerSection>,
    val selected: Set<String>,
)

/** ST11: choose which apps' notifications go to the dash. Each tap saves. */
class AppPickerViewModel(private val settings: SettingsRepository, source: InstalledApps) : ViewModel() {
    private val apps = MutableStateFlow<List<AppEntry>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch { apps.value = source.launchable() }
    }

    /** The apps ticked when the picker opened: the list keeps its order while ticking, so a row never moves from under the finger. */
    private val opened: Set<String> = settings.snapshot.value.notificationSourceApps

    private fun build(all: List<AppEntry>?, q: String, selected: Set<String>) = AppPickerUi(
        loading = all == null, query = q, selected = selected,
        sections = AppPickerList.build(all.orEmpty(), opened, q),
    )

    val ui: StateFlow<AppPickerUi> = combine(apps, query, settings.snapshot) { all, q, snap -> build(all, q, snap.notificationSourceApps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(null, "", settings.snapshot.value.notificationSourceApps))

    fun setQuery(q: String) = query.update { q }

    fun toggle(packageName: String) = settings.update {
        val now = notificationSourceApps
        notificationSourceApps = if (packageName in now) now - packageName else now + packageName
    }

    companion object {
        fun factory(container: AppContainer, context: Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppPickerViewModel(container.settings, AndroidInstalledApps(app)) as T
        }
    }
}
