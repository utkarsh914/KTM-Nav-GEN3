package com.navigator.app.ui.feature.navigate

import android.content.Context
import com.navigator.app.ble.BccuConnectionService.ConnectionState
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.ConnectionRepository
import com.navigator.app.data.connection.LinkSources
import com.navigator.app.data.places.PlacesRepository
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.PlacesStore
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.nav.providers.StartFailure
import com.navigator.app.settings.AppSettings
import com.navigator.app.trip.GuidanceSource
import com.navigator.app.trip.TripController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigateViewModelTest {
    private class Guidance : GuidanceSource {
        override val state = MutableStateFlow(NormalizedNavigationState(NavSessionState.IDLE))
        override val startFailure = MutableStateFlow<StartFailure?>(null)
        override val arrivals = emptyFlow<Unit>()
        override fun currentTarget(): Pair<Double, Double>? = null
        override fun clearStartFailure() {}
        override fun start(dest: NavDestination) {}
        override fun stop() {}
    }

    private lateinit var ctx: Context
    private lateinit var settings: SettingsRepository
    private lateinit var places: PlacesRepository
    private val guidance = Guidance()
    private val link = MutableStateFlow(ConnectionState.DISCONNECTED)
    private var rePaired = 0

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(PlacesStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(AppSettings(ctx))
        places = PlacesRepository(ctx)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): NavigateViewModel {
        val connection = ConnectionRepository(
            backgroundScope, settings,
            LinkSources(
                state = link, scanning = MutableStateFlow(false), attempt = MutableStateFlow(0),
                repairHint = MutableStateFlow(false), lastError = MutableStateFlow(null),
                deviceName = MutableStateFlow(null), bluetoothOn = MutableStateFlow(true),
            ),
            stopService = { rePaired++ }, cancelNotifications = {},
        )
        val trip = TripController(backgroundScope, guidance, MutableStateFlow(true))
        return NavigateViewModel(settings, connection, places, trip, clock = { 0L }).also { vm ->
            backgroundScope.launch { vm.state.collect { } }
        }
    }

    @Test fun tilesFollowSavedAndRecentPlacesWithoutARefresh() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        assertEquals(TileAction.SetSlot(FavoriteSlot.WORK), vm.state.value.tiles[1].action)
        val office = SavedPlace(12.1, 77.1, "Office")
        PlacesStore(ctx).saveFavorite(office, FavoriteSlot.WORK) // written by an old screen or a restore
        assertEquals(TileAction.Ride(office), vm.state.value.tiles[1].action)
        PlacesStore(ctx).addRecent(SavedPlace(13.0, 77.5, "Toit"))
        assertEquals("Toit", vm.state.value.tiles[2].place?.label)
    }

    @Test fun theFirstFrameIsAlreadyTheRealState() = runTest(UnconfinedTestDispatcher()) {
        settings.legacy.bondedDeviceAddress = "AA:00"
        settings.legacy.bondedDeviceName = "KTM3638"
        settings.refresh()
        PlacesStore(ctx).addRecent(SavedPlace(13.0, 77.5, "Toit"))
        link.value = ConnectionState.AUTHENTICATED
        val vm = vm()
        // Read before anything subscribes: no "Pair your bike" flash, no empty tiles.
        assertEquals(BikeStatus.Connected("KTM3638"), vm.state.value.bikeStatus)
        assertEquals("Toit", vm.state.value.tiles[2].place?.label)
    }

    @Test fun distancesAppearOnceThePositionIsKnown() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        PlacesStore(ctx).addRecent(SavedPlace(12.01, 77.0, "Toit"))
        assertNull(vm.state.value.tiles[2].distance)
        vm.updateOrigin(12.0 to 77.0)
        assertEquals("1.1 km", vm.state.value.tiles[2].distance)
    }

    @Test fun beepsAndMessagesAreTheStoredSettings() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        assertTrue(vm.state.value.beepsOn)
        vm.setBeeps(false)
        assertFalse(vm.state.value.beepsOn)
        assertFalse("the store itself changed", settings.legacy.turnBeepEnabled)
        vm.setMessages(false)
        assertFalse(vm.state.value.messagesOn)
        assertFalse(settings.legacy.mirrorEnabled)
    }

    @Test fun bikeStatusFollowsPairingAndTheLink() = runTest(UnconfinedTestDispatcher()) {
        val vm = vm()
        assertEquals(BikeStatus.NotPaired, vm.state.value.bikeStatus)
        assertTrue(vm.state.value.bike.opensPairing)
        settings.legacy.bondedDeviceAddress = "AA:00"
        settings.legacy.bondedDeviceName = "KTM3638"
        settings.refresh()
        link.value = ConnectionState.AUTHENTICATED
        assertEquals(BikeStatus.Connected("KTM3638"), vm.state.value.bikeStatus)
        vm.rePair()
        assertEquals(1, rePaired)
        assertEquals(BikeStatus.NotPaired, vm.state.value.bikeStatus)
    }

    @Test fun connectingWhileTheScreenIsHereGreetsForFourSeconds() = runTest(UnconfinedTestDispatcher()) {
        settings.legacy.bondedDeviceAddress = "AA:00"
        settings.legacy.userName = "Arjun"
        settings.refresh()
        val vm = vm()
        assertNull("not connected yet", vm.state.value.greeting)
        link.value = ConnectionState.CONNECTING
        link.value = ConnectionState.AUTHENTICATED
        assertEquals(GreetingUi("Arjun"), vm.state.value.greeting)
        testScheduler.advanceTimeBy(NavigateViewModel.GREETING_MS - 1)
        assertNotNull(vm.state.value.greeting)
        testScheduler.advanceTimeBy(2)
        assertNull(vm.state.value.greeting)
    }

    @Test fun beingConnectedAlreadyOrStayingConnectedDoesNotGreet() = runTest(UnconfinedTestDispatcher()) {
        settings.legacy.bondedDeviceAddress = "AA:00"
        settings.refresh()
        link.value = ConnectionState.AUTHENTICATED
        val vm = vm()
        assertNull(vm.state.value.greeting)
        settings.legacy.userName = "Sam"
        settings.refresh()
        assertNull(vm.state.value.greeting)
    }

    @Test fun theCardAppearsWhenATripIsMinimisedAndGoesWhenResumed() = runTest(UnconfinedTestDispatcher()) {
        val trip = TripController(backgroundScope, guidance, MutableStateFlow(true), clock = { 10_000L })
        val connection = ConnectionRepository(
            backgroundScope, settings,
            LinkSources(
                state = link, scanning = MutableStateFlow(false), attempt = MutableStateFlow(0),
                repairHint = MutableStateFlow(false), lastError = MutableStateFlow(null),
                deviceName = MutableStateFlow(null), bluetoothOn = MutableStateFlow(true),
            ),
            stopService = {}, cancelNotifications = {},
        )
        val vm = NavigateViewModel(settings, connection, places, trip, clock = { 0L })
        backgroundScope.launch { vm.state.collect { } }
        assertNull(vm.state.value.activity)
        trip.start(NavDestination(13.0, 77.5, "Toit"), null, null)
        guidance.state.value = NormalizedNavigationState(NavSessionState.ENROUTE, distanceToManeuverMeters = 200)
        assertNull("riding view is up", vm.state.value.activity)
        trip.setMinimised(true)
        assertNotNull(vm.state.value.activity)
        trip.setMinimised(false)
        assertNull(vm.state.value.activity)
    }
}
