package com.navigator.app.ui.feature.navigate.search

import android.content.Context
import com.navigator.app.nav.destination.PlaceCategory
import com.navigator.app.nav.destination.PlaceLocation
import com.navigator.app.nav.destination.PlacesClient

/** The network lookups the search needs, so the view model can be tested with a fake. */
interface PlacesApi {
    suspend fun autocomplete(input: String, sessionToken: String, origin: Pair<Double, Double>?): PlacesClient.AutocompleteResult
    suspend fun details(placeId: String, sessionToken: String): PlaceLocation?
    suspend fun category(category: PlaceCategory, origin: Pair<Double, Double>?, route: List<Pair<Double, Double>>?): PlacesClient.CategorySearch
}

class PlacesClientApi(context: Context) : PlacesApi {
    private val auth = PlacesClient.androidAuth(context)

    override suspend fun autocomplete(input: String, sessionToken: String, origin: Pair<Double, Double>?) =
        PlacesClient.autocompleteResult(input, sessionToken, auth, origin?.first, origin?.second)

    override suspend fun details(placeId: String, sessionToken: String) = PlacesClient.details(placeId, sessionToken, auth)

    override suspend fun category(category: PlaceCategory, origin: Pair<Double, Double>?, route: List<Pair<Double, Double>>?) =
        PlacesClient.searchCategory(category, auth, origin, route)
}
