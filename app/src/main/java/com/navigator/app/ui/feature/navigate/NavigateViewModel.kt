package com.navigator.app.ui.feature.navigate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.data.connection.DashSnapshot
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.trip.TripController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * State and actions of the Navigate browse screen: tiles, the bike, the running-trip card and the two
 * dash toggles. The planning stages (place, route, riding) are still the old screen's until B8-B10.
 */
class NavigateViewModel(
    private val settings: SettingsRepository,
    private val connection: ConnectionRepository,
    places: PlacesRepository,
    trip: TripController,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val origin = MutableStateFlow<Pair<Double, Double>?>(null)

    /** The rider's last known position, for the distance captions. */
    fun updateOrigin(position: Pair<Double, Double>?) { origin.value = position }

    private val bikePart = combine(connection.status, connection.dash) { status, dash -> status to dash }
    private val placesPart = combine(places.favorites, places.recents, origin) { f, r, o -> Triple(f, r, o) }
    private val tripPart = combine(trip.state, trip.navState) { t, nav -> ActivityCard.build(t, nav, clock()) }

    private val greeting = MutableStateFlow<GreetingUi?>(null)
    private var greetingJob: Job? = null

    init {
        viewModelScope.launch {
            var previous: BikeStatus? = null
            connection.status.collect { now ->
                // Only a connection made while the screen is here; already being connected is not news.
                if (previous != null && previous !is BikeStatus.Connected && now is BikeStatus.Connected) showGreeting()
                previous = now
            }
        }
    }

    private fun showGreeting() {
        greetingJob?.cancel()
        greetingJob = viewModelScope.launch {
            greeting.value = GreetingUi(settings.snapshot.value.userName?.trim().orEmpty())
            delay(GREETING_MS)
            greeting.value = null
        }
    }

    private fun build(
        status: BikeStatus, dash: DashSnapshot, favorites: List<FavoritePlace>, recents: List<SavedPlace>,
        position: Pair<Double, Double>?, activity: ActivityCardModel?, snap: SettingsSnapshot,
        greeting: GreetingUi? = null,
    ) = NavigateUiState(
        tiles = WhereToTiles.build(favorites, recents) { StraightLine.label(position, it) },
        favorites = favorites,
        recents = recents,
        bikeStatus = status,
        bike = BikePillMapper.map(status),
        dash = dash,
        beepsOn = snap.turnBeepEnabled,
        messagesOn = snap.mirrorEnabled,
        activity = activity,
        userName = snap.userName?.trim().orEmpty(),
        greeting = greeting,
        origin = position,
    )

    private val prefsPart = combine(settings.snapshot, greeting) { snap, hello -> snap to hello }

    val state: StateFlow<NavigateUiState> = combine(
        bikePart, placesPart, tripPart, prefsPart,
    ) { (status, dash), (favorites, recents, position), activity, (snap, hello) ->
        build(status, dash, favorites, recents, position, activity, snap, hello)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        // Right from the first frame: the real status and places, not a "Pair your bike" flash.
        build(
            connection.status.value, connection.dash.value, places.favorites.value, places.recents.value,
            null, ActivityCard.build(trip.state.value, trip.navState.value, clock()), settings.snapshot.value,
        ),
    )

    fun setBeeps(on: Boolean) = settings.update { turnBeepEnabled = on }

    fun setMessages(on: Boolean) = settings.update { mirrorEnabled = on }

    /** [message] is the test banner text, already in the rider's language. */
    fun sendTest(message: String) {
        connection.sendTestNotification(message)
        connection.sendTestTurn()
    }

    fun rePair() = connection.rePair()

    companion object {
        /** How long the welcome stays (HM8). */
        const val GREETING_MS = 4_000L

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NavigateViewModel(container.settings, container.connection, container.places, container.trip) as T
        }
    }
}
