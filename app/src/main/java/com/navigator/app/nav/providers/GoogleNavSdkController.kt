package com.navigator.app.nav.providers

import android.app.Activity
import android.content.Context
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

    /** True when a key is compiled in and Google Play services are usable. */
    fun isAvailable(context: Context): Boolean =
        BuildConfig.NAV_SDK_API_KEY.isNotBlank() &&
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Phase-6 test entry point: navigate to Silk Board Junction. */
    fun startTest(activity: Activity) = startNavigation(activity, SILK_BOARD)

    fun startNavigation(activity: Activity, dest: NavDestination) {
        if (!isAvailable(activity)) {
            AppLogger.log("Nav", "Google Nav SDK unavailable (no key or Play services); ignoring")
            return
        }
        if (!apiKeySet) {
            NavigationApi.setApiKey(BuildConfig.NAV_SDK_API_KEY)
            apiKeySet = true
        }
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
