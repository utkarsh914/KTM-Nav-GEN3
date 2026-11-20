package com.navigator.app.ui.feature.navigate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var mapButton by mutableStateOf(MapButtonState())
}
