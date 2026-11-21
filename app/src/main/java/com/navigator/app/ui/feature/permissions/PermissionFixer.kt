package com.navigator.app.ui.feature.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.ui.findHostActivity

/** Carries out a [FixStep]: Android's dialogs and screens, and the explainer sheet that comes first. */
class PermissionFixer internal constructor(
    private val run: (FixStep) -> Unit,
    private val explainer: Explainer?,
    private val go: (Explainer) -> Unit,
    private val dismiss: () -> Unit,
) {
    fun fix(step: FixStep) = run(step)

    /** Shows the explainer when a fix needs one; place it once on the screen. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Sheet() {
        explainer?.let { PermissionExplainerSheet(it, onGo = { go(it) }, onDismiss = dismiss) }
    }
}

/**
 * The launchers behind every Allow / Open button, shared by Settings › Permissions, onboarding and pairing.
 * [onResult] runs after each answer so the caller can re-read the grants.
 */
@Composable
fun rememberPermissionFixer(onResult: () -> Unit): PermissionFixer {
    val context = LocalContext.current
    val refresh by rememberUpdatedState(onResult)
    var explainer by rememberSaveable { mutableStateOf<Explainer?>(null) }

    fun openAppSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
    }

    // Once Android stops showing its dialog ("don't ask again"), the only way left is the app's own settings.
    fun cannotAskAgain(permission: String): Boolean {
        val activity = context.findHostActivity() ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh()
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) BccuConnectionService.reevaluateLocationIfRunning()
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty() && denied.all(::cannotAskAgain)) openAppSettings()
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        if (granted) BccuConnectionService.reevaluateLocationIfRunning() else openAppSettings()
    }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    // The ask is the point of the Battery row, and the app needs it to stay connected to the bike.
    @SuppressLint("BatteryLife")
    fun batteryIntent() = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))

    return PermissionFixer(
        run = { step ->
            when (step) {
                is FixStep.Request -> request.launch(step.permissions.toTypedArray())
                is FixStep.Explain -> explainer = step.explainer
                FixStep.OpenBattery -> runCatching { battery.launch(batteryIntent()) }
            }
        },
        explainer = explainer,
        go = { which ->
            explainer = null
            when (which) {
                Explainer.BACKGROUND_LOCATION -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                Explainer.NOTIFICATION_ACCESS ->
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        },
        dismiss = { explainer = null },
    )
}
