package com.navigator.app.nav

import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.model.TravelMode
import kotlinx.coroutines.flow.StateFlow

/**
 * A source of [NormalizedNavigationState]. Providers are swappable at runtime;
 * the KTM protocol layer never sees which one is active.
 *
 * - [com.navigator.app.nav.providers.NotificationNavProvider] is passive
 *   (mirrors another nav app's notifications).
 * - `GoogleNavSdkProvider` (and a future `RoutesApiProvider`) own the session
 *   and additionally implement [RoutingNavigationProvider].
 */
interface NavigationProvider {
    /** Stable identifier for logging / settings (e.g. "notification", "google_nav_sdk"). */
    val id: String

    /** The latest normalised navigation state; starts at [NormalizedNavigationState.IDLE]. */
    val state: StateFlow<NormalizedNavigationState>

    /** Begin producing state (register listeners, bind services, etc.). */
    fun attach()

    /** Stop producing state and release resources. */
    fun detach()
}

/**
 * A provider that OWNS the navigation session and can be told where to go
 * (Google Nav SDK now; Routes API later).
 */
interface RoutingNavigationProvider : NavigationProvider {
    fun startNavigation(dest: NavDestination, mode: TravelMode)
    fun stopNavigation()
}
