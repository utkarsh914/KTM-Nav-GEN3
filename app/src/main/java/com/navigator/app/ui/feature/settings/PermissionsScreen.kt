package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.ui.feature.permissions.rememberPermissionFixer

/** The Permissions page: [PermissionsContent] over its view model, with the Android requests and the explainers. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: PermissionsViewModel = viewModel(factory = PermissionsViewModel.factory(LocalContext.current.appContainer))
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val fixer = rememberPermissionFixer(onResult = vm::refresh)
    PermissionsContent(ui, PermissionsActions(onBack) { id -> fixer.fix(vm.stepFor(id)) }, modifier)
    fixer.Sheet()
}
