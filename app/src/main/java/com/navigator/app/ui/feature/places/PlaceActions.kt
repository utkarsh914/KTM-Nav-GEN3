package com.navigator.app.ui.feature.places

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import java.util.Locale

/** What a place's ⋮ offers. */
enum class PlaceAction { Change, Directions, ShowOnMap, OpenInMaps, SetHome, SetWork, Remove }

/** The place the ⋮ is about: Home, Work, or one of the saved places. */
sealed interface PlaceTarget {
    val place: SavedPlace

    data class Slot(val slot: FavoriteSlot, override val place: SavedPlace) : PlaceTarget
    data class Saved(override val place: SavedPlace) : PlaceTarget
}

/** The pure rules of PC1/PC5: which actions a place has, in order, Remove last. */
object PlaceActions {
    /**
     * Home and Work change or go. A saved place can be routed to, or shown on the map (in the app's own maps; in mirror mode
     * Google Maps does both), and promoted to Home or Work.
     */
    fun forTarget(target: PlaceTarget, inApp: Boolean): List<PlaceAction> = when (target) {
        is PlaceTarget.Slot -> listOf(PlaceAction.Change, PlaceAction.Remove)
        is PlaceTarget.Saved -> buildList {
            if (inApp) { add(PlaceAction.Directions); add(PlaceAction.ShowOnMap) } else add(PlaceAction.OpenInMaps)
            add(PlaceAction.SetHome)
            add(PlaceAction.SetWork)
            add(PlaceAction.Remove)
        }
    }

    /** Google Maps starts guidance to [place] (mirror mode, Home or Work). */
    fun navigationUri(place: SavedPlace): Uri = "google.navigation:q=${coords(place)}".toUri()

    /** Google Maps shows [place] with its name on the pin. */
    fun mapsUri(place: SavedPlace): Uri =
        "geo:${coords(place)}?q=${coords(place)}(${Uri.encode(place.label)})".toUri()

    fun navigationIntent(place: SavedPlace): Intent = mapsIntent(navigationUri(place))
    fun showIntent(place: SavedPlace): Intent = mapsIntent(mapsUri(place))

    private fun mapsIntent(uri: Uri) = Intent(Intent.ACTION_VIEW, uri).setPackage(MAPS_PACKAGE)

    private fun coords(p: SavedPlace) = String.format(Locale.ROOT, "%.6f,%.6f", p.lat, p.lng)

    const val MAPS_PACKAGE = "com.google.android.apps.maps"
}
