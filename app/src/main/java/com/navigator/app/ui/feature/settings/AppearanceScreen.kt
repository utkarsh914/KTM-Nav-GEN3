package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.ui.shell.resolveAppDark
import com.navigator.app.ui.theme.accentFor

/** The Appearance page: [AppearanceContent] over its view model, with the custom-colour sheet. */
@Composable
fun AppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: AppearanceViewModel = viewModel(factory = AppearanceViewModel.factory(LocalContext.current.appContainer))
    val ui by vm.ui.collectAsState()
    var custom by rememberSaveable { mutableStateOf(false) }
    AppearanceContent(
        ui, modifier = modifier,
        actions = AppearanceActions(onBack, vm::setTheme, vm::setRidingScreens, vm::setFont, vm::setAccent, onCustom = { custom = true }),
    )
    if (custom) {
        CustomAccentSheet(
            initial = accentFor(ui.accentId).tones.fill, dark = resolveAppDark(ui.theme, isSystemInDarkTheme()),
            onSave = { vm.setAccent(it); custom = false }, onDismiss = { custom = false },
        )
    }
}
