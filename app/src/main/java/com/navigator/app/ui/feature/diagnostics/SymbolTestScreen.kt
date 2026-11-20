package com.navigator.app.ui.feature.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.shell.LocalFeedback

@Composable
fun SymbolTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SymbolTestViewModel = viewModel(factory = SymbolTestViewModel.factory(LocalContext.current.appContainer))
    val ui by vm.ui.collectAsState()
    val feedback = LocalFeedback.current
    val offline = stringResource(R.string.symbols_offline)
    LaunchedEffect(vm) { vm.notSent.collect { feedback.snackbar(offline) } }
    SymbolTestContent(ui, SymbolTestActions(onBack, vm::selectKind, vm::showInfo, vm::send, vm::mark), modifier)
}
