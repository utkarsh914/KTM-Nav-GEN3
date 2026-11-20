package com.navigator.app.ui.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.navigator.app.R
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.ui.theme.KtmIcons
import kotlinx.serialization.Serializable

/** Every destination of the app. Arguments are part of the type, so a call site can't get them wrong. */
@Serializable
sealed interface Route {
    @Serializable data object Onboarding : Route

    /**
     * [forced] is first-run pairing (no way back); [toHome] lands on Navigate afterwards instead of
     * returning to the screen that opened it.
     */
    @Serializable data class Pairing(val forced: Boolean = false, val toHome: Boolean = false) : Route

    @Serializable data object Navigate : Route
    @Serializable data object Rides : Route
    @Serializable data object Places : Route
    @Serializable data object Settings : Route

    @Serializable data object Logs : Route
    @Serializable data object SymbolTest : Route
    @Serializable data object TurnCalibration : Route
    @Serializable data class RideReplay(val rideId: String) : Route
    @Serializable data class EditRide(val rideId: String) : Route
    @Serializable data class TrimRide(val rideId: String) : Route
    @Serializable data class PickPlace(val slot: FavoriteSlot) : Route
    @Serializable data class MergeRides(val rideIds: List<String>) : Route
}

/** The four top-level destinations of the tab bar / rail. */
enum class Tab(val route: Route, @StringRes val label: Int, val icon: ImageVector) {
    Navigate(Route.Navigate, R.string.tab_navigate, KtmIcons.Navigation),
    Rides(Route.Rides, R.string.tab_rides, KtmIcons.Route),
    Places(Route.Places, R.string.tab_places, KtmIcons.Bookmark),
    Settings(Route.Settings, R.string.tab_settings, KtmIcons.Settings),
}

fun NavDestination?.tab(): Tab? = this?.let { d ->
    when {
        d.hasRoute<Route.Navigate>() -> Tab.Navigate
        d.hasRoute<Route.Rides>() -> Tab.Rides
        d.hasRoute<Route.Places>() -> Tab.Places
        d.hasRoute<Route.Settings>() -> Tab.Settings
        else -> null
    }
}
