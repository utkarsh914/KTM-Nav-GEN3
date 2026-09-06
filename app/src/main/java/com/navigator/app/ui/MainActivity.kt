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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

private enum class AppRoute { BRAND, ONBOARDING, PAIRING, NAV_HOME, MIRROR_HOME, SETTINGS, LOGS, SYMBOL_TEST, TURN_CALIBRATION, PLACES, RECORDINGS, RIDE_REPLAY }

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
        // Apply the saved brand accent + light/dark appearance before the first
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
            com.navigator.app.ui.theme.Ktm.apply(settings.brand, dark)
        }

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
        // Shared source of truth (BLUETOOTH_*, location, POST_NOTIFICATIONS) plus
        // the one extra this entry point needs: legacy storage for the log export
        // on pre-Android-10.
        val perms = OpenDashPermissions.runtimePermissions().toMutableList()
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

    // Appearance: the chosen mode (persisted) resolved against the live system
    // night setting, so LIGHT/DARK force and SYSTEM follows the phone. Re-applied
    // whenever either changes, re-theming the whole app live.
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var navThemeMode by remember { mutableStateOf(settings.navThemeMode) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        com.navigator.app.ui.theme.ThemeMode.LIGHT -> false
        com.navigator.app.ui.theme.ThemeMode.DARK -> true
        com.navigator.app.ui.theme.ThemeMode.SYSTEM -> systemDark
    }
    // Re-evaluate day/night roughly once a minute so the map home flips at
    // 06:00 / 18:00 while the app stays open.
    val nightNow by androidx.compose.runtime.produceState(
        initialValue = com.navigator.app.ui.theme.isNightTime(),
    ) {
        while (true) {
            value = com.navigator.app.ui.theme.isNightTime()
            delay(60_000)
        }
    }

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
    // Direction-aware screen transitions: forward navigations (goTo) slide the new
    // screen in from the right; back/up navigations (goBack) slide it in from the
    // left. The flag is set synchronously with the route change so the transition
    // spec reads the correct direction on the same recomposition.
    var routeBack by remember { mutableStateOf(false) }
    fun goTo(to: AppRoute) { routeBack = false; route = to }
    fun goBack(to: AppRoute) { routeBack = true; route = to }
    // Apply the effective light/dark, re-theming the whole app live. Day/Night
    // applies ONLY while actively navigating on the map home (turn-by-turn
    // guidance) — the browse map and every other screen follow the app's own theme.
    val navSession by GoogleNavSdkProvider.state.collectAsState()
    val activelyNavigating = route == AppRoute.NAV_HOME && navSession.sessionState.isActiveNav()
    val navDark = if (navThemeMode == com.navigator.app.ui.theme.NavThemeMode.DAY_NIGHT) nightNow else effectiveDark
    val themeDark = if (activelyNavigating) navDark else effectiveDark
    LaunchedEffect(themeDark) {
        com.navigator.app.ui.theme.Ktm.apply(settings.brand, themeDark)
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
            goTo(homeRoute())
        }
    }
    // Notification tapped while navigating -> jump to the map home, which resumes
    // the NAVIGATING view from the live session state.
    val openNav by MainActivity.openNavRequest.collectAsState()
    LaunchedEffect(openNav) {
        if (openNav &&
            route != AppRoute.ONBOARDING && route != AppRoute.BRAND && route != AppRoute.PAIRING
        ) {
            goTo(homeRoute())
        }
        if (openNav) MainActivity.openNavRequest.value = false
    }

    var logsReturnRoute by remember { mutableStateOf(AppRoute.SETTINGS) }
    // The ride selected in the history list, shown on the replay screen.
    var selectedRideId by remember { mutableStateOf<String?>(null) }
    // Multi-select state hoisted here so it survives opening a ride and coming
    // back (the history screen leaves composition on navigation).
    var rideSelectionMode by remember { mutableStateOf(false) }
    var rideSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
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
                goBack(brandReturnRoute ?: homeRoute())
                brandReturnRoute = null
            }
            AppRoute.SETTINGS -> goBack(settingsReturnRoute)
            AppRoute.LOGS -> goBack(logsReturnRoute)
            AppRoute.PLACES -> goBack(AppRoute.SETTINGS)
            AppRoute.SYMBOL_TEST -> goBack(AppRoute.SETTINGS)
            AppRoute.TURN_CALIBRATION -> goBack(AppRoute.SETTINGS)
            AppRoute.RECORDINGS -> {
                // While multi-select is active, back exits it instead of leaving.
                if (rideSelectionMode) {
                    rideSelectionMode = false
                    rideSelectedIds = emptySet()
                } else goBack(AppRoute.SETTINGS)
            }
            AppRoute.RIDE_REPLAY -> goBack(AppRoute.RECORDINGS)
            AppRoute.NAV_HOME -> (context as? ComponentActivity)?.moveTaskToBack(true)
            AppRoute.MIRROR_HOME -> (context as? ComponentActivity)?.moveTaskToBack(true)
            AppRoute.PAIRING -> { goBack(pairingReturnRoute ?: homeRoute()); pairingReturnRoute = null }
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

    // Retained map surface: created lazily on first map use and kept alive across
    // route changes so returning to the map home re-attaches an already-loaded map
    // (no reload flash) instead of rebuilding the NavigationView each time.
    val retainedNav = com.navigator.app.ui.screens.rememberRetainedNavigationView()

    OpenDashTheme(highContrast = false) {
      androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        // Direction-aware cross-screen transition: a gentle fade with a small (~6%)
        // horizontal drift. Forward navigations slide the new screen in from the
        // right; back/up navigations reverse it (new screen from the left). Keeps
        // screen changes from being a hard cut, and reads as push/pop.
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val enterDur = 260
                val exitDur = 180
                val dir = if (routeBack) -1 else 1
                (fadeIn(tween(enterDur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(enterDur, easing = FastOutSlowInEasing)) { w -> dir * w / 16 }) togetherWith
                    (fadeOut(tween(exitDur, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(enterDur, easing = FastOutSlowInEasing)) { w -> -dir * w / 16 })
            },
            label = "route",
        ) { r ->
        when (r) {
            AppRoute.BRAND -> com.navigator.app.ui.screens.BrandSelectScreen(
                onPair = { brand ->
                    settings.brand = brand
                    com.navigator.app.ui.theme.Ktm.applyBrand(brand)
                    // From Settings → just return; on first run → continue setup.
                    goTo(brandReturnRoute ?: if (!settings.onboardingComplete) AppRoute.ONBOARDING
                        else if (settings.bondedDeviceAddress == null) AppRoute.PAIRING
                        else homeRoute())
                    brandReturnRoute = null
                },
                onBack = brandReturnRoute?.let { back -> {
                    // Cancelled: revert live preview to the persisted brand.
                    com.navigator.app.ui.theme.Ktm.applyBrand(settings.brand)
                    goBack(back)
                    brandReturnRoute = null
                } },
            )
            AppRoute.ONBOARDING -> com.navigator.app.ui.screens.OnboardingScreen(
                settings = settings,
                onComplete = { goTo(AppRoute.PAIRING) }
            )
            AppRoute.PAIRING -> PairingScreen(
                settings = settings,
                onPaired = { goTo(pairingReturnRoute ?: homeRoute()); pairingReturnRoute = null },
                onOpenLogs = { logsReturnRoute = AppRoute.PAIRING; goTo(AppRoute.LOGS) },
                onBack = pairingReturnRoute?.let { back -> { goBack(back); pairingReturnRoute = null } },
            )
            AppRoute.NAV_HOME -> com.navigator.app.ui.screens.NavigationHomeScreen(
                retainedNav = retainedNav,
                onOpenConnect = { pairingReturnRoute = AppRoute.NAV_HOME; goTo(AppRoute.PAIRING) },
                onOpenSettings = { settingsReturnRoute = AppRoute.NAV_HOME; goTo(AppRoute.SETTINGS) },
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
                onOpenSettings = { settingsReturnRoute = AppRoute.MIRROR_HOME; goTo(AppRoute.SETTINGS) },
                onOpenConnect = { pairingReturnRoute = AppRoute.MIRROR_HOME; goTo(AppRoute.PAIRING) },
                onExit = onExit,
            )
            AppRoute.SETTINGS -> SettingsScreen(
                settings = settings,
                onBack = { goBack(settingsReturnRoute) },
                onOpenLogs = { logsReturnRoute = AppRoute.SETTINGS; goTo(AppRoute.LOGS) },
                onOpenSymbolTest = { goTo(AppRoute.SYMBOL_TEST) },
                onOpenTurnCalibration = { goTo(AppRoute.TURN_CALIBRATION) },
                onChangeBrand = { brandReturnRoute = AppRoute.SETTINGS; goTo(AppRoute.BRAND) },
                onOpenPlaces = { goTo(AppRoute.PLACES) },
                onOpenRecordings = { goTo(AppRoute.RECORDINGS) },
                themeMode = themeMode,
                onThemeModeChanged = { mode -> themeMode = mode; settings.themeMode = mode },
                navThemeMode = navThemeMode,
                onNavThemeModeChanged = { mode -> navThemeMode = mode; settings.navThemeMode = mode },
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
                    goTo(AppRoute.PAIRING)
                }
            )
            AppRoute.LOGS -> com.navigator.app.ui.screens.LogsScreen(
                onBack = { goBack(logsReturnRoute) }
            )
            AppRoute.PLACES -> com.navigator.app.ui.screens.SavedPlacesScreen(
                onBack = { goBack(AppRoute.SETTINGS) }
            )
            AppRoute.SYMBOL_TEST -> com.navigator.app.ui.screens.SymbolTestScreen(
                onBack = { goBack(AppRoute.SETTINGS) }
            )
            AppRoute.TURN_CALIBRATION -> com.navigator.app.ui.screens.TurnCalibrationScreen(
                onBack = { goBack(AppRoute.SETTINGS) }
            )
            AppRoute.RECORDINGS -> com.navigator.app.ui.screens.RideHistoryScreen(
                onBack = {
                    if (rideSelectionMode) {
                        rideSelectionMode = false
                        rideSelectedIds = emptySet()
                    } else goBack(AppRoute.SETTINGS)
                },
                onOpen = { id -> selectedRideId = id; goTo(AppRoute.RIDE_REPLAY) },
                selectionMode = rideSelectionMode,
                selectedIds = rideSelectedIds,
                onSelectionModeChange = { rideSelectionMode = it },
                onSelectedIdsChange = { rideSelectedIds = it },
            )
            AppRoute.RIDE_REPLAY -> com.navigator.app.ui.screens.RideReplayScreen(
                retainedNav = retainedNav,
                rideId = selectedRideId,
                onBack = { goBack(AppRoute.RECORDINGS) },
            )
        }
        }
        // Soft fade for the connect greeting overlay instead of a hard pop.
        AnimatedVisibility(
            visible = showGreeting,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutSlowInEasing)),
        ) {
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
