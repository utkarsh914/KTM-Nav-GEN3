package com.navigator.app.ui.feature.settings.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.AppContainer
import com.navigator.app.app.appContainer
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.components.KtmRadioRow
import com.navigator.app.ui.components.KtmTopBar
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NavAppPickerUi(val apps: List<AppEntry>, val selected: String?)

/** Which nav app to mirror when it is not detected. Choosing one turns detecting off. */
class NavAppPickerViewModel(private val settings: SettingsRepository, labels: AppLabels) : ViewModel() {
    private val apps = AppSettings.KNOWN_NAV_APPS.map { AppEntry(it, labels.label(it)) }.sortedBy { it.label.lowercase() }

    val ui: StateFlow<NavAppPickerUi> = settings.snapshot.map { NavAppPickerUi(apps, it.navAppOverride) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavAppPickerUi(apps, settings.snapshot.value.navAppOverride))

    fun choose(packageName: String) = settings.update { navAppOverride = packageName }

    companion object {
        fun factory(container: AppContainer, context: android.content.Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NavAppPickerViewModel(container.settings, AndroidAppLabels(app)) as T
        }
    }
}

/** DL8 pattern for the nav app: one tap chooses and leaves. Stateless. */
@Composable
fun NavAppPickerContent(ui: NavAppPickerUi, onBack: () -> Unit, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        KtmTopBar(title = stringResource(R.string.picker_nav_title), onBack = onBack)
        Text(
            stringResource(R.string.picker_nav_hint), style = MaterialTheme.typography.bodySmall, color = c.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.edge).padding(bottom = KtmSpacing.xs),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
            ui.apps.forEach { KtmRadioRow(it.label, selected = it.packageName == ui.selected, onSelect = { onChoose(it.packageName) }) }
        }
    }
}

/** The nav-app page; choosing leaves it. */
@Composable
fun NavAppPickerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: NavAppPickerViewModel = viewModel(factory = NavAppPickerViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    NavAppPickerContent(ui, onBack, { vm.choose(it); onBack() }, modifier)
}
