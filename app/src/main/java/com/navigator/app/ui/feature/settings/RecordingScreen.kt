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

/** The Recording page: [RecordingContent] over its view model, with the type-a-number sheet. */
@Composable
fun RecordingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: RecordingViewModel = viewModel(factory = RecordingViewModel.factory(LocalContext.current.appContainer, LocalContext.current.applicationContext))
    val ui by vm.ui.collectAsState()
    var editing by rememberSaveable { mutableStateOf<RecordingField?>(null) }
    RecordingContent(
        ui, modifier = modifier,
        actions = RecordingActions(onBack, vm::setRecording, vm::setAvgMode, vm::set, { editing = it }),
    )
    editing?.let { field ->
        EditNumberSheet(
            title = field.label(), initial = ui.valueOf(field), spec = field.spec, unit = field.unit(),
            onSave = { vm.set(field, it); editing = null }, onDismiss = { editing = null },
        )
    }
}
