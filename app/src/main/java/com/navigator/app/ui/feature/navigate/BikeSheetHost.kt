package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Bike sheet over any Navigate surface: the browse map and, while riding, the trip sheet's dash row and the
 * dash-lost chip. Shares the screen's [NavigateViewModel]. Re-pairing closes the sheet and opens the bike screen.
 */
@Composable
fun BikeSheetHost(tripRunning: Boolean, onOpenPairing: () -> Unit, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: NavigateViewModel = viewModel(factory = NavigateViewModel.factory(container))
    val state by vm.state.collectAsState()
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    BikeSheet(
        state = state, dashTime = time,
        onBeeps = vm::setBeeps, onMessages = vm::setMessages, onSendTest = vm::sendTest,
        onRePair = { onDismiss(); vm.rePair(); onOpenPairing() },
        onDismiss = onDismiss,
        tripRunning = tripRunning,
    )
}
