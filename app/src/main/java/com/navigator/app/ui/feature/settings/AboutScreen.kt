package com.navigator.app.ui.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.ble.BikePresenceSpike

/** The About & diagnostics page: [AboutContent] over its view model. */
@Composable
fun AboutScreen(onBack: () -> Unit, onLogs: () -> Unit, onSymbols: () -> Unit, onCalibration: () -> Unit, modifier: Modifier = Modifier) {
    val vm: AboutViewModel = viewModel(factory = AboutViewModel.factory(LocalContext.current.appContainer))
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    AboutContent(
        ui,
        AboutActions(
            onBack, onLogs, vm::setCopyLogs, onSymbols, onCalibration, vm::setOverlay,
            onPresenceTest = { (context as? Activity)?.let { a -> BikePresenceSpike.start(a) { launcher.launch(IntentSenderRequest.Builder(it).build()) } } },
        ),
        modifier,
    )
}
