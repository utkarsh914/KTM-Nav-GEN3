package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.ui.feature.navigate.riding.MapButtonState

/**
 * What only the riding stage opens or tracks: the trip sheet, the bike sheet, the add-a-stop sheet, the end-trip
 * confirm and the map button's state. Held outside [NavigatingStage] so a request (a notification's End trip, an
 * add-a-stop from the minimised card) can set it before the stage is on screen.
 */
internal class RidingOverlayState {
    var showTripSheet by mutableStateOf(false)
    var showTripStops by mutableStateOf(false)
    var showBike by mutableStateOf(false)
    var showEndConfirm by mutableStateOf(false)

    /** Picking the next stop on the map (SE4): the riding screen steps aside, guidance carries on underneath. */
    var pickingStop by mutableStateOf(false)
    var mapButton by mutableStateOf(MapButtonState())

    /**
     * The last real guidance of this trip (not state: written while the riding screen composes). Held here, not in the
     * stage, so coming back to the riding screen in the middle of a re-plan still has a turn to show.
     */
    var lastActive: NormalizedNavigationState? = null
    private var lastActiveTrip = -1L

    fun rememberActive(trip: Long, nav: NormalizedNavigationState, guiding: Boolean) {
        if (trip != lastActiveTrip) { lastActiveTrip = trip; lastActive = null }
        if (guiding) lastActive = nav
    }

    /**
     * The stage changed: sheets close. The end-trip confirm only outlives a change *into* the riding stage (a
     * request opens it as it brings the stage up); on any other stage (arrival, cancel) it would sit over the
     * trip summary and end a trip that is already over.
     */
    fun onStageChanged(stage: NavStage, openAddStop: Boolean) {
        showTripSheet = false
        showTripStops = openAddStop && stage == NavStage.NAVIGATING
        showBike = false
        pickingStop = false
        if (stage == NavStage.NAVIGATING) mapButton = MapButtonState() else showEndConfirm = false
    }
}
