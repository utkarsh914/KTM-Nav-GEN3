package com.navigator.app.ui.feature.settings

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

/** The Settings tab: [SettingsHomeContent] over its view model, with the re-pair confirm. */
@Composable
fun SettingsHomeScreen(
    onOpenPage: (SettingsPage) -> Unit,
    onRePaired: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SettingsHomeViewModel = viewModel(factory = SettingsHomeViewModel.factory(LocalContext.current.appContainer))
    val ui by vm.ui.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    SettingsHomeContent(ui, SettingsHomeActions(onOpenPage, onRePair = { confirm = true }, onQuit = onQuit), modifier)
    if (confirm) RePairDialog(onConfirm = { confirm = false; vm.rePair(); onRePaired() }, onDismiss = { confirm = false })
}
