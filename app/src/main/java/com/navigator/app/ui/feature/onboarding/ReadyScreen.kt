package com.navigator.app.ui.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.ui.feature.settings.SettingsPage

/** The Ready screen: [ReadyContent] over its view model. */
@Composable
fun ReadyScreen(onOpenPage: (SettingsPage) -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: ReadyViewModel = viewModel(factory = ReadyViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    // Nothing is left behind this screen, so Back goes where Start riding does.
    BackHandler(onBack = onStart)
    ReadyContent(ui, ReadyActions(onOpenPage, onStart), modifier)
}
