package com.navigator.app.ui.feature.navigate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.theme.KtmTheme

private val place = SavedPlace(12.97, 77.59, "Toit Brewpub", "Indiranagar")

private val sample = NavigateUiState(
    tiles = WhereToTiles.build(listOf(FavoritePlace(place.copy(label = "Flat"), FavoriteSlot.HOME)), listOf(place)) { "3.2 km" },
    favorites = listOf(FavoritePlace(place, FavoriteSlot.OTHER)),
    recents = listOf(place, place.copy(label = "Cubbon Park")),
    bikeStatus = BikeStatus.Connected("KTM 7F21"),
    bike = BikePillMapper.map(BikeStatus.Connected("KTM 7F21")),
    activity = ActivityCardModel("300 m", "100 Feet Rd", "Toit", "18:30"),
)

private val noActions = NavigateBrowseActions(WhereToActions({}, {}, {}, {}, {}, {}, {}), {}, {}, {}, {}, {}, {}, { _, _ -> })

@Composable
private fun Browse(dark: Boolean, state: NavigateUiState, location: LocationState, map: MapState) = KtmTheme(darkTheme = dark) {
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF2B3A33) else Color(0xFFDDE5D0))) {
        NavigateBrowse(state, location, map, { 20f }, true, noActions, recordControls = { KtmButton("Record", {}) })
    }
}

@Preview(name = "Browse dark, trip running", widthDp = 400, heightDp = 800)
@Composable
private fun BrowseDark() = Browse(true, sample, LocationState.Ok, MapState.Ready)

@Preview(name = "Browse light, location off, map failed", widthDp = 400, heightDp = 800)
@Composable
private fun BrowseLightProblems() = Browse(false, sample.copy(activity = null), LocationState.ServicesOff, MapState.Failed)

@Preview(name = "Browse landscape", widthDp = 874, heightDp = 403)
@Composable
private fun BrowseLandscape() = Browse(true, sample, LocationState.Ok, MapState.Ready)

@Preview(name = "Bike sheet states", widthDp = 400, heightDp = 400)
@Composable
private fun BikeStatusRows() = KtmTheme(darkTheme = true) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BikeSheet(sample.copy(bikeStatus = BikeStatus.NeedsRepair("KTM 7F21")), "12:00", {}, {}, {}, {}, {})
    }
}
