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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.navigator.app.R
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.location.PermissionAsk
import com.navigator.app.ui.findHostActivity
import com.navigator.app.ui.shell.LocalFeedback

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

    // The app's settings are offered, never jumped to: a dismissed first prompt is a "not now", not a "never".
    val feedback = LocalFeedback.current
    val resources = LocalResources.current
    fun offerSettings() = feedback.snackbar(
        resources.getString(R.string.permission_blocked), resources.getString(R.string.common_open_settings),
    ) { openAppSettings() }

    // A first prompt that is only dismissed is "not now"; see PermissionAsk. (Lost on a rotation mid-prompt: then no offer, which is safe.)
    val ask = remember { PermissionAsk() }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh()
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) BccuConnectionService.reevaluateLocationIfRunning()
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty() && ask.refusedForGood(context.findHostActivity(), denied)) offerSettings()
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh()
        // Android itself opens the app's location page for this one: a refusal is just the rider leaving it, so nothing is said.
        if (granted) BccuConnectionService.reevaluateLocationIfRunning()
    }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    // The ask is the point of the Battery row, and the app needs it to stay connected to the bike.
    @SuppressLint("BatteryLife")
    fun batteryIntent() = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))

    return PermissionFixer(
        run = { step ->
            when (step) {
                is FixStep.Request -> {
                    ask.begin(context.findHostActivity(), step.permissions)
                    request.launch(step.permissions.toTypedArray())
                }
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
