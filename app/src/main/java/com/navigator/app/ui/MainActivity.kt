package com.navigator.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.navigator.app.BuildConfig
import com.navigator.app.app.AppIntent
import com.navigator.app.app.AppIntents
import com.navigator.app.app.appContainer
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.DashPreview
import com.navigator.app.nav.destination.AppShortcuts
import com.navigator.app.notifications.MapsIconExporter
import com.navigator.app.ride.RideRecordingService
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.shell.KtmApp
import com.navigator.app.ui.theme.AppFonts
import kotlinx.coroutines.launch

/**
 * Window setup, the system permission and Bluetooth prompts, auto-connect and forwarding launch
 * intents. Everything visible lives in [KtmApp].
 */
class MainActivity : ComponentActivity() {

    private lateinit var settings: AppSettings

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // The long-lived connection service usually starts BEFORE this async
        // grant lands - tell it to re-check location-gated features (overspeed
        // monitor, route recorder, FGS location type) now.
        BccuConnectionService.reevaluateLocationIfRunning()
        // Now that BLUETOOTH_CONNECT may have just been granted, we can legally
        // ask the user to turn Bluetooth on if it's off.
        maybePromptEnableBluetooth()
    }

    private var btPrompted = false
    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user accepted or declined; nothing else to do here */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw edge-to-edge from the very first frame so the layout fills the
        // screen immediately instead of settling once the window insets arrive
        // (each screen applies edgePadding() to keep content clear of the
        // status/nav bars). Without this the UI visibly "jumped" on launch.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settings = appContainer.settings.legacy
        appContainer.activityHolder.current = this
        if (BuildConfig.DEBUG) {
            DashPreview.setOverlayEnabled(settings.dashPreviewOverlay)
        }

        // First-run users grant permissions with context in the onboarding flow;
        // returning users get a silent top-up request for anything since revoked.
        // Only on a fresh launch (not rotation/recreation) and only when something is missing.
        if (savedInstanceState == null && settings.onboardingComplete &&
            OpenDashPermissions.runtimePermissions().any { !OpenDashPermissions.isGranted(this, it) }
        ) requestRuntimePermissions()

        // Auto-connect: if we already have a bonded bike, bring the connection
        // service up as soon as the app is opened (it's a foreground service and
        // owns its own reconnect loop from there on). The AutoConnectReceiver
        // covers the "app never opened" cases (boot, bike back in range).
        settings.bondedDeviceAddress?.let { BccuConnectionService.start(this, it) }

        // A recreated activity re-delivers the original shortcut intent: only act on a fresh launch.
        forwardIntent(intent, freshLaunch = savedInstanceState == null)

        // The brand font from the first frame: the setting is applied after composition only when it changes.
        AppFonts.apply(appContainer.settings.snapshot.value.fontMode)
        setContent { KtmApp(onQuit = { exitApp() }) }

        // DEBUG-only: `adb shell am broadcast -a com.navigator.app.EXPORT_MANEUVERS`
        // renders Google Maps' own labeled maneuver icons to files/maneuver_dataset/
        // to build a training set for an independent classifier. Stripped from release.
        if (BuildConfig.DEBUG) {
            maneuverExportReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    lifecycleScope.launch {
                        val msg = MapsIconExporter.exportAll(this@MainActivity)
                        AppLogger.log("Dataset", msg)
                    }
                }
            }
            val filter = IntentFilter("com.navigator.app.EXPORT_MANEUVERS")
            registerReceiver(maneuverExportReceiver, filter, Context.RECEIVER_EXPORTED)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardIntent(intent, freshLaunch = true)
    }

    /** Hand what the launch intent carries (a share, a shortcut, a notification tap) to the app. */
    private fun forwardIntent(intent: Intent?, freshLaunch: Boolean) {
        // Reopened from Recents: the intent is the old launch's, and its share or request was already served.
        if (intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (freshLaunch) AppShortcuts.reportUsed(this, intent)
        AppIntents.fromIntent(intent)
            // A recreated activity must not re-open a dialog or page the rider already used.
            .filter { freshLaunch || it == AppIntent.OpenNav }
            .forEach { appContainer.intents.post(it) }
    }

    private var maneuverExportReceiver: BroadcastReceiver? = null

    override fun onDestroy() {
        if (appContainer.activityHolder.current === this) appContainer.activityHolder.current = null
        maneuverExportReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AppForegroundState.isForeground = true
        // While visible we're eligible for while-in-use location even without
        // background permission - let the service (re)claim the location FGS
        // type and start any location features that were waiting on it.
        BccuConnectionService.reevaluateLocationIfRunning()
        maybePromptEnableBluetooth()
    }

    /**
     * Fully quit: end the in-app trip, stop the ride recording, stop the dash-link foreground service
     * (removes its ongoing notification), cancel any of our notifications, then remove the app task entirely. Invoked
     * from the Quit confirm (Settings' Quit row or the notification's Quit).
     */
    private fun exitApp() {
        // What the Quit dialog promises: guidance and recording stop too, not just the dash link.
        appContainer.trip.cancel()
        RideRecordingService.stop(this)
        appContainer.connection.quit()
        finishAndRemoveTask()
    }

    /**
     * If the phone's Bluetooth is off, ask the user to turn it on (the dash link
     * needs it). Once per process, and only when we hold BLUETOOTH_CONNECT on
     * Android 12+ (otherwise the request throws); it's re-tried right after that
     * permission is granted.
     */
    private fun maybePromptEnableBluetooth() {
        if (btPrompted) return
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (adapter.isEnabled) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        btPrompted = true
        runCatching {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    override fun onPause() {
        super.onPause()
        AppForegroundState.isForeground = false
    }

    private fun requestRuntimePermissions() {
        // Shared source of truth (BLUETOOTH_*, location, POST_NOTIFICATIONS).
        permissionLauncher.launch(OpenDashPermissions.runtimePermissions())
    }
}
