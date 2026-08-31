package com.navigator.app.nav.providers

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.navigator.app.BuildConfig
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.TravelMode

/**
 * Orchestrates the Activity-side handshake the Navigation SDK requires (API key,
 * Terms of Service, `getNavigator`), then hands the [Navigator] to
 * [GoogleNavSdkProvider] and switches the dash pipeline over to it.
 *
 * Phase 6 uses a hardcoded test destination ([SILK_BOARD]); Phase 5 will replace
 * the trigger with real destination entry.
 */
object GoogleNavSdkController {

    /** Hardcoded Phase-6 test destination: Silk Board Junction, Bengaluru. */
    val SILK_BOARD = NavDestination(lat = 12.9172, lng = 77.6229, label = "Silk Board Junction")

    @Volatile private var apiKeySet = false

    /** Notification id for the SDK's own turn-by-turn foreground notification
     *  (distinct from the BLE service = 1 and overspeed = 42). */
    private const val NAV_NOTIFICATION_ID = 1010

    @Volatile private var navNotificationInit = false

    /**
     * Point the Nav SDK's own navigation notification (the maneuver/ETA one it
     * shows while guiding) at our activity, so tapping it returns to the active
     * navigation screen - matching the BLE connection notification. Must run once
     * per process before guidance starts; the SDK throws if initialised twice, so
     * it's guarded and wrapped defensively.
     */
    private fun ensureNavNotification(activity: Activity) {
        if (navNotificationInit) return
        runCatching {
            val app = activity.application
            val resumeIntent = Intent(app, com.navigator.app.ui.MainActivity::class.java).apply {
                putExtra(com.navigator.app.ui.MainActivity.EXTRA_OPEN_NAV, true)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            NavigationApi.initForegroundServiceManagerMessageAndIntent(
                app, NAV_NOTIFICATION_ID, "Navigating", resumeIntent,
            )
            navNotificationInit = true
        }.onFailure { AppLogger.log("Nav", "!! nav notification init failed: ${it.message}") }
    }

    /** True when a key is compiled in and Google Play services are usable. */
    fun isAvailable(context: Context): Boolean =
        BuildConfig.NAV_SDK_API_KEY.isNotBlank() &&
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Set the Nav SDK API key once (idempotent). Safe to call before showing the map. */
    fun ensureApiKey() {
        if (!apiKeySet && BuildConfig.NAV_SDK_API_KEY.isNotBlank()) {
            NavigationApi.setApiKey(BuildConfig.NAV_SDK_API_KEY)
            apiKeySet = true
        }
    }

    /** Phase-6 test entry point: navigate to Silk Board Junction. */
    fun startTest(activity: Activity) = startNavigation(activity, SILK_BOARD)

    /**
     * Warm up the Navigation SDK for the map home: sets the key, shows the Terms
     * dialog if needed, and obtains the [Navigator] (required before a
     * `NavigationView` can render). Does NOT switch the dash provider or start
     * guidance - that stays in [startNavigation]. Idempotent; [onReady] fires on
     * the main thread once the navigator is available (or immediately again on a
     * later call, since the SDK returns the same navigator).
     */
    fun prepare(activity: Activity, onReady: () -> Unit = {}, onError: (Int) -> Unit = {}) {
        if (!isAvailable(activity)) {
            AppLogger.log("Nav", "prepare(): Google Nav SDK unavailable; skipping")
            onError(-1)
            return
        }
        ensureApiKey()
        ensureNavNotification(activity)
        AppLogger.log("Nav", "prepare(): requesting navigator…")
        NavigationApi.getNavigator(activity, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(navigator: Navigator) {
                AppLogger.log("Nav", "prepare(): navigator ready")
                // Unblock the map first; provider wiring is best-effort (startNavigation
                // re-obtains the navigator + registers the provider anyway).
                onReady()
                runCatching { GoogleNavSdkProvider.onNavigatorReady(navigator, activity.applicationContext) }
                    .onFailure { AppLogger.log("Nav", "!! prepare(): provider setup failed: ${it.message}") }
            }

            override fun onError(errorCode: Int) {
                AppLogger.log("Nav", "!! prepare(): getNavigator error=$errorCode")
                onError(errorCode)
            }
        })
    }

    fun startNavigation(activity: Activity, dest: NavDestination) {
        if (!isAvailable(activity)) {
            AppLogger.log("Nav", "Google Nav SDK unavailable (no key or Play services); ignoring")
            return
        }
        if (!apiKeySet) {
            NavigationApi.setApiKey(BuildConfig.NAV_SDK_API_KEY)
            apiKeySet = true
        }
        ensureNavNotification(activity)
        // getNavigator(Activity, ...) shows the ToS dialog itself if not accepted.
        NavigationApi.getNavigator(activity, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(navigator: Navigator) {
                AppLogger.log("Nav", "Navigator ready - starting guidance to ${dest.label}")
                GoogleNavSdkProvider.onNavigatorReady(navigator, activity.applicationContext)
                BccuConnectionService.setNavProviderIfRunning(GoogleNavSdkProvider)
                GoogleNavSdkProvider.startNavigation(dest, TravelMode.TWO_WHEELER)
            }

            override fun onError(errorCode: Int) {
                AppLogger.log("Nav", "!! getNavigator error code=$errorCode (see NavigationApi.ErrorCode)")
            }
        })
    }

    /** Stop Google navigation and hand the dash back to the notification path. */
    fun stop() {
        GoogleNavSdkProvider.stopNavigation()
        BccuConnectionService.setNavProviderIfRunning(NotificationNavProvider)
    }
}
