package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer

/** The Navigation page: [NavigationContent] over its view model. */
@Composable
fun NavigationScreen(onBack: () -> Unit, onChooseNavApp: () -> Unit, onOpenPlaces: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: NavigationViewModel = viewModel(factory = NavigationViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    NavigationContent(
        ui, modifier = modifier,
        actions = NavigationActions(
            onBack = onBack, onEngine = vm::setEngine, onVehicle = vm::setVehicle, onMarker = vm::setMarker,
            onLiveUpdates = vm::setLiveUpdates,
            // On: back to detecting. Off: pick the app (the pick is what turns detecting off).
            onAutoDetect = { if (it) vm.detectNavApp() else onChooseNavApp() },
            onChooseNavApp = onChooseNavApp, onDrivingSide = vm::setDrivingSide, onOpenPlaces = onOpenPlaces,
        ),
    )
}
