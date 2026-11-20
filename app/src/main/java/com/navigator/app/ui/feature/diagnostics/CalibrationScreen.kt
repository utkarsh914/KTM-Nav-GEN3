package com.navigator.app.ui.feature.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer

@Composable
fun CalibrationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: CalibrationViewModel = viewModel(factory = CalibrationViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    CalibrationContent(ui, CalibrationActions(onBack, vm::toggle, vm::assign, vm::clear), modifier)
}
