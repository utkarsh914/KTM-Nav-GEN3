package com.navigator.app.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.navigator.app.app.AppIntent
import com.navigator.app.app.AppIntents
import com.navigator.app.app.appContainer
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.logging.AppLogger
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.shell.KtmApp
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.ThemeMode
import com.navigator.app.ui.theme.accentFor
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
        // Apply the saved accent + light/dark appearance before the first
        // frame so the app opens in the right skin with no flash of the wrong one.
        // System appearance is read from the current config's night flag here;
        // OpenDashApp keeps it in sync afterwards via isSystemInDarkTheme().
        run {
            val nightNow = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val dark = when (settings.themeMode) {
                com.navigator.app.ui.theme.ThemeMode.LIGHT -> false
                com.navigator.app.ui.theme.ThemeMode.DARK -> true
                com.navigator.app.ui.theme.ThemeMode.SYSTEM -> nightNow
            }
            com.navigator.app.ui.theme.Ktm.apply(
                dark,
                com.navigator.app.ui.theme.accentFor(settings.accentColorId),
            )
        }

        if (com.navigator.app.BuildConfig.DEBUG) {
            com.navigator.app.nav.DashPreview.setOverlayEnabled(settings.dashPreviewOverlay)
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

        // Notification "Exit" pressed while this activity sits in the recents
        // stack: the service can't finish us, so it broadcasts and we do.
        finishReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                finishAndRemoveTask()
            }
        }.also {
            val filter = android.content.IntentFilter(BccuConnectionService.ACTION_FINISH_ACTIVITY)
            registerReceiver(it, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        }

        // A recreated activity re-delivers the original shortcut intent: only act on a fresh launch.
        forwardIntent(intent, freshLaunch = savedInstanceState == null)

        setContent { KtmApp(onQuit = { exitApp() }) }

        // DEBUG-only: `adb shell am broadcast -a com.navigator.app.EXPORT_MANEUVERS`
        // renders Google Maps' own labeled maneuver icons to files/maneuver_dataset/
        // to build a training set for an independent classifier. Stripped from release.
        if (com.navigator.app.BuildConfig.DEBUG) {
            maneuverExportReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                    lifecycleScope.launch {
                        val msg = com.navigator.app.notifications.MapsIconExporter.exportAll(this@MainActivity)
                        AppLogger.log("Dataset", msg)
                    }
                }
            }
            val filter = android.content.IntentFilter("com.navigator.app.EXPORT_MANEUVERS")
            registerReceiver(maneuverExportReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardIntent(intent, freshLaunch = true)
    }

    /** Hand what the launch intent carries (a share, a shortcut, a notification tap) to the app. */
    private fun forwardIntent(intent: Intent?, freshLaunch: Boolean) {
        AppIntents.fromIntent(intent)
            // A recreated activity must not re-open a dialog or page the rider already used.
            .filter { freshLaunch || it is AppIntent.SharedLink || it == AppIntent.OpenNav }
            .forEach { appContainer.intents.post(it) }
    }

    private var maneuverExportReceiver: android.content.BroadcastReceiver? = null
    private var finishReceiver: android.content.BroadcastReceiver? = null

    override fun onDestroy() {
        if (appContainer.activityHolder.current === this) appContainer.activityHolder.current = null
        maneuverExportReceiver?.let { runCatching { unregisterReceiver(it) } }
        finishReceiver?.let { runCatching { unregisterReceiver(it) } }
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
     * Fully quit: stop the foreground service (removes its ongoing notification),
     * cancel any of our notifications, then remove the app task entirely. Invoked
     * from Settings' Quit row and the notification "Exit".
     */
    private fun exitApp() {
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
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return
        if (adapter.isEnabled) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        btPrompted = true
        runCatching {
            enableBtLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
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
