package com.navigator.app.ui.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.ui.feature.navigate.findHostActivity
import com.navigator.app.ui.feature.permissions.Explainer
import com.navigator.app.ui.feature.permissions.FixStep
import com.navigator.app.ui.feature.permissions.PermissionExplainerSheet

/** The Permissions page: [PermissionsContent] over its view model, with the Android requests and the explainers. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: PermissionsViewModel = viewModel(factory = PermissionsViewModel.factory(context.appContainer))
    val ui by vm.ui.collectAsState()
    var explainer by rememberSaveable { mutableStateOf<Explainer?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }

    fun openAppSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
    }

    // Once Android stops showing its dialog ("don't ask again"), the only way left is the app's own settings.
    fun cannotAskAgain(permission: String): Boolean {
        val activity = context.findHostActivity() ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.refresh()
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) BccuConnectionService.reevaluateLocationIfRunning()
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty() && denied.all(::cannotAskAgain)) openAppSettings()
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refresh()
        if (granted) BccuConnectionService.reevaluateLocationIfRunning() else openAppSettings()
    }
    // The ask is the point of the Battery row, and the app needs it to stay connected to the bike.
    @SuppressLint("BatteryLife")
    fun batteryIntent() = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refresh() }

    PermissionsContent(
        ui, PermissionsActions(onBack) { id: PermissionId ->
            when (val step = vm.stepFor(id)) {
                is FixStep.Request -> request.launch(step.permissions.toTypedArray())
                is FixStep.Explain -> explainer = step.explainer
                FixStep.OpenBattery -> runCatching {
                    battery.launch(batteryIntent())
                }
            }
        },
        modifier,
    )
    explainer?.let { which ->
        PermissionExplainerSheet(
            which,
            onGo = {
                explainer = null
                when (which) {
                    Explainer.BACKGROUND_LOCATION -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    Explainer.NOTIFICATION_ACCESS ->
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            },
            onDismiss = { explainer = null },
        )
    }
}
