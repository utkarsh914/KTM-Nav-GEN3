package com.navigator.app.ui.feature.navigate.pick

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.ui.map.MapController
import com.navigator.app.ui.map.MapPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Turns a map point into the nearest street's name (null when unknown or offline). */
fun interface ReverseGeocoder {
    suspend fun streetName(point: MapPoint): String?
}

object StreetName {
    /** The most useful name of what is there: the street, else the area, else the building or place. */
    fun pick(thoroughfare: String?, subLocality: String?, locality: String?, featureName: String?): String? =
        listOf(thoroughfare, subLocality, featureName, locality)
            .firstOrNull { !it.isNullOrBlank() && !it.all(Char::isDigit) }
            ?.trim()
}

class AndroidReverseGeocoder(context: Context) : ReverseGeocoder {
    private val appContext = context.applicationContext

    override suspend fun streetName(point: MapPoint): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return suspendCancellableCoroutine { cont ->
            runCatching {
                geocoder.getFromLocation(point.lat, point.lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        val a = addresses.firstOrNull()
                        if (cont.isActive) cont.resume(a?.let { StreetName.pick(it.thoroughfare, it.subLocality, it.locality, it.featureName) })
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}

data class PickOnMapState(
    val center: MapPoint? = null,
    /** The street under the pin; null while it is being looked up or when there is none to name. */
    val street: String? = null,
    val resolving: Boolean = false,
)

/**
 * Pick on map (HM7): the pin stays fixed in the middle of the screen and the map moves under it; the
 * street under the pin is looked up each time the camera settles. [start] switches the map's camera
 * listener on, [stop] off.
 */
class PickOnMapController(
    private val scope: CoroutineScope,
    private val map: MapController,
    private val geocoder: ReverseGeocoder,
) {
    private val _state = MutableStateFlow(PickOnMapState())
    val state: StateFlow<PickOnMapState> = _state.asStateFlow()
    private var lookup: Job? = null
    private var collector: Job? = null

    fun start() {
        collector?.cancel()
        map.setCameraIdleListening(true)
        map.cameraCenter()?.let(::moved)
        collector = scope.launch { map.cameraIdle.collect(::moved) }
    }

    fun stop() {
        collector?.cancel()
        lookup?.cancel()
        map.setCameraIdleListening(false)
    }

    private fun moved(point: MapPoint) {
        // The old street belongs to the old point: forget it so Use never names the new point after it.
        _state.update { it.copy(center = point, street = null, resolving = true) }
        lookup?.cancel()
        lookup = scope.launch {
            val name = geocoder.streetName(point)
            _state.update { it.copy(street = name, resolving = false) }
        }
    }

    /** The place for the point under the pin; [fallbackLabel] names it when no street was found. */
    fun use(fallbackLabel: String): SavedPlace? {
        val s = _state.value
        val c = s.center ?: return null
        return SavedPlace(c.lat, c.lng, s.street ?: fallbackLabel)
    }
}
