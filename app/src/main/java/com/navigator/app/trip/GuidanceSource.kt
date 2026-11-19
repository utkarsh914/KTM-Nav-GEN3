package com.navigator.app.trip

import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.nav.providers.GoogleNavSdkProvider
import com.navigator.app.nav.providers.StartFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** What [TripController] needs from the guidance engine, so it can be tested with fake flows. */
interface GuidanceSource {
    val state: StateFlow<NormalizedNavigationState>
    val startFailure: StateFlow<StartFailure?>

    /** One event per arrival; the state flow is conflated and can lose an ARRIVED. */
    val arrivals: Flow<Unit>

    /** Where guidance is heading right now (the next waypoint), or null when unknown. */
    fun currentTarget(): Pair<Double, Double>?
    fun clearStartFailure()
    fun start(dest: NavDestination)
    fun stop()
}

/** The in-app engine: the Navigation SDK behind [GoogleNavSdkProvider] and [GoogleNavSdkController]. */
class SdkGuidanceSource(private val activity: () -> android.app.Activity?) : GuidanceSource {
    override val state get() = GoogleNavSdkProvider.state
    override val startFailure get() = GoogleNavSdkProvider.startFailure
    override val arrivals: Flow<Unit> = GoogleNavSdkProvider.tripEnded.map { }

    override fun currentTarget() = GoogleNavSdkProvider.currentTarget()
    override fun clearStartFailure() = GoogleNavSdkProvider.clearStartFailure()

    override fun start(dest: NavDestination) {
        val host = activity()
        if (host == null) {
            // getNavigator needs an Activity (it may show the terms dialog), so a start with the app gone fails visibly.
            AppLogger.log("Nav", "!! no activity to start guidance from")
            GoogleNavSdkProvider.reportStartFailure(StartFailure.NAVIGATOR_UNAVAILABLE)
            return
        }
        GoogleNavSdkController.startNavigation(host, dest)
    }

    override fun stop() = GoogleNavSdkController.stop()
}
