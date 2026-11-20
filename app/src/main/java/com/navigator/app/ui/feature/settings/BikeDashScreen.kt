package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.shell.LocalFeedback

/** The Bike & dash page: [BikeDashContent] over its view model, with the name sheet, the re-pair confirm and the test sends. */
@Composable
fun BikeDashScreen(
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onRePaired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val feedback = LocalFeedback.current
    val vm: BikeDashViewModel = viewModel(factory = BikeDashViewModel.factory(LocalContext.current.appContainer, LocalContext.current))
    val ui by vm.ui.collectAsState()
    var editName by rememberSaveable { mutableStateOf(false) }
    var confirmRePair by rememberSaveable { mutableStateOf(false) }
    val sent = stringResource(R.string.bikedash_test_sent)
    val testMessage = resources.getString(
        R.string.bike_test_message, ui.userName.ifBlank { resources.getString(R.string.bike_test_name_fallback) },
    )

    BikeDashContent(
        ui, modifier = modifier,
        actions = BikeDashActions(
            onBack = onBack,
            onRePair = { confirmRePair = true },
            onShowOnDash = vm::setShowOnDash,
            onOpenApps = onOpenApps,
            onMarquee = vm::setMarquee,
            onLatinOnly = vm::setLatinOnly,
            onOpenPermissions = onOpenPermissions,
            onEditName = { editName = true },
            onTestMessage = { vm.sendTestMessage(testMessage); feedback.snackbar(sent) },
            onTestTurn = { vm.sendTestTurn(); feedback.snackbar(sent) },
        ),
    )
    if (editName) {
        EditTextSheet(
            title = stringResource(R.string.bikedash_name), label = stringResource(R.string.edit_value_name_label), initial = ui.userName,
            onSave = { vm.setName(it); editName = false }, onDismiss = { editName = false },
        )
    }
    if (confirmRePair) RePairDialog(onConfirm = { confirmRePair = false; vm.rePair(); onRePaired() }, onDismiss = { confirmRePair = false })
}
