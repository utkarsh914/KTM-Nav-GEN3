package com.navigator.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.isActiveNav
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.settings.AppSettings
import com.navigator.app.ui.screens.PairingScreen
import com.navigator.app.ui.screens.SettingsScreen
import com.navigator.app.ui.theme.OpenDashTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class AppRoute { BRAND, ONBOARDING, PAIRING, NAV_HOME, MIRROR_HOME, SETTINGS, LOGS, SYMBOL_TEST, TURN_CALIBRATION, PLACES }

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Text shared into the app (ACTION_SEND) that may contain a Google Maps
         * link. Compose observes this to open the destination screen in Link
         * mode; cleared once consumed. A flow (not an intent extra) because with
         * launchMode=singleTask a second share arrives via onNewIntent on the LIVE
         * activity - there is no recomposition-from-scratch to re-read an extra from.
         */
        val sharedNavLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        /**
         * Set true when the foreground-service notification is tapped while a trip
         * is running: Compose observes it to jump to the navigation home (which
         * then resumes the NAVIGATING view). Flow (not just an extra) for the same
         * singleTask/onNewIntent reason as [sharedNavLink].
         */
        val openNavRequest = kotlinx.coroutines.flow.MutableStateFlow(false)

        /** Notification deep-link extra: bring up the active navigation screen. */
        const val EXTRA_OPEN_NAV = "com.navigator.app.extra.OPEN_NAV"
    }

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
        // (each screen applies systemBarsPadding() to keep content clear of the
        // status/nav bars). Without this the UI visibly "jumped" on launch.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settings = AppSettings(this)
        // Apply the saved brand theme before the first frame so the app opens in
        // the right skin (KTM dark / Husqvarna light) with no flash of the wrong one.
        com.navigator.app.ui.theme.Ktm.applyBrand(settings.brand)

        // First-run users grant permissions with context in the onboarding flow;
        // returning users get a silent top-up request for anything since revoked.
        if (settings.onboardingComplete) requestRuntimePermissions()

        // Auto-connect: if we already have a bonded bike, bring the connection
        // service up as soon as the app is opened (it's a foreground service and
        // owns its own reconnect loop from there on). The AutoConnectReceiver
        // covers the "app never opened" cases (boot, bike back in range).
        settings.bondedDeviceAddress?.let { BccuConnectionService.start(this, it) }

        // While a navigation session is active (either engine), keep the screen
        // awake and let the app show over the lock screen - like Google Maps.
        // Cleared the moment guidance ends so we don't sit over the keyguard.
        lifecycleScope.launch {
            combine(
                GoogleNavSdkProvider.state,
                NotificationNavProvider.state,
            ) { sdk, mirror ->
                sdk.sessionState.isActiveNav() || mirror.sessionState.isActiveNav()
            }.distinctUntilChanged().collect { navigating ->
                applyNavWindowFlags(navigating)
            }
        }

        // Notification "Exit" pressed while this activity sits in the recents
        // stack: the service can't finish us, so it broadcasts and we do.
        finishReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                finishAndRemoveTask()
            }
        }.also {
            val filter = android.content.IntentFilter(BccuConnectionService.ACTION_FINISH_ACTIVITY)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(it, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(it, filter)
            }
        }

        handleSendIntent(intent)
        handleOpenNavIntent(intent)

        setContent {
            OpenDashApp(settings = settings, onExit = { exitApp() })
        }

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
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(maneuverExportReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(maneuverExportReceiver, filter)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
        handleOpenNavIntent(intent)
    }

    /** Notification tap while navigating: ask Compose to show the nav screen. */
    private fun handleOpenNavIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NAV, false) == true) {
            openNavRequest.value = true
        }
    }

    /** ACTION_SEND text/plain (e.g. "Share" from Google Maps): capture the text for the destination screen. */
    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (!text.isNullOrBlank()) {
            AppLogger.log("MapsUrl", "Received shared text (${text.length} chars)")
            sharedNavLink.value = text
        }
    }

    private var maneuverExportReceiver: android.content.BroadcastReceiver? = null
    private var finishReceiver: android.content.BroadcastReceiver? = null

    override fun onDestroy() {
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
     * from the home screens' exit-confirm dialog and the notification "Exit".
     */
    private fun exitApp() {
        BccuConnectionService.stop(this)
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancelAll()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
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

    /**
     * Keep the screen on and show over the lock screen while navigating so the
     * rider can glance at guidance without unlocking (mirrors Google Maps).
     * setShowWhenLocked/setTurnScreenOn is API 27+; older devices fall back to
     * the (now-deprecated but still honoured) window flags.
     */
    private fun applyNavWindowFlags(active: Boolean) {
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(active)
            setTurnScreenOn(active)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (active) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        // GPS: overspeed alerts + waypoints. FINE alone is silently ignored on
        // Android 12+; COARSE must be in the same request.
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        perms += Manifest.permission.ACCESS_COARSE_LOCATION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

@Composable
private fun OpenDashApp(
    settings: AppSettings,
    onExit: () -> Unit,
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    // SDK engine -> map-first NAV_HOME; notification-mirror engine (or no SDK
    // available) -> the mirror home. The two engines stay mutually exclusive.
    fun homeRoute(): AppRoute =
        if (com.navigator.app.nav.providers.GoogleNavSdkController.isAvailable(appContext) &&
            settings.googleNavEnabled
        ) AppRoute.NAV_HOME else AppRoute.MIRROR_HOME
    var route by remember {
        mutableStateOf(
            when {
                !settings.brandChosen -> AppRoute.BRAND      // first run: pick the bike brand first
                !settings.onboardingComplete -> AppRoute.ONBOARDING
                settings.bondedDeviceAddress == null -> AppRoute.PAIRING
                else -> homeRoute()
            }
        )
    }
    // Where "Change bike / brand" returns to: Settings when reached from there,
    // null (forward-only to pairing) on first run.
    var brandReturnRoute by remember { mutableStateOf<AppRoute?>(null) }
    // Return targets for screens reachable from more than one parent.
    var settingsReturnRoute by remember { mutableStateOf(homeRoute()) }
    // Non-null when Pairing is reached from the map home's Connect pill (so it
    // gets a back affordance); null on the forced first-run pairing step.
    var pairingReturnRoute by remember { mutableStateOf<AppRoute?>(null) }

    // In-app Google navigation is offered when a key + Play Services are present
    // AND the user hasn't switched to mirror-only in Settings.
    val googleNavOffered = com.navigator.app.nav.providers.GoogleNavSdkController.isAvailable(appContext) &&
        settings.googleNavEnabled

    // A Google Maps link shared into the app -> open the map home, which resolves
    // the link and drops into the confirm stage.
    val sharedLink by MainActivity.sharedNavLink.collectAsState()
    LaunchedEffect(sharedLink) {
        if (sharedLink != null && googleNavOffered &&
            route != AppRoute.ONBOARDING && route != AppRoute.BRAND && route != AppRoute.PAIRING
        ) {
            route = homeRoute()
        }
    }
    // Notification tapped while navigating -> jump to the map home, which resumes
    // the NAVIGATING view from the live session state.
    val openNav by MainActivity.openNavRequest.collectAsState()
    LaunchedEffect(openNav) {
        if (openNav &&
            route != AppRoute.ONBOARDING && route != AppRoute.BRAND && route != AppRoute.PAIRING
        ) {
            route = homeRoute()
        }
        if (openNav) MainActivity.openNavRequest.value = false
    }

    var logsReturnRoute by remember { mutableStateOf(AppRoute.SETTINGS) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Touch back navigation: sub-pages return to their parent instead of
    // minimizing the app.
    androidx.activity.compose.BackHandler(
        enabled = route != AppRoute.ONBOARDING &&
            !(route == AppRoute.PAIRING && pairingReturnRoute == null) &&
            !(route == AppRoute.BRAND && brandReturnRoute == null)
    ) {
        when (route) {
            AppRoute.BRAND -> {
                com.navigator.app.ui.theme.Ktm.applyBrand(settings.brand)
                route = brandReturnRoute ?: homeRoute()
                brandReturnRoute = null
            }
            AppRoute.SETTINGS -> route = settingsReturnRoute
            AppRoute.LOGS -> route = logsReturnRoute
            AppRoute.PLACES -> route = AppRoute.SETTINGS
            AppRoute.SYMBOL_TEST -> route = AppRoute.SETTINGS
            AppRoute.TURN_CALIBRATION -> route = AppRoute.SETTINGS
            AppRoute.NAV_HOME -> (context as? ComponentActivity)?.moveTaskToBack(true)
            AppRoute.MIRROR_HOME -> (context as? ComponentActivity)?.moveTaskToBack(true)
            AppRoute.PAIRING -> { route = pairingReturnRoute ?: homeRoute(); pairingReturnRoute = null }
            AppRoute.ONBOARDING -> {}
        }
    }

    // Connect greeting: when the bike authenticates, show a brief "Hi <name>!"
    // banner with the walking-person icon over whatever screen is up.
    val connState by BccuConnectionService.connectionState.collectAsState()
    var showGreeting by remember { mutableStateOf(false) }
    var prevConn by remember { mutableStateOf(connState) }
    LaunchedEffect(connState) {
        if (connState == BccuConnectionService.ConnectionState.AUTHENTICATED &&
            prevConn != BccuConnectionService.ConnectionState.AUTHENTICATED
        ) {
            showGreeting = true
            delay(4000)
            showGreeting = false
        }
        prevConn = connState
    }

    OpenDashTheme(highContrast = false) {
      androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        when (route) {
            AppRoute.BRAND -> com.navigator.app.ui.screens.BrandSelectScreen(
                onPair = { brand ->
                    settings.brand = brand
                    com.navigator.app.ui.theme.Ktm.applyBrand(brand)
                    // From Settings → just return; on first run → continue setup.
                    route = brandReturnRoute ?: if (!settings.onboardingComplete) AppRoute.ONBOARDING
                        else if (settings.bondedDeviceAddress == null) AppRoute.PAIRING
                        else homeRoute()
                    brandReturnRoute = null
                },
                onBack = brandReturnRoute?.let { back -> {
                    // Cancelled: revert live preview to the persisted brand.
                    com.navigator.app.ui.theme.Ktm.applyBrand(settings.brand)
                    route = back
                    brandReturnRoute = null
                } },
            )
            AppRoute.ONBOARDING -> com.navigator.app.ui.screens.OnboardingScreen(
                settings = settings,
                onComplete = { route = AppRoute.PAIRING }
            )
            AppRoute.PAIRING -> PairingScreen(
                settings = settings,
                onPaired = { route = pairingReturnRoute ?: homeRoute(); pairingReturnRoute = null },
                onOpenLogs = { logsReturnRoute = AppRoute.PAIRING; route = AppRoute.LOGS },
                onBack = pairingReturnRoute?.let { back -> { route = back; pairingReturnRoute = null } },
            )
            AppRoute.NAV_HOME -> com.navigator.app.ui.screens.NavigationHomeScreen(
                onOpenConnect = { pairingReturnRoute = AppRoute.NAV_HOME; route = AppRoute.PAIRING },
                onOpenSettings = { settingsReturnRoute = AppRoute.NAV_HOME; route = AppRoute.SETTINGS },
                onStartNavigation = { dest ->
                    (appContext as? android.app.Activity)?.let {
                        com.navigator.app.nav.providers.GoogleNavSdkController.startNavigation(it, dest)
                    }
                },
                onExit = onExit,
                sharedLink = sharedLink,
                onSharedLinkConsumed = { MainActivity.sharedNavLink.value = null },
            )
            AppRoute.MIRROR_HOME -> com.navigator.app.ui.screens.MirrorHomeScreen(
                onOpenSettings = { settingsReturnRoute = AppRoute.MIRROR_HOME; route = AppRoute.SETTINGS },
                onOpenConnect = { pairingReturnRoute = AppRoute.MIRROR_HOME; route = AppRoute.PAIRING },
                onExit = onExit,
            )
            AppRoute.SETTINGS -> SettingsScreen(
                settings = settings,
                onBack = { route = settingsReturnRoute },
                onOpenLogs = { logsReturnRoute = AppRoute.SETTINGS; route = AppRoute.LOGS },
                onOpenSymbolTest = { route = AppRoute.SYMBOL_TEST },
                onOpenTurnCalibration = { route = AppRoute.TURN_CALIBRATION },
                onChangeBrand = { brandReturnRoute = AppRoute.SETTINGS; route = AppRoute.BRAND },
                onOpenPlaces = { route = AppRoute.PLACES },
                onEngineChanged = { googleNav ->
                    // Switching to the mirror engine ends any in-app SDK trip so
                    // the two engines never drive the dash at once. Return to the
                    // newly-selected engine's home when leaving Settings.
                    if (!googleNav) com.navigator.app.nav.providers.GoogleNavSdkController.stop()
                    settingsReturnRoute = homeRoute()
                },
                onRepair = {
                    // Forget the pairing flag too, not just the address - otherwise
                    // re-pairing the same bike still replies GENERATE_KEYS and the
                    // handshake stalls if the dash has lost its keys. Clearing it
                    // makes the next handshake do a fresh HELLO (dash prompts).
                    settings.bondedDeviceAddress?.let { settings.clearPairedBefore(it) }
                    settings.bondedDeviceAddress = null
                    settings.bondedDeviceName = null
                    BccuConnectionService.stop(context)
                    route = AppRoute.PAIRING
                }
            )
            AppRoute.LOGS -> com.navigator.app.ui.screens.LogsScreen(
                onBack = { route = logsReturnRoute }
            )
            AppRoute.PLACES -> com.navigator.app.ui.screens.SavedPlacesScreen(
                onBack = { route = AppRoute.SETTINGS }
            )
            AppRoute.SYMBOL_TEST -> com.navigator.app.ui.screens.SymbolTestScreen(
                onBack = { route = AppRoute.SETTINGS }
            )
            AppRoute.TURN_CALIBRATION -> com.navigator.app.ui.screens.TurnCalibrationScreen(
                onBack = { route = AppRoute.SETTINGS }
            )
        }
        if (showGreeting) {
            ConnectGreeting(name = settings.userName?.trim().orEmpty())
        }
      }
    }
}

/** Brief "Hi <name>!" banner with the walking-person icon, shown on connect. */
@Composable
private fun ConnectGreeting(name: String) {
    val text = if (name.isNotBlank()) "Hi $name!" else "Welcome!"
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(top = 96.dp),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = androidx.compose.ui.Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(com.navigator.app.ui.theme.Ktm.Orange)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(com.navigator.app.R.drawable.dash_pedestrian),
                contentDescription = null,
                tint = com.navigator.app.ui.theme.Ktm.OnAccent,
                modifier = androidx.compose.ui.Modifier.size(34.dp),
            )
            androidx.compose.material3.Text(
                text,
                color = com.navigator.app.ui.theme.Ktm.OnAccent,
                fontFamily = com.navigator.app.ui.theme.BarlowCondensed,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 26.sp,
                modifier = androidx.compose.ui.Modifier.padding(start = 12.dp),
            )
        }
    }
}
