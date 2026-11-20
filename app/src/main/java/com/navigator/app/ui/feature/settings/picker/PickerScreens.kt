package com.navigator.app.ui.feature.settings.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer

/** The apps-on-your-dash page. */
@Composable
fun AppPickerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: AppPickerViewModel = viewModel(factory = AppPickerViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    AppPickerContent(ui, AppPickerActions(onBack, vm::setQuery, vm::toggle), modifier)
}

/** The call-audio-device page; choosing leaves it. */
@Composable
fun AudioPickerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: AudioPickerViewModel = viewModel(factory = AudioPickerViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    AudioPickerContent(ui, AudioPickerActions(onBack) { vm.choose(it); onBack() }, modifier)
}
