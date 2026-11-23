package com.navigator.app.ui.shell

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * The destinations whose page is the map: it shows the shared map layer through a transparent page and moves like the
 * map (see PageTransitions). Pick place is not one: it opens on its search, an opaque page, and only turns into the map
 * inside the page, so it moves like any other page ([paintsOwnGround] still applies to it).
 */
internal fun NavDestination.isMapPage(): Boolean =
    hasRoute<Route.Navigate>() || hasRoute<Route.RideReplay>() || hasRoute<Route.TrimRide>()

/** The page paints its own ground (or none, over the map) itself: [PageHost] adds none. */
internal fun NavDestination.paintsOwnGround(): Boolean = isMapPage() || hasRoute<Route.PickPlace>()
