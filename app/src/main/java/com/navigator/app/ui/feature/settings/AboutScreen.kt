package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer

/** The About & diagnostics page: [AboutContent] over its view model. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLogs: () -> Unit, onSymbols: () -> Unit, onCalibration: () -> Unit, modifier: Modifier = Modifier) {
    val vm: AboutViewModel = viewModel(factory = AboutViewModel.factory(LocalContext.current.appContainer))
    val ui by vm.ui.collectAsState()
    AboutContent(ui, AboutActions(onBack, onLogs, vm::setCopyLogs, onSymbols, onCalibration, vm::setOverlay), modifier)
}
