package com.navigator.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.core.net.toUri
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.navigator.app.R
import com.navigator.app.app.AppIntent
import com.navigator.app.app.AppIntents
import com.navigator.app.app.AppReadiness
import com.navigator.app.app.IntentOutcome
import com.navigator.app.app.RejectReason
import com.navigator.app.app.appContainer
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.DialogAction
import com.navigator.app.ui.components.KtmDialog
import com.navigator.app.ui.screens.LogsScreen
import com.navigator.app.ui.screens.MirrorHomeScreen
import com.navigator.app.ui.feature.navigate.link.SharedLinkActions
import com.navigator.app.ui.feature.navigate.link.SharedLinkCard
import com.navigator.app.ui.feature.navigate.link.SharedLinkState
import com.navigator.app.ui.screens.NavigationHomeScreen
import com.navigator.app.ui.screens.OnboardingScreen
import com.navigator.app.ui.screens.PairingScreen
import com.navigator.app.ui.screens.RideHistoryScreen
import com.navigator.app.ui.screens.RideReplayScreen
import com.navigator.app.ui.screens.SavedPlacesScreen
import com.navigator.app.ui.screens.SettingsScreen
import com.navigator.app.ui.screens.SymbolTestScreen
import com.navigator.app.ui.screens.TurnCalibrationScreen
import com.navigator.app.ui.map.rememberRetainedNavigationView
import com.navigator.app.ui.theme.AppFonts
import com.navigator.app.ui.theme.Ktm
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.accentFor

/**
 * Root of the UI: theme, navigation, the tab shell, feedback and the handling of outside requests.
 * [onQuit] stops the bike connection and removes the task (the activity owns that).
 */
@Composable
fun KtmApp(onQuit: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = context.appContainer
    val settings = container.settings
    val legacy = settings.legacy
    val snap by settings.snapshot.collectAsState()
    val intents = container.intents

    LaunchedEffect(snap.fontMode) { AppFonts.apply(snap.fontMode) }

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = entry?.destination.tab()

    // The whole app follows the rider's own light/dark; Day/Night applies to the riding surfaces only (RidingTheme).
    val dark = resolveAppDark(snap.themeMode, isSystemInDarkTheme())
    val accent = remember(snap.accentColorId) { accentFor(snap.accentColorId) }
    LaunchedEffect(dark, snap.accentColorId) { Ktm.apply(dark, accent) }

    val feedback = rememberFeedback()
    val chrome = remember { ShellChrome() }
    val retainedNav = rememberRetainedNavigationView()

    // Permission state is re-read whenever the app comes back to the front (the rider may have
    // changed it in system settings).
    val permissions = container.permissions
    val permissionState by permissions.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissions.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // In-app Google navigation is offered when a key + Play Services are present AND the rider
    // hasn't switched to mirror-only in Settings.
    val inAppMaps = remember(snap.engine) { GoogleNavSdkController.isActive(context, legacy) }
    val inAppState = rememberUpdatedState(inAppMaps)
    // First-run screens are not a place to land on: a request waits until setup has finished and
    // the stack has been replaced by Navigate.
    // Not only the top entry: Logs opened from the first-run pairing sits over it.
    val setupTracker = remember { SetupTracker() }
    val inSetupScreen = setupTracker.inSetup(entry)
    // Not "paired": after Re-pair the rider is back on the tabs without a bike and requests must still work.
    val setupDone = snap.onboardingComplete && !inSetupScreen
    val pending by intents.pending.collectAsState()
    val reducedMotion = KtmMotionSpecs.isReducedMotion(context)
    // A request that stays pending (the mirror card waits for a tap) takes the rider to Navigate once,
    // not again each time something else changes.
    val taken = remember { mutableSetOf<AppIntent>() }
    LaunchedEffect(pending, setupDone, inAppMaps) {
        val readiness = AppReadiness(setupDone, inAppMaps)
        taken.retainAll(
            listOfNotNull(
                pending.sharedLink?.let { AppIntent.SharedLink(it) }, pending.quickRoute?.let { AppIntent.QuickRoute(it) },
                pending.place?.let { AppIntent.OpenPlace(it) }, AppIntent.OpenNav.takeIf { pending.openNav },
            ).toSet(),
        )
        fun handle(intent: AppIntent, consume: () -> Unit) {
            when (val outcome = AppIntents.decide(intent, readiness)) {
                IntentOutcome.Navigate -> if (taken.add(intent)) navController.openTab(Tab.Navigate)
                IntentOutcome.Defer -> {}
                IntentOutcome.Drop -> consume()
                is IntentOutcome.Reject -> {
                    consume()
                    feedback.snackbar(resources.getString(outcome.reason.message()))
                }
            }
        }
        pending.sharedLink?.let { handle(AppIntent.SharedLink(it), intents::consumeSharedLink) }
        pending.quickRoute?.let { handle(AppIntent.QuickRoute(it), intents::consumeQuickRoute) }
        pending.place?.let { handle(AppIntent.OpenPlace(it), intents::consumePlace) }
        // The Navigate screen reads the flag when it composes (it may only be composing now), then clears it.
        if (pending.openNav) handle(AppIntent.OpenNav, intents::consumeOpenNav)
    }

    var showQuit by remember { mutableStateOf(false) }
    val rideList = remember { RideListUiState() }
    val settingsListState = rememberLazyListState()
    val startRoute = remember {
        when {
            !snap.onboardingComplete -> Route.Onboarding
            !snap.isPaired -> Route.Pairing(forced = true)
            else -> Route.Navigate
        }
    }

    KtmTheme(darkTheme = dark, accent = accent) {
        CompositionLocalProvider(LocalFeedback provides feedback, LocalShellChrome provides chrome) {
            NavWindowEffects()
            Box(Modifier.fillMaxSize()) {
                MainShell(
                    currentTab = currentTab,
                    immersive = chrome.immersive,
                    settingsNeedsAttention = permissionState.attentionNeeded,
                    onSelectTab = { navController.openTab(it) },
                    feedback = feedback,
                    strip = {
                        RecordingStrip(
                            feedback = feedback,
                            onOpenActivity = { navController.openTab(Tab.Navigate) },
                            onOpenRides = { navController.openTab(Tab.Rides) },
                        )
                    },
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startRoute,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { enterFor(reducedMotion) },
                        exitTransition = { exitFor(reducedMotion) },
                        popEnterTransition = { popEnterFor(reducedMotion) },
                        popExitTransition = { popExitFor(reducedMotion) },
                    ) {
                        composable<Route.Onboarding> {
                            OnboardingScreen(
                                settings = legacy,
                                onComplete = {
                                    navController.navigate(Route.Pairing(forced = true)) {
                                        popUpTo<Route.Onboarding> { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable<Route.Pairing> { backStackEntry ->
                            val route = backStackEntry.toRoute<Route.Pairing>()
                            val back = { navController.popFrom(backStackEntry) }
                            PairingScreen(
                                settings = legacy,
                                onPaired = {
                                    if (route.forced || route.toHome) navController.openRoot(Route.Navigate)
                                    // Not a tap: it fires when the handshake completes, even while the app is paused.
                                    else navController.popBackStack()
                                },
                                onOpenLogs = { navController.push(Route.Logs) },
                                onBack = if (route.forced) null else back,
                            )
                        }
                        composable<Route.Navigate> {
                            val openNav = pending.openNav
                            LaunchedEffect(openNav) { if (openNav) intents.consumeOpenNav() }
                            if (inAppState.value) {
                                NavigationHomeScreen(
                                    retainedNav = retainedNav,
                                    onOpenConnect = { navController.push(Route.Pairing()) },
                                    onOpenReplay = { id -> navController.push(Route.RideReplay(id)) },
                                    onOpenPlaces = { navController.openTab(Tab.Places) },
                                    sharedLink = pending.sharedLink,
                                    onSharedLinkConsumed = intents::consumeSharedLink,
                                    pendingPlace = pending.place,
                                    onPendingPlaceConsumed = intents::consumePlace,
                                    quickRoute = pending.quickRoute?.name,
                                    onQuickRouteConsumed = intents::consumeQuickRoute,
                                    openNavRequest = pending.openNav,
                                )
                            } else {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                                    MirrorHomeScreen(onOpenConnect = { navController.push(Route.Pairing()) })
                                    // A Google Maps link shared in mirror mode: Google Maps does the navigating.
                                    pending.sharedLink?.let { link ->
                                        SharedLinkCard(
                                            SharedLinkState.Mirror,
                                            SharedLinkActions(
                                                onRetry = {}, onSearchInstead = {},
                                                onDismiss = intents::consumeSharedLink,
                                                onOpenMaps = {
                                                    intents.consumeSharedLink()
                                                    if (!openInGoogleMaps(context, link)) {
                                                        feedback.snackbar(resources.getString(R.string.shared_link_mirror_unreadable))
                                                    }
                                                },
                                                onSwitchToInApp = if (GoogleNavSdkController.isAvailable(context)) {
                                                    { container.settings.update { googleNavEnabled = true } }
                                                } else null,
                                            ),
                                            Modifier.align(Alignment.BottomCenter),
                                        )
                                    }
                                }
                            }
                        }
                        composable<Route.Rides> {
                            RideHistoryScreen(
                                onOpen = { id -> navController.push(Route.RideReplay(id)) },
                                selectionMode = rideList.selectionMode,
                                selectedIds = rideList.selectedIds,
                                onSelectionModeChange = { rideList.selectionMode = it },
                                onSelectedIdsChange = { rideList.selectedIds = it },
                                query = rideList.query,
                                onQueryChange = { rideList.query = it },
                                sortField = rideList.sortField,
                                onSortFieldChange = { rideList.sortField = it },
                                sortDesc = rideList.sortDesc,
                                onSortDescChange = { rideList.sortDesc = it },
                            )
                        }
                        composable<Route.Places> {
                            SavedPlacesScreen(onOpenOnMap = { place -> intents.post(AppIntent.OpenPlace(place)) })
                        }
                        composable<Route.Settings> {
                            SettingsScreen(
                                settings = legacy,
                                onQuit = { showQuit = true },
                                onOpenLogs = { navController.push(Route.Logs) },
                                onOpenSymbolTest = { navController.push(Route.SymbolTest) },
                                onOpenTurnCalibration = { navController.push(Route.TurnCalibration) },
                                onOpenPlaces = { navController.openTab(Tab.Places) },
                                onOpenRecordings = { navController.openTab(Tab.Rides) },
                                themeMode = snap.themeMode,
                                onThemeModeChanged = { mode -> settings.update { themeMode = mode } },
                                fontMode = snap.fontMode,
                                onFontModeChanged = { mode -> settings.update { fontMode = mode } },
                                navThemeMode = snap.ridingScreens,
                                onNavThemeModeChanged = { mode -> settings.update { navThemeMode = mode } },
                                accentColorId = snap.accentColorId,
                                onAccentColorChanged = { id -> settings.update { accentColorId = id } },
                                listState = settingsListState,
                                onEngineChanged = { googleNav ->
                                    // Switching to the mirror engine ends any in-app SDK trip so the two
                                    // engines never drive the dash at once.
                                    if (!googleNav) container.trip.cancel()
                                },
                                onRepair = {
                                    container.connection.rePair()
                                    navController.push(Route.Pairing(toHome = true))
                                },
                            )
                        }
                        composable<Route.Logs> { e -> LogsScreen(onBack = { navController.popFrom(e) }) }
                        composable<Route.SymbolTest> { e -> SymbolTestScreen(onBack = { navController.popFrom(e) }) }
                        composable<Route.TurnCalibration> { e -> TurnCalibrationScreen(onBack = { navController.popFrom(e) }) }
                        composable<Route.RideReplay> { backStackEntry ->
                            RideReplayScreen(
                                retainedNav = retainedNav,
                                rideId = backStackEntry.toRoute<Route.RideReplay>().rideId,
                                onBack = { navController.popFrom(backStackEntry) },
                            )
                        }
                    }
                }
                if (com.navigator.app.BuildConfig.DEBUG) {
                    val overlayOn by com.navigator.app.nav.DashPreview.overlayEnabled.collectAsState()
                    if (overlayOn) com.navigator.app.ui.DashPreviewPanel()
                }
            }

            if (showQuit) {
                KtmDialog(
                    title = stringResource(R.string.quit_title),
                    message = stringResource(R.string.quit_message),
                    onDismiss = { showQuit = false },
                    confirm = DialogAction(stringResource(R.string.quit_confirm), { showQuit = false; onQuit() }, ButtonStyle.Danger),
                    dismiss = DialogAction(stringResource(R.string.common_cancel), { showQuit = false }),
                )
            }
        }
    }
}

private fun RejectReason.message(): Int = when (this) {
    RejectReason.SHORTCUT_NEEDS_IN_APP -> R.string.intent_shortcut_needs_in_app
    RejectReason.MAP_NEEDS_IN_APP -> R.string.intent_map_needs_in_app
}

/**
 * Whether the rider is in first-run setup (Onboarding, or the forced Pairing). A page pushed over a setup
 * screen (Logs from Pairing) keeps the answer of the screen below it; any tab root or re-pairing ends it.
 */
class SetupTracker {
    private var setup = false

    fun inSetup(entry: NavBackStackEntry?): Boolean {
        val d = entry?.destination ?: return setup
        when {
            d.hasRoute<Route.Onboarding>() -> setup = true
            d.hasRoute<Route.Pairing>() -> setup = entry.toRoute<Route.Pairing>().forced
            Tab.entries.any { d.hasRoute(it.route::class) } -> setup = false
        }
        return setup
    }
}

/**
 * A tab switch keeps each tab's back stack and state. A page pushed over a tab (Pairing, Logs, a replay)
 * is closed first: otherwise the restore below would put that page straight back.
 */
fun NavHostController.openTab(tab: Tab) {
    var guard = 0
    while (guard++ < 8 && currentIsPushedPage() && previousBackStackEntry != null) popBackStack()
    navigate(tab.route) {
        popUpTo<Route.Navigate> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.currentIsPushedPage(): Boolean {
    val d = currentDestination ?: return false
    return Tab.entries.none { d.hasRoute(it.route::class) }
}

/** Open a page over the current one, once: a second tap while it animates in must not stack a copy. */
fun NavHostController.push(route: Route) {
    navigate(route) { launchSingleTop = true }
}

/**
 * Close [entry]'s page. Only while it is the resumed page: a second tap during the exit animation would
 * otherwise pop whatever lies underneath (down to the root, leaving a blank screen).
 */
fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}

/** Replace everything with [route] (end of first-run pairing, or after re-pairing). */
fun NavHostController.openRoot(route: Route) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

// ---- Transitions: tabs fade through (the map stays put); pushed pages share the X axis. ----

private const val FADE_MS = 200
private const val SLIDE_MS = 300

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch(): Boolean =
    initialState.destination.tab() != null && targetState.destination.tab() != null

// With "Remove animations" every move becomes a crossfade.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFor(reduced: Boolean): EnterTransition = when {
    tabSwitch() || reduced -> fadeIn(tween(FADE_MS, delayMillis = FADE_MS / 2))
    else -> slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 12 } + fadeIn(tween(FADE_MS))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitFor(reduced: Boolean): ExitTransition = when {
    tabSwitch() || reduced -> fadeOut(tween(FADE_MS / 2))
    else -> slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -it / 12 } + fadeOut(tween(FADE_MS))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterFor(reduced: Boolean): EnterTransition = when {
    tabSwitch() || reduced -> fadeIn(tween(FADE_MS, delayMillis = FADE_MS / 2))
    else -> slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -it / 12 } + fadeIn(tween(FADE_MS))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitFor(reduced: Boolean): ExitTransition = when {
    tabSwitch() || reduced -> fadeOut(tween(FADE_MS / 2))
    else -> slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / 12 } + fadeOut(tween(FADE_MS))
}

/**
 * Hand a shared Google Maps link back to Google Maps, which starts the navigation the mirror then follows.
 * False when the share holds nothing Maps can open.
 */
private fun openInGoogleMaps(context: android.content.Context, sharedText: String): Boolean {
    val resolver = com.navigator.app.nav.destination.MapsUrlResolver
    // Maps shares "Place name\nhttps://..." : only the link is a URI. Coordinates alone go as a geo: URI.
    val uri = resolver.extractUrl(sharedText)?.toUri()
        ?: resolver.parseCoordinates(sharedText)?.let { "geo:${it.lat},${it.lng}".toUri() }
        ?: return false
    val view = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    val maps = android.content.Intent(view).setPackage("com.google.android.apps.maps")
    return runCatching { context.startActivity(maps) }.recoverCatching { context.startActivity(view) }.isSuccess
}
