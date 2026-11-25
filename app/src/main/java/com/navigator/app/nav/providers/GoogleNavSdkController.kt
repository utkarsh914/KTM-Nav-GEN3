package com.navigator.app.nav.providers

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.navigator.app.BuildConfig
import com.navigator.app.app.AppIntents
import com.navigator.app.ble.BccuConnectionService
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.notify.StatusNotification
import com.navigator.app.settings.AppSettings

/**
 * Orchestrates the Activity-side handshake the Navigation SDK requires (API key,
 * Terms of Service, `getNavigator`), then hands the [Navigator] to
 * [GoogleNavSdkProvider] and switches the dash pipeline over to it.
 */
object GoogleNavSdkController {

    @Volatile private var apiKeySet = false

    /** The SDK service's foreground id: the app's one status notification (shared with the bike link and the recording). */
    private const val NAV_NOTIFICATION_ID = StatusNotification.ID

    @Volatile private var navNotificationInit = false

    /**
     * Give the Nav SDK's foreground service the app's status notification. Must run once per process before
     * guidance starts; the SDK throws if initialised twice, so it's guarded and wrapped defensively.
     */
    private fun ensureNavNotification(activity: Activity) {
        if (navNotificationInit) return
        runCatching {
            val app = activity.application
            val resumeIntent = AppIntents.internal(app).apply {
                putExtra(AppIntents.EXTRA_OPEN_NAV, true)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                // The app's one notification (it shares its id with the bike link and the recording) instead of the SDK's own.
                NavigationApi.initForegroundServiceManagerProvider(app, NAV_NOTIFICATION_ID, NavLiveUpdate(app))
            } catch (e: Throwable) {
                AppLogger.log("Nav", "!! status notification unavailable (${e.javaClass.simpleName}); using the SDK default")
                NavigationApi.clearForegroundServiceManager()
                NavigationApi.initForegroundServiceManagerMessageAndIntent(
                    app, NAV_NOTIFICATION_ID, "Navigating", resumeIntent,
                )
            }
            navNotificationInit = true
        }.onFailure { AppLogger.log("Nav", "!! nav notification init failed: ${it.message}") }
    }

    /** True when a key is compiled in and Google Play services are usable. */
    fun isAvailable(context: Context): Boolean =
        BuildConfig.NAV_SDK_API_KEY.isNotBlank() &&
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** True when the in-app Nav SDK engine is both available and selected in Settings
     *  (the single source of truth for notification mirroring vs in-app guidance). */
    fun isActive(context: Context, settings: AppSettings): Boolean =
        settings.googleNavEnabled && isAvailable(context)

    /** Set the Nav SDK API key once (idempotent). Safe to call before showing the map. */
    fun ensureApiKey() {
        if (!apiKeySet && BuildConfig.NAV_SDK_API_KEY.isNotBlank()) {
            NavigationApi.setApiKey(BuildConfig.NAV_SDK_API_KEY)
            apiKeySet = true
        }
    }

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

    private val startRequests = StartRequests()

    /** A start that failed before asking for the navigator still supersedes an earlier one that is waiting for it. */
    fun cancelPendingStart() = startRequests.invalidate()

    fun startNavigation(activity: Activity, dest: NavDestination) {
        startRequests.invalidate()
        GoogleNavSdkProvider.clearStartFailure()
        if (!isAvailable(activity)) {
            AppLogger.log("Nav", "Google Nav SDK unavailable (no key or Play services); ignoring")
            GoogleNavSdkProvider.reportStartFailure(StartFailure.UNAVAILABLE)
            return
        }
        if (!apiKeySet) {
            NavigationApi.setApiKey(BuildConfig.NAV_SDK_API_KEY)
            apiKeySet = true
        }
        ensureNavNotification(activity)
        val request = startRequests.begin()
        // getNavigator(Activity, ...) shows the ToS dialog itself if not accepted.
        NavigationApi.getNavigator(activity, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(navigator: Navigator) {
                if (!startRequests.isCurrent(request)) {
                    AppLogger.log("Nav", "stale start dropped (cancelled before the navigator was ready)")
                    return
                }
                AppLogger.log("Nav", "Navigator ready - starting guidance to ${AppLogger.sensitive(dest.label)}")
                GoogleNavSdkProvider.onNavigatorReady(navigator, activity.applicationContext)
                BccuConnectionService.setNavProviderIfRunning(GoogleNavSdkProvider)
                GoogleNavSdkProvider.startNavigation(dest, dest.travelMode)
            }

            override fun onError(errorCode: Int) {
                AppLogger.log("Nav", "!! getNavigator error code=$errorCode (see NavigationApi.ErrorCode)")
                if (!startRequests.isCurrent(request)) return
                GoogleNavSdkProvider.reportStartFailure(StartFailure.NAVIGATOR_UNAVAILABLE)
            }
        })
    }

    /** Stop Google navigation and hand the dash back to the notification path. */
    fun stop() {
        startRequests.invalidate()
        GoogleNavSdkProvider.stopNavigation()
        BccuConnectionService.setNavProviderIfRunning(NotificationNavProvider)
    }
}
