package com.navigator.app.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionItem
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.permissions.PermissionsState
import com.navigator.app.ui.feature.permissions.FixStep
import com.navigator.app.ui.feature.permissions.PermissionFix
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PermissionsUi(
    val items: List<PermissionItem>,
    /** Required and off: these say what stops working. */
    val requiredMissing: List<PermissionId>,
    val optionalMissing: List<PermissionId>,
) {
    companion object {
        fun of(state: PermissionsState) = PermissionsUi(
            items = state.items,
            requiredMissing = state.requiredMissing.map { it.id },
            optionalMissing = state.items.filter { !it.required && !it.granted }.map { it.id },
        )
    }
}

/** ST7: one status list for everything Android can take away. */
class PermissionsViewModel(private val permissions: PermissionsRepository) : ViewModel() {
    val ui: StateFlow<PermissionsUi> = permissions.state.map(PermissionsUi::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermissionsUi.of(permissions.state.value))

    /** Android has no change callback: re-read when the page opens and after every result. */
    fun refresh() = permissions.refresh()

    fun stepFor(id: PermissionId): FixStep = PermissionFix.stepFor(id, permissions.state.value)

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PermissionsViewModel(container.permissions) as T
        }
    }
}
