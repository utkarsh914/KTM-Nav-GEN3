package com.navigator.app.ui.feature.navigate

import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.connection.DashSnapshot
import com.navigator.app.nav.DashPreview
import com.navigator.app.nav.destination.FavoritePlace
import com.navigator.app.nav.destination.FavoriteSlot
import com.navigator.app.nav.destination.SavedPlace
import com.navigator.app.nav.ktm.KtmManeuverMapping
import com.navigator.app.nav.model.NavDestination
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.model.NormalizedManeuver
import com.navigator.app.nav.model.NormalizedNavigationState
import com.navigator.app.trip.TripState
import com.navigator.app.ui.components.DashPreviewState
import com.navigator.app.ui.components.PillTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateStateTest {
    private val home = SavedPlace(12.0, 77.0, "Flat")
    private val work = SavedPlace(12.1, 77.1, "Office")
    private val last = SavedPlace(13.0, 77.5, "Toit")

    @Test fun unsetSlotsOpenThePickerAndSetSlotsRideStraightThere() {
        val tiles = WhereToTiles.build(listOf(FavoritePlace(home, FavoriteSlot.HOME)), listOf(last)) { "1 km" }
        assertEquals(listOf(TileKind.Home, TileKind.Work, TileKind.LastTrip, TileKind.PickOnMap), tiles.map { it.kind })
        assertEquals(TileAction.Ride(home), tiles[0].action)
        assertEquals("unset work", TileAction.SetSlot(FavoriteSlot.WORK), tiles[1].action)
        assertNull(tiles[1].distance)
        assertEquals(TileAction.Ride(last), tiles[2].action)
        assertEquals(TileAction.Pick, tiles[3].action)
        assertEquals("1 km", tiles[0].distance)
    }

    @Test fun withNoTripsTheLastTripTileDoesNothing() {
        val tiles = WhereToTiles.build(emptyList(), emptyList()) { null }
        assertEquals(TileAction.None, tiles[2].action)
        assertEquals(TileAction.SetSlot(FavoriteSlot.HOME), tiles[0].action)
    }

    @Test fun otherSavedPlacesDoNotFillHomeOrWork() {
        val tiles = WhereToTiles.build(listOf(FavoritePlace(home, FavoriteSlot.OTHER)), emptyList()) { null }
        assertNull(tiles[0].place)
    }

    @Test fun straightLineDistance() {
        assertEquals(0, StraightLine.meters(12.0 to 77.0, 12.0, 77.0))
        val m = StraightLine.meters(12.0 to 77.0, 12.01, 77.0)
        assertTrue("about 1.1 km north, was $m", m in 1_100..1_120)
        assertNull(StraightLine.label(null, home))
        assertNull("standing on it", StraightLine.label(12.0 to 77.0, SavedPlace(12.0, 77.0, "x")))
        assertEquals("1.1 km", StraightLine.label(12.0 to 77.0, SavedPlace(12.01, 77.0, "x")))
    }

    @Test fun bikePillTable() {
        assertEquals(BikePillUi(R.string.bike_pill_pair, null, PillTone.NotPaired, true, R.string.bike_state_not_paired), BikePillMapper.map(BikeStatus.NotPaired))
        assertEquals(PillTone.Offline, BikePillMapper.map(BikeStatus.BluetoothOff).tone)
        assertEquals(BikePillUi(R.string.bike_pill_named, "KTM3638", PillTone.Connected, false, R.string.bike_state_connected), BikePillMapper.map(BikeStatus.Connected("KTM3638")))
        assertEquals(R.string.bike_pill_generic, BikePillMapper.map(BikeStatus.Connected(null)).label)
        assertEquals(PillTone.Connecting, BikePillMapper.map(BikeStatus.Connecting("K", false)).tone)
        assertEquals(PillTone.Connecting, BikePillMapper.map(BikeStatus.Reconnecting("K", 2)).tone)
        assertEquals(R.string.bike_pill_cant_connect, BikePillMapper.map(BikeStatus.NeedsRepair("K")).label)
        assertEquals(PillTone.Offline, BikePillMapper.map(BikeStatus.Disconnected("K")).tone)
        assertFalse(BikePillMapper.map(BikeStatus.Disconnected("K")).opensPairing)
    }

    @Test fun bikeSheetStatusWords() {
        assertEquals(R.string.bike_sheet_reconnecting, BikeSheetMapper.status(BikeStatus.Reconnecting("K", 3)).title)
        assertEquals(3, BikeSheetMapper.status(BikeStatus.Reconnecting("K", 3)).arg)
        assertEquals(R.string.bike_sheet_connecting_first, BikeSheetMapper.status(BikeStatus.Connecting("K", true)).title)
        assertEquals(R.string.bike_sheet_cant_connect_hint, BikeSheetMapper.status(BikeStatus.NeedsRepair("K")).hint)
        assertNull(BikeSheetMapper.status(BikeStatus.Connected("K")).hint)
    }

    @Test fun everyBikeStateHasWordsForScreenReaders() {
        listOf(
            BikeStatus.NotPaired, BikeStatus.BluetoothOff, BikeStatus.Connected("K"), BikeStatus.Connecting("K", false),
            BikeStatus.Reconnecting("K", 1), BikeStatus.Disconnected("K"), BikeStatus.NeedsRepair("K"),
        ).forEach { assertTrue("$it", BikePillMapper.map(it).state != 0) }
        assertTrue(
            "connected and disconnected differ in words, not only in colour",
            BikePillMapper.map(BikeStatus.Connected("K")).state != BikePillMapper.map(BikeStatus.Disconnected("K")).state,
        )
    }

    @Test fun locationStateNamesTheCause() {
        assertEquals(LocationState.PermissionOff, LocationState.of(permissionGranted = false, servicesOn = false))
        assertEquals(LocationState.PermissionOff, LocationState.of(false, true))
        assertEquals(LocationState.ServicesOff, LocationState.of(true, false))
        assertEquals(LocationState.Ok, LocationState.of(true, true))
    }

    @Test fun mapPaddingFollowsTheSheetButNeverPastHalfTheScreen() {
        assertEquals(300, SheetPadding.mapBottomPx(300, 2000))
        assertEquals(1000, SheetPadding.mapBottomPx(1800, 2000))
        assertEquals(0, SheetPadding.mapBottomPx(-5, 2000))
        assertTrue(SheetPadding.controlsVisible(1000, 2000))
        assertFalse("past where the padding stops, the controls are hidden", SheetPadding.controlsVisible(1001, 2000))
        assertTrue(SheetPadding.controlsVisible(0, 0))
    }

    private val dest = NavDestination(13.0, 77.5, "Toit")
    private val guiding = NormalizedNavigationState(
        NavSessionState.ENROUTE, distanceToManeuverMeters = 300, roadName = "100 Feet Rd", remainingTimeSeconds = 600,
        preformattedEta = "18:30",
    )

    @Test fun onlyAMinimisedLiveTripGetsACard() {
        val trip = TripState(dest = dest, minimised = true)
        val card = ActivityCard.build(trip, guiding, 0L)!!
        assertEquals("300 m", card.distance)
        assertEquals("100 Feet Rd", card.road)
        assertEquals("Toit", card.destination)
        assertEquals("18:30", card.eta)
        assertNull("riding view is up", ActivityCard.build(trip.copy(minimised = false), guiding, 0L))
        assertNull("no trip", ActivityCard.build(TripState(minimised = true), guiding, 0L))
        assertNull("ended", ActivityCard.build(trip.copy(arrivalLatched = true), guiding, 0L))
        assertNull("guidance stopped", ActivityCard.build(trip, NormalizedNavigationState(NavSessionState.IDLE), 0L))
    }

    @Test fun theCardCarriesTheNextTurnGlyphForTheStrip() {
        val right = guiding.copy(maneuver = NormalizedManeuver.RIGHT)
        val card = ActivityCard.build(TripState(dest = dest, minimised = true), right, 0L)!!
        assertEquals(KtmManeuverMapping.resolveIcon(NormalizedManeuver.RIGHT), card.glyph)
    }

    @Test fun noKnownManeuverMeansNoGlyph() {
        val card = ActivityCard.build(TripState(dest = dest, minimised = true), guiding, 0L)!!
        assertNull(card.glyph)
    }

    private val idle = DashPreviewState.NotConnected("i", "i")
    private val offline = DashPreviewState.NotConnected("o", "o")
    private fun preview(s: DashPreview.State, connected: Boolean = true) =
        DashPreviewMapper.map(DashSnapshot(s), connected, "12:00", idle, offline, "Message")

    @Test fun dashPreviewShowsGuidanceThenTheBannerThenIdle() {
        assertEquals(offline, preview(DashPreview.State(guidanceOn = true, distance = "50 m"), connected = false))
        val turn = preview(DashPreview.State(guidanceOn = true, distance = "50 m", road = "Main", eta = "18:30", remaining = "4 km"))
        assertEquals(DashPreviewState.Turn("12:00", null, "50 m", "Main", "18:30", "4 km"), turn)
        assertEquals(DashPreviewState.Message("12:00", "Message", "Hi Sam!"), preview(DashPreview.State(lastBanner = "Hi Sam!")))
        assertEquals("guidance off wins over stale text", DashPreviewState.Message("12:00", "Message", "x"),
            preview(DashPreview.State(guidanceOn = false, distance = "50 m", lastBanner = "x")))
        assertEquals(idle, preview(DashPreview.State()))
    }

    @Test fun peekHeightGrowsByTheActivityRow() {
        assertTrue(WhereToMetrics.peekHeight(true) > WhereToMetrics.peekHeight(false))
    }
}
